package com.md.mdprint

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Minimal IPP 1.1 client: Print-Job and Get-Printer-Attributes over plain HTTP. */
object Ipp {
    data class Result(
        val ok: Boolean,
        val status: Int,                 // -1 network error, -2 HTTP error, otherwise IPP status code
        val message: String,
        val attrs: Map<String, String> = emptyMap()
    )

    private fun ByteArrayOutputStream.u16(v: Int) { write(v shr 8 and 0xff); write(v and 0xff) }
    private fun ByteArrayOutputStream.u32(v: Int) { u16(v ushr 16 and 0xffff); u16(v and 0xffff) }
    private fun ByteArrayOutputStream.attr(tag: Int, name: String, value: ByteArray) {
        write(tag)
        val n = name.toByteArray()
        u16(n.size); write(n)
        u16(value.size); write(value)
    }
    private fun ByteArrayOutputStream.str(tag: Int, name: String, v: String) = attr(tag, name, v.toByteArray())
    private fun ByteArrayOutputStream.i32(tag: Int, name: String, v: Int) {
        val b = ByteArrayOutputStream(); b.u32(v); attr(tag, name, b.toByteArray())
    }

    private fun header(op: Int, p: Printer): ByteArrayOutputStream {
        val o = ByteArrayOutputStream()
        o.u16(0x0101); o.u16(op); o.u32(1)
        o.write(0x01)
        o.str(0x47, "attributes-charset", "utf-8")
        o.str(0x48, "attributes-natural-language", "en")
        o.str(0x45, "printer-uri", p.uri)
        o.str(0x42, "requesting-user-name", "mdprint")
        return o
    }

    fun getAttributes(p: Printer): Result {
        val o = header(0x000B, p)
        o.str(0x44, "requested-attributes", "printer-name")
        for (a in listOf("printer-make-and-model", "printer-state", "printer-state-reasons", "document-format-supported"))
            o.str(0x44, "", a)
        o.write(0x03)
        return send(p, o.toByteArray())
    }

    fun printJob(p: Printer, jobName: String, mime: String, s: PrintSettings, data: ByteArray): Result {
        val o = header(0x0002, p)
        o.str(0x42, "job-name", jobName.take(120))
        o.str(0x49, "document-format", mime)
        o.write(0x02)
        o.i32(0x21, "copies", s.copies)
        o.str(0x44, "sides", s.sides.ipp)
        o.str(0x44, "print-color-mode", if (s.color) "color" else "monochrome")
        o.str(0x44, "media", s.paper.ipp)
        o.write(0x03)
        o.write(data)
        return send(p, o.toByteArray())
    }

    private fun send(p: Printer, body: ByteArray): Result {
        var c: HttpURLConnection? = null
        return try {
            c = URL(p.httpUrl).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 6000
            c.readTimeout = 60000
            c.setRequestProperty("Content-Type", "application/ipp")
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            if (code != 200) return Result(false, -2, "HTTP $code (printer may need ipps/TLS or a different path)")
            val resp = c.inputStream.use { it.readBytes() }
            if (resp.size < 8) return Result(false, -1, "Empty response")
            val st = (resp[2].toInt() and 0xff shl 8) or (resp[3].toInt() and 0xff)
            Result(st < 0x0100, st, statusText(st), parse(resp))
        } catch (e: Exception) {
            Result(false, -1, e.message ?: "Network error")
        } finally {
            c?.disconnect()
        }
    }

    private fun statusText(st: Int) = when {
        st < 0x0100 -> "OK"
        st == 0x0400 -> "Bad request"
        st == 0x0404 -> "Printer not found at this path"
        st == 0x040A -> "Document format not supported"
        st == 0x0405 || st == 0x0407 -> "Printer refused the job"
        st == 0x0506 -> "Printer busy"
        st == 0x0504 -> "Printer not accepting jobs"
        else -> "IPP error 0x" + st.toString(16)
    }

    private fun u16(b: ByteArray, p: Int) = (b[p].toInt() and 0xff shl 8) or (b[p + 1].toInt() and 0xff)

    private fun parse(b: ByteArray): Map<String, String> {
        val m = linkedMapOf<String, String>()
        try {
            var p = 8
            var last = ""
            while (p < b.size) {
                val t = b[p].toInt() and 0xff; p++
                if (t == 0x03) break
                if (t < 0x10) continue
                val nl = u16(b, p); p += 2
                val name = String(b, p, nl); p += nl
                val vl = u16(b, p); p += 2
                val key = if (name.isEmpty()) last else name.also { last = it }
                val v = when {
                    t in 0x40..0x4f -> String(b, p, vl)
                    (t == 0x21 || t == 0x23) && vl == 4 ->
                        ((b[p].toInt() and 0xff shl 24) or (b[p + 1].toInt() and 0xff shl 16) or
                            (b[p + 2].toInt() and 0xff shl 8) or (b[p + 3].toInt() and 0xff)).toString()
                    else -> ""
                }
                p += vl
                if (v.isNotEmpty()) m[key] = if (m.containsKey(key)) m[key] + ", " + v else v
            }
        } catch (_: Exception) { }
        return m
    }
}
