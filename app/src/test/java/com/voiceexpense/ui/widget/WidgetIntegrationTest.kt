package com.voiceexpense.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WidgetIntegrationTest {
    @Test
    fun providerOnUpdate_doesNotCrash() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val provider = ExpenseWidgetProvider()
        val manager = AppWidgetManager.getInstance(context)
        val cn = ComponentName(context, ExpenseWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(cn)
        // Even if ids is empty in test, the call should be safe
        provider.onUpdate(context, manager, ids)
        assertThat(true).isTrue()
    }
}

