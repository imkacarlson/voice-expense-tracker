package com.voiceexpense.testutil

import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.idling.CountingIdlingResource

object AndroidTestUtils {
    val idling = CountingIdlingResource("global")

    fun registerIdling() {
        IdlingRegistry.getInstance().register(idling)
    }

    fun unregisterIdling() {
        IdlingRegistry.getInstance().unregister(idling)
    }
}

