@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kabi.pillpal.meditick.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabi.pillpal.meditick.model.*
import com.kabi.pillpal.meditick.ui.theme.DS

@Composable
fun ThemedBackground(modifier: Modifier = Modifier) {
    val c = DS.colors
    Canvas(modifier.fillMaxSize().background(c.bg)) {
        val radius = size.minDimension * .65f
        drawCircle(
            brush = Brush.radialGradient(listOf(c.mint2.copy(alpha = .22f), Color.Transparent), center = Offset(0f, 0f), radius = radius),
            radius = radius, center = Offset(size.width * .05f, size.height * .04f),
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(c.cyan.copy(alpha = .18f), Color.Transparent), center = Offset(0f, 0f), radius = radius),
            radius = radius, center = Offset(size.width, size.height * .15f),
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(c.violet.copy(alpha = .15f), Color.Transparent), center = Offset(0f, 0f), radius = radius),
            radius = radius, center = Offset(size.width * .25f, size.height),
        )
    }
}

@Composable
fun ScreenBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) { ThemedBackground(); content() }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier, radius: Dp = 24.dp, onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp), content: @Composable ColumnScope.() -> Unit,
) {
    val c = DS.colors
    val shape = RoundedCornerShape(radius)
    Surface(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape, color = c.glass, border = androidx.compose.foundation.BorderStroke(1.dp, c.line),
    ) { Column(Modifier.padding(contentPadding).animateContentSize(), content = content) }
}

@Composable
fun GradientCard(
    modifier: Modifier = Modifier, radius: Dp = 26.dp, onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = DS.colors
    val shape = RoundedCornerShape(radius)
    Box(
        modifier.clip(shape).background(
            Brush.linearGradient(listOf(c.gradStart.copy(.17f), c.gradEnd.copy(.11f), c.violet.copy(.14f))),
        ).border(1.dp, c.line2, shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun PrimaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    leading: ImageVector? = null,
) {
    val c = DS.colors
    Button(
        onClick = onClick, enabled = enabled,
        modifier = modifier.height(56.dp), shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = c.onMint,
            disabledContainerColor = c.glass3, disabledContentColor = c.ink3),
        contentPadding = PaddingValues(0.dp),
    ) {
        Row(
            Modifier.fillMaxSize().then(if (enabled) Modifier.background(c.gradient) else Modifier),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.let { Icon(it, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold))
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, color = DS.colors.ink3, fontSize = 11.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
}

@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = DS.colors
    Surface(
        modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(14.dp),
        color = if (selected) c.mint.copy(.15f) else c.glass2,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) c.mint.copy(.5f) else c.line),
    ) {
        Text(text, Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            color = if (selected) c.mint else c.ink2, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .34f)).background(tint.copy(.13f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * .48f))
    }
}

@Composable
fun MedicationIcon(medication: Medication, size: Dp = 42.dp) {
    val tint = runCatching { Color(PillColor.valueOf(medication.colorName).hex) }.getOrDefault(DS.colors.mint)
    IconTile(formIcon(medication.form), tint, size)
}

@Composable
fun SlotIcon(time: Long, size: Dp = 36.dp) {
    val tod = TimeOfDay.fromEpoch(time)
    val (icon, tint) = when (tod.hour) {
        in 5..10 -> Icons.Default.WbSunny to DS.colors.amber
        in 11..15 -> Icons.Default.LightMode to DS.colors.cyan
        in 16..20 -> Icons.Default.NightsStay to DS.colors.violet
        else -> Icons.Default.Bedtime to DS.colors.mint
    }
    IconTile(icon, tint, size)
}

fun formIcon(form: MedicationForm): ImageVector = when (form) {
    MedicationForm.pill, MedicationForm.tablet, MedicationForm.capsule, MedicationForm.gummy -> Icons.Default.Medication
    MedicationForm.liquid, MedicationForm.drops -> Icons.Default.WaterDrop
    MedicationForm.injection -> Icons.Default.Vaccines
    MedicationForm.inhaler, MedicationForm.spray -> Icons.Default.Air
    MedicationForm.cream -> Icons.Default.BackHand
    MedicationForm.patch -> Icons.Default.Healing
    MedicationForm.powder -> Icons.Default.Grain
    MedicationForm.other -> Icons.Default.MedicalServices
}

@Composable
fun ProgressRing(
    progress: Float, modifier: Modifier = Modifier, lineWidth: Dp = 13.dp,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val c = DS.colors
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = lineWidth.toPx()
            val inset = stroke / 2
            drawArc(c.ringTrack, -90f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(c.gradStart, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        center()
    }
}

@Composable
fun FriendlyEmptyState(icon: ImageVector, title: String, message: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        IconTile(icon, DS.colors.mint, 82.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = DS.colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, color = DS.colors.ink2, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
        if (action != null && onAction != null) {
            Spacer(Modifier.height(20.dp)); PrimaryButton(action, onAction, Modifier.widthIn(max = 280.dp).fillMaxWidth())
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector, tint: Color, title: String, subtitle: String? = null,
    onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = DS.colors.ink)
            subtitle?.let { Text(it, color = DS.colors.ink3, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.width(8.dp))
        trailing?.invoke() ?: if (onClick != null) Icon(Icons.Default.ChevronRight, null, tint = DS.colors.ink3, modifier = Modifier.size(19.dp)) else Unit
    }
}

@Composable
fun RowDivider() { HorizontalDivider(Modifier.padding(start = 68.dp), color = DS.colors.line) }

@Composable
fun StatusPill(text: String, color: Color) {
    Text(text.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .7.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(.12f)).padding(horizontal = 9.dp, vertical = 5.dp))
}
