package com.md.mdprint

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper

/** Finds IPP printers on the local Wi-Fi via mDNS / Bonjour (_ipp._tcp). */
class Discovery(
    ctx: Context,
    private val onFound: (Printer) -> Unit,
    private val onDone: () -> Unit
) {
    private val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var running = false
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (running) return
        running = true
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) { main.post { stop() } }
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceLost(i: NsdServiceInfo) {}
            override fun onServiceFound(i: NsdServiceInfo) { main.post { queue.addLast(i); next() } }
        }
        listener = l
        try { nsd.discoverServices("_ipp._tcp.", NsdManager.PROTOCOL_DNS_SD, l) } catch (_: Exception) { stop(); return }
        main.postDelayed({ stop() }, 12000)
    }

    @Suppress("DEPRECATION")
    private fun next() {
        if (resolving || queue.isEmpty() || !running) return
        resolving = true
        val info = queue.removeFirst()
        try {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, e: Int) { main.post { resolving = false; next() } }
                override fun onServiceResolved(i: NsdServiceInfo) {
                    main.post {
                        resolving = false
                        val raw = i.host?.hostAddress?.substringBefore('%')
                        if (raw != null && running) {
                            val host = if (raw.contains(':')) "[$raw]" else raw
                            val rp = i.attributes?.get("rp")?.let { String(it) }?.trim('/') ?: "ipp/print"
                            onFound(Printer("$host:${i.port}", i.serviceName ?: host, host, i.port, rp))
                        }
                        next()
                    }
                }
            })
        } catch (_: Exception) { resolving = false; next() }
    }

    fun stop() {
        if (!running) return
        running = false
        listener?.let { try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {} }
        listener = null
        queue.clear()
        onDone()
    }
}
