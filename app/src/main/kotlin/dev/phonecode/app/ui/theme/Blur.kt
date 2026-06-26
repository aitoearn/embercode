package dev.phonecode.app.ui.theme

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * OUR blur (device feedback: "not Liquid Glass, our own kind of blur"): a backdrop blur tinted
 * with the theme background at ~55% - monochrome light passing through frosted tone, no hue,
 * no refraction. Real blur on Android 12+; Haze's scrim fallback below; forced to scrim under
 * Robolectric so screenshots stay deterministic and RenderEffect never runs on the JVM.
 * Radii trimmed twice on device feedback ("minimize the blur even more") - frost, not fog.
 */
@Composable
fun phoneHaze(): HazeStyle {
    val colors = MaterialTheme.colorScheme
    return HazeStyle(
        backgroundColor = colors.background,
        tints = listOf(HazeTint(colors.background.copy(alpha = 0.55f))),
        blurRadius = 14.dp,
        noiseFactor = 0f,
    )
}

/**
 * The DISSOLVE band style (status-bar / behind-composer zones): NO TINT AT ALL - the bar areas
 * are clear glass. Legibility comes from light progressive blur plus a gentle progressive darken
 * (see [blurFade]), never from a background wash (device feedback: any tint there reads as a
 * translucent navbar strip).
 */
@Composable
fun phoneHazeBand(): HazeStyle {
    val colors = MaterialTheme.colorScheme
    return HazeStyle(
        backgroundColor = colors.background,
        tints = emptyList(),
        blurRadius = 4.dp,
        noiseFactor = 0f,
    )
}

private val isRobolectric = Build.FINGERPRINT == "robolectric"

private fun HazeEffectScope.applyDefaults() {
    if (isRobolectric) blurEnabled = false
}

/** A floating blurred pill/chip - the v2 chrome surface (clip + tinted backdrop blur). */
fun Modifier.blurPill(state: HazeState, style: HazeStyle, shape: Shape = ShapePill): Modifier =
    clip(shape).hazeEffect(state, style) { applyDefaults() }

/**
 * A dissolve band: a light blur that ramps in a little before the bar, with the content fading
 * into the page background right at the edge - a clean fade-out, not a darkening frost slab.
 * [edgeColor] is the page background the content dissolves into.
 */
fun Modifier.blurFade(state: HazeState, style: HazeStyle, fromTop: Boolean, edgeColor: Color): Modifier {
    // Ramp in a little before the bar, then dissolve the content into the background at the edge.
    val onset = CubicBezierEasing(0.55f, 0f, 0.82f, 0.6f)
    return hazeEffect(state, style) {
        applyDefaults()
        progressive = HazeProgressive.verticalGradient(
            easing = onset,
            startIntensity = if (fromTop) 1f else 0f,
            endIntensity = if (fromTop) 0f else 1f,
        )
    }.background(
        Brush.verticalGradient(
            if (fromTop) listOf(edgeColor, Color.Transparent)
            else listOf(Color.Transparent, edgeColor),
        ),
    )
}
