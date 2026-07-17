package com.example.util

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.FileInputStream
import java.io.FileOutputStream

class PdfPrintAdapter(private val context: Context, private val pdfUri: Uri) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder("PassportPhotos.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()

        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onWriteCancelled()
            return
        }

        var input: FileInputStream? = null
        var output: FileOutputStream? = null
        var pfdInput: ParcelFileDescriptor? = null

        try {
            pfdInput = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: throw Exception("Failed to open PDF descriptor")
            input = FileInputStream(pfdInput.fileDescriptor)
            output = FileOutputStream(destination?.fileDescriptor)

            val buf = ByteArray(16384)
            var bytesRead: Int
            while (input.read(buf).also { bytesRead = it } >= 0) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }
                output.write(buf, 0, bytesRead)
            }

            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            e.printStackTrace()
            callback?.onWriteFailed(e.toString())
        } finally {
            try { input?.close() } catch (ex: Exception) {}
            try { output?.close() } catch (ex: Exception) {}
            try { pfdInput?.close() } catch (ex: Exception) {}
        }
    }
}

object PrintUtils {
    fun printPdf(context: Context, pdfUri: Uri, jobName: String = "DASMO PHOTO PRINT", isLandscape: Boolean = false) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
            // Since our compiled PDF files are now always physically Portrait oriented (with landscape content
            // drawn rotated inside it), we always print as ISO_A4 Portrait to prevent Android Print Spooler
            // from rotating, squeezing, or cutting off the pages.
            val mediaSize = PrintAttributes.MediaSize.ISO_A4.asPortrait()
            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(mediaSize)
                .setResolution(PrintAttributes.Resolution("300dpi", "300 DPI", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
            printManager.print(jobName, PdfPrintAdapter(context, pdfUri), printAttributes)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
