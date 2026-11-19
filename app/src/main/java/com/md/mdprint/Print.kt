package com.md.mdprint

import android.os.Bundle
import android.os.CancellationSignal
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Hands a ready PDF to Android's system print framework (works with every installed print service). */
class FilePrintAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {
    override fun onLayout(
        old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?,
        callback: LayoutResultCallback, extras: Bundle?
    ) {
        if (cancel?.isCanceled == true) { callback.onLayoutCancelled(); return }
        val info = PrintDocumentInfo.Builder(name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(runCatching { Pdf.pageCount(file) }.getOrDefault(PrintDocumentInfo.PAGE_COUNT_UNKNOWN))
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        try {
            FileInputStream(file).use { i -> FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}

fun PrintSettings.toAttributes(): PrintAttributes {
    val base = when (paper) {
        Paper.A4 -> PrintAttributes.MediaSize.ISO_A4
        Paper.LETTER -> PrintAttributes.MediaSize.NA_LETTER
        Paper.LEGAL -> PrintAttributes.MediaSize.NA_LEGAL
        Paper.A5 -> PrintAttributes.MediaSize.ISO_A5
        Paper.PHOTO -> PrintAttributes.MediaSize.NA_INDEX_4X6
    }
    val media = if (orientation == Orient.LANDSCAPE) base.asLandscape() else base.asPortrait()
    return PrintAttributes.Builder()
        .setMediaSize(media)
        .setColorMode(if (color) PrintAttributes.COLOR_MODE_COLOR else PrintAttributes.COLOR_MODE_MONOCHROME)
        .setDuplexMode(when (sides) {
            Sides.ONE -> PrintAttributes.DUPLEX_MODE_NONE
            Sides.LONG -> PrintAttributes.DUPLEX_MODE_LONG_EDGE
            Sides.SHORT -> PrintAttributes.DUPLEX_MODE_SHORT_EDGE
        })
        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
        .build()
}
