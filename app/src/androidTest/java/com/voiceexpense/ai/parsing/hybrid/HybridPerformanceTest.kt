package com.voiceexpense.ai.parsing.hybrid

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsingContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

private class PerfGateway(
    val available: Boolean,
    val delayMs: Long,
    val json: String
) : GenAiGateway {
    override fun isAvailable(): Boolean = available
    override suspend fun structured(prompt: String): Result<String> {
        delay(delayMs)
        return Result.success(json)
    }
}

@RunWith(AndroidJUnit4::class)
class HybridPerformanceTest {
    private val validJson = "{" +
        "\"amountUsd\":4.75," +
        "\"merchant\":\"Starbucks\"," +
        "\"type\":\"Expense\"," +
        "\"tags\":[\"coffee\"]," +
        "\"userLocalDate\":\"2025-01-01\"," +
        "\"confidence\":0.8}"

    @Test
    fun ai_path_is_within_bounds() = runBlocking {
        val gw = PerfGateway(true, 25, validJson)
        val parser = HybridTransactionParser(gw)
        val elapsed = measureTimeMillis {
            val res = parser.parse("spent 4.75 at starbucks", ParsingContext())
            assertThat(res.method).isEqualTo(ProcessingMethod.AI)
            assertThat(res.validated).isTrue()
        }
        // Budget generous upper bound for local device
        assertThat(elapsed).isLessThan(2000)
    }

    @Test
    fun fallback_is_fast() = runBlocking {
        val gw = PerfGateway(false, 0, validJson)
        val parser = HybridTransactionParser(gw)
        val elapsed = measureTimeMillis {
            val res = parser.parse("coffee 3 at cafe", ParsingContext())
            assertThat(res.method).isEqualTo(ProcessingMethod.HEURISTIC)
        }
        assertThat(elapsed).isLessThan(200)
    }
}

