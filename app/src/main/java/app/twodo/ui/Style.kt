package app.twodo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.twodo.model.BORDER_DASHED
import app.twodo.model.BORDER_DOTTED
import app.twodo.model.FONT_HANDWRITTEN
import app.twodo.model.FONT_MONO
import app.twodo.model.FONT_SERIF
import app.twodo.model.ITEMS_CARDS
import app.twodo.model.ITEMS_LINES
import app.twodo.model.ITEMS_OUTLINED
import app.twodo.model.ListStyle

/** A [ListStyle] worked out against the screen's colours: what list rows and headings draw with. */
class ResolvedStyle(
    val spacing: Dp,
    val items: String?,
    val shape: Shape,
    val borderWidth: Dp,
    val borderStyle: String?,
    val borderColor: Color,
    val shadow: Dp,
    val itemColor: Color?,
    val heading: Color,
    val font: FontFamily?,
) {
    /** Cards and outlines stand apart from each other; plain rows and lines run edge to edge. */
    val separate: Boolean get() = items == ITEMS_CARDS || items == ITEMS_OUTLINED
}

val DEFAULT_STYLE = ResolvedStyle(12.dp, null, RoundedCornerShape(0.dp), 0.dp, null, Color.Transparent, 0.dp, null, Color.Unspecified, null)

/** The style lists on screen are drawn in (set by [LookSurface]). */
val LocalListStyle = compositionLocalOf { DEFAULT_STYLE }

/** Applies [style]'s colours to [colors]: page and text colours, headings (secondary) and highlights (tertiary). */
fun styledColors(colors: ColorScheme, dark: Boolean, style: ListStyle): ColorScheme {
    var c = colors
    style.background?.let { argb ->
        val bg = Color(argb)
        // Text to suit the page, unless given: light on a dark page, dark on a light one.
        val text = style.text?.let { Color(it) } ?: if (bg.luminance() < 0.4f) Color(0xFFF2F2F2) else Color(0xFF1B1B1B)
        val muted = lerp(text, bg, 0.35f)
        c = c.copy(
            background = bg, surface = bg, onBackground = text, onSurface = text,
            surfaceVariant = lerp(bg, text, 0.08f), onSurfaceVariant = muted,
            surfaceContainer = lerp(bg, text, 0.06f), surfaceContainerHigh = lerp(bg, text, 0.09f),
            surfaceContainerHighest = lerp(bg, text, 0.12f), surfaceContainerLow = lerp(bg, text, 0.03f),
            outline = lerp(muted, bg, 0.15f), outlineVariant = lerp(bg, text, 0.15f),
        )
    } ?: style.text?.let { argb -> c = c.copy(onBackground = Color(argb), onSurface = Color(argb)) }
    val secondary = style.secondary?.let { Color(it) } ?: c.primary
    val tertiary = style.tertiary?.let { Color(it) } ?: c.primary
    return c.copy(
        secondary = secondary, onSecondary = onColor(secondary),
        secondaryContainer = lerp(c.background, tertiary, if (dark) 0.28f else 0.2f), onSecondaryContainer = lerp(c.onBackground, tertiary, 0.35f),
        tertiary = tertiary, onTertiary = onColor(tertiary),
        tertiaryContainer = lerp(c.background, tertiary, if (dark) 0.28f else 0.2f), onTertiaryContainer = lerp(c.onBackground, tertiary, 0.35f),
    )
}

private fun onColor(c: Color) = if (c.luminance() < 0.45f) Color.White else Color.Black

fun resolveStyle(style: ListStyle, colors: ColorScheme): ResolvedStyle {
    val corners = (style.corners ?: if (style.items == ITEMS_CARDS || style.items == ITEMS_OUTLINED) 12 else 0).dp
    val border = style.borderWidth ?: if (style.items == ITEMS_OUTLINED) 1 else 0
    return ResolvedStyle(
        spacing = (style.spacing ?: 12).dp,
        items = style.items,
        shape = RoundedCornerShape(corners),
        borderWidth = border.dp,
        borderStyle = style.borderStyle,
        borderColor = style.borderColor?.let { Color(it) } ?: colors.outline,
        shadow = (style.shadow ?: if (style.items == ITEMS_CARDS) 2 else 0).dp,
        itemColor = style.itemColor?.let { Color(it) },
        heading = colors.secondary,
        font = when (style.font) {
            FONT_SERIF -> FontFamily.Serif
            FONT_MONO -> FontFamily.Monospace
            FONT_HANDWRITTEN -> FontFamily.Cursive
            else -> null
        },
    )
}

/**
 * One row in the chosen style: list items, the lists in a group, groups on the home screen,
 * calendar entries, people and settings all use it, so a look applies to every screen alike.
 * [fill] is a highlight behind it; [contentPadding] sits inside the row, under the click ripple.
 */
