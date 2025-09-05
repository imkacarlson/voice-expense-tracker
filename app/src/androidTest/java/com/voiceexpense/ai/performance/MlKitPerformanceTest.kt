package com.voiceexpense.ai.performance

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.TransactionParser
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MlKitPerformanceTest {
    @Test
    fun parsing_completes_under_three_seconds_baseline() = runBlocking {
        val parser = TransactionParser()
        val start = System.currentTimeMillis()
        val res = parser.parse("I spent 23 at Starbucks for coffee")
        val elapsed = System.currentTimeMillis() - start
        assertThat(elapsed).isLessThan(3000)
        // ensure something returned
        assertThat(res.type).isNotEmpty()
    }
}

