package com.voiceexpense.ai.parsing.hybrid

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.ai.parsing.heuristic.HeuristicDraft
import org.junit.Test

class FocusedPromptBuilderTest {

    private val builder = FocusedPromptBuilder()

    @Test
    fun buildFocusedPrompt_forSingleField_usesTemplateLayout() {
        val draft = HeuristicDraft(
            merchant = null,
            confidences = mapOf(FieldKey.MERCHANT to 0.15f)
        )

        val prompt = builder.buildFocusedPrompt(
            input = "coffee at Blue Bottle",
            heuristicDraft = draft,
            targetFields = setOf(FieldKey.MERCHANT)
        )

        assertThat(prompt).contains("Field: Merchant")
        assertThat(prompt).contains("key \"merchant\"")
        assertThat(prompt).contains("Respond with compact JSON")
    }

    @Test
    fun buildFocusedPrompt_forThreeFields_requestsMinimalJson() {
        val draft = HeuristicDraft(
            merchant = "Starbucks",
            description = "Coffee",
            expenseCategory = null,
            confidences = mapOf(
                FieldKey.MERCHANT to 0.4f,
                FieldKey.DESCRIPTION to 0.3f,
                FieldKey.EXPENSE_CATEGORY to 0.1f
            )
        )

        val prompt = builder.buildFocusedPrompt(
            input = "Bought coffee at Starbucks",
            heuristicDraft = draft,
            targetFields = setOf(
                FieldKey.MERCHANT,
                FieldKey.DESCRIPTION,
                FieldKey.EXPENSE_CATEGORY
            )
        )

        assertThat(prompt).contains("Return a JSON object with only these keys: \"merchant\", \"description\", \"expenseCategory\"")
        assertThat(prompt).contains("Heuristic summary:")
        assertThat(prompt).doesNotContain("Examples:")
    }

    @Test
    fun buildFocusedPrompt_remainsUnderCharacterLimit() {
        val draft = HeuristicDraft(
            description = "Long form description",
            expenseCategory = null,
            tags = emptyList(),
            confidences = mapOf(
                FieldKey.DESCRIPTION to 0.2f,
                FieldKey.EXPENSE_CATEGORY to 0.2f,
                FieldKey.TAGS to 0.1f
            )
        )
        val context = ParsingContext(
            recentMerchants = List(20) { "Merchant$it" },
            allowedExpenseCategories = List(20) { "Category$it" },
            allowedTags = List(20) { "tag$it" }
        )

        val prompt = builder.buildFocusedPrompt(
            input = "Dinner with friends at Local Bistro",
            heuristicDraft = draft,
            targetFields = setOf(FieldKey.DESCRIPTION, FieldKey.EXPENSE_CATEGORY, FieldKey.TAGS),
            context = context
        )

        assertThat(prompt.length).isAtMost(1000)
    }

    @Test
    fun buildFocusedPrompt_includes_all_accounts_without_truncation() {
        val draft = HeuristicDraft(
            account = null,
            confidences = mapOf(FieldKey.ACCOUNT to 0f)
        )
        val accounts = listOf(
            "Checking",
            "Savings",
            "Corporate Card",
            "Business Platinum",
            "Travel Rewards",
            "Bilt Card",
            "Cashback Visa",
            "Investment Account",
            "Joint Checking"
        )
        val context = ParsingContext(
            allowedAccounts = accounts
        )

        val prompt = builder.buildFocusedPrompt(
            input = "Paid for dinner",
            heuristicDraft = draft,
            targetFields = setOf(FieldKey.ACCOUNT),
            context = context
        )

        assertThat(prompt).contains("accounts=${accounts.joinToString()}")
        assertThat(prompt).contains("Joint Checking")
    }
}
