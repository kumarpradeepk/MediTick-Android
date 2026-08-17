package com.kabi.pillpal.meditick.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kabi.pillpal.meditick.MainActivity
import com.kabi.pillpal.meditick.R
import com.kabi.pillpal.meditick.data.AppRepository
import com.kabi.pillpal.meditick.model.DoseEngine
import com.kabi.pillpal.meditick.model.DoseState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediTickWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, buildViews(context)) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MediTickWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { manager.updateAppWidget(it, buildViews(context)) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val repository = AppRepository.get(context)
            val doses = repository.doses(System.currentTimeMillis())
            val stats = DoseEngine.stats(doses)
            val percent = (stats.completionRatio * 100).toInt()
            val next = doses.firstOrNull { it.state == DoseState.DUE || it.state == DoseState.UPCOMING }
            val views = RemoteViews(context.packageName, R.layout.widget_meditick)
            views.setTextViewText(R.id.widget_progress_value, "$percent%")
            views.setProgressBar(R.id.widget_progress, 100, percent, false)
            views.setTextViewText(R.id.widget_summary, "${stats.taken} of ${stats.scheduled} doses ticked")
            views.setTextViewText(R.id.widget_next_name, next?.medication?.name ?: if (doses.isEmpty()) "No doses today" else "All done for today")
            views.setTextViewText(R.id.widget_next_time, next?.let { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it.time)) } ?: "You’re all caught up")
            val open = PendingIntent.getActivity(context, 100, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, open)
            return views
        }
    }
}
