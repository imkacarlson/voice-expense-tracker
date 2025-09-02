package com.voiceexpense.ui.confirmation.voice

import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

// Fields that can be corrected via voice
enum class Field {
    Amount,
    Merchant,
    Description,
    Type,
    ExpenseCategory,
    IncomeCategory,
    Tags,
    Account,
    OverallCharged,
    Date
}

// Ambiguity categories detected during parsing
enum class Ambiguity {
    AmountVsOverall,
    UnknownType,
    MalformedAmount,
    ConflictFields
}

// Prompt kinds emitted by the loop
enum class PromptKind {
    Summary,
    AskMissing,
    Clarify,
    Confirm
}

data class Timeouts(
    val initialSeconds: Int = 8,
    val repromptSeconds: Int = 8
)

// Public intents produced by speech parsing
sealed class CorrectionIntent {
    data class SetAmount(val amount: BigDecimal) : CorrectionIntent()
    data class SetMerchant(val name: String) : CorrectionIntent()
    data class SetDescription(val text: String) : CorrectionIntent()
    data class SetType(val type: TransactionType) : CorrectionIntent()
    data class SetExpenseCategory(val name: String) : CorrectionIntent()
    data class SetIncomeCategory(val name: String) : CorrectionIntent()
    data class SetTags(val tags: List<String>, val replace: Boolean = false) : CorrectionIntent()
    data class SetAccount(val name: String) : CorrectionIntent()
    data class SetOverallCharged(val amount: BigDecimal) : CorrectionIntent()
    data class SetDate(val date: LocalDate) : CorrectionIntent()
    object Confirm : CorrectionIntent()
    object Cancel : CorrectionIntent()
    object Repeat : CorrectionIntent()
    data class Unknown(val raw: String) : CorrectionIntent()
}

// Loop state tracked by the ViewModel and controller
data class LoopState(
    val awaiting: PromptKind = PromptKind.Summary,
    val lastPrompt: String = "",
    val missing: Set<Field> = emptySet(),
    val isSpeaking: Boolean = false,
    val timeouts: Timeouts = Timeouts()
)

