package com.voiceexpense.ai.parsing.hybrid

import android.util.Log
import com.voiceexpense.ai.parsing.ParsedResult
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.StructuredOutputValidator
import com.voiceexpense.ai.parsing.heuristic.FieldConfidenceThresholds
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.ai.parsing.heuristic.HeuristicDraft
import com.voiceexpense.ai.parsing.heuristic.HeuristicExtractor
import com.voiceexpense.ai.parsing.heuristic.toParsedResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.system.measureTimeMillis

private const val TAG = "StagedOrchestrator"
private const val AI_TIMEOUT_MS = 5_000L

/**
 * Coordinates the staged parsing pipeline: heuristics → focused AI refinement → merge.
 *
 * Each invocation executes Stage 1 heuristics, decides whether Stage 2 is required,
 * performs the focused prompt call, and merges the result back while respecting
 * user modifications.
 */
class StagedParsingOrchestrator(
    private val heuristicExtractor: HeuristicExtractor,
    private val genAiGateway: GenAiGateway,
    private val focusedPromptBuilder: FocusedPromptBuilder,
    private val thresholds: FieldConfidenceThresholds = FieldConfidenceThresholds.DEFAULT,
    private val fieldSelector: FieldSelectionStrategy = FieldSelectionStrategy,
    private val heuristicProvider: ((String, ParsingContext) -> HeuristicDraft)? = null
) {

    suspend fun parseStaged(
        input: String,
        context: ParsingContext = ParsingContext()
    ): StagedParsingResult {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "parseStaged input='${input.take(80)}'")
        }
        val refinementErrors = mutableListOf<String>()

        var heuristicDraft: HeuristicDraft
        val stage1DurationMs = measureTimeMillis {
            heuristicDraft = heuristicProvider?.invoke(input, context)
                ?: heuristicExtractor.extract(input, context)
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Stage1 duration=${stage1DurationMs}ms confidences=${heuristicDraft.confidences}")
        }

        val targetFields = fieldSelector
            .selectFieldsForRefinement(heuristicDraft, thresholds)
            .toSet()
        val genAiAvailable = genAiGateway.isAvailable()

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Target fields=${targetFields.joinToString()} aiAvailable=$genAiAvailable")
        }

        if (targetFields.isEmpty() || !genAiAvailable) {
            if (!genAiAvailable && targetFields.isNotEmpty()) {
                refinementErrors += "AI unavailable"
                Log.w(TAG, "GenAI unavailable; skipping refinement")
            }
            val merged = heuristicDraft.toParsedResult(context)
            return StagedParsingResult(
                heuristicDraft = heuristicDraft,
                refinedFields = emptyMap(),
                mergedResult = merged,
                fieldsRefined = emptySet(),
                refinementErrors = refinementErrors,
                stage1DurationMs = stage1DurationMs,
                stage2DurationMs = 0L
            )
        }

        val prompt = focusedPromptBuilder.buildFocusedPrompt(
            input = input,
            heuristicDraft = heuristicDraft,
            targetFields = targetFields,
            context = context
        )
        val refinedFields = mutableMapOf<FieldKey, Any?>()

        var stage2DurationMs = 0L
        var aiPayload: String? = null

        try {
            stage2DurationMs = measureTimeMillis {
                val result = withTimeout(AI_TIMEOUT_MS) {
                    genAiGateway.structured(prompt)
                }
                aiPayload = result.getOrElse { throwable ->
                    refinementErrors += "AI failure: ${throwable.message ?: throwable::class.simpleName}".trim()
                    Log.w(TAG, "GenAI structured call failed", throwable)
                    null
                }
            }
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Stage2 duration=${stage2DurationMs}ms payloadSize=${aiPayload?.length ?: 0}")
            }
        } catch (timeout: TimeoutCancellationException) {
            stage2DurationMs = AI_TIMEOUT_MS
            refinementErrors += "AI timeout after ${AI_TIMEOUT_MS}ms"
            Log.w(TAG, "GenAI refinement timed out", timeout)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            refinementErrors += "AI invocation error: ${t.message ?: t::class.simpleName}".trim()
            Log.e(TAG, "Unexpected error during GenAI call", t)
        }

        val fieldsRefined: Set<FieldKey>
        val mergedResult: ParsedResult

        if (aiPayload.isNullOrBlank()) {
            mergedResult = heuristicDraft.toParsedResult(context)
            fieldsRefined = emptySet()
            Log.d(TAG, "No AI payload returned; using heuristic result")
        } else {
            val validation = ValidationPipeline.validateRawResponse(aiPayload!!)
            if (!validation.valid || validation.normalizedJson.isNullOrBlank()) {
                if (validation.errors.isNotEmpty()) {
                    refinementErrors += validation.errors
                } else {
                    refinementErrors += "AI validation failed"
                }
                Log.w(TAG, "Validation failed for AI output errors=${validation.errors}")
                mergedResult = heuristicDraft.toParsedResult(context)
                fieldsRefined = emptySet()
            } else {
                val normalized = validation.normalizedJson
                refinedFields += extractFieldUpdates(normalized, targetFields)
                fieldsRefined = refinedFields.keys.toSet()
                mergedResult = mergeResults(
                    heuristicDraft = heuristicDraft,
                    refinedFields = refinedFields,
                    context = context
                )
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Applied refinements=${fieldsRefined.joinToString()} errors=${refinementErrors.size}")
                }
            }
        }

        Log.i(
            TAG,
            "Staged parsing finished stage1=${stage1DurationMs}ms stage2=${stage2DurationMs}ms refined=${fieldsRefined.size} errors=${refinementErrors.size}"
        )
        return StagedParsingResult(
            heuristicDraft = heuristicDraft,
            refinedFields = refinedFields,
            mergedResult = mergedResult,
            fieldsRefined = fieldsRefined,
            refinementErrors = refinementErrors,
            stage1DurationMs = stage1DurationMs,
            stage2DurationMs = stage2DurationMs
        )
    }

    private fun extractFieldUpdates(
        normalizedJson: String,
        targets: Set<FieldKey>
    ): Map<FieldKey, Any?> {
        return try {
            val json = JSONObject(normalizedJson)
            targets.mapNotNull { field ->
                val key = jsonKey(field)
                if (!json.has(key)) return@mapNotNull null
                val value = parseValue(field, json, key)
                field to value
            }.toMap()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to parse focused AI response", t)
            emptyMap()
        }
    }

    private fun parseValue(field: FieldKey, json: JSONObject, key: String): Any? {
        if (json.isNull(key)) return null
        return when (field) {
            FieldKey.MERCHANT,
            FieldKey.DESCRIPTION,
            FieldKey.EXPENSE_CATEGORY,
            FieldKey.INCOME_CATEGORY,
            FieldKey.NOTE -> json.optString(key, null)?.takeUnless { it.isBlank() }
            FieldKey.TAGS -> {
                val arr = json.optJSONArray(key)
                when {
                    arr != null -> jsonArrayToList(arr)
                    json.opt(key) is String -> listOfNotNull(json.optString(key).takeIf { it.isNotBlank() })
                    else -> emptyList()
                }
            }
            else -> json.opt(key)
        }
    }

    private fun jsonArrayToList(arr: JSONArray): List<String> {
        val values = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val v = arr.optString(i)
            if (!v.isNullOrBlank()) {
                values += v.trim()
            }
        }
        return values
    }

    private fun mergeResults(
        heuristicDraft: HeuristicDraft,
        refinedFields: Map<FieldKey, Any?>,
        context: ParsingContext
    ): ParsedResult {
        var merged = heuristicDraft.toParsedResult(context)
        refinedFields.forEach { (field, value) ->
            merged = when (field) {
                FieldKey.MERCHANT -> merged.copy(merchant = (value as? String)?.trim().takeUnless { it.isNullOrBlank() } ?: merged.merchant)
                FieldKey.DESCRIPTION -> merged.copy(description = (value as? String)?.trim())
                FieldKey.EXPENSE_CATEGORY -> merged.copy(expenseCategory = (value as? String)?.trim())
                FieldKey.INCOME_CATEGORY -> merged.copy(incomeCategory = (value as? String)?.trim())
                FieldKey.TAGS -> merged.copy(tags = (value as? List<*>)?.mapNotNull { (it as? String)?.trim() }?.filter { it.isNotEmpty() } ?: merged.tags)
                FieldKey.NOTE -> merged.copy(note = (value as? String)?.trim())
                else -> merged
            }
        }
        return StructuredOutputValidator.sanitizeAmounts(merged)
    }

    private fun jsonKey(field: FieldKey): String = when (field) {
        FieldKey.MERCHANT -> "merchant"
        FieldKey.DESCRIPTION -> "description"
        FieldKey.EXPENSE_CATEGORY -> "expenseCategory"
        FieldKey.INCOME_CATEGORY -> "incomeCategory"
        FieldKey.TAGS -> "tags"
        FieldKey.NOTE -> "note"
        else -> field.name.lowercase(Locale.US)
    }
}
