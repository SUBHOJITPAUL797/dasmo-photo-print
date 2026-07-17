package com.example.util

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import java.io.InputStream

object BitmapUtils {
    fun getOrientation(context: Context, uri: Uri): Int {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val exif = ExifInterface(inputStream)
                return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
        return 0
    }

    fun loadScaledBitmap(context: Context, uri: Uri, maxDim: Int = 2048): Bitmap? {
        try {
            var inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            val srcWidth = options.outWidth
            val srcHeight = options.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return null

            var sampleSize = 1
            while (srcWidth / sampleSize > maxDim || srcHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val decoded = BitmapFactory.decodeStream(inputStream, null, decodeOptions) ?: return null
            inputStream.close()

            // Handle rotation
            val rotation = getOrientation(context, uri)
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated != decoded) {
                    decoded.recycle()
                }
                return rotated
            }
            return decoded
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun createJointBitmap(
        bitmapA: Bitmap,
        bitmapB: Bitmap,
        jointWidthCm: Float,
        jointHeightCm: Float,
        splitRatio: Float = 0.5f,
        dividerEnabled: Boolean = false,
        dividerWidthPt: Float = 0.5f,
        dividerColor: Int = 0xFF000000.toInt()
    ): Bitmap {
        // We will construct this at 300 DPI:
        // 300 points per inch, and 1 inch is 2.54 cm.
        // Let's compute pixel sizes for layout.
        val dpi = 300
        val dpmm = dpi / 25.4f
        val pixelWidth = (jointWidthCm * 10f * dpmm).toInt()
        val pixelHeight = (jointHeightCm * 10f * dpmm).toInt()

        val composited = Bitmap.createBitmap(pixelWidth, pixelHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composited)
        canvas.drawColor(Color.WHITE)

        val splitPx = (pixelWidth * splitRatio).toInt()

        val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }

        // Draw Bitmap A on the left (center-crop scale)
        val dstRectA = RectF(0f, 0f, splitPx.toFloat(), pixelHeight.toFloat())
        drawWithCenterCrop(canvas, bitmapA, dstRectA, paint)

        // Draw Bitmap B on the right (center-crop scale)
        val dstRectB = RectF(splitPx.toFloat(), 0f, pixelWidth.toFloat(), pixelHeight.toFloat())
        drawWithCenterCrop(canvas, bitmapB, dstRectB, paint)

        // Draw divider
        if (dividerEnabled) {
            // divider thickness in pt to pixels:
            // 72 pt = 1 inch = 300 pixels
            val dividerPx = dividerWidthPt * (dpi / 72f)
            val dividerPaint = Paint().apply {
                color = dividerColor
                style = Paint.Style.STROKE
                strokeWidth = dividerPx
            }
            canvas.drawLine(splitPx.toFloat(), 0f, splitPx.toFloat(), pixelHeight.toFloat(), dividerPaint)
        }

        return composited
    }

    fun createIdCardBitmap(
        bitmapA: Bitmap, // Front
        bitmapB: Bitmap, // Back
        cardWidthCm: Float,
        cardHeightCm: Float,
        arrangement: String = "HORIZONTAL",
        gapCm: Float = 0.2f
    ): Bitmap {
        val dpi = 300
        val dpmm = dpi / 25.4f
        val pixelCardWidth = (cardWidthCm * 10f * dpmm).toInt()
        val pixelCardHeight = (cardHeightCm * 10f * dpmm).toInt()
        val pixelGap = (gapCm * 10f * dpmm).toInt()

        val pixelWidth: Int
        val pixelHeight: Int

        if (arrangement == "HORIZONTAL") {
            pixelWidth = pixelCardWidth * 2 + pixelGap
            pixelHeight = pixelCardHeight
        } else {
            pixelWidth = pixelCardWidth
            pixelHeight = pixelCardHeight * 2 + pixelGap
        }

        val composited = Bitmap.createBitmap(pixelWidth, pixelHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composited)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }

