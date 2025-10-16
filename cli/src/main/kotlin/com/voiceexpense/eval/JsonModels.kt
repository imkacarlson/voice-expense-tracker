package com.voiceexpense.eval

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.math.BigDecimal

/**
 * Data model for stdin payload consumed by the CLI. The shape mirrors the Python orchestrator
 * contract described in the spec so the evaluator can drive both heuristic-only and
 * AI-assisted flows by optionally supplying model responses.
 */
@JsonClass(generateAdapter = true)
data class CliInput(
    val utterance: String,
    val context: CliContext? = null,
    @Json(name = "model_responses")
    val modelResponses: Map<String, String>? = null
)

/** Representation of optional context fields used to construct a ParsingContext. */
@JsonClass(generateAdapter = true)
data class CliContext(
    @Json(name = "allowedExpenseCategories")
    val allowedExpenseCategories: List<String>? = null,
    @Json(name = "allowedIncomeCategories")
    val allowedIncomeCategories: List<String>? = null,
    @Json(name = "allowedTags")
    val allowedTags: List<String>? = null,
    @Json(name = "allowedAccounts")
    val allowedAccounts: List<String>? = null,
    @Json(name = "recentMerchants")
    val recentMerchants: List<String>? = null,
    @Json(name = "recentCategories")
    val recentCategories: List<String>? = null,
    @Json(name = "knownAccounts")
    val knownAccounts: List<String>? = null,
    @Json(name = "defaultDate")
    val defaultDate: String? = null
)

/** Result contract when the parser needs Python to supply AI responses. */
@JsonClass(generateAdapter = true)
data class CliOutputNeedsAi(
    val status: String = "needs_ai",
    @Json(name = "heuristic_results")
    val heuristicResults: HeuristicSummary? = null,
    @Json(name = "prompts_needed")
    val promptsNeeded: List<PromptRequest>,
    val stats: CliStats? = null
)

/** Result contract when parsing completes without further AI assistance required. */
@JsonClass(generateAdapter = true)
data class CliOutputComplete(
    val status: String = "complete",
    val parsed: ParsedSnapshot,
    val method: String,
    val stats: CliStats? = null
)

/** Compact snapshot of the parsed result for JSON interchange. */
@JsonClass(generateAdapter = true)
data class ParsedSnapshot(
    val amountUsd: BigDecimal?,
    val merchant: String?,
    val description: String?,
    val type: String?,
    val expenseCategory: String?,
    val incomeCategory: String?,
    val tags: List<String> = emptyList(),
    @Json(name = "userLocalDate")
    val userLocalDateIso: String?,
    val account: String?,
    val splitOverallChargedUsd: BigDecimal?,
    val confidence: Float?
)

/** Summary of heuristic output returned when AI refinement is required. */
@JsonClass(generateAdapter = true)
data class HeuristicSummary(
    val amountUsd: BigDecimal?,
    val merchant: String?,
    val description: String?,
    val type: String?,
    val expenseCategory: String?,
    val incomeCategory: String?,
    val tags: List<String>? = null,
    val splitOverallChargedUsd: BigDecimal? = null,
    val account: String?,
    val confidence: Float?
)

/** Individual prompt request that Python must fulfill. */
@JsonClass(generateAdapter = true)
data class PromptRequest(
    val field: String,
    val prompt: String
)

/** Flexible timing bucket used for both heuristic-only and full runs. */
@JsonClass(generateAdapter = true)
data class CliStats(
    @Json(name = "stage0_ms")
    val stage0Ms: Long? = null,
    @Json(name = "stage1_ms")
    val stage1Ms: Long? = null,
    @Json(name = "stage2_ms")
    val stage2Ms: Long? = null,
    @Json(name = "total_ms")
    val totalMs: Long? = null
)
