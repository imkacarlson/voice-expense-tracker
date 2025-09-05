package com.voiceexpense.ai.parsing.hybrid

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.ParsedResult
import kotlinx.coroutines.runBlocking
import org.junit.Test

private class FakeGateway(
    var available: Boolean = true,
    var response: Result<String> = Result.failure(IllegalStateException("no response"))
) : GenAiGateway {
    override fun isAvailable(): Boolean = available
    override suspend fun structured(prompt: String): Result<String> = response
}

class HybridTransactionParserTest {
    @Test
    fun ai_success_path_returns_validated_ai_result() = runBlocking {
        val json = "{" +
            "\"amountUsd\":4.75," +
            "\"merchant\":\"Starbucks\"," +
            "\"type\":\"Expense\"," +
            "\"tags\":[\"coffee\"]," +
            "\"userLocalDate\":\"2025-01-01\"," +
            "\"confidence\":0.8}"
        val gw = FakeGateway(available = true, response = Result.success(json))
        val parser = HybridTransactionParser(gw)
        val res = parser.parse("spent 4.75 at starbucks", ParsingContext())
        assertThat(res.method).isEqualTo(ProcessingMethod.AI)
        assertThat(res.validated).isTrue()
        assertThat(res.result.merchant).isEqualTo("Starbucks")
        assertThat(res.result.type).isEqualTo("Expense")
    }

    @Test
    fun ai_invalid_falls_back_to_heuristic() = runBlocking {
        val invalid = "{\"tags\":\"coffee\"}" // invalid tags type
        val gw = FakeGateway(available = true, response = Result.success(invalid))
        val parser = HybridTransactionParser(gw)
        val res = parser.parse("transfer 100 from checking to savings", ParsingContext())
        assertThat(res.method).isEqualTo(ProcessingMethod.HEURISTIC)
        assertThat(res.validated).isFalse()
        assertThat(res.result.type).isEqualTo("Transfer")
    }

    @Test
    fun unavailable_gateway_uses_heuristic() = runBlocking {
        val gw = FakeGateway(available = false)
        val parser = HybridTransactionParser(gw)
        val res = parser.parse("coffee 3 at starbucks", ParsingContext())
        assertThat(res.method).isEqualTo(ProcessingMethod.HEURISTIC)
        assertThat(res.result.type).isEqualTo("Expense")
        assertThat(res.validated).isFalse()
    }
}