        if (arrangement == "HORIZONTAL") {
            // Left half (Front)
            val dstRectA = RectF(0f, 0f, pixelCardWidth.toFloat(), pixelCardHeight.toFloat())
            drawWithCenterCrop(canvas, bitmapA, dstRectA, paint)

            // Right half (Back)
            val dstRectB = RectF((pixelCardWidth + pixelGap).toFloat(), 0f, (pixelCardWidth * 2 + pixelGap).toFloat(), pixelCardHeight.toFloat())
            drawWithCenterCrop(canvas, bitmapB, dstRectB, paint)

            // Draw a vertical dashed cutting line in the gap if we want to show a guide
            val dividerPaint = Paint().apply {
                color = 0xFFCCCCCC.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2f
                pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            }
            val middleX = pixelCardWidth + pixelGap / 2f
            canvas.drawLine(middleX, 0f, middleX, pixelHeight.toFloat(), dividerPaint)
        } else {
            // Top half (Front)
            val dstRectA = RectF(0f, 0f, pixelCardWidth.toFloat(), pixelCardHeight.toFloat())
            drawWithCenterCrop(canvas, bitmapA, dstRectA, paint)

            // Bottom half (Back)
            val dstRectB = RectF(0f, (pixelCardHeight + pixelGap).toFloat(), pixelCardWidth.toFloat(), (pixelCardHeight * 2 + pixelGap).toFloat())
            drawWithCenterCrop(canvas, bitmapB, dstRectB, paint)

            // Draw a horizontal dashed cutting line in the gap if we want to show a guide
            val dividerPaint = Paint().apply {
                color = 0xFFCCCCCC.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2f
                pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            }
            val middleY = pixelCardHeight + pixelGap / 2f
            canvas.drawLine(0f, middleY, pixelWidth.toFloat(), middleY, dividerPaint)
        }

