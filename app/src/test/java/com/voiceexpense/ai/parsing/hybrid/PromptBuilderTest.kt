package com.voiceexpense.ai.parsing.hybrid

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.heuristic.HeuristicExtractor
import org.junit.Test

class PromptBuilderTest {
    private val builder = PromptBuilder()

    @Test
    fun includes_system_and_constraints() {
        val p = builder.build("coffee 4.75 at starbucks")
        assertThat(p.lowercase()).contains("return json only")
        assertThat(p).contains("Examples:")
        assertThat(p).contains("Input: ")
    }

    @Test
    fun selects_transfer_examples_for_transfer_input() {
        val p = builder.build("transfer 100 from checking to savings")
        val examplesBlock = p.substringAfter("Examples:")
        assertThat(examplesBlock).contains("\"type\":\"Transfer\"")
    }

    @Test
    fun selects_split_examples_for_split_like_input() {
        val p = builder.build("dinner 60, my share 20, overall charged 60")
        val examplesBlock = p.substringAfter("Examples:")
        assertThat(examplesBlock).contains("splitOverallChargedUsd")
    }

    @Test
    fun includes_context_hints_when_available() {
        val ctx = ParsingContext(
            recentMerchants = listOf("Starbucks", "Chipotle"),
            knownAccounts = listOf("Checking", "Savings")
        )
        val p = builder.build("coffee 4.75", ctx)
        assertThat(p).contains("recentMerchants=")
        assertThat(p).contains("knownAccounts=")
    }

    @Test
    fun injects_hint_block() {
        val ctx = ParsingContext(
            defaultDate = java.time.LocalDate.of(2025, 9, 13),
            allowedAccounts = listOf("Citi Double Cash Card")
        )
        val heuristic = HeuristicExtractor().extract(
            "On September 12th I spent 11.10 at Domino's on my Citi Double Cash card",
            ctx
        )
        val p = builder.build(
            "On September 12th I spent 11.10 at Domino's on my Citi Double Cash card",
            ctx,
            heuristic
        )
        assertThat(p).contains("Heuristic hints (confidence 0..1; adjust if incorrect):")
        assertThat(p).contains("\\"account\\":{\\"value\\":\\"Citi Double Cash Card\\"")
    }

    @Test
    fun prompt_remains_under_character_limit() {
        val ctx = ParsingContext(
            recentMerchants = List(10) { "Merchant$it" },
            knownAccounts = List(10) { "Account$it" },
            allowedExpenseCategories = listOf("Eating Out", "Utilities", "Travel"),
            allowedIncomeCategories = listOf("Paycheck", "Bonus"),
            allowedTags = listOf("Auto-Paid", "Subscription", "Splitwise"),
            allowedAccounts = listOf(
                "Chase Sapphire Preferred",
                "Citi Double Cash Card",
                "Vanguard Cash Plus (Savings)"
            )
        )
        val heuristic = HeuristicExtractor().extract(
            "On September 11th Emily put into Splitwise that she reloaded our EZ Pass and I owe her 17 50 for that",
            ctx
        )
        val prompt = builder.build(
            "On September 11th Emily put into Splitwise that she reloaded our EZ Pass and I owe her 17 50 for that",
            ctx,
            heuristic
        )
        assertThat(prompt.length).isLessThan(2000)
    }
}
