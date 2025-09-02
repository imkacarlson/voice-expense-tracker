package com.voiceexpense.ui.confirmation.voice

import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

class CorrectionIntentParser {
    fun parse(text: String, current: Transaction): CorrectionIntent {
        val raw = text.trim()
        if (raw.isBlank()) return CorrectionIntent.Unknown(raw)

        val lc = raw.lowercase()

        // Confirm / Cancel / Repeat
        if (lc in setOf("yes", "save", "looks good", "confirm")) return CorrectionIntent.Confirm
        if (lc in setOf("cancel", "discard")) return CorrectionIntent.Cancel
        if (lc in setOf("repeat", "again", "say again")) return CorrectionIntent.Repeat

        // Type
        when {
            lc.contains("expense") -> return CorrectionIntent.SetType(TransactionType.Expense)
            lc.contains("income") -> return CorrectionIntent.SetType(TransactionType.Income)
            lc.contains("transfer") -> return CorrectionIntent.SetType(TransactionType.Transfer)
        }

        // Amounts (share and overall charged)
        // Heuristic: if phrase contains "overall" treat as overall charged
        val number = Regex("(\\d+)(?:\\.(\\d{1,2}))?").find(lc)?.value
        if (number != null) {
            val value = runCatching { BigDecimal(number) }.getOrNull()
            if (value != null) {
                if (lc.contains("overall") || lc.contains("charged") || lc.contains("total")) {
                    return CorrectionIntent.SetOverallCharged(value)
                }
                return CorrectionIntent.SetAmount(value)
            }
        }

        // Merchant / Description
        Regex("^(merchant|store)\\s+(.+)$").find(raw)?.let {
            return CorrectionIntent.SetMerchant(it.groupValues[2].trim())
        }
        Regex("^(description|desc)\\s+(.+)$").find(raw)?.let {
            return CorrectionIntent.SetDescription(it.groupValues[2].trim())
        }

        // Categories
        Regex("^(category|expense category)\\s+(.+)$", RegexOption.IGNORE_CASE).find(raw)?.let {
            return CorrectionIntent.SetExpenseCategory(it.groupValues[2].trim())
        }
        Regex("^(income category)\\s+(.+)$", RegexOption.IGNORE_CASE).find(raw)?.let {
            return CorrectionIntent.SetIncomeCategory(it.groupValues[2].trim())
        }

        // Tags (append by default, replace if phrase starts with replace tags)
        Regex("^(replace\\s+)?tags?:\\s*(.+)$", RegexOption.IGNORE_CASE).find(raw)?.let {
            val replace = !it.groupValues[1].isNullOrBlank()
            val tags = it.groupValues[2].split(',').map { s -> s.trim() }.filter { s -> s.isNotBlank() }
            return CorrectionIntent.SetTags(tags, replace)
        }

        // Account
        Regex("^(account|card)\\s+(.+)$", RegexOption.IGNORE_CASE).find(raw)?.let {
            return CorrectionIntent.SetAccount(it.groupValues[2].trim())
        }

        // Date (very basic: today, yesterday, YYYY-MM-DD)
        when (lc) {
            "today" -> return CorrectionIntent.SetDate(LocalDate.now())
            "yesterday" -> return CorrectionIntent.SetDate(LocalDate.now().minusDays(1))
        }
        Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(lc)?.let {
            val y = it.groupValues[1].toInt()
            val m = it.groupValues[2].toInt()
            val d = it.groupValues[3].toInt()
            return CorrectionIntent.SetDate(LocalDate.of(y, m, d))
        }

        return CorrectionIntent.Unknown(raw)
    }
}

