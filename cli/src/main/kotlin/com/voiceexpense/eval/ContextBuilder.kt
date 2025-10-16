package com.voiceexpense.eval

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.data.config.ConfigImportSchema
import java.time.LocalDate

/**
 * Utility for translating the on-device import format (ConfigImportSchema) into
 * the ParsingContext consumed by the hybrid parser.
 */
object ContextBuilder {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(ConfigImportSchema::class.java)

    fun fromJson(json: String, defaultDate: LocalDate = LocalDate.now()): ParsingContext {
        val schema = requireNotNull(adapter.fromJson(json)) { "Unable to parse config JSON" }
        return fromSchema(schema, defaultDate)
    }

    fun fromSchema(schema: ConfigImportSchema, defaultDate: LocalDate = LocalDate.now()): ParsingContext {
        val allowedExpense = schema.expenseCategories.activeLabels()
        val allowedIncome = schema.incomeCategories.activeLabels()
        val allowedAccounts = schema.accounts.activeLabels()
        val allowedTags = schema.tags.activeLabels()

        return ParsingContext(
            recentMerchants = emptyList(),
            recentCategories = allowedExpense,
            knownAccounts = allowedAccounts,
            defaultDate = defaultDate,
            allowedExpenseCategories = allowedExpense,
            allowedIncomeCategories = allowedIncome,
            allowedTags = allowedTags,
            allowedAccounts = allowedAccounts
        )
    }

    private fun List<com.voiceexpense.data.config.ConfigOptionJson>?.activeLabels(): List<String> {
        return this
            ?.filter { it.active }
            ?.mapNotNull { option -> option.label.takeUnless { it.isBlank() } }
            .orEmpty()
    }
}
