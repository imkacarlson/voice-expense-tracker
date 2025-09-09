package com.voiceexpense.data.config

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "config_options",
    indices = [Index(value = ["type", "position"])]
)
data class ConfigOption(
    @PrimaryKey val id: String,
    val type: ConfigType,
    val label: String,
    val position: Int = 0,
    val active: Boolean = true
)

enum class ConfigType {
    ExpenseCategory,
    IncomeCategory,
    TransferCategory,
    Account,
    Tag
}

