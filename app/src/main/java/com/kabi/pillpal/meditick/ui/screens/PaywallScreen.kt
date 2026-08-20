package com.kabi.pillpal.meditick.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.kabi.pillpal.meditick.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kabi.pillpal.meditick.billing.BillingManager
import com.kabi.pillpal.meditick.billing.BillingPlan
import com.kabi.pillpal.meditick.ui.components.*
import com.kabi.pillpal.meditick.ui.theme.DS

@Composable
fun PaywallScreen(billing: BillingManager, onClose: () -> Unit) {
    val activity = LocalActivity.current
    var selectedId by remember { mutableStateOf("yearly") }
    val selected = billing.plans.firstOrNull { it.id == selectedId } ?: billing.plans.firstOrNull()
    LaunchedEffect(billing.isPro) { if (billing.isPro) onClose() }
    ScreenBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                RoundIconButton(Icons.Default.Close, stringResource(R.string.action_close), onClose)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.appearFluidly(0)) { IconTile(Icons.Default.Medication, DS.colors.mint, 72.dp) }
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.paywall_title), color = DS.colors.ink, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, modifier = Modifier.appearFluidly(1))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.paywall_headline),
                    style = MaterialTheme.typography.headlineLarge.copy(brush = DS.colors.gradient, fontSize = 28.sp, lineHeight = 34.sp),
                    textAlign = TextAlign.Center, modifier = Modifier.appearFluidly(2))
                Spacer(Modifier.height(24.dp))
                Feature(Icons.Default.NotificationsActive, DS.colors.amber, stringResource(R.string.paywall_feature_alerts), Modifier.appearFluidly(3))
                Feature(Icons.Default.Alarm, DS.colors.cyan, stringResource(R.string.paywall_feature_follow_ups), Modifier.appearFluidly(4))
                Feature(Icons.Default.Medication, DS.colors.violet, stringResource(R.string.paywall_feature_unlimited), Modifier.appearFluidly(5))
                Feature(Icons.Default.BarChart, DS.colors.mint, stringResource(R.string.paywall_feature_insights), Modifier.appearFluidly(6))
                Spacer(Modifier.weight(1f))
            }
            if (billing.plans.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().appearFluidly(7), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    billing.plans.forEach { PlanCard(it, it.id == (selected?.id ?: selectedId), { selectedId = it.id }, Modifier.weight(1f)) }
                }
            } else {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(15.dp)) {
                    Text(if (billing.isLoading) stringResource(R.string.paywall_loading)
                        else billing.lastMessage ?: stringResource(R.string.paywall_unavailable), color = DS.colors.ink2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(if (selected == null) stringResource(R.string.action_try_again) else stringResource(R.string.paywall_upgrade), {
                if (selected != null && activity != null) billing.purchase(activity, selected) else billing.refresh()
            }, Modifier.fillMaxWidth().appearFluidly(8), enabled = !billing.isLoading)
            selected?.let { Text(if (it.id == "lifetime") stringResource(R.string.paywall_lifetime_note) else stringResource(R.string.paywall_renew_note),
                color = DS.colors.ink3, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 9.dp)) }
            Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.Center) {
                TextButton({ billing.restore() }) { Text(stringResource(R.string.paywall_restore), color = DS.colors.ink2, fontSize = 12.sp) }
            }
        }
    }
}

@Composable private fun Feature(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, color, 32.dp)
        Spacer(Modifier.width(12.dp)); Text(text, color = DS.colors.ink2, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable private fun PlanCard(plan: BillingPlan, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = DS.colors
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    // Selecting a plan pops it slightly and settles the border in.
    val border by animateColorAsState(if (selected) c.mint else c.line2, tween(200), label = "planBorder")
    val fill by animateColorAsState(if (selected) c.mint.copy(.1f) else c.glass, tween(200), label = "planFill")
    val lift by animateFloatAsState(if (selected) 1.04f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "planLift")
    Column(
        modifier.height(114.dp)
            .graphicsLayer { scaleX = lift; scaleY = lift }
            .pressScale(interaction, 0.95f)
            .clip(RoundedCornerShape(22.dp)).background(fill)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(22.dp))
            .clickable(interaction, ripple()) { haptics.tick(); onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(plan.title, color = c.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Text(plan.price, color = if (selected) c.mint else c.ink2, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(plan.subtitle, color = c.ink3, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 12.sp)
    }
}
