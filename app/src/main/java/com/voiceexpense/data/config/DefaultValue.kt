package com.voiceexpense.data.config

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "default_values")
data class DefaultValue(
    @PrimaryKey val field: DefaultField,
    val optionId: String?
)

enum class DefaultField {
    DefaultExpenseCategory,
    DefaultIncomeCategory,
    DefaultTransferCategory,
    DefaultAccount
}

