package com.peptideadvisor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Visual direction: a lab bench, not a dashboard.
 *
 * Colours come from the materials this app is actually about — benchtop laminate,
 * amber reagent glass, frosted vial labels, deep teal reagent caps. Dynamic colour
 * is deliberately not used: on One UI it would repaint the evidence tiers from the
 * wallpaper, and those colours carry meaning.
 */
@Immutable
data class Bench(
    val bench: Color,
    val paper: Color,
    val ink: Color,
    val steel: Color,
    val amber: Color,
    val signal: Color,
    val signalMuted: Color,
    val caution: Color,
    val hairline: Color,
)

private val LightBench = Bench(
    bench = Color(0xFFE9EBE6),
    paper = Color(0xFFF7F8F5),
    ink = Color(0xFF191C18),
    steel = Color(0xFF6E756B),
    amber = Color(0xFFB87A18),
    signal = Color(0xFF2C6153),
    signalMuted = Color(0xFF5A8A7E),
    caution = Color(0xFFA34031),
    hairline = Color(0xFFD2D6CD),
)

private val DarkBench = Bench(
    bench = Color(0xFF141714),
    paper = Color(0xFF1E221E),
    ink = Color(0xFFEDEFEA),
    steel = Color(0xFF9AA296),
    amber = Color(0xFFD9A03F),
    signal = Color(0xFF5FAE97),
    signalMuted = Color(0xFF76998F),
    caution = Color(0xFFE0705C),
    hairline = Color(0xFF2E332E),
)

val LocalBench = staticCompositionLocalOf { LightBench }

object Theme {
    val palette: Bench
        @Composable @ReadOnlyComposable get() = LocalBench.current

    val gutter = 16.dp
    val radius = 10.dp
}

/** Journal voice — screen titles and answer prose only. */
fun display(size: Int, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = size.sp,
    fontWeight = weight,
)

/** Anything you might read off and act on: doses, units, purity, dates. */
fun data(size: Int, weight: FontWeight = FontWeight.Medium) = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = size.sp,
    fontWeight = weight,
)

/** Interface chrome — labels, buttons, captions. */
fun ui(size: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size.sp,
    fontWeight = weight,
)

@Composable
fun PeptideAdvisorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkBench else LightBench

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.amber,
            onPrimary = palette.paper,
            secondary = palette.signal,
            background = palette.bench,
            onBackground = palette.ink,
            surface = palette.paper,
            onSurface = palette.ink,
            surfaceVariant = palette.hairline,
            onSurfaceVariant = palette.steel,
            error = palette.caution,
            outline = palette.hairline,
        )
    } else {
        lightColorScheme(
            primary = palette.amber,
            onPrimary = palette.paper,
            secondary = palette.signal,
            background = palette.bench,
            onBackground = palette.ink,
            surface = palette.paper,
            onSurface = palette.ink,
            surfaceVariant = palette.hairline,
            onSurfaceVariant = palette.steel,
            error = palette.caution,
            outline = palette.hairline,
        )
    }

    CompositionLocalProvider(LocalBench provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(
                titleLarge = display(24),
                titleMedium = display(19),
                bodyLarge = ui(15),
                bodyMedium = ui(14),
                labelSmall = ui(11, FontWeight.SemiBold),
            ),
            content = content,
        )
    }
}
