package com.kabi.pillpal.meditick.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabi.pillpal.meditick.ui.components.*
import com.kabi.pillpal.meditick.ui.theme.DS

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    ScreenBackground {
        AnimatedContent(page, label = "onboarding") { value ->
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
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedAppMark()
                Spacer(Modifier.height(19.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(c.amber.copy(.12f)).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Star, null, tint = c.amber, modifier = Modifier.size(11.dp))
                    Text("  PRIVATE · NO ACCOUNT · FREE  ", color = c.amber, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = .6.sp)
                    Icon(Icons.Default.Star, null, tint = c.amber, modifier = Modifier.size(11.dp))
                }
            }
        }
        Spacer(Modifier.height(30.dp))
        Text("Never miss\na dose again.", style = MaterialTheme.typography.displayLarge.copy(
            brush = c.gradient, fontSize = 40.sp, lineHeight = 43.sp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Gentle reminders, smart schedules, and a daily ring that fills as you care for yourself.",
            color = c.ink2, fontSize = 15.5.sp, lineHeight = 22.sp, modifier = Modifier.widthIn(max = 330.dp))
        Spacer(Modifier.height(22.dp))
        FeatureRow(Icons.Default.AutoAwesome, "Type a sentence, get a schedule")
        FeatureRow(Icons.Default.NotificationsActive, "Reminders that adapt to your meals")
        FeatureRow(Icons.Default.Lock, "Your health data never leaves your phone")
        Spacer(Modifier.weight(1f))
        PrimaryButton("Get started", next, Modifier.fillMaxWidth(), leading = Icons.Default.ArrowForward)
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, DS.colors.mint, 38.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, color = DS.colors.ink2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun AnimatedAppMark() {
    val transition = rememberInfiniteTransition(label = "mark")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "clock")
    val breathe by transition.animateFloat(.96f, 1.04f, infiniteRepeatable(tween(2500), RepeatMode.Reverse), label = "breathe")
    val c = DS.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(126.dp).graphicsLayer { scaleX = breathe; scaleY = breathe }) {
                drawCircle(brush = Brush.radialGradient(listOf(c.glow.copy(.35f), Color.Transparent)))
            }
            Canvas(Modifier.size(108.dp)) {
                drawRoundRect(c.gradient, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .34f))
                val center = Offset(size.width / 2, size.height / 2)
                repeat(12) { index ->
                    val angle = Math.toRadians(index * 30.0 - 90.0)
                    val r1 = size.width * .37f; val r2 = size.width * .42f
                    drawLine(Color.White.copy(if (index % 3 == 0) .55f else .24f),
                        Offset(center.x + kotlin.math.cos(angle).toFloat() * r1, center.y + kotlin.math.sin(angle).toFloat() * r1),
                        Offset(center.x + kotlin.math.cos(angle).toFloat() * r2, center.y + kotlin.math.sin(angle).toFloat() * r2),
                        strokeWidth = if (index % 3 == 0) 3.4f else 2.2f, cap = StrokeCap.Round)
                }
                fun hand(angleDegrees: Float, length: Float, width: Float, color: Color) {
                    val a = Math.toRadians(angleDegrees.toDouble() - 90)
                    drawLine(color, center, Offset(center.x + kotlin.math.cos(a).toFloat() * length, center.y + kotlin.math.sin(a).toFloat() * length), width, StrokeCap.Round)
                }
                hand(-60f, size.width * .24f, size.width * .095f, Color(0xFFA9DDD4))
                hand(rotation, size.width * .32f, size.width * .085f, Color(0xFFDCEEE9))
                drawCircle(Color.White, size.width * .055f, center)
            }
        }
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
        Triple(Icons.Default.NotificationsActive, "Remember every dose", "Quiet, dependable reminders"),
        Triple(Icons.Default.Restaurant, "Sync with meals", "Before, with or after food"),
        Triple(Icons.Default.LocalFireDepartment, "Build a streak", "See progress without guilt"),
        Triple(Icons.Default.Inventory2, "Track refills", "Know before you run out"),
    )
    var selected by remember { mutableStateOf(setOf(0)) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Spacer(Modifier.height(22.dp))
        SectionLabel("A 30-second setup")
        Spacer(Modifier.height(10.dp))
        Text("What brings you to MediTick?", style = MaterialTheme.typography.headlineLarge, color = c.ink)
        Spacer(Modifier.height(7.dp))
        Text("Choose everything that fits. You can change it later.", color = c.ink2)
        Spacer(Modifier.height(24.dp))
        purposes.forEachIndexed { index, value ->
            val active = index in selected
            GlassCard(
                Modifier.fillMaxWidth().padding(bottom = 11.dp), radius = 21.dp,
                onClick = { selected = if (active) selected - index else selected + index },
                contentPadding = PaddingValues(15.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(value.first, if (active) c.mint else c.ink3, 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(value.second, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(value.third, color = c.ink3, fontSize = 12.sp)
                    }
                    Icon(if (active) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null,
                        tint = if (active) c.mint else c.ink3)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("No sign-up. No email. Your health data stays on this device.",
            color = c.ink3, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp))
        PrimaryButton("Build my routine", finish, Modifier.fillMaxWidth(), leading = Icons.Default.Check)
    }
}
