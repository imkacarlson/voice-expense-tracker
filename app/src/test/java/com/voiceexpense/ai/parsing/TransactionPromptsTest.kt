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
        assertThat(s.lowercase()).contains("return only json")
    }

    @Test
    fun templates_produce_expected_phrasing() {
        val e = TransactionPrompts.expense("Starbucks", 4.75, "latte")
        assertThat(e).contains("spent 4.75 at Starbucks for latte")

        val i = TransactionPrompts.income("employer", 2200, "july")
        assertThat(i).contains("paycheck 2200, tag july")

        val t = TransactionPrompts.transfer(100, "checking", "savings")
        assertThat(t).isEqualTo("transfer 100 from checking to savings")
    }

    @Test
    fun example_utterances_present() {
        assertThat(TransactionPrompts.EXAMPLE_UTTERANCES).isNotEmpty()
        assertThat(TransactionPrompts.EXAMPLE_UTTERANCES.first().lowercase()).contains("coffee")
    }
}

