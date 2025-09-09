package com.voiceexpense.data.config

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfigDao {
    // Options CRUD
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOption(option: ConfigOption)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOptions(options: List<ConfigOption>)

    @Update
    suspend fun updateOption(option: ConfigOption): Int

    @Query("DELETE FROM config_options WHERE id = :id")
    suspend fun deleteOption(id: String): Int

    @Query("SELECT * FROM config_options WHERE type = :type AND active = 1 ORDER BY position ASC")
    fun observeOptions(type: ConfigType): Flow<List<ConfigOption>>

    @Query("SELECT * FROM config_options WHERE type = :type ORDER BY position ASC")
    suspend fun getOptions(type: ConfigType): List<ConfigOption>

    // Defaults
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setDefault(value: DefaultValue)

    @Query("DELETE FROM default_values WHERE field = :field")
    suspend fun clearDefault(field: DefaultField): Int

    @Query("SELECT optionId FROM default_values WHERE field = :field LIMIT 1")
    fun observeDefault(field: DefaultField): Flow<String?>
}

