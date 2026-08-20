package com.kabi.pillpal.meditick.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.kabi.pillpal.meditick.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabi.pillpal.meditick.ui.components.*
import com.kabi.pillpal.meditick.ui.theme.DS
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    ScreenBackground {
        AnimatedContent(
            page,
            transitionSpec = {
                (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 4 } + fadeIn(tween(220))) togetherWith
                    fadeOut(tween(160))
            },
            label = "onboarding",
        ) { value ->
            if (value == 0) SplashStep { page = 1 } else PurposeStep(onFinished)
        }
    }
}

@Composable
private fun SplashStep(next: () -> Unit) {
    val c = DS.colors
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(Modifier.weight(.25f))
        Box(Modifier.fillMaxWidth().appearFluidly(0), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CapsuleTickMark()
                Spacer(Modifier.height(19.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(c.amber.copy(.12f)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Star, null, tint = c.amber, modifier = Modifier.size(11.dp))
                    Text(stringResource(R.string.onboarding_badge), color = c.amber, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = .6.sp)
                    Icon(Icons.Default.Star, null, tint = c.amber, modifier = Modifier.size(11.dp))
                }
            }
        }
        Spacer(Modifier.height(30.dp))
        Text(stringResource(R.string.onboarding_headline), style = MaterialTheme.typography.displayLarge.copy(
            brush = c.gradient, fontSize = 40.sp, lineHeight = 43.sp),
            modifier = Modifier.appearFluidly(1),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.onboarding_subhead),
            color = c.ink2, fontSize = 15.5.sp, lineHeight = 22.sp,
            modifier = Modifier.widthIn(max = 330.dp).appearFluidly(2))
        Spacer(Modifier.height(22.dp))
        FeatureRow(Icons.Default.AutoAwesome, stringResource(R.string.onboarding_feature_sentence), Modifier.appearFluidly(3))
        FeatureRow(Icons.Default.NotificationsActive, stringResource(R.string.onboarding_feature_meals), Modifier.appearFluidly(4))
        FeatureRow(Icons.Default.Lock, stringResource(R.string.onboarding_feature_private), Modifier.appearFluidly(5))
        Spacer(Modifier.weight(1f))
        PrimaryButton(stringResource(R.string.action_get_started), next, Modifier.fillMaxWidth().appearFluidly(6), leading = Icons.Default.ArrowForward)
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, DS.colors.mint, 38.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, color = DS.colors.ink2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/**
 * The "Capsule Tick" brand mark, ported from the iOS boot splash: the two
 * capsule arms of the checkmark draw themselves in over the tick ring, the
 * glow blooms, then a small second hand keeps ticking — each advance lands
 * with a springy overshoot and a CLOCK_TICK haptic, so the mark feels like
 * a watch, not a static icon.
 */
