package com.kabi.pillpal.meditick.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClose) { Box(Modifier.size(38.dp).clip(CircleShape).background(DS.colors.glass2), contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, "Close", tint = DS.colors.ink2) } }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                IconTile(Icons.Default.Medication, DS.colors.mint, 72.dp)
                Spacer(Modifier.height(18.dp)); Text("MediTick Pro", color = DS.colors.ink, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                Spacer(Modifier.height(12.dp)); Text("Never wonder “did I take it?” again.", color = DS.colors.ink, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp,
                    lineHeight = 34.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Feature(Icons.Default.NotificationsActive, DS.colors.amber, "Alerts that keep your routine visible")
                Feature(Icons.Default.Alarm, DS.colors.cyan, "Follow-ups until every dose is ticked")
                Feature(Icons.Default.Medication, DS.colors.violet, "Unlimited meds and prescriptions")
                Feature(Icons.Default.BarChart, DS.colors.mint, "Streaks, insights and widgets")
                Spacer(Modifier.weight(1f))
            }
            if (billing.plans.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    billing.plans.forEach { PlanCard(it, it.id == (selected?.id ?: selectedId), { selectedId = it.id }, Modifier.weight(1f)) }
                }
            } else {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(15.dp)) {
                    Text(if (billing.isLoading) "Loading Google Play plans…" else billing.lastMessage ?: "Subscription plans are unavailable.", color = DS.colors.ink2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton(if (selected == null) "Try again" else "Upgrade to Pro", {
                if (selected != null && activity != null) billing.purchase(activity, selected) else billing.refresh()
            }, Modifier.fillMaxWidth(), enabled = !billing.isLoading)
            selected?.let { Text(if (it.id == "lifetime") "One payment. Pro for good." else "Renews automatically. Cancel any time in Google Play.",
                color = DS.colors.ink3, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 9.dp)) }
            Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.Center) {
                TextButton({ billing.restore() }) { Text("Restore purchases", color = DS.colors.ink2, fontSize = 12.sp) }
            }
        }
    }
}

@Composable private fun Feature(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(color.copy(.13f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.width(12.dp)); Text(text, color = DS.colors.ink2, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable private fun PlanCard(plan: BillingPlan, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val border = if (selected) DS.colors.mint else DS.colors.line2
    Column(modifier.height(114.dp).clip(RoundedCornerShape(22.dp)).background(DS.colors.glass).border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(plan.title, color = DS.colors.ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Text(plan.price, color = if (selected) DS.colors.mint else DS.colors.ink2, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(plan.subtitle, color = DS.colors.ink3, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 12.sp)
    }
}
