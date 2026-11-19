package com.md.mdprint

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val vm: AppVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        if (savedInstanceState == null) handle(intent)
        setContent { MdTheme { AppRoot(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    @Suppress("DEPRECATION")
    private fun handle(i: Intent?) {
        i ?: return
        when (i.action) {
            Intent.ACTION_SEND -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else i.getParcelableExtra(Intent.EXTRA_STREAM)
                if (uri != null) vm.loadUri(uri)
                else i.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.loadText(it, "Shared text") }
            }
            Intent.ACTION_VIEW -> i.data?.let { vm.loadUri(it) }
        }
    }
}

@Composable
fun AppRoot(vm: AppVM) {
    BackHandler(enabled = vm.screen != Screen.Main) { vm.screen = Screen.Main }
    Box(Modifier.fillMaxSize().background(C.Black)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            when (vm.screen) {
                Screen.Main -> when (vm.tab) {
                    Tab.HOME -> HomeScreen(vm)
                    Tab.PRINTERS -> PrintersScreen(vm)
                    Tab.HISTORY -> HistoryScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
                Screen.Setup -> SetupScreen(vm)
                Screen.Web -> WebScreen(vm)
                Screen.Text -> TextScreen(vm)
            }
        }
        if (vm.busy) LinearProgressIndicator(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding(),
            color = C.Cyan, trackColor = C.Black
        )
        if (vm.screen == Screen.Main) Box(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) { BottomBar(vm.tab) { vm.tab = it } }
    }
}