@Composable
fun StyledRow(
    modifier: Modifier = Modifier,
    fill: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = LocalListStyle.current.spacing),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth()
            .listRow(LocalListStyle.current, fill, MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/**
 * How one list row (item or heading) sits in the list: plain, with a line under it, as a card, or
 * outlined. [fill] is the row's own background (e.g. a highlight); [cardColor] is used for cards.
 * Use [StyledRow] rather than this where you can.
 */
fun Modifier.listRow(style: ResolvedStyle, fill: Color, cardColor: Color): Modifier = when (style.items) {
    ITEMS_CARDS -> padding(horizontal = 12.dp, vertical = 4.dp)
        .outerShadow(style.shadow, style.shape)
        .clip(style.shape)
        .background(if (fill != Color.Transparent && fill != Color.Unspecified) fill else (style.itemColor ?: cardColor).halved())
        .styledBorder(style)
    ITEMS_OUTLINED -> padding(horizontal = 12.dp, vertical = 4.dp)
        .clip(style.shape)
        .background(if (fill != Color.Transparent && fill != Color.Unspecified) fill else style.itemColor?.halved() ?: Color.Transparent)
        .styledBorder(style)
    ITEMS_LINES -> background(style.itemColor?.halved()?.takeIf { fill == Color.Transparent || fill == Color.Unspecified } ?: fill)
        .drawBehind {
            // As thick as the border, if one is set (otherwise a hairline), and that solid when it's thick.
            val width = style.borderWidth.coerceAtLeast(1.dp).toPx()
            val y = size.height - width / 2
            val colour = if (style.borderWidth > 1.dp) style.borderColor else style.borderColor.copy(alpha = 0.6f)
            drawLine(colour, Offset(16.dp.toPx(), y), Offset(size.width - 16.dp.toPx(), y), width)
        }
    else -> background(style.itemColor?.halved()?.takeIf { fill == Color.Transparent || fill == Color.Unspecified } ?: fill)
}

/**
 * A soft shadow around [shape], drawn only outside it. Android's own elevation shadow is a solid
 * shape under the card, and cards are see-through (see [halved]), so its dark middle showed through
 * as a band inside every card.
 */
private fun Modifier.outerShadow(elevation: Dp, shape: Shape): Modifier = if (elevation <= 0.dp) this else drawBehind {
    val own = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
    clipPath(own, ClipOp.Difference) {
        val reach = elevation.toPx() * 1.5f
        // Rings growing outwards, each faint, so it darkens towards the card and fades away from it;
        // a little lower than the card, as if lit from above.
        for (i in SHADOW_RINGS downTo 1) {
            val grow = reach * i / SHADOW_RINGS
            val ring = shape.createOutline(Size(size.width + grow * 2, size.height + grow * 2), layoutDirection, this@drawBehind)
            translate(-grow, -grow + elevation.toPx() / 2) { drawOutline(ring, Color.Black.copy(alpha = 0.025f)) }
        }
    }
}

private const val SHADOW_RINGS = 6

/** Item backgrounds sit at half their opacity, so the page or photo shows through. */
private fun Color.halved() = copy(alpha = alpha * 0.5f)

/** The style's border around the row's shape: solid, dashed or dotted. */
fun Modifier.styledBorder(style: ResolvedStyle): Modifier {
    if (style.borderWidth <= 0.dp) return this
    return drawBehind {
        val width = style.borderWidth.toPx()
        val effect = when (style.borderStyle) {
            BORDER_DASHED -> PathEffect.dashPathEffect(floatArrayOf(width * 4, width * 3))
            BORDER_DOTTED -> PathEffect.dashPathEffect(floatArrayOf(width * 0.01f, width * 2.5f))
            else -> null
        }
        val outline = style.shape.createOutline(size.copy(width = size.width - width, height = size.height - width), layoutDirection, this)
        translate(width / 2, width / 2) {
            drawOutline(outline, style.borderColor, style = Stroke(width, pathEffect = effect, cap = if (style.borderStyle == BORDER_DOTTED) StrokeCap.Round else StrokeCap.Butt))
        }
    }
}

/** [base] with every text style in [font] (unchanged if null). */
fun typographyIn(font: FontFamily?, base: Typography = Typography()): Typography = if (font == null) base else Typography(
    displayLarge = base.displayLarge.copy(fontFamily = font), displayMedium = base.displayMedium.copy(fontFamily = font),
    displaySmall = base.displaySmall.copy(fontFamily = font), headlineLarge = base.headlineLarge.copy(fontFamily = font),
    headlineMedium = base.headlineMedium.copy(fontFamily = font), headlineSmall = base.headlineSmall.copy(fontFamily = font),
    titleLarge = base.titleLarge.copy(fontFamily = font), titleMedium = base.titleMedium.copy(fontFamily = font),
    titleSmall = base.titleSmall.copy(fontFamily = font), bodyLarge = base.bodyLarge.copy(fontFamily = font),
    bodyMedium = base.bodyMedium.copy(fontFamily = font), bodySmall = base.bodySmall.copy(fontFamily = font),
    labelLarge = base.labelLarge.copy(fontFamily = font), labelMedium = base.labelMedium.copy(fontFamily = font),
    labelSmall = base.labelSmall.copy(fontFamily = font),
)
