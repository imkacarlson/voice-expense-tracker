package com.voiceexpense.ui.common

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.allOf
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.voiceexpense.R
import com.voiceexpense.data.config.ConfigRepository
import com.voiceexpense.data.config.ConfigType
import com.voiceexpense.data.local.AppDatabase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf as hamAllOf
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SettingsConfigImportTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var repository: ConfigRepository
    @Inject lateinit var dao: DelegatingConfigDao
    @Inject lateinit var db: AppDatabase

    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        hiltRule.inject()
        cacheDir = ApplicationProvider.getApplicationContext<Context>().cacheDir
        db.clearAllTables()
    }

    @After
    fun tearDown() {
        db.clearAllTables()
    }

    @Test
    fun testImportButton_LaunchesFilePicker() {
        Intents.init()
        try {
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))

            ActivityScenario.launch(SettingsActivity::class.java).use {
                onView(withId(R.id.btn_import_config)).perform(click())
                intended(allOf(hasAction(Intent.ACTION_OPEN_DOCUMENT), hasType("application/json")))
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun testSuccessfulImport_RefreshesUI() {
        runBlocking {
            repository.upsert(com.voiceexpense.data.config.ConfigOption("legacy", ConfigType.ExpenseCategory, "Legacy", 0, true))
        }
        val json = sampleJson(options = listOf("Travel", "Meals"))
        val uri = writeJson("success.json", json)

        Intents.init()
        try {
            stubFilePicker(uri, Activity.RESULT_OK)
            ActivityScenario.launch(SettingsActivity::class.java).use {
                onView(withId(R.id.btn_import_config)).perform(click())
                onView(withText(R.string.import_confirm_title)).check(matches(isDisplayed()))
                onView(withText(R.string.replace)).perform(click())

                waitForCondition {
                    runBlocking {
                        repository.options(ConfigType.ExpenseCategory).first().any { it.label == "Travel" }
                    }
                }

                onData(hamAllOf(instanceOf(String::class.java), equalTo("Travel")))
                    .inAdapterView(withId(R.id.list_options))
                    .check(matches(isDisplayed()))
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun testInvalidJson_ShowsErrorToast() {
        val uri = writeJson("invalid.json", "{invalid json")
        Intents.init()
        try {
            stubFilePicker(uri, Activity.RESULT_OK)
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                var decor: View? = null
                scenario.onActivity { decor = it.window.decorView }
                onView(withId(R.id.btn_import_config)).perform(click())
                onView(withText(R.string.import_confirm_title)).check(matches(isDisplayed()))
                onView(withText(R.string.replace)).perform(click())

                onView(withText(startsWith("Invalid JSON format")))
                    .inRoot(withDecorView(not(equalTo(decor))))
                    .check(matches(isDisplayed()))
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun testConfirmationDialog_ShowsBeforeImport() {
        val uri = writeJson("confirm.json", sampleJson())
        Intents.init()
        try {
            stubFilePicker(uri, Activity.RESULT_OK)
            ActivityScenario.launch(SettingsActivity::class.java).use {
                onView(withId(R.id.btn_import_config)).perform(click())
                onView(withText(R.string.import_confirm_title)).check(matches(isDisplayed()))
                onView(withText(R.string.import_confirm_message)).check(matches(isDisplayed()))
            }
        } finally {
            Intents.release()
        }
    }

    @Test
    fun testCancelConfirmation_NoChanges() {
        runBlocking {
            repository.upsert(com.voiceexpense.data.config.ConfigOption("existing", ConfigType.ExpenseCategory, "Existing", 0, true))
        }
        val uri = writeJson("cancel.json", sampleJson(options = listOf("X")))
        Intents.init()
        try {
            stubFilePicker(uri, Activity.RESULT_OK)
            ActivityScenario.launch(SettingsActivity::class.java).use {
                onView(withId(R.id.btn_import_config)).perform(click())
                onView(withText(R.string.cancel)).perform(click())
            }
        } finally {
            Intents.release()
        }

        val labels = runBlocking { repository.options(ConfigType.ExpenseCategory).first().map { it.label } }
        org.junit.Assert.assertTrue(labels.contains("Existing"))
        org.junit.Assert.assertFalse(labels.contains("X"))
    }

    @Test
    fun testDatabaseRollbackOnError_PreservesData() {
        runBlocking {
            repository.upsert(com.voiceexpense.data.config.ConfigOption("existing", ConfigType.ExpenseCategory, "Baseline", 0, true))
        }
        val uri = writeJson("fail.json", sampleJson(options = listOf("New")))
        dao.failOnUpsert = true
        Intents.init()
        try {
            stubFilePicker(uri, Activity.RESULT_OK)
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                var decor: View? = null
                scenario.onActivity { decor = it.window.decorView }
                onView(withId(R.id.btn_import_config)).perform(click())
                onView(withText(R.string.replace)).perform(click())

                onView(withText(startsWith("Import failed")))
                    .inRoot(withDecorView(not(equalTo(decor))))
                    .check(matches(isDisplayed()))
            }
        } finally {
            dao.failOnUpsert = false
            Intents.release()
        }

        val labels = runBlocking { repository.options(ConfigType.ExpenseCategory).first().map { it.label } }
        org.junit.Assert.assertTrue(labels.contains("Baseline"))
        org.junit.Assert.assertFalse(labels.contains("New"))
    }

    private fun stubFilePicker(uri: Uri, resultCode: Int) {
        val intent = Intent().apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val activityResult = Instrumentation.ActivityResult(resultCode, intent)
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(activityResult)
    }

    private fun sampleJson(options: List<String> = listOf("Food", "Transport")): String {
        val optionJson = options.joinToString(prefix = "[", postfix = "]") { label ->
            "{\"label\":\"$label\",\"position\":0,\"active\":true}"
        }
        val expense = optionJson
        val generic = "[{\"label\":\"Generic\",\"position\":0,\"active\":true}]"
        val defaults = "\"defaults\":{\"defaultExpenseCategory\":\"${options.first()}\",\"defaultIncomeCategory\":\"Generic\",\"defaultTransferCategory\":\"Generic\",\"defaultAccount\":\"Generic\"}"
        return "{\"ExpenseCategory\":$expense,\"IncomeCategory\":$generic,\"TransferCategory\":$generic,\"Account\":$generic,\"Tag\":$generic,$defaults}"
    }

    private fun writeJson(name: String, json: String): Uri {
        val file = File(cacheDir, name).apply { writeText(json) }
        return Uri.fromFile(file)
    }

    private fun waitForCondition(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getConstraints() = isDisplayed()
            override fun getDescription() = "wait for condition"
            override fun perform(uiController: UiController, view: View) {
                val start = SystemClock.uptimeMillis()
                while (!condition()) {
                    if (SystemClock.uptimeMillis() - start > timeoutMs) {
                        throw AssertionError("Condition not met within ${timeoutMs}ms")
                    }
                    uiController.loopMainThreadForAtLeast(50)
                }
            }
        })
    }

}
