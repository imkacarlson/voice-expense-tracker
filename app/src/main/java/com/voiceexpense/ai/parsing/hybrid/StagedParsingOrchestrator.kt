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
import org.json.JSONArray
import org.json.JSONObject
import java.util.LinkedHashSet
import java.util.Locale
import kotlin.system.measureTimeMillis

private const val TAG = "StagedOrchestrator"

data class FieldRefinementUpdate(
    val field: FieldKey,
    val value: Any?,
    val durationMs: Long,
    val error: String? = null
)

typealias FieldRefinementListener = suspend (FieldRefinementUpdate) -> Unit

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
    private val fieldSelector: FieldSelectionStrategy = FieldSelectionStrategy
) {

    data class Stage1Snapshot(
        val heuristicDraft: HeuristicDraft,
        val targetFields: List<FieldKey>,
        val stage1DurationMs: Long
    )

    suspend fun prepareStage1(
        input: String,
        context: ParsingContext = ParsingContext()
    ): Stage1Snapshot {
        val heuristicDraft: HeuristicDraft
        val durationMs = measureTimeMillis {
            heuristicDraft = heuristicExtractor.extract(input, context)
        }
        val targetFields = fieldSelector
            .selectFieldsForRefinement(heuristicDraft, thresholds)
        try {
            Log.d(
                "AI.Debug",
                "Stage1 heuristics duration=${durationMs}ms confidences=${heuristicDraft.confidences}"
            )
        } catch (_: Throwable) {}
        Log.d(TAG, "Stage1 duration=${durationMs}ms confidences=${heuristicDraft.confidences}")
        return Stage1Snapshot(
            heuristicDraft = heuristicDraft,
            targetFields = targetFields,
            stage1DurationMs = durationMs
        )
    }

    suspend fun parseStaged(
        input: String,
        context: ParsingContext = ParsingContext(),
        stage1Snapshot: Stage1Snapshot? = null,
        listener: FieldRefinementListener? = null
    ): StagedParsingResult {
        try {
            Log.d("AI.Debug", "Staged parse start input='${input.take(80)}'")
        } catch (_: Throwable) {}
        Log.d(TAG, "parseStaged input='${input.take(80)}'")
        val refinementErrors = mutableListOf<String>()

        val stage1 = stage1Snapshot ?: prepareStage1(input, context)
        var heuristicDraft = stage1.heuristicDraft
        val stage1DurationMs = stage1.stage1DurationMs
        val orderedTargetFields = stage1.targetFields
        val initialTargetSet = LinkedHashSet(orderedTargetFields)

        if (stage1Snapshot != null) {
            try {
                Log.d(
                    "AI.Debug",
                    "Stage1 heuristics precomputed duration=${stage1DurationMs}ms confidences=${heuristicDraft.confidences}"
                )
                Log.d(
                    "AI.Debug",
                    "Stage1 heuristics merchant='${heuristicDraft.merchant}' description='${heuristicDraft.description}'"
                )
            } catch (_: Throwable) {}
            Log.d(TAG, "Stage1 (precomputed) duration=${stage1DurationMs}ms confidences=${heuristicDraft.confidences}")
        }
        val genAiAvailable = genAiGateway.isAvailable()

        try {
            val summary = if (orderedTargetFields.isEmpty()) {
                "none"
            } else {
                orderedTargetFields.joinToString { field ->
                    val confidence = heuristicDraft.confidences[field]
                    val formatted = confidence?.let { String.format(Locale.US, "%.2f", it) } ?: "n/a"
                    "${field.name.lowercase(Locale.US)}=$formatted"
                }
            }
            Log.i(
                "AI.Fields",
                "focused refinement target=${summary} aiAvailable=$genAiAvailable"
            )
        } catch (_: Throwable) {}
        Log.d(TAG, "Target fields=${orderedTargetFields.joinToString()} aiAvailable=$genAiAvailable")

        if (orderedTargetFields.isEmpty() || !genAiAvailable) {
            if (!genAiAvailable && orderedTargetFields.isNotEmpty()) {
                refinementErrors += "AI unavailable"
                Log.w(TAG, "GenAI unavailable; skipping refinement")
                try {
                    Log.w("AI.Validate", "GenAI unavailable; skipping refinement for ${orderedTargetFields.joinToString()}")
                } catch (_: Throwable) {}
            }
            val merged = heuristicDraft.toParsedResult(context)
            return StagedParsingResult(
                heuristicDraft = heuristicDraft,
                refinedFields = emptyMap(),
                mergedResult = merged,
                fieldsRefined = emptySet(),
                targetFields = initialTargetSet,
                refinementErrors = refinementErrors,
                stage1DurationMs = stage1DurationMs,
                stage2DurationMs = 0L
            )
        }
        val cumulativeRefinements = linkedMapOf<FieldKey, Any?>()
        var stage2DurationMs = 0L
        orderedTargetFields.forEach { field ->
            val attempt = refineSingleField(
                field = field,
                input = input,
                context = context,
                draftForPrompt = heuristicDraft,
                refinementErrors = refinementErrors
            )
            stage2DurationMs += attempt.durationMs
            attempt.refinedValue?.let { value ->
                cumulativeRefinements[field] = value
                heuristicDraft = applyRefinementToDraft(heuristicDraft, field, value)
            }
            listener?.let { callback ->
                callback(
                    FieldRefinementUpdate(
                        field = field,
                        value = attempt.refinedValue,
                        durationMs = attempt.durationMs,
                        error = attempt.errorMessage
                    )
                )
            }
        }

        val mergedResult: ParsedResult = if (cumulativeRefinements.isEmpty()) {
            heuristicDraft.toParsedResult(context)
        } else {
            mergeResults(
                heuristicDraft = stage1.heuristicDraft,
                refinedFields = cumulativeRefinements,
                context = context
            )
        }
        val fieldsRefined: Set<FieldKey> = cumulativeRefinements.keys.toSet()

        Log.i(
            TAG,
            "Staged parsing finished stage1=${stage1DurationMs}ms stage2=${stage2DurationMs}ms refined=${fieldsRefined.size} errors=${refinementErrors.size}"
        )
        return StagedParsingResult(
            heuristicDraft = stage1.heuristicDraft,
            refinedFields = cumulativeRefinements,
            mergedResult = mergedResult,
            fieldsRefined = fieldsRefined,
            targetFields = initialTargetSet,
            refinementErrors = refinementErrors,
            stage1DurationMs = stage1DurationMs,
            stage2DurationMs = stage2DurationMs
        )
    }

    private suspend fun refineSingleField(
        field: FieldKey,
        input: String,
        context: ParsingContext,
        draftForPrompt: HeuristicDraft,
        refinementErrors: MutableList<String>
    ): SingleFieldAttempt {
        val prompt = focusedPromptBuilder.buildFocusedPrompt(
            input = input,
            heuristicDraft = draftForPrompt,
            targetFields = linkedSetOf(field),
            context = context
        )
        logFocusedPrompt(prompt)

        var durationMs = 0L
        var aiPayload: String? = null

        try {
            Log.d("AI.Debug", "Stage2 focused prompt length=${prompt.length}")
        } catch (_: Throwable) {}

        try {
            durationMs = measureTimeMillis {
                val result = genAiGateway.structured(prompt)
                aiPayload = result.getOrElse { throwable ->
                    val message = "AI failure: ${throwable.message ?: throwable::class.simpleName}".trim()
                    refinementErrors += message
                    Log.w(TAG, "GenAI structured call failed", throwable)
                    null
                }
            }
            try {
                Log.d(
                    "AI.Debug",
                    "Stage2 duration=${durationMs}ms payloadSize=${aiPayload?.length ?: 0}"
                )
            } catch (_: Throwable) {}
            Log.d(TAG, "Stage2 duration=${durationMs}ms payloadSize=${aiPayload?.length ?: 0}")
            aiPayload?.let { logFocusedResponse(it) }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            val message = "AI invocation error: ${t.message ?: t::class.simpleName}".trim()
            refinementErrors += message
            Log.e(TAG, "Unexpected error during GenAI call", t)
            try {
                Log.e("AI.Validate", "Unexpected error during focused refinement: ${t.message}")
            } catch (_: Throwable) {}
            return SingleFieldAttempt(durationMs = durationMs, errorMessage = message)
        }

        if (aiPayload.isNullOrBlank()) {
            Log.d(TAG, "No AI payload returned; using heuristic result")
            try { Log.d("AI.Debug", "Focused refinement returned blank payload; using heuristic result") } catch (_: Throwable) {}
            val message = "AI blank response"
            refinementErrors += message
            return SingleFieldAttempt(durationMs = durationMs, errorMessage = message)
        }

        val validation = ValidationPipeline.validateRawResponse(aiPayload!!)
        if (!validation.valid || validation.normalizedJson.isNullOrBlank()) {
            val message = if (validation.errors.isNotEmpty()) {
                refinementErrors += validation.errors
                validation.errors.joinToString()
            } else {
                val msg = "AI validation failed"
                refinementErrors += msg
                msg
            }
            Log.w(TAG, "Validation failed for AI output errors=${validation.errors}")
            try {
                val snippet = aiPayload!!.replace("\n", " ").take(200)
                Log.w(
                    "AI.Validate",
                    "Focused refinement invalid: errors='${validation.errors.joinToString()}' snippet='$snippet'"
                )
            } catch (_: Throwable) {}
            return SingleFieldAttempt(durationMs = durationMs, errorMessage = message.takeIf { it.isNotBlank() })
        }

        try {
            val snippet = aiPayload!!.replace("\n", " ").take(300)
            Log.i("AI.Output", snippet)
        } catch (_: Throwable) {}

        val normalized = validation.normalizedJson
        val updates = extractFieldUpdates(normalized, setOf(field))
        val value = updates[field]

        try {
            Log.d(
                "AI.Debug",
                "Applied refinement for ${field.name.lowercase(Locale.US)} totalErrors=${refinementErrors.size}"
            )
        } catch (_: Throwable) {}
        Log.d(TAG, "Applied refinements=${field.name} errors=${refinementErrors.size}")

        return SingleFieldAttempt(refinedValue = value, durationMs = durationMs)
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
            FieldKey.NOTE -> json.optString(key).takeUnless { it.isBlank() }?.trim()
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
        if (context.allowedTags.isNotEmpty()) {
            merged = merged.copy(tags = normalizeTags(merged.tags, context.allowedTags))
        }
        return StructuredOutputValidator.sanitizeAmounts(merged)
    }

    private fun normalizeTags(tags: List<String>, allowed: List<String>): List<String> {
        if (allowed.isEmpty()) return tags
        val normalizedAllowed = allowed.associateBy { it.trim().lowercase(Locale.US) }
        return tags.mapNotNull { tag ->
            val trimmed = tag.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            normalizedAllowed[trimmed.lowercase(Locale.US)]
        }.distinct()
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

    private fun logFocusedPrompt(prompt: String) {
        if (prompt.isEmpty()) return
        try {
            Log.d("AI.Debug", "Focused prompt start >>>")
            val chunkSize = 2000
            var index = 0
            var chunk = 1
            while (index < prompt.length) {
                val end = (index + chunkSize).coerceAtMost(prompt.length)
                val segment = prompt.substring(index, end)
                Log.d("AI.Debug", "Focused prompt chunk $chunk:\n$segment")
                index = end
                chunk += 1
            }
            Log.d("AI.Debug", "<<< Focused prompt end (${prompt.length} chars)")
        } catch (_: Throwable) {
            // ignore logging failures
        }
    }

    private fun logFocusedResponse(response: String) {
        try {
            val text = response.ifBlank { "<blank>" }
            Log.d("AI.Debug", "Focused response start >>>")
            val chunkSize = 2000
            var index = 0
            var chunk = 1
            while (index < text.length) {
                val end = (index + chunkSize).coerceAtMost(text.length)
                val segment = text.substring(index, end)
                Log.d("AI.Debug", "Focused response chunk $chunk:\n$segment")
                index = end
                chunk += 1
            }
            Log.d("AI.Debug", "<<< Focused response end (${text.length} chars)")
        } catch (_: Throwable) {
            // ignore logging failures
        }
    }

    private fun applyRefinementToDraft(
        draft: HeuristicDraft,
        field: FieldKey,
        value: Any?
    ): HeuristicDraft {
        val confidences = draft.confidences.toMutableMap()
        return when (field) {
            FieldKey.MERCHANT -> {
                val text = (value as? String)?.trim()
                confidences[field] = if (!text.isNullOrEmpty()) 0.95f else draft.confidence(field)
                draft.copy(merchant = text.takeUnless { it.isNullOrEmpty() } ?: draft.merchant, confidences = confidences.toMap())
            }
            FieldKey.DESCRIPTION -> {
                val text = (value as? String)?.trim()
                confidences[field] = if (!text.isNullOrEmpty()) 0.95f else draft.confidence(field)
                draft.copy(description = text.takeUnless { it.isNullOrEmpty() }, confidences = confidences.toMap())
            }
            FieldKey.EXPENSE_CATEGORY -> {
                val text = (value as? String)?.trim()
                confidences[field] = if (!text.isNullOrEmpty()) 0.9f else draft.confidence(field)
                draft.copy(expenseCategory = text.takeUnless { it.isNullOrEmpty() }, confidences = confidences.toMap())
            }
            FieldKey.INCOME_CATEGORY -> {
                val text = (value as? String)?.trim()
                confidences[field] = if (!text.isNullOrEmpty()) 0.9f else draft.confidence(field)
                draft.copy(incomeCategory = text.takeUnless { it.isNullOrEmpty() }, confidences = confidences.toMap())
            }
            FieldKey.TAGS -> {
                val list = (value as? List<*>)?.mapNotNull { (it as? String)?.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                if (list.isNotEmpty()) {
                    confidences[field] = 0.85f
                    draft.copy(tags = list, confidences = confidences.toMap())
                } else draft
            }
            FieldKey.NOTE -> {
                val text = (value as? String)?.trim()
                confidences[field] = if (!text.isNullOrEmpty()) 0.8f else draft.confidence(field)
                draft.copy(note = text.takeUnless { it.isNullOrEmpty() }, confidences = confidences.toMap())
            }
            else -> draft
        }
    }

    private data class SingleFieldAttempt(
        val refinedValue: Any? = null,
        val durationMs: Long = 0L,
        val errorMessage: String? = null
    )
}
