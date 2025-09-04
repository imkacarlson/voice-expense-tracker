package com.voiceexpense.service.voice

import android.app.Application
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ui.confirmation.TransactionConfirmationActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
class VoiceRecordingServiceNavigationTest {
    @Test
    fun start_emitsActivityIntent_withTransactionIdExtra() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val controller = Robolectric.buildService(VoiceRecordingService::class.java)
        val service = controller.create().get()

        val start = Intent(app, VoiceRecordingService::class.java).apply { action = VoiceRecordingService.ACTION_START }
        // Start the service with the ACTION_START intent
        service.onStartCommand(start, /*flags=*/0, /*startId=*/0)

        // Allow background coroutine to run
        Thread.sleep(100)

        val shadowApp = Shadows.shadowOf(app)
        val intent = shadowApp.nextStartedActivity
        // There should be a started activity with the TransactionConfirmationActivity component
        assertThat(intent).isNotNull()
        assertThat(intent.component?.className).isEqualTo(TransactionConfirmationActivity::class.java.name)
        val id = intent.getStringExtra(VoiceRecordingService.EXTRA_TRANSACTION_ID)
        assertThat(id).isNotEmpty()

        controller.destroy()
    }
}
