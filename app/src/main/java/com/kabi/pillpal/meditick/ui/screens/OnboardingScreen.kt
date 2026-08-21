package com.kabi.pillpal.meditick.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
 * The app icon, alive on the very first screen — a 1:1 port of the iOS
 * `AnimatedAppMark`: a clean vector dial with exactly two capsule pill
 * hands, one fixed at 10 o'clock and one that ticks once a second with a
 * springy overshoot, a breathing glow, a light sweep every few seconds,
 * and the "Tick" in the wordmark vibrating in sync with each tick.
 */
@Composable
private fun CapsuleTickMark() {
    val c = DS.colors
    val haptics = rememberHaptics()
    val density = LocalDensity.current

    val size = 108.dp
    val handLength = size * 0.32f
    val handWidth = size * 0.095f
    val staticHandLength = size * 0.24f
    val staticHandWidth = size * 0.105f
    // 10 o'clock — matches the short left arm of the app's own checkmark.
    val staticHandAngle = -60f

    var appeared by remember { mutableStateOf(false) }
    var tickCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        appeared = true
        // The clock: advance the second hand — and the "Tick" vibrate —
        // once a second for as long as this screen is on screen.
        while (true) {
            delay(1000)
            tickCount += 1
            haptics.tick()
        }
    }

    // Entrance: scale up from 0.6 with a soft spring, like the iOS mark.
    val entrance by animateFloatAsState(if (appeared) 1f else 0f, spring(dampingRatio = 0.68f, stiffness = 80f), label = "entrance")

    val transition = rememberInfiniteTransition(label = "mark")
    // Breathing: glow swells and brightens on a slow 3.2s cycle.
    val breathe by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    // Light sweep across the tile every 2.6s.
    val shimmerPhase by transition.animateFloat(
        -1f, 1.4f,
        infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart, StartOffset(900)),
        label = "shimmer",
    )

    // The second hand lands with an overshoot (iOS interpolatingSpring
    // stiffness 260 / damping 14 ≈ damping ratio 0.43).
    val handAngle by animateFloatAsState(tickCount * 6f, spring(dampingRatio = 0.43f, stiffness = 260f), label = "hand")
    // Drives the "Tick" shake; settles back to center each full unit.
    val shake by animateFloatAsState(tickCount.toFloat(), tween(320, easing = FastOutSlowInEasing), label = "shake")

    val tileShape = RoundedCornerShape(size * 0.34f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size * 1.4f), contentAlignment = Alignment.Center) {
            // Breathing glow bloom behind the tile.
            Canvas(
                Modifier.size(size * 2.05f).graphicsLayer {
                    val s = .92f + .16f * breathe
                    scaleX = s; scaleY = s
                    alpha = (.55f + .35f * breathe) * entrance
                },
            ) {
                drawCircle(Brush.radialGradient(listOf(c.glow.copy(.4f), Color.Transparent)))
            }

            // Tile + dial + shimmer, breathing gently and springing in.
            Box(
                Modifier.size(size).graphicsLayer {
                    val s = (1f + .03f * breathe) * (0.6f + 0.4f * entrance)
                    scaleX = s; scaleY = s
                    alpha = entrance
                },
            ) {
                Canvas(Modifier.size(size).clip(tileShape)) {
                    val s = this.size.width
                    val center = Offset(s / 2, s / 2)
                    drawRoundRect(c.gradient, cornerRadius = CornerRadius(s * .34f))
                    // Tick ring — majors brighter, exactly the iOS spacing.
                    repeat(12) { index ->
                        val angle = Math.toRadians(index * 30.0 - 90.0)
                        val major = index % 3 == 0
                        val half = (if (major) 9f else 6f) / 2f * density.density
                        val r = s * .4f
                        val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                        drawLine(
                            Color.White.copy(if (major) .55f else .22f),
                            center + dir * (r - half), center + dir * (r + half),
                            strokeWidth = (if (major) 3.4f else 2.2f) * density.density, cap = StrokeCap.Round,
                        )
                    }
                    // Faint inner circle.
                    drawCircle(Color.White.copy(.18f), radius = s * .4f, center = center, style = Stroke(1f * density.density))
                    // Light sweep: a tilted soft white bar gliding across.
                    rotate(24f, center) {
                        val x = center.x + shimmerPhase * s * 1.6f
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(.55f), Color.Transparent),
                                startX = x - s * .25f, endX = x + s * .25f,
                            ),
                            topLeft = Offset(x - s * .25f, -s * .4f),
                            size = androidx.compose.ui.geometry.Size(s * .5f, s * 1.8f),
                        )
                    }
                }
            }

            // Each hand pivots about its own bottom edge, then is lifted so
            // that pivot sits exactly on the dial center — deterministic,
            // whatever order the layer applies its transforms in.
            // Static short hand, fixed at 10 o'clock.
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                DetailedPillHand(
                    staticHandWidth, staticHandLength, Color(0xFFA9DDD4),
                    Modifier.graphicsLayer {
                        alpha = entrance
                        translationY = -staticHandLength.toPx() / 2
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        rotationZ = staticHandAngle
                    },
                )
            }
            // The ticking second hand.
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                DetailedPillHand(
                    handWidth, handLength, Color(0xFFDCEEE9),
                    Modifier.graphicsLayer {
                        alpha = entrance
                        translationY = -handLength.toPx() / 2
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        rotationZ = handAngle
                    },
                )
            }
            // Center cap.
            Box(
                Modifier.size(handWidth * 1.3f).graphicsLayer { alpha = entrance }
                    .shadow(2.dp, CircleShape, ambientColor = Color.Black.copy(.2f), spotColor = Color.Black.copy(.2f))
                    .clip(CircleShape).background(Color.White)
                    .border(1.5.dp, c.mint, CircleShape),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Text("Medi", color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Text(
                "Tick", style = TextStyle(brush = c.gradient, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
                modifier = Modifier.graphicsLayer {
                    // Quick horizontal vibrate, in sync with each tick.
                    translationX = 3.dp.toPx() * sin(shake * Math.PI.toFloat() * 3f)
                },
            )
        }
    }
}

/**
 * Capsule detailing shared by both hands, matching the iOS `detailedPill`:
 * a white-to-pale-tint body, a soft shaded underside, a center seam like a
 * real capsule's join, and a gloss highlight — so they read as pills, not
 * flat bars.
 */
@Composable
private fun DetailedPillHand(width: androidx.compose.ui.unit.Dp, length: androidx.compose.ui.unit.Dp, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(width = width, height = length)
            .shadow(2.dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(.26f), spotColor = Color.Black.copy(.26f))
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(Color.White, tint)))
            .border(0.75.dp, Color.Black.copy(.13f), RoundedCornerShape(50)),
    ) {
        // Shaded underside for roundness.
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(.12f))))
        // Center seam.
        Box(Modifier.align(Alignment.Center).size(width = width, height = 1.3.dp).background(Color.Black.copy(.18f)))
        // Gloss highlight, top-left.
        Box(
            Modifier.align(Alignment.TopCenter)
                .offset(x = -width * .15f, y = length * .1f)
                .size(width = width * .4f, height = length * .4f)
                .clip(RoundedCornerShape(50)).background(Color.White.copy(.7f)),
        )
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