        return composited
    }

    private fun drawWithCenterCrop(canvas: Canvas, srcBitmap: Bitmap, dstRect: RectF, paint: Paint) {
        val srcW = srcBitmap.width.toFloat()
        val srcH = srcBitmap.height.toFloat()
        val dstW = dstRect.width()
        val dstH = dstRect.height()

        if (srcW <= 0f || srcH <= 0f || dstW <= 0f || dstH <= 0f) return

        val srcRatio = srcW / srcH
        val dstRatio = dstW / dstH

        val cropRect: Rect
        if (srcRatio > dstRatio) {
            // src is wider than dst -> crop horizontally (left/right off)
            val newWidth = srcH * dstRatio
            val left = ((srcW - newWidth) / 2f).toInt()
            cropRect = Rect(left, 0, (left + newWidth).toInt(), srcH.toInt())
        } else {
            // src is taller than dst -> crop vertically (top/bottom off)
            val newHeight = srcW / dstRatio
            val top = ((srcH - newHeight) / 2f).toInt()
            cropRect = Rect(0, top, srcW.toInt(), (top + newHeight).toInt())
        }

        canvas.drawBitmap(srcBitmap, cropRect, dstRect, paint)
    }

    /**
     * Replaces background color in the given bitmap that matches colorToReplace
     * within a specified tolerance using a spatially connected BFS flood-fill and feathering.
     * This isolates color adjustments strictly to background regions, preventing leakage
     * onto face, hair, or body.
     */
    fun replaceBackgroundColor(
        src: Bitmap,
        colorToReplace: Int,
        replacementColor: Int,
        tolerance: Float, // 0.0f to 1.0f
        feather: Float = 0.15f, // soft edge feathering factor
        seedX: Int = -1,
        seedY: Int = -1
    ): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val targetR = Color.red(colorToReplace)
        val targetG = Color.green(colorToReplace)
        val targetB = Color.blue(colorToReplace)

        val repR = Color.red(replacementColor)
        val repG = Color.green(replacementColor)
        val repB = Color.blue(replacementColor)

        // Max possible Euclidean distance is sqrt(255^2 + 255^2 + 255^2) => 441.67
        val maxDist = 441.673f
        val baseTol = tolerance * maxDist
        val featherWidth = feather * maxDist
        val minTol = (baseTol - featherWidth).coerceAtLeast(0f)

        val visited = BooleanArray(width * height)
        val queue = IntArray(width * height)
        var head = 0
        var tail = 0

        // Fast helper to check color distance
        fun isColorMatch(p: Int, threshold: Float): Boolean {
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            val dist = Math.sqrt(
                ((r - targetR) * (r - targetR) +
                        (g - targetG) * (g - targetG) +
                        (b - targetB) * (b - targetB)).toDouble()
            ).toFloat()
            return dist <= threshold
        }

        val seeds = java.util.ArrayList<Pair<Int, Int>>()

        // 1. Explicit seed point (dynamically scaling if unspecified)
        val actualSeedX = if (seedX in 0 until width) seedX else (width / 50).coerceAtLeast(5)
        val actualSeedY = if (seedY in 0 until height) seedY else (height / 50).coerceAtLeast(5)
        if (actualSeedX in 0 until width && actualSeedY in 0 until height) {
            seeds.add(Pair(actualSeedX, actualSeedY))
        }

        // 2. Corner/Edge seeds (to ensure multi-sided backgrounds split by head/body are cleared)
        val candidateSeeds = listOf(
            Pair(5, 5),
            Pair(width - 6, 5),
            Pair(5, height - 6),
            Pair(width - 6, height - 6),
            Pair(width / 4, 5),
            Pair(3 * width / 4, 5),
            Pair(5, height / 2),
            Pair(width - 6, height / 2)
        )
        for (cand in candidateSeeds) {
            val cx = cand.first
            val cy = cand.second
            if (cx in 0 until width && cy in 0 until height) {
                val p = pixels[cy * width + cx]
                if (isColorMatch(p, baseTol)) {
                    seeds.add(cand)
                }
            }
        }

        // Initialize queue with all unique seeds
        for (seed in seeds) {
            val idx = seed.second * width + seed.first
            if (!visited[idx]) {
                visited[idx] = true
                queue[tail++] = idx
            }
        }

        // BFS traversal directions
        val dx = intArrayOf(0, 0, 1, -1)
        val dy = intArrayOf(1, -1, 0, 0)

        while (head < tail) {
            val currIdx = queue[head++]
            val cx = currIdx % width
            val cy = currIdx / width

            for (dir in 0 until 4) {
                val nx = cx + dx[dir]
                val ny = cy + dy[dir]
                if (nx in 0 until width && ny in 0 until height) {
                    val nIdx = ny * width + nx
                    if (!visited[nIdx]) {
                        val p = pixels[nIdx]
                        if (isColorMatch(p, baseTol)) {
                            visited[nIdx] = true
                            queue[tail++] = nIdx
                        }
                    }
                }
            }
        }

        // Apply replacement or blending strictly on visited pixels
        for (i in pixels.indices) {
            if (visited[i]) {
                val p = pixels[i]
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)

                val dist = Math.sqrt(
                    ((r - targetR) * (r - targetR) +
                            (g - targetG) * (g - targetG) +
                            (b - targetB) * (b - targetB)).toDouble()
                ).toFloat()

                if (dist <= minTol) {
                    pixels[i] = Color.argb(255, repR, repG, repB)
                } else if (dist < baseTol) {
                    val denom = if (baseTol > minTol) (baseTol - minTol) else 1f
                    val ratio = (dist - minTol) / denom
                    val finalR = (repR + ratio * (r - repR)).toInt().coerceIn(0, 255)
                    val finalG = (repG + ratio * (g - repG)).toInt().coerceIn(0, 255)
                    val finalB = (repB + ratio * (b - repB)).toInt().coerceIn(0, 255)
                    pixels[i] = Color.argb(255, finalR, finalG, finalB)
                }
            } else {
                val p = pixels[i]
                if (Color.alpha(p) == 0) {
                    pixels[i] = Color.argb(255, repR, repG, repB)
                }
            }
        }

        val out = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    /**
     * Applies high-performance hardware-accelerated studio adjustments for
     * brightness (offset), contrast (gain), and saturation to a bitmap.
     */
    fun applyStudioFilters(src: Bitmap, brightness: Float, contrast: Float, saturation: Float, isBlackAndWhite: Boolean = false): Bitmap {
        val b = brightness * 255f
        val c = contrast
        
        val satMatrix = android.graphics.ColorMatrix().apply { setSaturation(if (isBlackAndWhite) 0f else saturation) }
        
        val matrixValues = floatArrayOf(
            c, 0f, 0f, 0f, 128f * (1f - c) + b,
            0f, c, 0f, 0f, 128f * (1f - c) + b,
            0f, 0f, c, 0f, 128f * (1f - c) + b,
            0f, 0f, 0f, 1f, 0f
        )
        val contrastBrightnessMatrix = ColorMatrix(matrixValues)
        
        val finalMatrix = ColorMatrix()
        finalMatrix.setConcat(contrastBrightnessMatrix, satMatrix)
        
        val out = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            colorFilter = ColorMatrixColorFilter(finalMatrix)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }
}
