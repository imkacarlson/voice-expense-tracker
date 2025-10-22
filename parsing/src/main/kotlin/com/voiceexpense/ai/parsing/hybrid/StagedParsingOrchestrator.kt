package com.voiceexpense.ai.parsing.hybrid

import com.voiceexpense.ai.parsing.logging.Log
import com.voiceexpense.ai.parsing.ParsedResult
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.StructuredOutputValidator
import com.voiceexpense.ai.parsing.TagNormalizer
import com.voiceexpense.ai.parsing.heuristic.FieldConfidenceThresholds
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.ai.parsing.heuristic.HeuristicDraft
import com.voiceexpense.ai.parsing.heuristic.HeuristicExtractor
import com.voiceexpense.ai.parsing.heuristic.toParsedResult
import com.voiceexpense.ai.parsing.logging.ParsingRunLogEntryType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.LinkedHashSet
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.system.measureTimeMillis

private const val TAG = "StagedOrchestrator"
private const val GENAI_WAIT_TIMEOUT_MS = 8000L
private const val GENAI_WAIT_POLL_MS = 200L

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
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val mapAdapter = moshi.adapter<Map<String, Any?>>(
        Map::class.java as Class<Map<String, Any?>>
    )

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
        val logger = context.runLogBuilder

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
        val genAiAvailable = awaitGenAiAvailability(orderedTargetFields.isNotEmpty())

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
            logger?.addEntry(
                type = ParsingRunLogEntryType.SUMMARY,
                title = "Stage2 target fields",
                detail = "targets=${orderedTargetFields.joinToString()} aiAvailable=$genAiAvailable"
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
                logger?.addEntry(
                    type = ParsingRunLogEntryType.ERROR,
                    title = "GenAI unavailable",
                    detail = "targets=${orderedTargetFields.joinToString()}"
                )
            }
            val merged = heuristicDraft.toParsedResult(context)
            logger?.addEntry(
                type = ParsingRunLogEntryType.SUMMARY,
                title = "Stage2 skipped",
                detail = "refined=0 errors=${refinementErrors}"
            )
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
        var currentDraft = heuristicDraft  // Track the evolving draft as we refine fields
        orderedTargetFields.forEach { field ->
            val attempt = refineSingleField(
                field = field,
                input = input,
                context = context,
                draftForPrompt = currentDraft,  // Use the updated draft that includes previously refined fields
                refinementErrors = refinementErrors
            )
            stage2DurationMs += attempt.durationMs
            val normalizedValue = normalizeFieldValue(field, attempt.refinedValue, context)
            normalizedValue?.let { value ->
                cumulativeRefinements[field] = value
                currentDraft = applyRefinementToDraft(currentDraft, field, value)
                val postMerchantConf = String.format(Locale.US, "%.2f", currentDraft.confidence(FieldKey.MERCHANT))
                logger?.addEntry(
                    type = ParsingRunLogEntryType.SUMMARY,
                    title = "Refinement applied for ${field.name.lowercase(Locale.US)}",
                    detail = buildString {
                        appendLine("duration=${attempt.durationMs}ms")
                        appendLine("value=$value")
                        if (field == FieldKey.MERCHANT || field == FieldKey.DESCRIPTION) {
                            appendLine("merchant='${currentDraft.merchant}'")
                            appendLine("merchantConf=$postMerchantConf")
                        }
                    },
                    field = field
                )
                if (field == FieldKey.MERCHANT || field == FieldKey.DESCRIPTION) {
                    System.err.println(
                        "DEBUG [AI.Debug]: Applied refinement for ${field.name.lowercase(Locale.US)} merchant='${currentDraft.merchant}' merchantConf=$postMerchantConf"
                    )
                }
            }
            listener?.let { callback ->
                callback(
                    FieldRefinementUpdate(
                        field = field,
                        value = normalizedValue,
                        durationMs = attempt.durationMs,
                        error = attempt.errorMessage
                    )
                )
            }
            if (attempt.errorMessage != null) {
                logger?.addEntry(
                    type = ParsingRunLogEntryType.ERROR,
                    title = "Refinement error for ${field.name.lowercase(Locale.US)}",
                    detail = attempt.errorMessage,
                    field = field
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
        logger?.addEntry(
            type = ParsingRunLogEntryType.SUMMARY,
            title = "Staged parsing summary",
            detail = buildString {
                appendLine("refined=${fieldsRefined}")
                appendLine("errors=${refinementErrors}")
                appendLine("stage1=${stage1DurationMs}ms stage2=${stage2DurationMs}ms")
            }
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
        val logger = context.runLogBuilder
        try {
            val merchantConfidence = draftForPrompt.confidence(FieldKey.MERCHANT)
            Log.d(
                "AI.Debug",
                "Focused prompt context field=${field.name.lowercase(Locale.US)} merchant='${draftForPrompt.merchant}' merchantConf=${String.format(Locale.US, "%.2f", merchantConfidence)}"
            )
            System.err.println(
                "DEBUG [AI.Debug]: Focused prompt context field=${field.name.lowercase(Locale.US)} merchant='${draftForPrompt.merchant}' merchantConf=${String.format(Locale.US, "%.2f", merchantConfidence)}"
            )
        } catch (_: Throwable) {}
        val prePromptMerchantConf = String.format(Locale.US, "%.2f", draftForPrompt.confidence(FieldKey.MERCHANT))
        logger?.addEntry(
            type = ParsingRunLogEntryType.SUMMARY,
            title = "Draft before ${field.name.lowercase(Locale.US)} prompt",
            detail = buildString {
                appendLine("merchant='${draftForPrompt.merchant}'")
                appendLine("merchantConf=$prePromptMerchantConf")
            },
            field = field
        )
        val prompt = focusedPromptBuilder.buildFocusedPrompt(
            input = input,
            heuristicDraft = draftForPrompt,
            targetFields = linkedSetOf(field),
            context = context
        )
        logFocusedPrompt(prompt)
        logger?.addEntry(
            type = ParsingRunLogEntryType.PROMPT,
            title = "Focused prompt for ${field.name.lowercase(Locale.US)}",
            detail = prompt,
            field = field
        )

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
                    Log.e(TAG, "GenAI structured call failed", throwable)
                    logger?.addEntry(
                        type = ParsingRunLogEntryType.ERROR,
                        title = "AI failure for ${field.name.lowercase(Locale.US)}",
                        detail = throwable.stackTraceToString(),
                        field = field
                    )
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
                Log.e("AI.Validate", "Unexpected error during focused refinement: ${t.message}", t)
            } catch (_: Throwable) {}
            logger?.addEntry(
                type = ParsingRunLogEntryType.ERROR,
                title = "AI invocation error for ${field.name.lowercase(Locale.US)}",
                detail = t.stackTraceToString(),
                field = field
            )
            return SingleFieldAttempt(durationMs = durationMs, errorMessage = message)
        }

        if (aiPayload.isNullOrBlank()) {
            Log.d(TAG, "No AI payload returned; using heuristic result")
            try { Log.d("AI.Debug", "Focused refinement returned blank payload; using heuristic result") } catch (_: Throwable) {}
            val message = "AI blank response"
            refinementErrors += message
            logger?.addEntry(
                type = ParsingRunLogEntryType.ERROR,
                title = "AI blank response for ${field.name.lowercase(Locale.US)}",
                detail = "duration=${durationMs}ms",
                field = field
            )
            return SingleFieldAttempt(durationMs = durationMs, errorMessage = message)
        }

        val validation = ValidationPipeline.validateRawResponse(aiPayload!!)
        logger?.addEntry(
            type = ParsingRunLogEntryType.RESPONSE,
            title = "Focused response for ${field.name.lowercase(Locale.US)}",
            detail = aiPayload,
            field = field
        )
        if (!validation.valid || validation.normalizedJson.isNullOrBlank()) {
            val message = if (validation.errors.isNotEmpty()) {
                refinementErrors += validation.errors
                validation.errors.joinToString()
            } else {
                val msg = "AI validation failed"
                refinementErrors += msg
                msg
            }
            Log.e(TAG, "Validation failed for AI output errors=${validation.errors}")
            try {
                val snippet = aiPayload!!.replace("\n", " ").take(200)
                Log.w(
                    "AI.Validate",
                    "Focused refinement invalid: errors='${validation.errors.joinToString()}' snippet='$snippet'"
                )
            } catch (_: Throwable) {}
            logger?.addEntry(
                type = ParsingRunLogEntryType.VALIDATION,
                title = "Validation failed for ${field.name.lowercase(Locale.US)}",
                detail = buildString {
                    appendLine("errors=${validation.errors}")
                    val snippet = aiPayload!!.replace("\n", " ").take(300)
                    appendLine("responseSnippet=$snippet")
                },
                field = field
            )
            return SingleFieldAttempt(durationMs = durationMs, errorMessage = message.takeIf { it.isNotBlank() })
        }

        try {
            val snippet = aiPayload!!.replace("\n", " ").take(300)
            Log.i("AI.Output", snippet)
        } catch (_: Throwable) {}
        logger?.addEntry(
            type = ParsingRunLogEntryType.VALIDATION,
            title = "Validation succeeded for ${field.name.lowercase(Locale.US)}",
            detail = buildString {
                appendLine("duration=${durationMs}ms")
                appendLine("normalized=${validation.normalizedJson}")
            },
            field = field
        )

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
            val json = mapAdapter.fromJson(normalizedJson) ?: emptyMap()
            targets.mapNotNull { field ->
                val key = jsonKey(field)
                if (!json.containsKey(key)) return@mapNotNull null
                val value = parseValue(field, json, key)
                field to value
            }.toMap()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to parse focused AI response", t)
            emptyMap()
        }
    }

    private fun parseValue(field: FieldKey, json: Map<String, Any?>, key: String): Any? {
        val value = json[key]
        if (value == null) return null
        return when (field) {
            FieldKey.MERCHANT,
            FieldKey.DESCRIPTION,
            FieldKey.EXPENSE_CATEGORY,
            FieldKey.INCOME_CATEGORY -> (value as? String)?.takeUnless { it.isBlank() }?.trim()
            FieldKey.TAGS -> {
                when (value) {
                    is List<*> -> value.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotBlank() } }
                    is String -> listOfNotNull(value.takeIf { it.isNotBlank() })
                    else -> emptyList()
                }
            }
            else -> value
        }
    }

    private fun mergeResults(
        heuristicDraft: HeuristicDraft,
        refinedFields: Map<FieldKey, Any?>,
        context: ParsingContext
    ): ParsedResult {
        var merged = heuristicDraft.toParsedResult(context)
        refinedFields.forEach { (field, value) ->
            merged = when (field) {
                FieldKey.MERCHANT -> {
                    val normalized = capitalizeFirst((value as? String)?.trim())
                    merged.copy(merchant = normalized.takeUnless { it.isNullOrBlank() } ?: merged.merchant)
                }
                FieldKey.DESCRIPTION -> {
                    val normalized = capitalizeFirst((value as? String)?.trim())
                    merged.copy(description = normalized)
                }
                FieldKey.EXPENSE_CATEGORY -> merged.copy(expenseCategory = (value as? String)?.trim())
                FieldKey.INCOME_CATEGORY -> merged.copy(incomeCategory = (value as? String)?.trim())
                FieldKey.ACCOUNT -> merged.copy(account = (value as? String)?.trim()?.takeUnless { it.isNullOrEmpty() } ?: merged.account)
                FieldKey.TAGS -> {
                    val raw = (value as? List<*>)?.mapNotNull { (it as? String)?.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                    val normalized = TagNormalizer.normalize(raw, context.allowedTags)
                    val mergedTags = when {
                        normalized.isNotEmpty() -> normalized
                        raw.isEmpty() -> emptyList()
                        else -> merged.tags
                    }
                    merged.copy(tags = mergedTags)
                }
                else -> merged
            }
        }
        merged = merged.copy(tags = TagNormalizer.normalize(merged.tags, context.allowedTags))
        if (context.allowedAccounts.isNotEmpty() || context.knownAccounts.isNotEmpty()) {
            val accounts = context.allowedAccounts.ifEmpty { context.knownAccounts }
            merged = merged.copy(account = matchOption(merged.account, accounts))
        }
        return StructuredOutputValidator.sanitizeAmounts(merged)
    }

    private fun matchOption(value: String?, options: List<String>): String? {
        if (value.isNullOrBlank()) return null
        if (options.isEmpty()) return value.trim()
        val normalized = value.trim().lowercase(Locale.US)
        return options.firstOrNull { it.trim().lowercase(Locale.US) == normalized }
    }

    private fun capitalizeFirst(text: String?): String? {
        if (text.isNullOrEmpty()) return text
        return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
    }

    private fun normalizeFieldValue(
        field: FieldKey,
        value: Any?,
        context: ParsingContext
    ): Any? = when (field) {
        FieldKey.MERCHANT,
        FieldKey.DESCRIPTION -> capitalizeFirst((value as? String)?.trim()?.takeUnless { it.isEmpty() })

        FieldKey.EXPENSE_CATEGORY,
        FieldKey.INCOME_CATEGORY -> (value as? String)?.trim()?.takeUnless { it.isEmpty() }

        FieldKey.ACCOUNT -> {
            val accounts = context.allowedAccounts.ifEmpty { context.knownAccounts }
            val text = (value as? String)?.trim()
            matchOption(text, accounts)?.takeUnless { it.isNullOrEmpty() }
                ?: text?.takeUnless { it.isNullOrEmpty() }
        }

        FieldKey.TAGS -> {
            val raw = (value as? List<*>)?.mapNotNull { (it as? String)?.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList()
            val normalized = TagNormalizer.normalize(raw, context.allowedTags)
            when {
                normalized.isNotEmpty() -> normalized
                raw.isEmpty() -> emptyList()
                context.allowedTags.isEmpty() -> raw.distinct()
                else -> emptyList()
            }
        }

        else -> value
    }

    private fun jsonKey(field: FieldKey): String = when (field) {
        FieldKey.MERCHANT -> "merchant"
        FieldKey.DESCRIPTION -> "description"
        FieldKey.EXPENSE_CATEGORY -> "expenseCategory"
        FieldKey.INCOME_CATEGORY -> "incomeCategory"
        FieldKey.TAGS -> "tags"
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
        val updated = when (field) {
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
            FieldKey.ACCOUNT -> {
                val text = (value as? String)?.trim()
                confidences[field] = if (!text.isNullOrEmpty()) 0.9f else draft.confidence(field)
                draft.copy(account = text.takeUnless { it.isNullOrEmpty() }, confidences = confidences.toMap())
            }
            FieldKey.TAGS -> {
                val list = (value as? List<*>)?.mapNotNull { (it as? String)?.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                if (list.isNotEmpty()) {
                    confidences[field] = 0.85f
                    draft.copy(tags = list, confidences = confidences.toMap())
                } else draft
            }
            else -> draft
        }
        try {
            when (field) {
                FieldKey.MERCHANT -> Log.d(
                    "AI.Debug",
                    "Updated draft merchant='${updated.merchant}' merchantConf=${String.format(Locale.US, "%.2f", updated.confidence(FieldKey.MERCHANT))}"
                )
                FieldKey.DESCRIPTION -> Log.d(
                    "AI.Debug",
                    "Updated draft description='${updated.description}' descriptionConf=${String.format(Locale.US, "%.2f", updated.confidence(FieldKey.DESCRIPTION))}"
                )
                else -> {}
            }
        } catch (_: Throwable) {}
        return updated
    }

    private suspend fun awaitGenAiAvailability(hasTargets: Boolean): Boolean {
        if (!hasTargets) return genAiGateway.isAvailable()
        if (genAiGateway.isAvailable()) return true

        try {
            Log.w(
                TAG,
                "GenAI unavailable; waiting up to ${GENAI_WAIT_TIMEOUT_MS}ms for availability"
            )
            Log.w(
                "AI.Validate",
                "GenAI unavailable; waiting up to ${GENAI_WAIT_TIMEOUT_MS}ms before falling back"
            )
        } catch (_: Throwable) {}

        var available = false
        val waitedMs = measureTimeMillis {
            available = withTimeoutOrNull(GENAI_WAIT_TIMEOUT_MS) {
                while (!genAiGateway.isAvailable()) {
                    delay(GENAI_WAIT_POLL_MS)
                }
                true
            } ?: false
        }

        if (available) {
            try {
                Log.i(TAG, "GenAI became available after ${waitedMs}ms")
                Log.i("AI.Validate", "GenAI became available after ${waitedMs}ms")
            } catch (_: Throwable) {}
        } else {
            try {
                Log.w(TAG, "GenAI still unavailable after ${waitedMs}ms")
                Log.w("AI.Validate", "GenAI still unavailable after ${waitedMs}ms")
            } catch (_: Throwable) {}
        }
        return available
    }

    private data class SingleFieldAttempt(
        val refinedValue: Any? = null,
        val durationMs: Long = 0L,
        val errorMessage: String? = null
    )
}
