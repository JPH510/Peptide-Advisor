package com.peptideadvisor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peptideadvisor.core.EvidenceTier
import com.peptideadvisor.core.trimmed
import com.peptideadvisor.ui.theme.Theme
import com.peptideadvisor.ui.theme.data
import com.peptideadvisor.ui.theme.ui

/** A small uppercase label that names a field. Labels label; nothing else does. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = ui(11, FontWeight.SemiBold).copy(letterSpacing = 0.8.sp),
        color = Theme.palette.steel,
        modifier = modifier,
    )
}

/** Card container used for every discrete record in the app. */
@Composable
fun BenchCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Theme.radius))
            .background(Theme.palette.paper)
            .border(1.dp, Theme.palette.hairline, RoundedCornerShape(Theme.radius))
            .padding(Theme.gutter),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Evidence tier badge. Tapping swaps the label for its plain-language caveat. */
@Composable
fun TierBadge(tier: EvidenceTier, modifier: Modifier = Modifier) {
    var showCaveat by remember { mutableStateOf(false) }
    val tint = tier.tint(Theme.palette)

    Text(
        text = if (showCaveat) tier.caveat else tier.label,
        style = ui(11, FontWeight.SemiBold),
        color = tint,
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.13f))
            .border(1.dp, tint.copy(alpha = 0.35f), CircleShape)
            .clickable { showCaveat = !showCaveat }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Label on the left, monospaced value on the right. Used for every stat block. */
@Composable
fun FactRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = ui(14), color = Theme.palette.steel)
        Text(
            value,
            style = data(15, FontWeight.SemiBold),
            color = valueColor ?: Theme.palette.ink,
        )
    }
}

/** A stepper + slider pair for a physical quantity. */
@Composable
fun NumberRow(
    label: String,
    unit: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    step: Double,
    range: ClosedFloatingPointRange<Double>,
    decimals: Int = 2,
) {
    BenchCard {
        FieldLabel(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${value.trimmed(decimals)} $unit",
                style = data(20, FontWeight.SemiBold),
                color = Theme.palette.ink,
            )
            Spacer(Modifier.weight(1f))
            StepperButton("−") {
                onValueChange((value - step).coerceIn(range.start, range.endInclusive))
            }
            Spacer(Modifier.width(8.dp))
            StepperButton("+") {
                onValueChange((value + step).coerceIn(range.start, range.endInclusive))
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            steps = (((range.endInclusive - range.start) / step).toInt() - 1).coerceIn(0, 200),
            colors = SliderDefaults.colors(
                thumbColor = Theme.palette.amber,
                activeTrackColor = Theme.palette.amber,
                inactiveTrackColor = Theme.palette.hairline,
            ),
        )
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Theme.palette.hairline)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = data(16, FontWeight.Bold),
            color = Theme.palette.ink,
            textAlign = TextAlign.Center,
        )
    }
}
