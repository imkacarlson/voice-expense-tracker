package com.voiceexpense.data.config

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ConfigOptionJson(
    val label: String,
    val position: Int? = null,
    val active: Boolean = true
)

@JsonClass(generateAdapter = true)
data class ConfigDefaultsJson(
    @Json(name = "defaultExpenseCategory")
    val defaultExpenseCategory: String? = null,
    @Json(name = "defaultIncomeCategory")
    val defaultIncomeCategory: String? = null,
    @Json(name = "defaultTransferCategory")
    val defaultTransferCategory: String? = null,
    @Json(name = "defaultAccount")
    val defaultAccount: String? = null
)

@JsonClass(generateAdapter = true)
data class ConfigImportSchema(
    @Json(name = "ExpenseCategory")
    val expenseCategories: List<ConfigOptionJson>?,
    @Json(name = "IncomeCategory")
    val incomeCategories: List<ConfigOptionJson>?,
    @Json(name = "TransferCategory")
    val transferCategories: List<ConfigOptionJson>?,
    @Json(name = "Account")
    val accounts: List<ConfigOptionJson>?,
    @Json(name = "Tag")
    val tags: List<ConfigOptionJson>?,
    @Json(name = "defaults")
    val defaults: ConfigDefaultsJson? = null
)

sealed class ConfigImportResult {
    data class Success(val optionsImported: Int) : ConfigImportResult()
    data class InvalidJson(val message: String) : ConfigImportResult()
    data class ValidationError(val message: String) : ConfigImportResult()
    data class DatabaseError(val throwable: Throwable) : ConfigImportResult()
    data class FileReadError(val throwable: Throwable) : ConfigImportResult()
}
