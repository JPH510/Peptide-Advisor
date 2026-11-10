package com.peptideadvisor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.peptideadvisor.core.trimmed
import com.peptideadvisor.ui.theme.Theme
import com.peptideadvisor.ui.theme.data

/**
 * The one place this app spends its boldness.
 *
 * A calculated dose is not a number in a box — it's a mark on a barrel you have
 * to find at arm's length in bad light. So the result is drawn as the barrel:
 * gradations, the fill, and the stop, at the scale you'll read it.
 */
@Composable
fun SyringeScale(
    units: Double,
    isSuspect: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = Theme.palette
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val fraction by animateFloatAsState(
        targetValue = (units / CAPACITY).coerceIn(0.0, 1.0).toFloat(),
        animationSpec = tween(durationMillis = 250),
        label = "draw",
    )

    val markColor = if (isSuspect) palette.caution else palette.amber
    val description = "Syringe draw: ${units.trimmed(1)} of 100 units"

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .semantics { contentDescription = description },
    ) {
        val flange = with(density) { 14.dp.toPx() }
        val needle = with(density) { 26.dp.toPx() }
        val barrelLeft = flange + with(density) { 6.dp.toPx() }
        val barrelRight = size.width - needle
        val barrelWidth = (barrelRight - barrelLeft).coerceAtLeast(1f)
        val barrelTop = with(density) { 8.dp.toPx() }
        val barrelHeight = with(density) { 30.dp.toPx() }
        val corner = CornerRadius(with(density) { 3.dp.toPx() })

        // Glass
        drawRoundRect(
            color = palette.paper,
            topLeft = Offset(barrelLeft, barrelTop),
            size = Size(barrelWidth, barrelHeight),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = palette.hairline,
            topLeft = Offset(barrelLeft, barrelTop),
            size = Size(barrelWidth, barrelHeight),
            cornerRadius = corner,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
        )

        // Drawn volume
        val fillWidth = barrelWidth * fraction
        if (fillWidth > 0f) {
            drawRoundRect(
                color = markColor.copy(alpha = 0.28f),
                topLeft = Offset(barrelLeft, barrelTop),
                size = Size(fillWidth, barrelHeight),
                cornerRadius = corner,
            )
            // The stop — the line you actually align the plunger to.
            val stopX = barrelLeft + fillWidth
            drawLine(
                color = markColor,
                start = Offset(stopX, barrelTop - 4f),
                end = Offset(stopX, barrelTop + barrelHeight + 4f),
                strokeWidth = with(density) { 2.dp.toPx() },
            )
        }

        // Gradations: minor every 2u, major every 10u
        var u = 0.0
        while (u <= CAPACITY) {
            val x = barrelLeft + barrelWidth * (u / CAPACITY).toFloat()
            val isMajor = u % 10.0 == 0.0
            val length = with(density) { (if (isMajor) 11.dp else 6.dp).toPx() }
            drawLine(
                color = if (isMajor) palette.steel else palette.steel.copy(alpha = 0.45f),
                start = Offset(x, barrelTop + barrelHeight),
                end = Offset(x, barrelTop + barrelHeight - length),
                strokeWidth = if (isMajor) 1.5f else 1f,
            )
            u += 2.0
        }

        // Numerals under the major ticks
        var label = 0.0
        while (label <= CAPACITY) {
            val x = barrelLeft + barrelWidth * (label / CAPACITY).toFloat()
            drawUnitLabel(measurer, label.trimmed(0), x, barrelTop + barrelHeight + 4f, palette.steel)
            label += 20.0
        }

        // Flange
        drawRoundRect(
            color = palette.steel.copy(alpha = 0.35f),
            topLeft = Offset(0f, barrelTop - 5f),
            size = Size(flange, barrelHeight + 10f),
            cornerRadius = CornerRadius(2f),
        )

        // Needle
        val midY = barrelTop + barrelHeight / 2
        drawLine(
            color = palette.steel,
            start = Offset(barrelRight, midY),
            end = Offset(size.width - 2f, midY),
            strokeWidth = with(density) { 1.5.dp.toPx() },
        )
        drawRoundRect(
            color = palette.steel.copy(alpha = 0.5f),
            topLeft = Offset(barrelRight - 1f, barrelTop + 7f),
            size = Size(with(density) { 7.dp.toPx() }, with(density) { 16.dp.toPx() }),
            cornerRadius = CornerRadius(1f),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawUnitLabel(
    measurer: TextMeasurer,
    text: String,
    centerX: Float,
    top: Float,
    color: Color,
) {
    val layout = measurer.measure(text, style = data(9).copy(color = color))
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(centerX - layout.size.width / 2f, top),
    )
}

private const val CAPACITY = 100.0
