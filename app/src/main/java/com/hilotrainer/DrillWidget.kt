package com.hilotrainer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hilotrainer.ui.Prefs
import com.hilotrainer.ui.Screen

/** Home screen widget: today's progress and a button that opens the 1-minute drill. */
class DrillWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        const val EXTRA_OPEN = "open"

        fun views(context: Context): RemoteViews {
            val d = Prefs(context).daily()
            val v = RemoteViews(context.packageName, R.layout.widget_drill)
            val streak = if (d.streak == 0) "start a streak today" else "${d.streak}-day streak"
            val status = if (d.metToday) "Goal done today · $streak"
            else "${d.points} of ${d.goal} points today · $streak"
            v.setTextViewText(R.id.widget_status, status)
            val open = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN, Screen.QUICK_DRILL.name)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(context, 7, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.widget_button, pi)
            v.setOnClickPendingIntent(R.id.widget_root, pi)
            return v
        }

        /** Redraw every placed widget, for example after a practice session. */
        fun refresh(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(ComponentName(context, DrillWidget::class.java))
            if (ids.isNotEmpty()) ids.forEach { m.updateAppWidget(it, views(context)) }
        }
    }
}
