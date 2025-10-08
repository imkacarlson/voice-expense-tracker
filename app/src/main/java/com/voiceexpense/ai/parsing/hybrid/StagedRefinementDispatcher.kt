package com.voiceexpense.ai.parsing.hybrid

import com.voiceexpense.ai.parsing.heuristic.FieldKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Broadcasts staged parsing refinement updates to interested UI observers.
 */
object StagedRefinementDispatcher {

    data class RefinementEvent(
        val transactionId: String,
        val refinedFields: Map<FieldKey, Any?>,
        val targetFields: Set<FieldKey>,
        val errors: List<String>,
        val stage1DurationMs: Long,
        val stage2DurationMs: Long,
        val confidence: Float?
    )

    private val _updates = MutableSharedFlow<RefinementEvent>(
        extraBufferCapacity = 32
    )

    val updates: SharedFlow<RefinementEvent> = _updates.asSharedFlow()

    suspend fun emit(event: RefinementEvent) {
        _updates.emit(event)
    }
}
