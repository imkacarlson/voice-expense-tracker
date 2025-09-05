package com.voiceexpense.ai.parsing.hybrid

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsingContext
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccuracyValidationTest {
    @Test
    fun synthetic_accuracy_over_simple_set() = runBlocking {
        val samples = listOf(
            "spent 4.75 at starbucks for latte" to "{\"amountUsd\":4.75,\"merchant\":\"Starbucks\",\"type\":\"Expense\",\"tags\":[\"coffee\"],\"userLocalDate\":\"2025-01-01\"}",
            "paycheck 2200, tag july" to "{\"amountUsd\":2200,\"merchant\":\"\",\"type\":\"Income\",\"incomeCategory\":\"Salary\",\"tags\":[\"july\"],\"userLocalDate\":\"2025-01-01\"}",
            "transfer 100 from checking to savings" to "{\"amountUsd\":100,\"merchant\":\"\",\"type\":\"Transfer\",\"tags\":[],\"userLocalDate\":\"2025-01-01\"}"
        )

        var correct = 0
        for ((utterance, json) in samples) {
            val gw = object : GenAiGateway {
                override fun isAvailable(): Boolean = true
                override suspend fun structured(prompt: String): Result<String> = Result.success(json)
            }
            val parser = HybridTransactionParser(gw)
            val res = parser.parse(utterance, ParsingContext())
            val expectedType = when {
                utterance.contains("paycheck", true) -> "Income"
                utterance.contains("transfer", true) -> "Transfer"
                else -> "Expense"
            }
            if (res.result.type == expectedType) correct++
        }
        val accuracy = correct.toFloat() / samples.size
        assertThat(accuracy).isAtLeast(0.66f)
    }
}

