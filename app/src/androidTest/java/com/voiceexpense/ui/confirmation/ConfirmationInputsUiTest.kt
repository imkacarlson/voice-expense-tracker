package com.voiceexpense.ui.confirmation

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.voiceexpense.R
import com.voiceexpense.service.voice.VoiceRecordingService
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfirmationInputsUiTest {
    @Test fun showsTypeAndDateControls() {
        val intent = Intent(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext, TransactionConfirmationActivity::class.java)
        intent.putExtra(VoiceRecordingService.EXTRA_TRANSACTION_ID, "test-txn-id")
        // Activity will attempt to load the transaction; in test, just verify controls exist regardless
        ActivityScenario.launch<TransactionConfirmationActivity>(intent).use {
            onView(withId(R.id.spinner_type)).check(matches(isDisplayed()))
            onView(withId(R.id.field_date)).check(matches(isDisplayed()))
        }
    }
}

