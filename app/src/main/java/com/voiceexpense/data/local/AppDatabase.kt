package com.voiceexpense.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.voiceexpense.data.config.ConfigDao
import com.voiceexpense.data.config.ConfigOption
import com.voiceexpense.data.config.DefaultValue
import com.voiceexpense.data.model.Converters
import com.voiceexpense.data.model.Transaction

@Database(
    entities = [Transaction::class, ConfigOption::class, DefaultValue::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun configDao(): ConfigDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new nullable columns for transfer metadata; additive + no data loss.
                db.execSQL("ALTER TABLE transactions ADD COLUMN transferCategory TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN transferDestination TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create config_options table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS config_options (
                        id TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        label TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        active INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_config_options_type_position ON config_options(type, position)")

                // Create default_values table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS default_values (
                        field TEXT NOT NULL PRIMARY KEY,
                        optionId TEXT
                    )
                    """.trimIndent()
                )
            }
        }

    }
}
