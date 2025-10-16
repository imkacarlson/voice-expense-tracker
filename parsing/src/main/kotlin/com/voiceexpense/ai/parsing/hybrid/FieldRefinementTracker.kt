package com.voiceexpense.ai.parsing.hybrid

import com.voiceexpense.ai.parsing.logging.Log
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages per-field refinement state and preserves user edits during staged parsing.
 */
class FieldRefinementTracker {

    private val tag = "FieldRefinement"

    private val _refinementState = MutableStateFlow<Map<FieldKey, FieldRefinementStatus>>(emptyMap())
    val refinementState: StateFlow<Map<FieldKey, FieldRefinementStatus>> = _refinementState.asStateFlow()

    fun markRefining(fields: Set<FieldKey>) {
        if (fields.isEmpty()) return

        _refinementState.update { current ->
            val next = current.toMutableMap()
            fields.forEach { field ->
                if (next[field] !is FieldRefinementStatus.UserModified) {
                    next[field] = FieldRefinementStatus.Refining
                }
            }
            next
        }
        Log.d(tag, "markRefining fields=${fields.joinToString()}")
    }

    fun markCompleted(field: FieldKey, value: Any?) {
        _refinementState.update { current ->
            val next = current.toMutableMap()
            if (next[field] !is FieldRefinementStatus.UserModified) {
                next[field] = FieldRefinementStatus.Completed(value)
            }
            next
        }
        Log.d(tag, "markCompleted field=$field value=$value")
    }

    fun markUserModified(field: FieldKey) {
        _refinementState.update { current ->
            val next = current.toMutableMap()
            next[field] = FieldRefinementStatus.UserModified
            next
        }
        Log.d(tag, "markUserModified field=$field")
    }

    fun isUserModified(field: FieldKey): Boolean {
        return refinementState.value[field] is FieldRefinementStatus.UserModified
    }
}
