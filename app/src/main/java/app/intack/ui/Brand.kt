package app.intack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.intack.R

/** Intack's brand (design canvas "Wordmark, palette and type"). Inside groups, the family's own look takes over. */
object Brand {
    val Pine = Color(0xFF24594F)
    val Marigold = Color(0xFFF2B544)
    val MarigoldDeep = Color(0xFF9A5B12)
    val Paper = Color(0xFFF7F3EC)
    val Card = Color(0xFFFFFDF9)
    val Ink = Color(0xFF1E2421)
    val Muted = Color(0xFF5C635E)
    val Line = Color(0xFFE4DED3)

    val SeaMint = Color(0xFF7CC4B2)
    val MarigoldNight = Color(0xFFF4C266)
    val Night = Color(0xFF141A18)
    val NightCard = Color(0xFF1E2623)
    val NightInk = Color(0xFFEFEAE0)
    val NightMuted = Color(0xFFA7ADA8)
    val NightLine = Color(0xFF2E3834)

    /** Headings and the wordmark. Body text stays on the phone's own font. */
    val Serif = FontFamily(Font(R.font.young_serif))
}

/** The K2 pin, in its 32 × 60 box: [head] for the cap, waist and flare, [base] for the disc and needle. */
fun pinVector(head: Color, base: Color): ImageVector {
    val headPaths = listOf(
        "M6.25,0 H25.75 A3.25,3.25 0 0 1 25.75,6.5 H6.25 A3.25,3.25 0 0 1 6.25,0 Z",
        "M8,6 H24 C21,9 19.5,11 19.5,14 C19.5,19 24,22 29,24 H3 C8,22 12.5,19 12.5,14 C12.5,11 11,9 8,6 Z",
    )
    val basePaths = listOf(
        "M3.25,23.5 H28.75 A2.25,2.25 0 0 1 28.75,28 H3.25 A2.25,2.25 0 0 1 3.25,23.5 Z",
        "M14.6,28 H17.4 L16.6,58 Q16,60 15.4,58 Z",
    )
    return ImageVector.Builder("pin", 32.dp, 60.dp, 32f, 60f).apply {
        headPaths.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(head)) }
        basePaths.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(base)) }
    }.build()
}

/**
 * "Intack" with the pin standing in for the t: Young Serif, the needle's tip on the baseline, the pin
 * 0.76 em tall. [dark] picks the pin's colours for a dark background (cream base instead of pine).
 */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    color: Color = MaterialTheme.colorScheme.onBackground,
    dark: Boolean = color.luminance() > 0.5f,
) {
    val pin = remember(dark) {
        if (dark) pinVector(Brand.MarigoldNight, Brand.NightInk) else pinVector(Brand.Marigold, Brand.Pine)
    }
    val painter = rememberVectorPainter(pin)
    val text = remember { buildAnnotatedString { append("In"); appendInlineContent("pin", "t"); append("ack") } }
    val inline = mapOf(
        "pin" to InlineTextContent(Placeholder(0.435.em, 0.76.em, PlaceholderVerticalAlign.AboveBaseline)) {
            // 0.405 em of pin plus 0.015 em either side.
            Image(painter, "t", Modifier.fillMaxSize())
        },
    )
    Text(text, modifier, color = color, fontSize = fontSize, fontFamily = Brand.Serif, inlineContent = inline, maxLines = 1)
}

