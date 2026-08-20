@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kabi.pillpal.meditick.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabi.pillpal.meditick.data.SettingsStore
import com.kabi.pillpal.meditick.ui.theme.DS
import kotlin.math.cos
import kotlin.math.sin

// MARK: - Haptics
//
// The iOS build plays a haptic on every meaningful touch (Haptics.swift);
// this is the Android twin, routed through the view so it follows the
// system "touch feedback" setting, and gated on the in-app haptics toggle.

class Haptics internal constructor(private val view: View, private val enabled: () -> Boolean) {
    private fun play(constant: Int) { if (enabled()) view.performHapticFeedback(constant) }

    /** Light tap for ordinary presses. */
    fun tap() = play(HapticFeedbackConstants.KEYBOARD_TAP)

    /** Crisp tick for selections, chips and the onboarding clock. */
    fun tick() = play(HapticFeedbackConstants.CLOCK_TICK)

    /** Firm confirmation for saves and dose check-offs. */
    fun success() = play(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.CONTEXT_CLICK,
    )

    fun warning() = play(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS,
    )
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val settings = SettingsStore.get(LocalContext.current)
    return remember(view) { Haptics(view) { settings.hapticsEnabled } }
}

// MARK: - Press feedback

/** The spring every press animation shares — quick with a soft overshoot. */
val PressSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 900f)

/**
 * Scale-on-press, matching the iOS `BouncyPressStyle`. The interaction
 * source must be the same one handed to the clickable/Surface so the press
 * state is observed, not duplicated.
 */
fun Modifier.pressScale(
    interaction: MutableInteractionSource,
    pressedScale: Float = 0.965f,
): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, PressSpring, label = "pressScale")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * One-shot entry: fade in while drifting up, staggered by [index]. Applied
 * to top-of-screen blocks so screens assemble instead of popping in.
 */
fun Modifier.appearFluidly(index: Int = 0): Modifier = composed {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(durationMillis = 360, delayMillis = 40 * index.coerceAtMost(7), easing = LinearOutSlowInEasing),
        label = "appear",
    )
    graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 26.dp.toPx()
    }
}

// MARK: - Buttons

/**
 * Circular utility button (the iOS `RoundIconButton`): a glass rounded
 * square with a hairline border, press bounce and a light haptic. Used for
 * back / close / edit in every top bar.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = DS.colors.ink,
) {
    val c = DS.colors
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.size(42.dp).pressScale(interaction, 0.92f)
            .clip(shape).background(c.glass2).border(1.dp, c.line2, shape)
            .clickable(interaction, ripple()) { haptics.tap(); onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(19.dp)) }
}

/** Secondary full-width action (the iOS `GhostButtonStyle`): glass capsule. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    tint: Color = DS.colors.ink,
    borderTint: Color = DS.colors.line2,
    fillTint: Color = DS.colors.glass2,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(50)
    Row(
        modifier.height(52.dp).pressScale(interaction)
            .clip(shape).background(fillTint).border(1.dp, borderTint, shape)
            .clickable(interaction, ripple()) { haptics.tap(); onClick() },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { Icon(it, null, tint = tint, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)) }
        Text(text, color = tint, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Destructive full-width action (the iOS `DangerButtonStyle`). */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: ImageVector? = null) {
    val coral = DS.colors.coral
    GhostButton(
        text, onClick, modifier, leading,
        tint = coral, borderTint = coral.copy(.3f), fillTint = coral.copy(.12f),
    )
}

// MARK: - Celebration

private class ConfettiPiece(index: Int) {
    // Deterministic layout, identical to the iOS ConfettiBurst.
    val xFraction = (6 + (index * 37) % 89) / 100f
    val delay = ((index * 13) % 10) / 20f          // 0…0.45s stagger
    val rotation = ((index * 47) % 360).toFloat()
    val colorIndex = index % 5
}

/**
 * Party-popper confetti falling from the top of the screen — a 1:1 port of
 * the iOS `ConfettiBurst` on the medication-saved screen: 18 pieces drop
 * from above the screen to below it over 1.5s, tumbling two full turns.
 */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier) {
    val c = DS.colors
    val pieces = remember { List(18, ::ConfettiPiece) }
    // One master clock; each piece reads its own delayed window from it.
    val clock = remember { Animatable(0f) }
    val total = 1.5f + 0.45f
    LaunchedEffect(Unit) { clock.animateTo(1f, tween((total * 1000).toInt(), easing = LinearEasing)) }
    val colors = listOf(c.gradStart, c.gradEnd, c.violet, c.amber, c.coral)
    androidx.compose.foundation.Canvas(modifier.fillMaxSize()) {
        val elapsed = clock.value * total
        val w = 8.dp.toPx(); val h = 14.dp.toPx()
        pieces.forEach { piece ->
            val t = ((elapsed - piece.delay) / 1.5f).coerceIn(0f, 1f)
            if (t >= 1f) return@forEach
            val eased = t * t // easeIn, like the iOS fall
            val x = size.width * piece.xFraction
            val y = -30.dp.toPx() + eased * (size.height + 70.dp.toPx())
            rotate(degrees = piece.rotation + 720f * eased, pivot = Offset(x, y)) {
                drawRoundRect(
                    color = colors[piece.colorIndex].copy(alpha = 1f - 0.3f * eased),
                    topLeft = Offset(x - w / 2, y - h / 2),
                    size = androidx.compose.ui.geometry.Size(w, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                )
            }
        }
    }
}

// MARK: - Top bars

/**
 * The shared modal/detail top bar: round glass buttons flanking a centered
 * two-line title, mirroring the iOS form and detail headers.
 */
@Composable
fun ScreenTopBar(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingDescription: String? = null,
    onLeading: (() -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    trailingDescription: String? = null,
    onTrailing: (() -> Unit)? = null,
    trailingTint: Color = DS.colors.ink,
    modifier: Modifier = Modifier,
) {
    val c = DS.colors
    Row(
        modifier.height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null && onLeading != null) RoundIconButton(leadingIcon, leadingDescription, onLeading)
        else Spacer(Modifier.width(42.dp))
        androidx.compose.foundation.layout.Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            subtitle?.let { Text(it, color = c.ink3, fontSize = 11.sp, maxLines = 1) }
        }
        if (trailingIcon != null && onTrailing != null) RoundIconButton(trailingIcon, trailingDescription, onTrailing, tint = trailingTint)
        else Spacer(Modifier.width(42.dp))
    }
}

/** Consistent bottom-sheet drag handle in the theme's hairline color. */
@Composable
fun SheetDragHandle() {
    androidx.compose.material3.BottomSheetDefaults.DragHandle(color = DS.colors.line2)
}
