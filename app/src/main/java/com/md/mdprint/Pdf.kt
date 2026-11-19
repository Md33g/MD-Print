package com.md.mdprint

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Everything is normalised to a PDF before printing. */
object Pdf {
    private fun dir(ctx: Context) = File(ctx.cacheDir, "docs").also { it.mkdirs() }

    fun cleanup(ctx: Context) {
        val cutoff = System.currentTimeMillis() - 24L * 3600 * 1000
        dir(ctx).listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    private fun displayName(ctx: Context, uri: Uri): String {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.let { n -> return n }
        }
        return uri.lastPathSegment ?: "Document"
    }

    fun docFromUri(ctx: Context, uri: Uri): Doc {
        val mime = ctx.contentResolver.getType(uri) ?: ""
        val name = displayName(ctx, uri)
        val lower = name.lowercase()
        val kind = when {
            mime == "application/pdf" || lower.endsWith(".pdf") -> "PDF"
            mime.startsWith("image/") -> "Photo"
            mime.startsWith("text/") || lower.endsWith(".txt") -> "Text"
            else -> error("Unsupported file type")
        }
        val f = File(dir(ctx), "src_${System.nanoTime()}")
        ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        return if (kind == "Text") {
            val t = f.readText().take(2_000_000); f.delete(); Doc(name, kind, text = t)
        } else Doc(name, kind, file = f)
    }

    fun prepare(ctx: Context, d: Doc, s: PrintSettings): File = when (d.kind) {
        "PDF" -> d.file!!
        "Photo" -> imageToPdf(ctx, d.file!!, s)
        else -> textToPdf(ctx, d.text ?: "", s)
    }

    private fun out(ctx: Context) = File(dir(ctx), "out_${System.nanoTime()}.pdf")

    private fun decode(f: File, maxDim: Int = 2600): Bitmap {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var sample = 1
        while (max(o.outWidth, o.outHeight) / sample > maxDim) sample *= 2
        var b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Cannot decode image")
        val rot = runCatching {
            when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (rot != 0f) {
            val r = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rot) }, true)
            if (r !== b) b.recycle()
            b = r
        }
        return b
    }

    private fun pageSize(s: PrintSettings, landscape: Boolean) =
        if (landscape) s.paper.hPt to s.paper.wPt else s.paper.wPt to s.paper.hPt

    private fun imageToPdf(ctx: Context, src: File, s: PrintSettings): File {
        val bmp = decode(src)
        val land = when (s.orientation) {
            Orient.LANDSCAPE -> true
            Orient.PORTRAIT -> false
            Orient.AUTO -> bmp.width > bmp.height
        }
        val (pw, ph) = pageSize(s, land)
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, 1).create())
        val m = if (s.paper == Paper.PHOTO) 0f else 18f
        val scale = min((pw - 2 * m) / bmp.width, (ph - 2 * m) / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        val l = (pw - w) / 2f
        val t = (ph - h) / 2f
        page.canvas.drawBitmap(bmp, null, RectF(l, t, l + w, t + h), Paint(Paint.FILTER_BITMAP_FLAG))
        doc.finishPage(page)
        val f = out(ctx)
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        bmp.recycle()
        return f
    }

    private fun textToPdf(ctx: Context, text: String, s: PrintSettings): File {
        val (pw, ph) = pageSize(s, s.orientation == Orient.LANDSCAPE)
        val margin = 40
        val contentW = pw - 2 * margin
        val contentH = ph - 2 * margin
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f }
        val str = if (text.isBlank()) " " else text
        val layout = StaticLayout.Builder.obtain(str, 0, str.length, paint, contentW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(2f, 1f).build()
        val doc = PdfDocument()
        var line = 0
        var no = 1
        while (line < layout.lineCount) {
            val top = layout.getLineTop(line)
            var end = line
            while (end < layout.lineCount && layout.getLineBottom(end) - top <= contentH) end++
            if (end == line) end = line + 1
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, no).create())
            val c = page.canvas
            c.save()
            c.clipRect(margin, margin, pw - margin, margin + contentH)
            c.translate(margin.toFloat(), (margin - top).toFloat())
            layout.draw(c)
            c.restore()
            doc.finishPage(page)
            line = end
            no++
        }
        val f = out(ctx)
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    fun pageCount(f: File): Int {
        val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = PdfRenderer(pfd)
        try { return r.pageCount } finally { r.close(); pfd.close() }
    }

    /** Renders a page. [width] receives the page width in points and returns the bitmap width in px. */
    fun render(f: File, index: Int, forPrint: Boolean = false, width: (Int) -> Int): Bitmap {
        val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = PdfRenderer(pfd)
        try {
            val p = r.openPage(index)
            try {
                val w = width(p.width).coerceAtLeast(1)
                val h = (w.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                b.eraseColor(Color.WHITE)
                p.render(b, null, null,
                    if (forPrint) PdfRenderer.Page.RENDER_MODE_FOR_PRINT else PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return b
            } finally { p.close() }
        } finally { r.close(); pfd.close() }
    }

    fun jpeg(f: File, index: Int): ByteArray {
        val b = render(f, index, true) { max(it * 200 / 72, 1600) }
        val o = ByteArrayOutputStream()
        b.compress(Bitmap.CompressFormat.JPEG, 92, o)
        b.recycle()
        return o.toByteArray()
    }
}
