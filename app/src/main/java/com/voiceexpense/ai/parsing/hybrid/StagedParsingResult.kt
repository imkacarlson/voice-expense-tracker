package com.voiceexpense.ai.parsing.hybrid

import com.voiceexpense.ai.parsing.ParsedResult
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.ai.parsing.heuristic.HeuristicDraft

/**
 * Immutable result of staged parsing with metadata for downstream consumers.
 *
 * The object bundles the original heuristics, any AI-refined fields, the merged
 * ParsedResult, error history, and timing information for each stage.
 */
data class StagedParsingResult(
    val heuristicDraft: HeuristicDraft,
    val refinedFields: Map<FieldKey, Any?>,
    val mergedResult: ParsedResult,
    val fieldsRefined: Set<FieldKey>,
    val targetFields: Set<FieldKey>,
    val refinementErrors: List<String>,
    val stage1DurationMs: Long,
    val stage2DurationMs: Long
) {
    /** Returns true when AI provided an update for the given field. */
    fun wasFieldRefined(field: FieldKey): Boolean = fieldsRefined.contains(field)

    /** Returns the AI-provided value for the given field, if any. */
    fun refinementValue(field: FieldKey): Any? = refinedFields[field]

    /** Indicates whether any refinement errors were encountered. */
    val hasErrors: Boolean
        get() = refinementErrors.isNotEmpty()

    /** Total time spent across heuristics and refinement stages. */
    val totalDurationMs: Long
        get() = stage1DurationMs + stage2DurationMs
}
