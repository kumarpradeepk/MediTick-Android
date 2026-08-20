@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kabi.pillpal.meditick.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.kabi.pillpal.meditick.R
import androidx.compose.ui.unit.dp
import com.kabi.pillpal.meditick.billing.BillingManager
import com.kabi.pillpal.meditick.data.AppRepository
import com.kabi.pillpal.meditick.data.SettingsStore
import com.kabi.pillpal.meditick.ui.screens.*
import com.kabi.pillpal.meditick.ui.theme.DS

private sealed interface Route {
    data object Main : Route
    data class MedicationForm(val editingId: String? = null, val prescriptionId: String? = null) : Route
    data class MedicationDetail(val id: String) : Route
    data class PrescriptionDetail(val id: String) : Route
    data object Paywall : Route
}

private enum class MainTab(@StringRes val title: Int, val selected: ImageVector, val idle: ImageVector) {
    TODAY(R.string.tab_today, Icons.Filled.Home, Icons.Outlined.Home),
    CARE(R.string.tab_treatments, Icons.Filled.Medication, Icons.Outlined.Medication),
    PROGRESS(R.string.tab_progress, Icons.Filled.BarChart, Icons.Outlined.BarChart),
    SETTINGS(R.string.tab_settings, Icons.Filled.Tune, Icons.Outlined.Tune),
}

@Composable
fun MediTickApp(
    repository: AppRepository, settings: SettingsStore, billing: BillingManager,
    requestNotificationPermission: () -> Unit,
) {
    if (!settings.hasCompletedOnboarding) {
        OnboardingScreen(onFinished = { settings.completeOnboarding(); requestNotificationPermission() })
        return
    }

    val routes = remember { mutableStateListOf<Route>(Route.Main) }
    val current = routes.last()
    val navigate: (Route) -> Unit = { routes += it }
    val back = { if (routes.size > 1) routes.removeAt(routes.lastIndex) }
    BackHandler(enabled = routes.size > 1, onBack = back)

    Box(Modifier.fillMaxSize()) {
        when (current) {
            Route.Main -> MainShell(repository, settings, billing, navigate, requestNotificationPermission)
            is Route.MedicationForm -> MedicationFormScreen(
                repository = repository, editingId = current.editingId, prescriptionId = current.prescriptionId,
                onClose = back, onSaved = back,
            )
            is Route.MedicationDetail -> MedicationDetailScreen(
                repository, current.id, onBack = back,
                onEdit = { navigate(Route.MedicationForm(editingId = current.id)) },
            )
            is Route.PrescriptionDetail -> PrescriptionDetailScreen(
                repository, current.id, billing.isPro, onBack = back,
                onMedication = { navigate(Route.MedicationDetail(it)) },
                onAddMedication = {
                    if (billing.isPro || repository.activeMedications.isEmpty()) navigate(Route.MedicationForm(prescriptionId = current.id))
                    else navigate(Route.Paywall)
                },
            )
            Route.Paywall -> PaywallScreen(billing, onClose = back)
        }
    }
}

@Composable
private fun MainShell(
    repository: AppRepository, settings: SettingsStore, billing: BillingManager,
    navigate: (Route) -> Unit, requestNotificationPermission: () -> Unit,
) {
    var tabName by rememberSaveable { mutableStateOf(MainTab.TODAY.name) }
    var showAddChoice by remember { mutableStateOf(false) }
    var showPrescriptionEditor by remember { mutableStateOf(false) }
    val tab = MainTab.valueOf(tabName)
    Box(Modifier.fillMaxSize()) {
        when (tab) {
            MainTab.TODAY -> TodayScreen(repository,
                onAdd = { showAddChoice = true },
                onMedication = { navigate(Route.MedicationDetail(it)) },
            )
            MainTab.CARE -> CareScreen(repository,
                onAddMedication = { prescriptionId ->
                    if (billing.isPro || repository.activeMedications.isEmpty()) navigate(Route.MedicationForm(prescriptionId = prescriptionId))
                    else navigate(Route.Paywall)
                },
                onMedication = { navigate(Route.MedicationDetail(it)) },
                onPrescription = { navigate(Route.PrescriptionDetail(it)) },
            )
            MainTab.PROGRESS -> ProgressScreen(repository, settings, billing.isPro,
                onRequirePro = { navigate(Route.Paywall) })
            MainTab.SETTINGS -> SettingsScreen(repository, settings, billing,
                onShowPaywall = { navigate(Route.Paywall) }, requestNotificationPermission = requestNotificationPermission)
        }
        Dock(
            selected = tab, onSelect = { tabName = it.name },
            onAdd = { showAddChoice = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    if (showAddChoice) ModalBottomSheet(onDismissRequest = { showAddChoice = false }, containerColor = DS.colors.bg3) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
            Text(stringResource(R.string.add_sheet_title), style = MaterialTheme.typography.titleLarge, color = DS.colors.ink)
            Spacer(Modifier.height(14.dp))
            AddChoiceRow(Icons.Default.Description, stringResource(R.string.add_sheet_prescription), stringResource(R.string.add_sheet_prescription_sub)) {
                showAddChoice = false; showPrescriptionEditor = true
            }
            Spacer(Modifier.height(9.dp))
            AddChoiceRow(Icons.Default.Medication, stringResource(R.string.add_sheet_medication), stringResource(R.string.add_sheet_medication_sub)) {
                showAddChoice = false
                if (billing.isPro || repository.activeMedications.isEmpty()) navigate(Route.MedicationForm()) else navigate(Route.Paywall)
            }
        }
    }
    if (showPrescriptionEditor) PrescriptionEditor(null, { showPrescriptionEditor = false }) { value, _ ->
        repository.addPrescription(value); showPrescriptionEditor = false
    }
}

@Composable
private fun AddChoiceRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = DS.colors.glass, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = DS.colors.mint); Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = DS.colors.ink); Text(subtitle, color = DS.colors.ink3) }
            Icon(Icons.Default.ChevronRight, null, tint = DS.colors.ink3)
        }
    }
}

@Composable
private fun Dock(selected: MainTab, onSelect: (MainTab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = DS.colors
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding()
            .height(70.dp).fillMaxWidth().shadow(22.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp)).background(c.dockBg).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockItem(MainTab.TODAY, selected, onSelect, Modifier.weight(1f))
        DockItem(MainTab.CARE, selected, onSelect, Modifier.weight(1f))
        Box(
            Modifier.padding(horizontal = 6.dp).size(58.dp).shadow(15.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp)).background(c.gradient).clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Add, stringResource(R.string.add_sheet_medication), tint = c.onMint, modifier = Modifier.size(27.dp)) }
        DockItem(MainTab.PROGRESS, selected, onSelect, Modifier.weight(1f))
        DockItem(MainTab.SETTINGS, selected, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun DockItem(item: MainTab, selected: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier) {
    val active = item == selected
    val c = DS.colors
    Column(
        modifier.fillMaxHeight().clickable { onSelect(item) },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(width = 46.dp, height = 30.dp).clip(RoundedCornerShape(12.dp))
                .background(if (active) c.mint.copy(.13f) else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { Icon(if (active) item.selected else item.idle, stringResource(item.title), tint = if (active) c.mint else c.ink3, modifier = Modifier.size(20.dp)) }
        Text(stringResource(item.title), style = MaterialTheme.typography.labelSmall, color = if (active) c.mint else c.ink3)
    }
}
