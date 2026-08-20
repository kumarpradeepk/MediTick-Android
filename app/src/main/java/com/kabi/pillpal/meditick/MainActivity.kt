package com.kabi.pillpal.meditick

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.kabi.pillpal.meditick.billing.BillingManager
import com.kabi.pillpal.meditick.data.AppRepository
import com.kabi.pillpal.meditick.data.SettingsStore
import com.kabi.pillpal.meditick.notifications.NotificationScheduler
import com.kabi.pillpal.meditick.ui.MediTickApp
import com.kabi.pillpal.meditick.ui.theme.MediTickTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        NotificationScheduler.scheduleAll(this)
    }

    /// Applies the in-app language before any resource is resolved.
    override fun attachBaseContext(base: android.content.Context) {
        val tag = SettingsStore.get(base).languageTag
        super.attachBaseContext(LocaleSupport.wrap(base, tag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.navigationBarColor = Color.Transparent.toArgb()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val repository = AppRepository.get(this)
        val settings = SettingsStore.get(this)
        val billing = BillingManager.get(this)
        NotificationScheduler.createChannels(this)
        NotificationScheduler.scheduleAll(this, repository, settings)

        setContent {
            MediTickTheme(settings) {
                MediTickApp(
                    repository = repository, settings = settings, billing = billing,
                    requestNotificationPermission = ::requestNotificationPermission,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        NotificationScheduler.scheduleAll(this)
        BillingManager.get(this).refresh()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else NotificationScheduler.scheduleAll(this)
    }
}
