package com.voiceexpense.data.config

import kotlinx.coroutines.flow.Flow

class ConfigRepository(private val dao: ConfigDao) {
    // Observe active options by type
    fun options(type: ConfigType): Flow<List<ConfigOption>> = dao.observeOptions(type)

    // Upsert/delete options
    suspend fun upsert(option: ConfigOption) = dao.upsertOption(option)
    suspend fun upsertAll(options: List<ConfigOption>) = dao.upsertOptions(options)
    suspend fun delete(id: String) = dao.deleteOption(id)

    // Defaults
    fun defaultFor(field: DefaultField): Flow<String?> = dao.observeDefault(field)
    suspend fun setDefault(field: DefaultField, optionId: String?) = dao.setDefault(DefaultValue(field, optionId))
    suspend fun clearDefault(field: DefaultField) = dao.clearDefault(field)
}

