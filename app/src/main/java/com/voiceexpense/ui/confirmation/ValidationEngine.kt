package com.voiceexpense.ui.confirmation

import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionType
import java.math.BigDecimal

data class ValidationResult(
    val fieldErrors: Map<String, String>,
    val formValid: Boolean
)

class ValidationEngine {
    fun validate(t: Transaction): ValidationResult {
        val errors = mutableMapOf<String, String>()
        fun require(cond: Boolean, field: String, msg: String) { if (!cond) errors[field] = msg }

        // Date
        require(t.userLocalDate != null, "date", "Date is required")

        when (t.type) {
            TransactionType.Expense -> {
                require(t.amountUsd != null && positive(t.amountUsd), "amount", "Amount must be positive")
                require(!t.expenseCategory.isNullOrBlank(), "expenseCategory", "Expense category required")
                t.splitOverallChargedUsd?.let { overall ->
                    if (t.amountUsd != null) require(overall >= t.amountUsd, "overall", "Overall must be ≥ amount")
                }
            }
            TransactionType.Income -> {
                require(t.amountUsd != null && positive(t.amountUsd), "amount", "Amount must be positive")
                require(!t.incomeCategory.isNullOrBlank(), "incomeCategory", "Income category required")
            }
            TransactionType.Transfer -> {
                // No amount for transfer in CSV model; require future transfer fields when UI supports them
            }
        }

        return ValidationResult(errors, errors.isEmpty())
    }

    private fun positive(bd: BigDecimal?): Boolean = try {
        bd != null && bd > BigDecimal.ZERO
    } catch (_: Throwable) { false }
}

