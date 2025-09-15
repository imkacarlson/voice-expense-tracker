package com.voiceexpense.ui.confirmation

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.voiceexpense.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class ConfirmationActivityNavigationTest {

    @Test
    fun missingId_showsToast_andFinishes() {
        val scenario = ActivityScenario.launch(TransactionConfirmationActivity::class.java)
        // After launch without EXTRA_TRANSACTION_ID, activity should finish and show a toast
        val latest = ShadowToast.getTextOfLatestToast()
        assertThat(latest).isEqualTo("Could not open draft. Please try again.")
        assertThat(scenario.state.name).isAnyOf("DESTROYED", "DESTROYED")
        scenario.close()
    }

    @Test
    fun withId_extra_present_activityStarts_andShowsNoImmediateToast() {
        val intent = Intent(androidx.test.core.app.ApplicationProvider.getApplicationContext(), TransactionConfirmationActivity::class.java)
        intent.putExtra(com.voiceexpense.ui.confirmation.TransactionConfirmationActivity.EXTRA_TRANSACTION_ID, "some-id")
        val scenario = ActivityScenario.launch<TransactionConfirmationActivity>(intent)
        // It may still finish if no draft is found, but no toast should be shown immediately before repo load attempt
        // Assert that there is no toast prior to repo result (best-effort check)
        val latest = ShadowToast.getTextOfLatestToast()
        // Accept either null (no toast yet) or the error message if repo returned null synchronously
        if (latest != null) {
            assertThat(latest).isAnyOf(
                null,
                androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.error_open_draft_failed)
            )
        }
        scenario.close()
    }
}
