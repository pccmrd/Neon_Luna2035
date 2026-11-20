package com.example.keres9.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.keres9.ui.theme.KeresColors
import com.example.keres9.ui.theme.RobotoMonoFamily
import kotlin.math.min

fun chamferedShape(cutPx: Float): Shape = GenericShape { size, _ ->
    val c = min(cutPx, min(size.width, size.height) * 0.45f)
    moveTo(c, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height - c)
    lineTo(size.width - c, size.height)
    lineTo(0f, size.height)
    lineTo(0f, c)
    close()
}

@Composable
fun rememberChamferShape(cut: Dp = 9.dp): Shape {
    val px = with(LocalDensity.current) { cut.toPx() }
    return remember(px) { chamferedShape(px) }
}

fun Modifier.chamferBorder(
    cut: Dp = 9.dp,
    borderColor: Color = KeresColors.Steel,
    strokeWidth: Dp = 1.dp,
    aiHighlight: Boolean = false
): Modifier = drawWithContent {
    drawContent()
    val c = min(cut.toPx(), min(size.width, size.height) * 0.45f)
    val path = Path().apply {
        moveTo(c, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height - c)
        lineTo(size.width - c, size.height)
        lineTo(0f, size.height)
        lineTo(0f, c)
        close()
    }
    drawPath(
        path = path,
        color = if (aiHighlight) KeresColors.Green else borderColor,
        style = Stroke(width = (if (aiHighlight) 2.dp else strokeWidth).toPx())
    )
}

@Composable
fun ChamferPanel(
    modifier: Modifier = Modifier,
    cut: Dp = 9.dp,
    backgroundColor: Color = KeresColors.Panel,
    borderColor: Color = KeresColors.Steel,
    aiHighlight: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = rememberChamferShape(cut)
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .chamferBorder(cut = cut, borderColor = borderColor, aiHighlight = aiHighlight),
        content = content
    )
}

@Composable
fun KeresButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    primaryCtx: Boolean = false,
    ready: Boolean = false,
    danger: Boolean = false,
    greenAccent: Boolean = false,
    aiHighlight: Boolean = false,
    cut: Dp = 9.dp,
    minHeight: Dp = 48.dp,
    contentPaddingHorizontal: Dp = 12.dp,
    contentPaddingVertical: Dp = 6.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = rememberChamferShape(cut)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val topCol = if (primaryCtx) KeresColors.CtxTop else KeresColors.ButtonTop
    val botCol = if (primaryCtx) KeresColors.CtxBottom else KeresColors.ButtonBottom

    val borderColor = when {
        aiHighlight -> KeresColors.Green
        ready -> KeresColors.Green
        active -> KeresColors.Cyan
        greenAccent -> KeresColors.Green
        danger -> KeresColors.Red
        pressed -> KeresColors.Amber
        primaryCtx -> KeresColors.Ember
        else -> KeresColors.Steel2
    }

    val textColor = when {
        ready -> KeresColors.Green
        active -> KeresColors.Cyan
        greenAccent -> KeresColors.Green
        danger -> KeresColors.RedTint
        pressed || primaryCtx -> KeresColors.AmberBright
        else -> KeresColors.Amber
    }

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = minHeight)
            .alpha(if (enabled) 1f else 0.34f)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(topCol, botCol)))
            .chamferBorder(cut = cut, borderColor = borderColor, aiHighlight = aiHighlight)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = contentPaddingHorizontal, vertical = contentPaddingVertical),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides textColor) {
            ProvideTextStyle(
                TextStyle(
                    fontFamily = RobotoMonoFamily,
                    fontWeight = if (primaryCtx || ready) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp,
                    textAlign = TextAlign.Center
                )
            ) {
                content()
            }
        }
    }
}

@Composable
fun CrtAtmosphereOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Subtle horizontal CRT scanlines
        val step = 3.dp.toPx().coerceAtLeast(3f)
        var y = 0f
        val lineCol = Color(0x22000000)
        while (y < size.height) {
            drawLine(lineCol, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
        // Soft edge vignette
        val radius = size.maxDimension * 0.72f
        drawRect(
            brush = Brush.radialGradient(
                0.52f to Color.Transparent,
                1.0f to Color(0x99020408),
                center = Offset(size.width * 0.5f, size.height * 0.46f),
                radius = radius.coerceAtLeast(1f)
            )
        )
    }
}
