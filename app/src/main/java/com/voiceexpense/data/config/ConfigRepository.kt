package com.voiceexpense.data.config

import androidx.room.Transaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ConfigRepository(private val dao: ConfigDao) {
    // Observe active options by type
    fun options(type: ConfigType): Flow<List<ConfigOption>> = dao.observeOptions(type)

    // Upsert/delete options
    suspend fun upsert(option: ConfigOption) = dao.upsertOption(option)
    suspend fun upsertAll(options: List<ConfigOption>) = dao.upsertOptions(options)
    suspend fun deleteAllOptions() = dao.deleteAllOptions()
    suspend fun delete(id: String) = dao.deleteOption(id)

    // Defaults
    fun defaultFor(field: DefaultField): Flow<String?> = dao.observeDefault(field)
    suspend fun setDefault(field: DefaultField, optionId: String?) = dao.setDefault(DefaultValue(field, optionId))
    suspend fun clearDefault(field: DefaultField) = dao.clearDefault(field)
    suspend fun clearAllDefaults() = dao.clearAllDefaults()

    @Transaction
    suspend fun importConfiguration(schema: ConfigImportSchema): Int {
        deleteAllOptions()
        clearAllDefaults()

        val expenseOptions = schema.expenseCategories.toEntities(ConfigType.ExpenseCategory)
        val incomeOptions = schema.incomeCategories.toEntities(ConfigType.IncomeCategory)
        val transferOptions = schema.transferCategories.toEntities(ConfigType.TransferCategory)
        val accountOptions = schema.accounts.toEntities(ConfigType.Account)
        val tagOptions = schema.tags.toEntities(ConfigType.Tag)

        val allOptions = buildList {
            addAll(expenseOptions)
            addAll(incomeOptions)
            addAll(transferOptions)
            addAll(accountOptions)
            addAll(tagOptions)
        }

        if (allOptions.isNotEmpty()) {
            upsertAll(allOptions)
        }

        applyDefaults(schema.defaults, expenseOptions, incomeOptions, transferOptions, accountOptions)

        return allOptions.size
    }

    private fun List<ConfigOptionJson>?.toEntities(type: ConfigType): List<ConfigOption> = this
        ?.mapIndexed { index, option -> option.toEntity(type, index) }
        .orEmpty()

    private fun ConfigOptionJson.toEntity(type: ConfigType, index: Int): ConfigOption = ConfigOption(
        id = UUID.randomUUID().toString(),
        type = type,
        label = label,
        position = position ?: index,
        active = active
    )

    private suspend fun applyDefaults(
        defaults: ConfigDefaultsJson?,
        expenseOptions: List<ConfigOption>,
        incomeOptions: List<ConfigOption>,
        transferOptions: List<ConfigOption>,
        accountOptions: List<ConfigOption>
    ) {
        if (defaults == null) return

        defaults.defaultExpenseCategory
            ?.let { label -> expenseOptions.find { it.label == label } }
            ?.let { option -> setDefault(DefaultField.DefaultExpenseCategory, option.id) }

        defaults.defaultIncomeCategory
            ?.let { label -> incomeOptions.find { it.label == label } }
            ?.let { option -> setDefault(DefaultField.DefaultIncomeCategory, option.id) }

        defaults.defaultTransferCategory
            ?.let { label -> transferOptions.find { it.label == label } }
            ?.let { option -> setDefault(DefaultField.DefaultTransferCategory, option.id) }

        defaults.defaultAccount
            ?.let { label -> accountOptions.find { it.label == label } }
            ?.let { option -> setDefault(DefaultField.DefaultAccount, option.id) }
    }
}
