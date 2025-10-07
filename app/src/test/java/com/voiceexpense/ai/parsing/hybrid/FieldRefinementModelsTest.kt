package com.voiceexpense.ai.parsing.hybrid

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import org.junit.Test

class FieldRefinementModelsTest {

    @Test
    fun completed_status_preserves_ai_value() {
        val status = FieldRefinementStatus.Completed("Coffee Shop")

        assertThat(status.value).isEqualTo("Coffee Shop")
    }

    @Test
    fun user_modified_status_is_singleton() {
        val first = FieldRefinementStatus.UserModified
        val second = FieldRefinementStatus.UserModified

        assertThat(first).isSameInstanceAs(second)
    }

    @Test
    fun field_update_defaults_timestamp() {
        val before = System.currentTimeMillis()
        val update = FieldUpdate(FieldKey.MERCHANT, "Trader Joe's")
        val after = System.currentTimeMillis()

        assertThat(update.timestamp).isAtLeast(before)
        assertThat(update.timestamp).isAtMost(after)
    }
}
