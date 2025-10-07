package com.voiceexpense.ai.parsing.hybrid

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import org.junit.Test

class FieldRefinementTrackerTest {

    @Test
    fun markRefining_sets_fields_to_refining() {
        val tracker = FieldRefinementTracker()

        tracker.markRefining(setOf(FieldKey.MERCHANT, FieldKey.DESCRIPTION))

        val state = tracker.refinementState.value
        assertThat(state[FieldKey.MERCHANT]).isEqualTo(FieldRefinementStatus.Refining)
        assertThat(state[FieldKey.DESCRIPTION]).isEqualTo(FieldRefinementStatus.Refining)
    }

    @Test
    fun markCompleted_persists_completed_value() {
        val tracker = FieldRefinementTracker()

        tracker.markRefining(setOf(FieldKey.MERCHANT))
        tracker.markCompleted(FieldKey.MERCHANT, "Trader Joe's")

        val status = tracker.refinementState.value[FieldKey.MERCHANT]
        assertThat(status).isInstanceOf(FieldRefinementStatus.Completed::class.java)
        val completed = status as FieldRefinementStatus.Completed
        assertThat(completed.value).isEqualTo("Trader Joe's")
    }

    @Test
    fun markUserModified_prevents_future_ai_updates() {
        val tracker = FieldRefinementTracker()

        tracker.markUserModified(FieldKey.MERCHANT)
        tracker.markRefining(setOf(FieldKey.MERCHANT))
        tracker.markCompleted(FieldKey.MERCHANT, "Starbucks")

        val status = tracker.refinementState.value[FieldKey.MERCHANT]
        assertThat(status).isEqualTo(FieldRefinementStatus.UserModified)
        assertThat(tracker.isUserModified(FieldKey.MERCHANT)).isTrue()
    }

    @Test
    fun markRefining_retains_existing_status_for_other_fields() {
        val tracker = FieldRefinementTracker()

        tracker.markRefining(setOf(FieldKey.MERCHANT))
        tracker.markCompleted(FieldKey.MERCHANT, "Trader Joe's")

        tracker.markRefining(setOf(FieldKey.DESCRIPTION))

        val state = tracker.refinementState.value
        assertThat(state[FieldKey.MERCHANT]).isInstanceOf(FieldRefinementStatus.Completed::class.java)
        assertThat(state[FieldKey.DESCRIPTION]).isEqualTo(FieldRefinementStatus.Refining)
    }
}
