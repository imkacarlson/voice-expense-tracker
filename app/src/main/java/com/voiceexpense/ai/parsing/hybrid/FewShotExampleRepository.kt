package com.voiceexpense.ai.parsing.hybrid

import com.voiceexpense.ai.parsing.TransactionPrompts
import com.voiceexpense.ai.parsing.TransactionPrompts.PromptCategory

/** Provides curated Input→Output sample pairs used by PromptBuilder. */
object FewShotExampleRepository {
    data class ExamplePair(
        val id: String,
        val input: String,
        val outputJson: String,
        val categories: Set<PromptCategory>
    )

    private val examples: List<ExamplePair> = TransactionPrompts.SAMPLE_MAPPINGS.map {
        ExamplePair(it.id, it.input, it.outputJson, it.tags)
    }

    fun defaultExpense(): ExamplePair? =
        examples.firstOrNull { it.categories.contains(PromptCategory.EXPENSE) && !it.categories.contains(PromptCategory.SPLIT) }

    fun splitExpense(): ExamplePair? =
        examples.firstOrNull { it.categories.contains(PromptCategory.SPLIT) }

    fun subscriptionExpense(): ExamplePair? =
        examples.firstOrNull { it.categories.contains(PromptCategory.SUBSCRIPTION) }

    fun income(): ExamplePair? = examples.firstOrNull { it.categories.contains(PromptCategory.INCOME) }

    fun transfer(): ExamplePair? = examples.firstOrNull { it.categories.contains(PromptCategory.TRANSFER) }

    fun all(): List<ExamplePair> = examples
}
