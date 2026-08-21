package com.kabi.pillpal.meditick.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabi.pillpal.meditick.R
import com.kabi.pillpal.meditick.ui.theme.DS
import androidx.compose.ui.res.stringResource
import kotlin.math.floor

//
// Instant Scan v2 — the shared "this is the AI thing" vocabulary: a slowly
// rotating aurora border, the NEW pill, the AI-filled tag, and the entry card
// that appears on Today, in Add New and in step 1 of the add flow. Keeping it
// in one place is what stops the three entry points from drifting apart.
//

// MARK: - Aurora border

/** Samples a cyclic colour ramp; `colors.last()` must equal `colors.first()`. */
private fun cyclicColor(colors: List<Color>, t: Float): Color {
    val segments = colors.size - 1
    val x = (t - floor(t)) * segments
    val index = x.toInt().coerceIn(0, segments - 1)
    return lerp(colors[index], colors[index + 1], x - index)
}

/**
 * A sweep gradient rotated by `phase` turns. Compose's sweep gradient has a
 * fixed start angle, so the rotation is baked into resampled colour stops
 * instead — rotating the draw call itself would rotate the rounded rect too.
 */
private fun rotatingSweep(colors: List<Color>, phase: Float, center: Offset): Brush {
    val steps = 24
    val stops = Array(steps + 1) { step ->
        val t = step / steps.toFloat()
        t to cyclicColor(colors, t - phase)
    }
    return Brush.sweepGradient(*stops, center = center)
}

/** The slowly turning mint → cyan → violet ring that marks an AI surface. */
fun Modifier.auroraBorder(radius: Dp, width: Dp = 2.5.dp, durationMillis: Int = 3600): Modifier = composed {
    val c = DS.colors
    val colors = listOf(c.gradStart, c.cyan, c.violet, c.gradStart)
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(durationMillis, easing = LinearEasing)), label = "auroraPhase",
    )
    drawWithContent {
        drawContent()
        val stroke = width.toPx()
        val corner = (radius.toPx() - stroke / 2f).coerceAtLeast(0f)
        drawRoundRect(
            brush = rotatingSweep(colors, phase, center),
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(stroke),
        )
    }
}

// MARK: - Badges

/** The violet → cyan "NEW" pill that rides beside the Instant Scan title. */
@Composable
fun NewBadge(modifier: Modifier = Modifier) {
    val c = DS.colors
    Text(
        stringResource(R.string.scan_badge_new),
        color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(listOf(c.violet, c.cyan)))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** "AI FILLED" / "AI" — the tag that floats over a field the scan populated. */
@Composable
fun AIFilledBadge(text: String, modifier: Modifier = Modifier) {
    val c = DS.colors
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(c.gradient)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Default.AutoAwesome, null, tint = c.onMint, modifier = Modifier.size(8.dp))
        Text(text, color = c.onMint, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
    }
}

/** The "SCANNED" chip on a medication Instant Scan created. */
@Composable
fun ScannedBadge(modifier: Modifier = Modifier) {
    val c = DS.colors
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(c.mint.copy(alpha = .12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Default.AutoAwesome, null, tint = c.mint, modifier = Modifier.size(9.dp))
        Text(
            stringResource(R.string.scan_badge_scanned),
            color = c.mint, fontSize = 10.5.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp,
        )
    }
}

// MARK: - Scanner tile

/** The gradient scan-frame tile with a light beam sweeping down it. */
@Composable
fun ScanIconTile(size: Dp = 52.dp, radius: Dp = 18.dp) {
    val c = DS.colors
    val transition = rememberInfiniteTransition(label = "scanTile")
    val beam by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "beam",
    )
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).background(c.gradient),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxWidth(0.88f).height(size * 0.3f)
                .offset(y = (size - size * 0.3f) * (beam - 0.5f))
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = .5f), Color.Transparent),
                    ),
                ),
        )
        Icon(Icons.Default.DocumentScanner, null, tint = c.onMint, modifier = Modifier.size(size * 0.46f))
    }
}

// MARK: - Entry card

/**
 * The Instant Scan entry row. Used verbatim on Today, in the Add New sheet and
 * in step 1 of the add flow so the feature always looks like the same door.
 */
@Composable
fun InstantScanCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    borderWidth: Dp = 2.5.dp,
    onClick: () -> Unit,
) {
    val c = DS.colors
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(radius)
    Surface(
        onClick = { haptics.tap(); onClick() },
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().pressScale(interaction, 0.98f).auroraBorder(radius, borderWidth),
        shape = shape,
        color = c.bg2,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScanIconTile()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, color = c.ink, fontSize = 16.5.sp, fontWeight = FontWeight.Black)
                    NewBadge()
                }
                Text(subtitle, color = c.ink2, fontSize = 12.5.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = c.ink3, modifier = Modifier.size(18.dp))
        }
    }
}

// MARK: - Toast

/** A one-line confirmation over the dock — the twin of iOS `ToastCenter`. */
object ToastCenter {
    var message by mutableStateOf<String?>(null)
        private set

    private var token = 0

    fun say(text: String) {
        message = text
        token += 1
    }

    /** Cleared by [ToastHost] once the message has had its time on screen. */
    fun clear() {
        message = null
    }

    val revision: Int get() = token
}

@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val c = DS.colors
    val message = ToastCenter.message
    LaunchedEffect(ToastCenter.revision) {
        if (message != null) {
            kotlinx.coroutines.delay(2600)
            ToastCenter.clear()
        }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn(tween(200)) + scaleIn(tween(260), initialScale = 0.92f),
            exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.96f),
        ) {
            Row(
                Modifier.padding(bottom = 104.dp).navigationBarsPadding()
                    .clip(RoundedCornerShape(50)).background(c.toastBg)
                    .padding(horizontal = 20.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = c.mint, modifier = Modifier.size(14.dp))
                Text(
                    message.orEmpty(), color = c.toastInk, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                )
            }
        }
    }
}
