package com.voiceexpense.ui.setup

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.voiceexpense.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetupGuideTest {
    @Test
    fun setup_guide_displays_core_elements() {
        ActivityScenario.launch(SetupGuidePage::class.java).use {
            onView(withId(R.id.text_setup_title)).check(matches(isDisplayed()))
            onView(withId(R.id.text_setup_steps)).check(matches(isDisplayed()))
            onView(withId(R.id.btn_test_ai_setup)).check(matches(isDisplayed()))
        }
    }
}

