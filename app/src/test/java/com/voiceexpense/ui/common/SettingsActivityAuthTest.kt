package com.voiceexpense.ui.common

import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsActivityAuthTest {
    @Test
    fun launches_andShowsDefaultGating() {
        val scenario = ActivityScenario.launch(SettingsActivity::class.java)
        scenario.onActivity { activity ->
            val tv = activity.findViewById<android.widget.TextView>(com.voiceexpense.R.id.text_sync_gating)
            assertThat(tv.text.toString()).contains("Sign in")
        }
        scenario.close()
    }
}

