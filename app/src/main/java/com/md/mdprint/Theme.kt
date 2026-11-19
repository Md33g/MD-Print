package com.md.mdprint

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object C {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF080A10)
    val Card = Color(0xFF0E1118)
    val Line = Color(0xFF1B2332)
    val Blue = Color(0xFF2F7BFF)
    val BlueLight = Color(0xFF6DB0FF)
    val Cyan = Color(0xFF3DD9FF)
    val Text = Color(0xFFF2F5FA)
    val Muted = Color(0xFF8792A8)
    val Ok = Color(0xFF3DDC97)
    val Err = Color(0xFFFF5C7A)
    val Grad: Brush = Brush.linearGradient(listOf(Blue, Cyan))
}

@Composable
fun MdTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Blue, onPrimary = Color.White, secondary = C.Cyan,
            background = C.Black, onBackground = C.Text, surface = C.Surface, onSurface = C.Text,
            surfaceVariant = C.Card, outline = C.Line
        ),
        content = content
    )
}

@Composable
fun GlowCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    var m = modifier.clip(shape).background(C.Card)
        .border(1.dp, Brush.linearGradient(listOf(C.Blue.copy(alpha = .55f), C.Line, C.Cyan.copy(alpha = .25f))), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(16.dp), content = content)
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val bg: Brush = if (selected) C.Grad else SolidColor(C.Surface)
    Box(
        Modifier.clip(shape).background(bg)
            .border(1.dp, if (selected) Color.Transparent else C.Line, shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, color = if (selected) Color.Black else C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun GradButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val bg: Brush = if (enabled) C.Grad else SolidColor(C.Line)
    Box(
        modifier.height(54.dp).clip(shape).background(bg).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) Color.Black else C.Muted, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.height(54.dp).clip(shape).border(1.dp, C.Blue, shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = C.BlueLight, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
}

@Composable
fun SmallAction(text: String, color: Color = C.BlueLight, onClick: () -> Unit) {
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp))
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepBtn("−") { onChange((value - 1).coerceAtLeast(1)) }
        Text("$value", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(52.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepBtn("+") { onChange((value + 1).coerceAtMost(99)) }
    }
}

@Composable
private fun StepBtn(t: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(C.Blue.copy(alpha = .2f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) { Text(t, color = C.BlueLight, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun Title(text: String, sub: String? = null) {
    Column(Modifier.padding(top = 16.dp, bottom = 14.dp)) {
        Text(text, color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        if (sub != null) Text(sub, color = C.Muted, fontSize = 13.sp)
    }
}

@Composable
fun BottomBar(tab: Tab, onTab: (Tab) -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        Modifier.fillMaxWidth()
            .shadow(24.dp, shape, ambientColor = C.Blue, spotColor = C.Blue)
            .clip(shape).background(Color(0xFF090B11))
            .border(1.dp, Brush.linearGradient(listOf(C.Blue.copy(alpha = .8f), C.Line, C.Cyan.copy(alpha = .4f))), shape)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tab.values().forEach { t ->
            val sel = t == tab
            val bg by animateColorAsState(if (sel) C.Blue.copy(alpha = .22f) else Color.Transparent, label = "tab")
            Row(
                Modifier.clip(RoundedCornerShape(24.dp)).animateContentSize().background(bg)
                    .clickable { onTab(t) }
                    .padding(horizontal = if (sel) 16.dp else 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t.emoji, fontSize = 20.sp)
                if (sel) {
                    Spacer(Modifier.width(8.dp))
                    Text(t.label, color = C.BlueLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
