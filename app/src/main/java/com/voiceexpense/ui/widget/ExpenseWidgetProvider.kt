package com.voiceexpense.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.voiceexpense.R
import com.voiceexpense.service.voice.VoiceRecordingService

class ExpenseWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_expense)
            val intent = Intent(context, VoiceRecordingService::class.java).apply { action = VoiceRecordingService.ACTION_START }
            val pending = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

