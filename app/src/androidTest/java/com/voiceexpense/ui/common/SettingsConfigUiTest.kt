package com.voiceexpense.ui.common

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.voiceexpense.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsConfigUiTest {
    @Test fun showsDropdownConfigurationSection() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.spinner_option_type)).check(matches(isDisplayed()))
            onView(withId(R.id.list_options)).check(matches(isDisplayed()))
            onView(withId(R.id.input_new_option)).check(matches(isDisplayed()))
        }
    }
}

