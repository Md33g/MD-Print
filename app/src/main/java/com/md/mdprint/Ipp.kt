package com.md.mdprint

import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
        o.i32(0x23, "print-quality", s.quality.ipp)
        o.write(0x03)
        o.write(data)
        return send(p, o.toByteArray())
    }

    // Printers on a LAN usually have self-signed certificates, so TLS to the printer itself skips CA validation.
    private val trustAll: SSLSocketFactory by lazy {
        val tm = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<X509Certificate>?, a: String?) {}
            override fun checkServerTrusted(c: Array<X509Certificate>?, a: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        SSLContext.getInstance("TLS").apply { init(null, tm, SecureRandom()) }.socketFactory
    }

    /** Tries plain HTTP first (or HTTPS for 443/8443) and falls back to the other if the printer refuses. */
    private fun send(p: Printer, body: ByteArray): Result {
        val tlsFirst = p.port == 443 || p.port == 8443
        val first = post(if (tlsFirst) p.httpsUrl else p.httpUrl, body, tlsFirst)
        if (first.ok || first.status >= 0) return first
        if (first.status == -2 || first.status == -3) {
            val second = post(if (tlsFirst) p.httpUrl else p.httpsUrl, body, !tlsFirst)
            if (second.ok || second.status >= 0) return second
        }
        return first
    }

    private fun post(url: String, body: ByteArray, tls: Boolean): Result {
        var c: HttpURLConnection? = null
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            c = conn
            if (tls && conn is HttpsURLConnection) {
                conn.sslSocketFactory = trustAll
                conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
            }
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 6000
            conn.readTimeout = 120000
            conn.setRequestProperty("Content-Type", "application/ipp")
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            if (code != 200) return Result(false, -2, "HTTP $code")
            val resp = conn.inputStream.use { it.readBytes() }
            if (resp.size < 8) return Result(false, -1, "Empty response")
            val st = (resp[2].toInt() and 0xff shl 8) or (resp[3].toInt() and 0xff)
            Result(st < 0x0100, st, statusText(st), parse(resp))
        } catch (e: ConnectException) {
            Result(false, -3, e.message ?: "Cannot connect")
        } catch (e: SSLException) {
            Result(false, -3, e.message ?: "TLS error")
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

/** Raw TCP "JetDirect" printing (port 9100): streams the file straight to the printer. */
object RawPort {
    fun send(host: String, port: Int, data: ByteArray): Ipp.Result = try {
        Socket().use { sock ->
            sock.connect(InetSocketAddress(host.trim('[', ']'), port), 6000)
            sock.getOutputStream().apply { write(data); flush() }
        }
        Ipp.Result(true, 0, "OK")
    } catch (e: Exception) {
        Ipp.Result(false, -1, e.message ?: "Cannot reach port $port")
    }

    fun ping(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host.trim('[', ']'), port), 3000) }
        true
    } catch (_: Exception) { false }
}
