package app.twodo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.twodo.BuildConfig
import app.twodo.R
import app.twodo.data.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Settings › Diagnostics: a small floating readout of CPU use on top of every screen. Tap it for the
 * full breakdown (CPU by area and thread, network, sync activity) and Copy to send it on; drag to move.
 */
@Composable
fun DiagnosticsOverlay() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf("CPU …") }
    var report by remember { mutableStateOf("") }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val header = remember {
        "${context.getString(R.string.brand_name)} ${BuildConfig.VERSION_NAME} on ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"
    }
    // Fresh numbers every couple of seconds while on screen (the background sampling is once a minute).
    LaunchedEffect(expanded) {
        while (true) {
            withContext(Dispatchers.IO) {
                Diagnostics.sample()
                summary = Diagnostics.summary()
                if (expanded) report = Diagnostics.report(header)
            }
            delay(REFRESH_MS)
        }
    }
    val text = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp)
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .padding(top = 4.dp, start = 8.dp, end = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xF2101418))
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        offsetX += drag.x
                        offsetY += drag.y
                    }
                },
        ) {
            Text(
                "◷ $summary" + if (expanded) "  ▲" else "  ▼",
                style = text.copy(fontSize = 12.sp),
                modifier = Modifier.clickable { expanded = !expanded }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
            if (expanded) {
                val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
                Text(
                    report.ifEmpty { "Measuring…" },
                    style = text,
                    softWrap = false,
                    modifier = Modifier
                        .heightIn(max = maxHeight)
                        .width((LocalConfiguration.current.screenWidthDp - 16).dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp),
                )
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostics", report))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy", color = Color(0xFF9FD3FF)) }
                    Spacer(Modifier.weight(1f))
                    Text("Turn off in Settings", style = text.copy(color = Color(0xFF9AA3AB)))
                    TextButton(onClick = { expanded = false }) { Text("Hide", color = Color(0xFF9FD3FF)) }
                }
            }
        }
    }
}

private const val REFRESH_MS = 2_000L
