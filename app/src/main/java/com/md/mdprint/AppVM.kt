package com.md.mdprint

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import kotlinx.coroutines.Job
import android.net.Uri
import android.print.PrintManager
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppVM(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication<Application>()
    private val store = Store(app)

    val printers = mutableStateListOf<Printer>()
    val found = mutableStateListOf<Printer>()
    val history = mutableStateListOf<HistoryItem>()
    val info = mutableStateMapOf<String, String>()

    var selectedId by mutableStateOf<String?>(null)
    var settings by mutableStateOf(PrintSettings())
    var doc by mutableStateOf<Doc?>(null)
    var screen by mutableStateOf(Screen.Main)
    var tab by mutableStateOf(Tab.HOME)
    var busy by mutableStateOf(false)
    var scanning by mutableStateOf(false)
    var status by mutableStateOf("")

    private var discovery: Discovery? = null
    private var lanJob: Job? = null
    private var mdnsRunning = false
    private var lanRunning = false

    init {
        printers.addAll(store.loadPrinters())
        history.addAll(store.loadHistory())
        selectedId = store.selected
        settings = store.loadSettings()
        viewModelScope.launch(Dispatchers.IO) { Pdf.cleanup(ctx) }
    }

    fun selected(): Printer? = printers.firstOrNull { it.id == selectedId }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

    fun updateSettings(s: PrintSettings) { settings = s; store.saveSettings(s) }

    // ---- documents ----
    fun loadUri(uri: Uri) = loadUris(listOf(uri))

    fun loadUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busy = true; status = ""
            val d = withContext(Dispatchers.IO) { runCatching { Pdf.docFromUris(ctx, uris) }.getOrNull() }
            busy = false
            if (d == null) toast("Unsupported or unreadable file") else { doc = d; screen = Screen.Setup }
        }
    }

    fun loadQr(text: String) {
        if (text.isBlank()) { toast("Nothing to encode"); return }
        viewModelScope.launch {
            busy = true; status = ""
            val f = withContext(Dispatchers.IO) {
                runCatching {
                    val n = 900
                    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, n, n)
                    val px = IntArray(n * n) { i -> if (m.get(i % n, i / n)) Color.BLACK else Color.WHITE }
                    val bmp = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
                    val dir = File(ctx.cacheDir, "docs").also { it.mkdirs() }
                    File(dir, "qr_${System.nanoTime()}.png").also { out -> out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                }.getOrNull()
            }
            busy = false
            if (f == null) toast("Could not create QR code") else { doc = Doc("QR code", "Photo", files = listOf(f)); screen = Screen.Setup }
        }
    }

    fun loadText(text: String, name: String = "Text") {
        if (text.isBlank()) { toast("Nothing to print"); return }
        status = ""
        doc = Doc(name, "Text", text = text)
        screen = Screen.Setup
    }

    // ---- printers ----
    private fun addFound(p: Printer) { if (found.none { it.id == p.id }) found.add(p) }

    fun scan() {
        if (scanning) return
        found.clear()
        mdnsRunning = true; lanRunning = true; scanning = true
        discovery = Discovery(ctx, { addFound(it) }, { mdnsRunning = false; scanning = mdnsRunning || lanRunning })
            .also { it.start() }
        lanJob = viewModelScope.launch {
            try { LanScan.scan { p -> viewModelScope.launch { addFound(p) } } }
            finally { lanRunning = false; scanning = mdnsRunning || lanRunning }
        }
    }

    fun stopScan() { discovery?.stop(); lanJob?.cancel() }

    fun addPrinter(p: Printer) {
        val i = printers.indexOfFirst { it.id == p.id }
        if (i >= 0) printers[i] = p else printers.add(p)
        store.savePrinters(printers)
        if (selectedId == null) select(p)
    }

    fun select(p: Printer) { selectedId = p.id; store.selected = p.id }

    fun remove(p: Printer) {
        printers.removeAll { it.id == p.id }
        store.savePrinters(printers)
        if (selectedId == p.id) { selectedId = printers.firstOrNull()?.id; store.selected = selectedId }
    }

    fun test(p: Printer) {
        info[p.id] = "Checking…"
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                if (p.port == 9100) {
                    if (RawPort.ping(p.host, p.port)) "● Raw port 9100 open" else "✕ Cannot reach port 9100"
                } else {
                    val r = Ipp.getAttributes(p)
                    if (r.ok) {
                        val state = when (r.attrs["printer-state"]) { "3" -> "Ready"; "4" -> "Printing"; "5" -> "Stopped"; else -> "Online" }
                        "● $state  ${r.attrs["printer-make-and-model"] ?: ""}".trim()
                    } else "✕ ${r.message}"
                }
            }
            info[p.id] = text
        }
    }

    // ---- printing ----
    private fun log(d: Doc, target: String, ok: Boolean) {
        history.add(0, HistoryItem(System.currentTimeMillis(), d.name, d.kind, target, ok))
        store.saveHistory(history)
    }

    fun clearHistory() { history.clear(); store.saveHistory(history) }

    fun printIpp() {
        val p = selected()
        val d = doc
        if (p == null) { toast("Select a printer first"); return }
        if (d == null) return
        val s = settings
        viewModelScope.launch {
            busy = true; status = "Preparing…"
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val f = Pdf.prepare(ctx, d, s)
                    if (p.port == 9100) {
                        val bytes = f.readBytes()
                        var r = Ipp.Result(true, 0, "OK")
                        repeat(s.copies) { if (r.ok) r = RawPort.send(p.host, p.port, bytes) }
                        return@runCatching r
                    }
                    if (!s.asImage) {
                        val r = Ipp.printJob(p, d.name, "application/pdf", s, f.readBytes())
                        if (r.ok || r.status < 0) return@runCatching r
                    }
                    val n = Pdf.pageCount(f)
                    var last = Ipp.Result(false, -1, "No pages")
                    for (i in 0 until n) {
                        last = Ipp.printJob(p, "${d.name} (${i + 1}/$n)", "image/jpeg", s, Pdf.jpeg(f, i))
                        if (!last.ok) break
                    }
                    last
                }.getOrElse { Ipp.Result(false, -1, it.message ?: "Error") }
            }
            busy = false
            status = if (res.ok) "Sent to ${p.name} ✓" else "Failed: ${res.message}"
            log(d, p.name, res.ok)
        }
    }

    fun printSystem(activity: Activity) {
        val d = doc ?: return
        val s = settings
        viewModelScope.launch {
            busy = true
            val f = withContext(Dispatchers.IO) { runCatching { Pdf.prepare(ctx, d, s) }.getOrNull() }
            busy = false
            if (f == null) { toast("Could not prepare document"); return@launch }
            val pm = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
            pm.print(d.name, FilePrintAdapter(f, d.name), s.toAttributes())
            status = "Opened system print dialog"
            log(d, "System print", true)
        }
    }

    fun logWeb(title: String) {
        history.add(0, HistoryItem(System.currentTimeMillis(), title, "Web", "System print", true))
        store.saveHistory(history)
    }

    override fun onCleared() { discovery?.stop(); lanJob?.cancel() }
}
