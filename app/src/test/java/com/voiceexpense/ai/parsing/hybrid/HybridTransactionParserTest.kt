package com.voiceexpense.ai.parsing.hybrid

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.heuristic.FieldConfidenceThresholds
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import kotlinx.coroutines.runBlocking
import org.junit.Test

private class FakeGateway(
    var available: Boolean = true,
    private val handler: (String) -> Result<String> = { _ ->
        Result.failure(IllegalStateException("no response"))
    }
) : GenAiGateway {
    var callCount: Int = 0
    override fun isAvailable(): Boolean = available
    override suspend fun structured(prompt: String): Result<String> {
        callCount += 1
        return handler(prompt)
    }

    companion object {
        private val FIELD_REGEX = Regex("key \"([a-zA-Z]+)\"")
        fun fieldKey(prompt: String): String? = FIELD_REGEX.find(prompt)?.groupValues?.getOrNull(1)
    }
}

private fun handlerFromMap(
    responses: Map<String, Result<String>>,
    default: Result<String>? = null
): (String) -> Result<String> = { prompt ->
    val key = FakeGateway.fieldKey(prompt)
    responses[key] ?: default ?: Result.failure(IllegalStateException("no response for ${key ?: "unknown"}"))
}

class HybridTransactionParserTest {
    @Test
    fun ai_success_path_returns_validated_ai_result() = runBlocking {
        val responses = mapOf(
            "merchant" to Result.success("""{"merchant":"Starbucks"}"""),
            "description" to Result.success("""{"description":"Latte"}"""),
            "expenseCategory" to Result.success("""{"expenseCategory":"Eating Out"}"""),
            "tags" to Result.success("""{"tags":["coffee"]}""")
        )
        val thresholds = FieldConfidenceThresholds(
            mandatoryThresholds = mapOf(
                FieldKey.MERCHANT to 0.95f,
                FieldKey.DESCRIPTION to 0.95f,
                FieldKey.EXPENSE_CATEGORY to 0.95f
            ),
            defaultThreshold = 0.9f
        )
        val gw = FakeGateway(available = true, handler = handlerFromMap(responses))
        val parser = HybridTransactionParser(
            gw,
            thresholds = thresholds,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = true)
        )
        val res = parser.parse("spent 4.75 at starbucks", ParsingContext())
        assertThat(res.method).isEqualTo(ProcessingMethod.AI)
        assertThat(res.validated).isTrue()
        assertThat(res.result.merchant).isEqualTo("Starbucks")
        assertThat(res.result.type).isEqualTo("Expense")
        assertThat(gw.callCount).isAtLeast(1)
    }

    @Test
    fun ai_invalid_falls_back_to_heuristic() = runBlocking {
        val invalid = Result.success("[]")
        val responses = mapOf(
            "merchant" to invalid,
            "description" to invalid,
            "expenseCategory" to invalid
        )
        val thresholds = FieldConfidenceThresholds(
            mandatoryThresholds = mapOf(
                FieldKey.MERCHANT to 0.95f,
                FieldKey.DESCRIPTION to 0.95f,
                FieldKey.EXPENSE_CATEGORY to 0.95f
            ),
            defaultThreshold = 0.9f
        )
        val gw = FakeGateway(available = true, handler = handlerFromMap(responses))
        val parser = HybridTransactionParser(
            gw,
            thresholds = thresholds,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = true)
        )
        val res = parser.parse("transfer 100 from checking to savings", ParsingContext())
        assertThat(res.method).isEqualTo(ProcessingMethod.HEURISTIC)
        assertThat(res.validated).isFalse()
        assertThat(res.result.type).isEqualTo("Transfer")
        assertThat(gw.callCount).isAtLeast(1)
    }

    @Test
    fun unavailable_gateway_uses_heuristic() = runBlocking {
        val gw = FakeGateway(available = false)
        val parser = HybridTransactionParser(gw)
        val res = parser.parse("coffee 3 at starbucks", ParsingContext())
        assertThat(res.method).isEqualTo(ProcessingMethod.HEURISTIC)
        assertThat(res.result.type).isEqualTo("Expense")
        assertThat(res.validated).isFalse()
        assertThat(gw.callCount).isEqualTo(0)
    }

    @Test
    fun heuristic_coverage_skips_ai_call() = runBlocking {
        val gw = FakeGateway(available = true)
        val parser = HybridTransactionParser(gw)
        val context = ParsingContext(
            defaultDate = java.time.LocalDate.of(2025, 9, 13),
            allowedAccounts = listOf("Everyday Rewards Card")
        )

        val res = parser.parse(
            "On September 12th I spent 11.10 getting a takeout pizza from Domino's on my everyday rewards card",
            context
        )

        assertThat(gw.callCount).isEqualTo(0)
        assertThat(res.method).isEqualTo(ProcessingMethod.HEURISTIC)
        assertThat(res.result.userLocalDate).isEqualTo(java.time.LocalDate.of(2025, 9, 12))
        assertThat(res.result.account).isEqualTo("Everyday Rewards Card")
        assertThat(res.result.amountUsd?.toPlainString()).isEqualTo("11.10")
    }

    @Test
    fun normalizes_to_allowed_options() = runBlocking {
        val responses = mapOf(
            "merchant" to Result.success("""{"merchant":"Gas Bill"}"""),
            "expenseCategory" to Result.success("""{"expenseCategory":"utilities"}"""),
            "tags" to Result.success("""{"tags":["splitwise"]}"""),
            "account" to Result.success("""{"account":"example savings account (1234)"}""")
        )
        val thresholds = FieldConfidenceThresholds(
            mandatoryThresholds = mapOf(
                FieldKey.MERCHANT to 0.95f,
                FieldKey.EXPENSE_CATEGORY to 0.95f,
                FieldKey.TAGS to 0.85f,
                FieldKey.ACCOUNT to 0.9f
            ),
            defaultThreshold = 0.9f
        )
        val gw = FakeGateway(available = true, handler = handlerFromMap(responses))
        val parser = HybridTransactionParser(
            gw,
            thresholds = thresholds,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = true)
        )
        val context = ParsingContext(
            defaultDate = java.time.LocalDate.of(2025, 9, 13),
            allowedExpenseCategories = listOf("Utilities", "Groceries"),
            allowedTags = listOf("Auto-Paid", "Splitwise"),
            allowedAccounts = listOf("Example Savings Account (1234)")
        )

        val res = parser.parse(
            "On September 11th the gas bill was charged to my example savings account for 22.24 and I owe 11.12",
            context
        )

        assertThat(res.validated).isTrue()
        assertThat(res.result.expenseCategory).isEqualTo("Utilities")
        assertThat(res.result.account).isEqualTo("Example Savings Account (1234)")
        assertThat(res.result.tags).containsExactly("Splitwise")
    }

    @Test
    fun `splitwise fallback applies even when not in allowed list`() = runBlocking {
        val responses = mapOf(
            "merchant" to Result.success("""{"merchant":"Jenny's Ice Cream"}"""),
            "tags" to Result.success("""{"tags":["splitwise"]}""")
        )
        val thresholds = FieldConfidenceThresholds(
            mandatoryThresholds = mapOf(
                FieldKey.MERCHANT to 0.95f,
                FieldKey.TAGS to 0.85f
            ),
            defaultThreshold = 0.9f
        )
        val gw = FakeGateway(available = true, handler = handlerFromMap(responses))
        val parser = HybridTransactionParser(
            gw,
            thresholds = thresholds,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = true)
        )
        val context = ParsingContext(
            defaultDate = java.time.LocalDate.of(2025, 10, 14),
            allowedTags = listOf("Subscription", "Auto-Paid")
        )

        val res = parser.parse(
            "I went to Jenny's ice cream and from that I owe 4.98 after splitwise",
            context
        )

        assertThat(res.result.tags).containsExactly("Splitwise")
    }

    @Test
    fun staged_parsing_refines_low_confidence_fields() = runBlocking {
        val responses = mapOf(
            "merchant" to Result.success("""{"merchant":"Whole Foods"}"""),
            "description" to Result.success("""{"description":"Weekly groceries"}"""),
            "expenseCategory" to Result.success("""{"expenseCategory":"Groceries"}""")
        )
        val gw = FakeGateway(available = true, handler = handlerFromMap(responses))
        val thresholds = FieldConfidenceThresholds(
            mandatoryThresholds = mapOf(
                FieldKey.MERCHANT to 0.95f,
                FieldKey.DESCRIPTION to 0.95f,
                FieldKey.EXPENSE_CATEGORY to 0.95f
            ),
            defaultThreshold = 0.9f
        )
        val parser = HybridTransactionParser(
            gw,
            thresholds = thresholds,
            stagedConfig = HybridTransactionParser.StagedParsingConfig(enabled = true)
        )

        val res = parser.parse("Bought groceries for 45 dollars", ParsingContext())

        assertThat(gw.callCount).isEqualTo(1)
        assertThat(res.method).isEqualTo(ProcessingMethod.AI)
        assertThat(res.validated).isTrue()
        assertThat(res.result.merchant).isEqualTo("Whole Foods")
        assertThat(res.result.expenseCategory).isEqualTo("Groceries")
        assertThat(res.staged).isNotNull()
        assertThat(res.staged!!.targetFields).containsAtLeast(
            FieldKey.MERCHANT,
            FieldKey.DESCRIPTION,
            FieldKey.EXPENSE_CATEGORY
        )
        assertThat(res.errors).isEmpty()
    }
}
