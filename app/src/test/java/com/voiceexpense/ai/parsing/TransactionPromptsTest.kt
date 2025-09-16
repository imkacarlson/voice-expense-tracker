package com.voiceexpense.ai.parsing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TransactionPromptsTest {
    @Test
    fun system_instruction_mentions_required_fields() {
        val s = TransactionPrompts.SYSTEM_INSTRUCTION
        listOf(
            "amountUsd",
            "merchant",
            "description",
            "type",
            "tags",
            "userLocalDate",
            "account",
            "confidence"
        ).forEach { key ->
            assertThat(s).contains(key)
        }
        assertThat(s.lowercase()).contains("return json only")
        assertThat(s).contains("splitOverallChargedUsd")
    }

    @Test
    fun sample_mappings_cover_key_cases() {
        val examples = TransactionPrompts.SAMPLE_MAPPINGS
        assertThat(examples).isNotEmpty()
        assertThat(examples.any { it.tags.contains(TransactionPrompts.PromptCategory.SPLIT) }).isTrue()
        assertThat(examples.any { it.tags.contains(TransactionPrompts.PromptCategory.INCOME) }).isTrue()
        assertThat(examples.any { it.tags.contains(TransactionPrompts.PromptCategory.TRANSFER) }).isTrue()
    }
}