@Composable
private fun CapsuleTickMark() {
    val c = DS.colors
    val haptics = rememberHaptics()

    val detailsAlpha = remember { Animatable(0f) }
    val mintProgress = remember { Animatable(0f) }   // short arm
    val whiteProgress = remember { Animatable(0f) }  // long arm
    val glow = remember { Animatable(0f) }
    // The hand's absolute angle in degrees; advanced discretely each second.
    val handAngle = remember { Animatable(0f) }

    val transition = rememberInfiniteTransition(label = "mark")
    val breathe by transition.animateFloat(.97f, 1.03f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "breathe")

    LaunchedEffect(Unit) {
        // Draw-on timeline, matching the iOS splash beats.
        delay(120)
        detailsAlpha.animateTo(1f, tween(280))
        mintProgress.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        whiteProgress.animateTo(1f, tween(440, easing = FastOutSlowInEasing))
        glow.animateTo(0.8f, tween(320))
        haptics.success()
        // Then the watch starts: one springy 6° tick per second.
        while (true) {
            delay(1000)
            haptics.tick()
            handAngle.animateTo(
                handAngle.value + 6f,
                spring(dampingRatio = 0.32f, stiffness = 700f),
            )
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(140.dp).graphicsLayer { scaleX = breathe; scaleY = breathe }) {
                drawCircle(brush = Brush.radialGradient(listOf(c.glow.copy(.42f * glow.value.coerceAtLeast(.3f)), Color.Transparent)))
            }
            Canvas(Modifier.size(112.dp)) {
                val s = size.width
                val center = Offset(s / 2, s / 2)
                // Gradient tile.
                drawRoundRect(c.gradient, cornerRadius = CornerRadius(s * .3f))

                // Tick ring — majors brighter, like the icon.
                repeat(12) { index ->
                    val angle = Math.toRadians(index * 30.0 - 90.0)
                    val major = index % 3 == 0
                    val r1 = s * .36f; val r2 = s * .42f
                    drawLine(
                        Color.White.copy((if (major) .55f else .22f) * detailsAlpha.value),
                        Offset(center.x + cos(angle).toFloat() * r1, center.y + sin(angle).toFloat() * r1),
                        Offset(center.x + cos(angle).toFloat() * r2, center.y + sin(angle).toFloat() * r2),
                        strokeWidth = if (major) s * .028f else s * .018f, cap = StrokeCap.Round,
                    )
                }

                // The ticking second hand, behind the checkmark.
                run {
                    val a = Math.toRadians(handAngle.value.toDouble() - 90)
                    drawLine(
                        Color.White.copy(.4f * detailsAlpha.value), center,
                        Offset(center.x + cos(a).toFloat() * s * .33f, center.y + sin(a).toFloat() * s * .33f),
                        strokeWidth = s * .03f, cap = StrokeCap.Round,
                    )
                }

                // Checkmark arms in icon-space (240×240), drawn on by progress.
                fun iconPoint(x: Float, y: Float) = Offset(x / 240f * s, y / 240f * s)
                fun arm(from: Offset, to: Offset, progress: Float, color: Color) {
                    if (progress <= 0f) return
                    val end = Offset(from.x + (to.x - from.x) * progress, from.y + (to.y - from.y) * progress)
                    // Soft drop shadow first, then the capsule stroke.
                    drawLine(Color(0x57021E1A), from + Offset(s * .017f, s * .025f), end + Offset(s * .017f, s * .025f),
                        strokeWidth = s * .18f, cap = StrokeCap.Round)
                    drawLine(color, from, end, strokeWidth = s * .18f, cap = StrokeCap.Round)
                }
                arm(iconPoint(66f, 128f), iconPoint(102f, 164f), mintProgress.value, Color(0xFFA9DDD4))
                arm(iconPoint(102f, 164f), iconPoint(172f, 84f), whiteProgress.value, Color(0xFFF4FAF9))

                // Cap gleam once the draw completes.
                if (whiteProgress.value >= 1f) {
                    drawCircle(Color.White.copy(.9f * glow.value), s * .026f, iconPoint(168f, 79f))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Text("Medi", color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text("Tick", style = TextStyle(brush = c.gradient, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp))
        }
    }
}

@Composable
private fun PurposeStep(finish: () -> Unit) {
    val c = DS.colors
    val purposes = listOf(
        Triple(Icons.Default.NotificationsActive, R.string.onboarding_purpose_doses, R.string.onboarding_purpose_doses_sub),
        Triple(Icons.Default.Restaurant, R.string.onboarding_purpose_meals, R.string.onboarding_purpose_meals_sub),
        Triple(Icons.Default.LocalFireDepartment, R.string.onboarding_purpose_streak, R.string.onboarding_purpose_streak_sub),
        Triple(Icons.Default.Inventory2, R.string.onboarding_purpose_refills, R.string.onboarding_purpose_refills_sub),
    )
    var selected by remember { mutableStateOf(setOf(0)) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Spacer(Modifier.height(22.dp))
        SectionLabel(stringResource(R.string.onboarding_setup_label), Modifier.appearFluidly(0))
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.onboarding_question), style = MaterialTheme.typography.headlineLarge, color = c.ink, modifier = Modifier.appearFluidly(1))
        Spacer(Modifier.height(7.dp))
        Text(stringResource(R.string.onboarding_choose_all), color = c.ink2, modifier = Modifier.appearFluidly(2))
        Spacer(Modifier.height(24.dp))
        purposes.forEachIndexed { index, value ->
            val active = index in selected
            GlassCard(
                Modifier.fillMaxWidth().padding(bottom = 11.dp).appearFluidly(3 + index), radius = 21.dp,
                onClick = { selected = if (active) selected - index else selected + index },
                contentPadding = PaddingValues(15.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(value.first, if (active) c.mint else c.ink3, 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(value.second), color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(stringResource(value.third), color = c.ink3, fontSize = 12.sp)
                    }
                    Icon(if (active) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                        tint = if (active) c.mint else c.ink3)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.onboarding_no_signup),
            color = c.ink3, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp))
        PrimaryButton(stringResource(R.string.onboarding_build_routine), finish, Modifier.fillMaxWidth(), leading = Icons.Default.Check)
    }
}
