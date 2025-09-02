package com.voiceexpense.ui.confirmation.voice

import com.voiceexpense.data.model.Transaction

class PromptRenderer {
    fun summary(t: Transaction): String {
        val amount = t.amountUsd?.toPlainString() ?: "—"
        val parts = mutableListOf<String>()
        parts += amount
        if (t.merchant.isNotBlank()) parts += t.merchant
        t.description?.takeIf { it.isNotBlank() }?.let { parts += it }
        return buildString {
            append(parts.joinToString(" · "))
            append(". Say yes to save or say a field to change.")
        }.take(160) // allow a bit more for summary
    }

    fun askMissing(missing: Set<Field>): String {
        val label = when {
            missing.isEmpty() -> "Say yes to save or a field to change."
            missing.size == 1 -> "I need ${missing.first().name.lowercase()}."
            else -> "I need ${missing.joinToString { it.name.lowercase() }}."
        }
        return (label + " You can say amount, merchant, type, or date.").take(120)
    }

    fun confirm(): String = "Looks good. Save it? Say yes or cancel.".take(120)

    fun clarify(kind: Ambiguity, candidates: List<String> = emptyList()): String = when (kind) {
        Ambiguity.AmountVsOverall -> "You said two amounts. Is the larger overall charged and the smaller your share?"
        Ambiguity.UnknownType -> "Is this an expense, income, or transfer?"
        Ambiguity.MalformedAmount -> "I couldn’t parse the amount. Say it like twenty three point fifty."
        Ambiguity.ConflictFields -> "That conflicts with existing fields. Say the type or category again."
    }.take(120)
}

