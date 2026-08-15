package com.example.domain.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.domain.model.LayoutSettings
import com.example.domain.model.PageLayout
import com.example.domain.model.UnitSizeCm

object PdfExporter {
    fun export(
        context: Context,
        pages: List<PageLayout>,
        croppedBitmap: Bitmap,
        unitSize: UnitSizeCm,
        settings: LayoutSettings,
        outputUri: Uri,
        mode: String = "SINGLE",
        croppedBitmapA: Bitmap? = null,
        croppedBitmapB: Bitmap? = null,
        quantityA: Int = 0
    ): Boolean {
        val pdfDocument = PdfDocument()
        val CM_TO_PT = 28.3465f

        // 1. Resample bitmap once to target DPI resolution
        val targetPxW = (unitSize.widthCm * settings.dpi / 2.54f).toInt().coerceAtLeast(1)
        val targetPxH = (unitSize.heightCm * settings.dpi / 2.54f).toInt().coerceAtLeast(1)

        val resampledBitmap = try {
            Bitmap.createScaledBitmap(croppedBitmap, targetPxW, targetPxH, true)
        } catch (e: Exception) {
            e.printStackTrace()
            croppedBitmap
        }

        val resampledBitmapA = croppedBitmapA?.let {
            try {
                Bitmap.createScaledBitmap(it, targetPxW, targetPxH, true)
            } catch (e: Exception) {
                e.printStackTrace()
                it
            }
        }

        val resampledBitmapB = croppedBitmapB?.let {
            try {
                Bitmap.createScaledBitmap(it, targetPxW, targetPxH, true)
            } catch (e: Exception) {
                e.printStackTrace()
                it
            }
        }

        val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = settings.cuttingGuideColor
            style = Paint.Style.STROKE
            strokeWidth = settings.cuttingGuideThicknessPt
            pathEffect = if (settings.cuttingGuideStyle == "dashed") DashPathEffect(floatArrayOf(4f, 4f), 0f) else null
        }

        var outputStream: java.io.OutputStream? = null
        try {
            for (pageLayout in pages) {
                // Determine layout orientation. If wider than high, it is a landscape layout.
                val isLayoutLandscape = settings.pageWidthCm > settings.pageHeightCm

                // Physical PDF dimensions are ALWAYS portrait to match physical paper and prevent printer clipping
                val pWidthPt = if (isLayoutLandscape) (settings.pageHeightCm * CM_TO_PT).toInt() else (settings.pageWidthCm * CM_TO_PT).toInt()
                val pHeightPt = if (isLayoutLandscape) (settings.pageWidthCm * CM_TO_PT).toInt() else (settings.pageHeightCm * CM_TO_PT).toInt()

                val pageInfo = PdfDocument.PageInfo.Builder(pWidthPt, pHeightPt, pageLayout.pageIndex).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                if (isLayoutLandscape) {
                    // Rotate the landscape coordinate system 90 degrees clockwise to fit onto the portrait A4 canvas
                    canvas.translate(pWidthPt.toFloat(), 0f)
                    canvas.rotate(90f)
                }

                // Cumulative placements count before this page
                var placementsCountBefore = 0
                for (pIdx in 0 until pageLayout.pageIndex) {
                    placementsCountBefore += pages[pIdx].placements.size
                }

                for ((placementIndex, placement) in pageLayout.placements.withIndex()) {
                    val xPt = placement.xCm * CM_TO_PT
                    val yPt = placement.yCm * CM_TO_PT

                    val isCellRotated = if (placement.widthCm > 0f) placement.isRotated else pageLayout.isRotated
                    val cellW = if (placement.widthCm > 0f) placement.widthCm else pageLayout.cellWidthCm
                    val cellH = if (placement.heightCm > 0f) placement.heightCm else pageLayout.cellHeightCm

                    val cellWPt = cellW * CM_TO_PT
                    val cellHPt = cellH * CM_TO_PT

                    val globalIndex = placementsCountBefore + placementIndex
                    val activeBmp = if (mode == "MULTI_PERSON") {
                        if (globalIndex < quantityA) (resampledBitmapA ?: resampledBitmap) else (resampledBitmapB ?: resampledBitmap)
                    } else if (mode == "BATCH_PAPER_SAVER") {
                        croppedBitmap
                    } else {
                        resampledBitmap
                    }

                    if (isCellRotated) {
                        canvas.save()
                        canvas.translate(xPt + cellWPt / 2f, yPt + cellHPt / 2f)
                        canvas.rotate(90f)
                        val dstRect = RectF(-cellHPt / 2f, -cellWPt / 2f, cellHPt / 2f, cellWPt / 2f)
                        canvas.drawBitmap(activeBmp, null, dstRect, paint)
                        canvas.restore()
                    } else {
                        val dstRect = RectF(xPt, yPt, xPt + cellWPt, yPt + cellHPt)
                        canvas.drawBitmap(activeBmp, null, dstRect, paint)
                    }

                    // Render cutting guides if enabled
                    if (settings.cuttingGuidesEnabled) {
                        canvas.drawRect(xPt, yPt, xPt + cellWPt, yPt + cellHPt, borderPaint)
                    }
                }

                pdfDocument.finishPage(page)
            }

            outputStream = context.contentResolver.openOutputStream(outputUri)
            if (outputStream != null) {
                pdfDocument.writeTo(outputStream)
                return true
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try { outputStream?.close() } catch (e: Exception) {}
            pdfDocument.close()
            // Recycle resampled only if we created a new scaled copy
            if (resampledBitmap != croppedBitmap) {
                resampledBitmap.recycle()
            }
            if (resampledBitmapA != null && resampledBitmapA != croppedBitmapA) {
                resampledBitmapA.recycle()
            }
            if (resampledBitmapB != null && resampledBitmapB != croppedBitmapB) {
                resampledBitmapB.recycle()
            }
        }
    }
}
