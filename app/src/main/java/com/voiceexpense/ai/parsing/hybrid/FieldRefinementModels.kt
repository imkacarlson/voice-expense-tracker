package com.voiceexpense.ai.parsing.hybrid

import com.voiceexpense.ai.parsing.heuristic.FieldKey

/**
 * Tracks the refinement status of individual fields during staged parsing.
 *
 * The sealed hierarchy allows exhaustive `when` handling when applying AI updates
 * inside the UI layer.
 */
sealed class FieldRefinementStatus {
    /** Default state before refinement begins. */
    object NotStarted : FieldRefinementStatus()

    /** Indicates that focused AI refinement is in progress for the field. */
    object Refining : FieldRefinementStatus()

    /** Marks the field as completed by AI with the final value that was applied. */
    data class Completed(val value: Any?) : FieldRefinementStatus()

    /** Signals that the user edited the field, so AI updates should be skipped. */
    object UserModified : FieldRefinementStatus()
}

/**
 * Represents a single staged parsing field update to communicate with the UI layer.
 */
data class FieldUpdate(
    val field: FieldKey,
    val value: Any?,
    val timestamp: Long = System.currentTimeMillis()
)
