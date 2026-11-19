package com.md.mdprint

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

@Composable
private fun ScreenBody(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        content()
        Spacer(Modifier.navigationBarsPadding().height(130.dp))
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.Blue, unfocusedBorderColor = C.Line,
    focusedTextColor = C.Text, unfocusedTextColor = C.Text, cursorColor = C.Cyan,
    focusedLabelColor = C.BlueLight, unfocusedLabelColor = C.Muted
)

// ───────────────────────── HOME ─────────────────────────
@Composable
fun HomeScreen(vm: AppVM) {
    val ctx = LocalContext.current
    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::loadUri) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::loadUri) }
    val sel = vm.selected()

    ScreenBody {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(C.Grad), contentAlignment = Alignment.Center) {
                Text("🦁", fontSize = 32.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("MD Print", color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Print anything, anywhere", color = C.Muted, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        GlowCard(Modifier.fillMaxWidth(), onClick = { vm.tab = Tab.PRINTERS }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🖨️", fontSize = 30.sp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(sel?.name ?: "No printer selected", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(if (sel != null) "${sel.host}:${sel.port}" else "Tap to find or add a printer", color = C.Muted, fontSize = 13.sp)
                }
                Text("›", color = C.BlueLight, fontSize = 26.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Print", color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("📄", "Document", "PDF, text") { pickDoc.launch(arrayOf("application/pdf", "text/plain", "image/*")) }
            Tile("🖼️", "Photo", "JPG, PNG, WebP") { pickPhoto.launch(arrayOf("image/*")) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("🌐", "Web page", "Any website") { vm.screen = Screen.Web }
            Tile("📝", "Text", "Type or paste") { vm.screen = Screen.Text }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("📋", "Clipboard", "Print copied text") {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
                if (t.isNullOrBlank()) vm.toast("Clipboard is empty") else vm.loadText(t, "Clipboard")
            }
            Tile("🧾", "Test page", "Check your printer") {
                vm.loadText("🦁 MD Print – test page\n\nIf you can read this, your printer works.\n\n" +
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ\nabcdefghijklmnopqrstuvwxyz\n0123456789\n\n" +
                    "Printed with MD Print (com.md.mdprint).", "Test page")
            }
        }
    }
}

@Composable
private fun RowScope.Tile(emoji: String, title: String, sub: String, onClick: () -> Unit) {
    GlowCard(Modifier.weight(1f).height(142.dp), onClick = onClick) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(C.Blue.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 23.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(title, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(sub, color = C.Muted, fontSize = 12.sp)
    }
}

// ───────────────────────── PRINTERS ─────────────────────────
@Composable
fun PrintersScreen(vm: AppVM) {
    var showAdd by remember { mutableStateOf(false) }
    ScreenBody {
        Title("Printers", "Wi-Fi / network printers (IPP, AirPrint, Mopria)")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GradButton(if (vm.scanning) "Scanning…" else "🔍  Scan Wi-Fi", Modifier.weight(1f), enabled = !vm.scanning) { vm.scan() }
            OutlineButton("＋  Add by IP", Modifier.weight(1f)) { showAdd = true }
        }
        Spacer(Modifier.height(18.dp))

        if (vm.printers.isNotEmpty()) {
            Text("Saved", color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            vm.printers.forEach { p ->
                val sel = p.id == vm.selectedId
                GlowCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), onClick = { vm.select(p) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sel, onClick = { vm.select(p) },
                            colors = RadioButtonDefaults.colors(selectedColor = C.Cyan, unselectedColor = C.Muted))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = C.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${p.host}:${p.port}/${p.path}", color = C.Muted, fontSize = 12.sp)
                        }
                    }
                    vm.info[p.id]?.let {
                        Text(it, color = if (it.startsWith("✕")) C.Err else C.Ok, fontSize = 13.sp,
                            modifier = Modifier.padding(start = 12.dp, top = 4.dp))
                    }
                    Row {
                        SmallAction("Test connection") { vm.test(p) }
                        SmallAction("Remove", C.Err) { vm.remove(p) }
                    }
                }
            }
        }

        val fresh = vm.found.filter { f -> vm.printers.none { it.id == f.id } }
        if (fresh.isNotEmpty()) {
            Text("Discovered", color = C.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            fresh.forEach { p ->
                GlowCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📡", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = C.Text, fontWeight = FontWeight.Bold)
                            Text("${p.host}:${p.port}", color = C.Muted, fontSize = 12.sp)
                        }
                        SmallAction("Save & use") { vm.addPrinter(p); vm.select(p) }
                    }
                }
            }
        }

        if (vm.printers.isEmpty() && fresh.isEmpty()) {
            GlowCard(Modifier.fillMaxWidth()) {
                Text("🦁  No printers yet", color = C.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text("Connect your phone to the same Wi-Fi as the printer, then tap Scan. " +
                    "If it isn't found, add it by IP address (port 631). You can also always use the system print dialog " +
                    "with any installed print service.", color = C.Muted, fontSize = 13.sp)
            }
        }
    }
    if (showAdd) AddPrinterDialog(onDismiss = { showAdd = false }) { vm.addPrinter(it); vm.select(it); showAdd = false }
}

@Composable
private fun AddPrinterDialog(onDismiss: () -> Unit, onAdd: (Printer) -> Unit) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("631") }
    var path by remember { mutableStateOf("ipp/print") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        title = { Text("Add printer", color = C.Text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, colors = fieldColors())
                OutlinedTextField(host, { host = it.trim() }, label = { Text("IP address, e.g. 192.168.1.50") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), colors = fieldColors())
                OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = fieldColors())
                OutlinedTextField(path, { path = it.trim() }, label = { Text("Path (ipp/print)") }, singleLine = true, colors = fieldColors())
            }
        },
        confirmButton = {
            TextButton(enabled = host.isNotBlank(), onClick = {
                val p = port.toIntOrNull() ?: 631
                onAdd(Printer("$host:$p", name.ifBlank { host }, host, p, path.ifBlank { "ipp/print" }, true))
            }) { Text("Add", color = C.Cyan, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Muted) } }
    )
}

// ───────────────────────── HISTORY ─────────────────────────
@Composable
fun HistoryScreen(vm: AppVM) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Title("History", "${vm.history.size} jobs") }
            if (vm.history.isNotEmpty()) SmallAction("Clear", C.Err) { vm.clearHistory() }
        }
        if (vm.history.isEmpty()) {
            GlowCard(Modifier.fillMaxWidth()) {
                Text("🦁  Nothing printed yet", color = C.Text, fontWeight = FontWeight.Bold)
                Text("Your print jobs will show up here.", color = C.Muted, fontSize = 13.sp)
            }
        } else {
            val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 140.dp)) {
                items(vm.history, key = { it.id }) { h ->
                    GlowCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(when (h.kind) { "PDF" -> "📄"; "Photo" -> "🖼️"; "Web" -> "🌐"; else -> "📝" }, fontSize = 26.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.name, color = C.Text, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${h.target} · ${fmt.format(Date(h.id))}", color = C.Muted, fontSize = 12.sp)
                            }
                            Text(if (h.ok) "✓" else "✕", color = if (h.ok) C.Ok else C.Err, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── SETTINGS ─────────────────────────
@Composable
fun SettingsScreen(vm: AppVM) {
    val ctx = LocalContext.current
    ScreenBody {
        Title("Settings", "Default print options")
        GlowCard(Modifier.fillMaxWidth()) { SettingsPanel(vm.settings) { vm.updateSettings(it) } }
        Spacer(Modifier.height(14.dp))
        OutlineButton("Open Android print services", Modifier.fillMaxWidth()) {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_PRINT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        Spacer(Modifier.height(14.dp))
        GlowCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🦁", fontSize = 34.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("MD Print", color = C.Text, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("Version 1.0.0 · com.md.mdprint", color = C.Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Tip: share a PDF, photo or text from any app and pick MD Print to print it instantly.",
                color = C.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun SettingsPanel(s: PrintSettings, onChange: (PrintSettings) -> Unit) {
    @Composable fun Label(t: String) = Text(t, color = C.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Copies", color = C.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Stepper(s.copies) { onChange(s.copy(copies = it)) }
        }
        Label("PAPER")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Paper.values().forEach { Chip(it.label, s.paper == it) { onChange(s.copy(paper = it)) } }
        }
        Label("COLOR")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Color", s.color) { onChange(s.copy(color = true)) }
            Chip("Black & white", !s.color) { onChange(s.copy(color = false)) }
        }
        Label("ORIENTATION (photos & text)")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Orient.values().forEach { Chip(it.label, s.orientation == it) { onChange(s.copy(orientation = it)) } }
        }
        Label("SIDES")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sides.values().forEach { Chip(it.label, s.sides == it) { onChange(s.copy(sides = it)) } }
        }
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Compatibility mode", color = C.Text, fontWeight = FontWeight.SemiBold)
                Text("Send pages as images (for printers that reject PDF)", color = C.Muted, fontSize = 12.sp)
            }
            Switch(checked = s.asImage, onCheckedChange = { onChange(s.copy(asImage = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = C.Blue, checkedThumbColor = Color.White))
        }
    }
}

// ───────────────────────── PRINT SETUP ─────────────────────────
@Composable
fun SetupScreen(vm: AppVM) {
    val ctx = LocalContext.current
    val doc = vm.doc
    if (doc == null) { LaunchedEffect(Unit) { vm.screen = Screen.Main }; return }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var pages by remember { mutableStateOf(0) }

    LaunchedEffect(doc, vm.settings.paper, vm.settings.orientation) {
        val r = withContext(Dispatchers.IO) {
            runCatching {
                val f = Pdf.prepare(ctx, doc, vm.settings)
                Pdf.pageCount(f) to Pdf.render(f, 0) { 520 }
            }.getOrNull()
        }
        if (r != null) { pages = r.first; preview = r.second }
    }

    val sel = vm.selected()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = C.BlueLight, fontSize = 36.sp,
                modifier = Modifier.clip(CircleShape).clickable { vm.screen = Screen.Main }.padding(horizontal = 14.dp))
            Text("Print setup", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(10.dp))
        GlowCard(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF111111)),
                contentAlignment = Alignment.Center) {
                val b = preview
                if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                else CircularProgressIndicator(color = C.Cyan)
            }
            Spacer(Modifier.height(10.dp))
            Text(doc.name, color = C.Text, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("${doc.kind}${if (pages > 0) " · $pages page" + if (pages > 1) "s" else "" else ""}", color = C.Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(14.dp))
        GlowCard(Modifier.fillMaxWidth()) { SettingsPanel(vm.settings) { vm.updateSettings(it) } }
        Spacer(Modifier.height(16.dp))
        GradButton(if (sel != null) "🖨️  Print to ${sel.name}" else "Select a printer to print directly",
            Modifier.fillMaxWidth(), enabled = sel != null && !vm.busy) { vm.printIpp() }
        Spacer(Modifier.height(10.dp))
        OutlineButton("Use system print dialog", Modifier.fillMaxWidth()) {
            ctx.findActivity()?.let { vm.printSystem(it) }
        }
        if (vm.status.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(vm.status, color = if (vm.status.startsWith("Failed")) C.Err else C.Ok, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.navigationBarsPadding().height(30.dp))
    }
}

// ───────────────────────── TEXT ─────────────────────────
@Composable
fun TextScreen(vm: AppVM) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).navigationBarsPadding().imePadding()) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = C.BlueLight, fontSize = 36.sp,
                modifier = Modifier.clip(CircleShape).clickable { vm.screen = Screen.Main }.padding(horizontal = 14.dp))
            Text("Print text", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().weight(1f),
            placeholder = { Text("Type or paste anything…", color = C.Muted) }, colors = fieldColors())
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlineButton("📋 Paste", Modifier.weight(1f)) {
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.let { text += it }
            }
            GradButton("Continue", Modifier.weight(1f), enabled = text.isNotBlank()) { vm.loadText(text, "Text note") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ───────────────────────── WEB ─────────────────────────
@Composable
fun WebScreen(vm: AppVM) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf("") }
    var web by remember { mutableStateOf<WebView?>(null) }
    fun go() {
        var u = url.trim()
        if (u.isEmpty()) return
        if (!u.startsWith("http")) u = "https://$u"
        web?.loadUrl(u)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding().imePadding()) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = C.BlueLight, fontSize = 36.sp,
                modifier = Modifier.clip(CircleShape).clickable { vm.screen = Screen.Main }.padding(horizontal = 14.dp))
            Text("Print web page", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(url, { url = it }, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("example.com", color = C.Muted) }, colors = fieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = androidx.compose.ui.text.input.ImeAction.Go),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { go() }))
            Spacer(Modifier.width(8.dp))
            SmallAction("Go") { go() }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)) {
            AndroidView(factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    web = this
                }
            }, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        GradButton("🖨️  Print this page", Modifier.fillMaxWidth()) {
            val w = web
            val act = ctx.findActivity()
            if (w != null && act != null) {
                val title = w.title?.takeIf { it.isNotBlank() } ?: "Web page"
                val pm = act.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
                pm.print(title, w.createPrintDocumentAdapter(title), vm.settings.toAttributes())
                vm.logWeb(title)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
