package com.voiceexpense.data.config

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.voiceexpense.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ConfigRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ConfigDao
    private lateinit var repo: ConfigRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.configDao()
        repo = ConfigRepository(dao)
    }

    @After fun tearDown() { db.close() }

    @Test fun upsertAndDefaults() {
        runBlocking {
            val id1 = UUID.randomUUID().toString()
            val id2 = UUID.randomUUID().toString()
            repo.upsert(ConfigOption(id1, ConfigType.Account, label = "Visa", position = 0, active = true))
            repo.upsert(ConfigOption(id2, ConfigType.Account, label = "Checking", position = 1, active = true))

            val list = repo.options(ConfigType.Account).first()
            assertThat(list.map { it.label }).containsExactly("Visa", "Checking").inOrder()

            repo.setDefault(DefaultField.DefaultAccount, id2)
            val def = repo.defaultFor(DefaultField.DefaultAccount).first()
            assertThat(def).isEqualTo(id2)

            repo.delete(id1)
            val list2 = repo.options(ConfigType.Account).first()
            assertThat(list2.map { it.label }).containsExactly("Checking")
        }
    }
}
