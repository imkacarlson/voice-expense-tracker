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
import java.util.LinkedHashSet
import java.util.Locale
import kotlin.collections.ArrayDeque
import kotlin.system.measureTimeMillis

private const val TAG = "StagedOrchestrator"
private const val AI_TIMEOUT_MS = 12_000L

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
        stage1Snapshot: Stage1Snapshot? = null
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

        suspend fun runAttempt(
            targets: LinkedHashSet<FieldKey>
        ): RefinementAttempt {
            if (targets.isEmpty()) return RefinementAttempt.empty()
            return refineTargets(
                input = input,
                context = context,
                draftForPrompt = heuristicDraft,
                targets = targets,
                refinementErrors = refinementErrors
            ).also { attempt ->
                stage2DurationMs += attempt.durationMs
                cumulativeRefinements += attempt.refinedFields
                if (attempt.refinedFields.isNotEmpty()) {
                    heuristicDraft = applyRefinementsToDraft(heuristicDraft, attempt.refinedFields)
                }
            }
        }

        val remainingTargets = ArrayDeque(orderedTargetFields)
        val firstTargets = remainingTargets
            .removeFirstOrNull()
            ?.let { linkedSetOf(it) }
            ?: linkedSetOf()

        val firstAttempt = runAttempt(firstTargets)

        var continueToSecondCall = remainingTargets.isNotEmpty()
        if (continueToSecondCall) {
            val stillNeeded = fieldSelector
                .selectFieldsForRefinement(heuristicDraft, thresholds)
                .toSet()
            remainingTargets.removeIf { it !in stillNeeded }
            continueToSecondCall = remainingTargets.isNotEmpty() && firstAttempt.mayProceed()
        }

        if (continueToSecondCall) {
            val secondTargets = LinkedHashSet(remainingTargets)
            runAttempt(secondTargets)
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

    private suspend fun refineTargets(
        input: String,
        context: ParsingContext,
        draftForPrompt: HeuristicDraft,
        targets: LinkedHashSet<FieldKey>,
        refinementErrors: MutableList<String>
    ): RefinementAttempt {
        val prompt = focusedPromptBuilder.buildFocusedPrompt(
            input = input,
            heuristicDraft = draftForPrompt,
            targetFields = targets,
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
                val result = withTimeout(AI_TIMEOUT_MS) {
                    genAiGateway.structured(prompt)
                }
                aiPayload = result.getOrElse { throwable ->
                    refinementErrors += "AI failure: ${throwable.message ?: throwable::class.simpleName}".trim()
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
        } catch (timeout: TimeoutCancellationException) {
            durationMs = AI_TIMEOUT_MS
            refinementErrors += "AI timeout after ${AI_TIMEOUT_MS}ms"
            Log.w(TAG, "GenAI refinement timed out", timeout)
            try {
                Log.w("AI.Validate", "GenAI refinement timed out after ${AI_TIMEOUT_MS}ms for ${targets.joinToString()}")
            } catch (_: Throwable) {}
            return RefinementAttempt.empty(durationMs = durationMs, timedOut = true)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            refinementErrors += "AI invocation error: ${t.message ?: t::class.simpleName}".trim()
            Log.e(TAG, "Unexpected error during GenAI call", t)
            try {
                Log.e("AI.Validate", "Unexpected error during focused refinement: ${t.message}")
            } catch (_: Throwable) {}
            return RefinementAttempt.empty(durationMs = durationMs, failed = true)
        }

        if (aiPayload.isNullOrBlank()) {
            Log.d(TAG, "No AI payload returned; using heuristic result")
            try { Log.d("AI.Debug", "Focused refinement returned blank payload; using heuristic result") } catch (_: Throwable) {}
            return RefinementAttempt.empty(durationMs = durationMs)
        }

        val validation = ValidationPipeline.validateRawResponse(aiPayload!!)
        if (!validation.valid || validation.normalizedJson.isNullOrBlank()) {
            if (validation.errors.isNotEmpty()) {
                refinementErrors += validation.errors
            } else {
                refinementErrors += "AI validation failed"
            }
            Log.w(TAG, "Validation failed for AI output errors=${validation.errors}")
            try {
                val snippet = aiPayload!!.replace("\n", " ").take(200)
                Log.w(
                    "AI.Validate",
                    "Focused refinement invalid: errors='${validation.errors.joinToString()}' snippet='$snippet'"
                )
            } catch (_: Throwable) {}
            return RefinementAttempt.empty(durationMs = durationMs, failed = true)
        }

        try {
            val snippet = aiPayload!!.replace("\n", " ").take(300)
            Log.i("AI.Output", snippet)
        } catch (_: Throwable) {}

        val normalized = validation.normalizedJson
        val updates = extractFieldUpdates(normalized, targets)
        val refinedFields = updates.filterKeys { it in targets }

        try {
            Log.d(
                "AI.Debug",
                "Applied refinements=${refinedFields.keys.joinToString()} totalErrors=${refinementErrors.size}"
            )
        } catch (_: Throwable) {}
        Log.d(TAG, "Applied refinements=${refinedFields.keys.joinToString()} errors=${refinementErrors.size}")

        return RefinementAttempt(
            refinedFields = refinedFields,
            durationMs = durationMs
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

    private fun applyRefinementsToDraft(
        draft: HeuristicDraft,
        refinements: Map<FieldKey, Any?>
    ): HeuristicDraft {
        if (refinements.isEmpty()) return draft
        val confidences = draft.confidences.toMutableMap()

        var merchant = draft.merchant
        var description = draft.description
        var expenseCategory = draft.expenseCategory
        var incomeCategory = draft.incomeCategory
        var tags = draft.tags
        var note = draft.note

        refinements.forEach { (field, value) ->
            when (field) {
                FieldKey.MERCHANT -> {
                    val text = (value as? String)?.trim()
                    merchant = text.takeUnless { it.isNullOrEmpty() } ?: merchant
                    text?.let { confidences[field] = 0.95f }
                }
                FieldKey.DESCRIPTION -> {
                    val text = (value as? String)?.trim().takeUnless { it.isNullOrEmpty() }
                    description = text
                    text?.let { confidences[field] = 0.95f }
                }
                FieldKey.EXPENSE_CATEGORY -> {
                    val text = (value as? String)?.trim().takeUnless { it.isNullOrEmpty() }
                    expenseCategory = text
                    text?.let { confidences[field] = 0.9f }
                }
                FieldKey.INCOME_CATEGORY -> {
                    val text = (value as? String)?.trim().takeUnless { it.isNullOrEmpty() }
                    incomeCategory = text
                    text?.let { confidences[field] = 0.9f }
                }
                FieldKey.TAGS -> {
                    val list = (value as? List<*>)?.mapNotNull { (it as? String)?.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                    if (list.isNotEmpty()) {
                        tags = list
                        confidences[field] = 0.85f
                    }
                }
                FieldKey.NOTE -> {
                    val text = (value as? String)?.trim().takeUnless { it.isNullOrEmpty() }
                    note = text
                    text?.let { confidences[field] = 0.8f }
                }
                else -> {
                    // Non-refinable fields are ignored here.
                }
            }
        }

        return draft.copy(
            merchant = merchant,
            description = description,
            expenseCategory = expenseCategory,
            incomeCategory = incomeCategory,
            tags = tags,
            note = note,
            confidences = confidences.toMap()
        )
    }

    private data class RefinementAttempt(
        val refinedFields: Map<FieldKey, Any?>,
        val durationMs: Long,
        val timedOut: Boolean = false,
        val failed: Boolean = false
    ) {
        fun mayProceed(): Boolean = !timedOut

        companion object {
            fun empty(durationMs: Long = 0L, timedOut: Boolean = false, failed: Boolean = false): RefinementAttempt =
                RefinementAttempt(emptyMap(), durationMs, timedOut = timedOut, failed = failed)
        }
    }
}
