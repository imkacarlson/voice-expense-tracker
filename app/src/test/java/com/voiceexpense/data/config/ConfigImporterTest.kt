package com.voiceexpense.data.config

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import com.voiceexpense.testutil.MainDispatcherRule
import org.mockito.Mockito.never

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigImporterTest {

    @get:Rule val dispatcherRule = MainDispatcherRule()

    private lateinit var repository: ConfigRepository
    private lateinit var contentResolver: ContentResolver
    private lateinit var importer: ConfigImporter
    private val moshi = Moshi.Builder().build()
    private val testUri = Uri.parse("content://test/config.json")

    @Before
    fun setUp() {
        repository = mock(ConfigRepository::class.java)
        contentResolver = mock(ContentResolver::class.java)
        importer = ConfigImporter(repository, moshi)
    }

    @Test
    fun testValidJsonParsing_Success() = runTest {
        stubFile(jsonWithDefaults())
        whenever(repository.importConfiguration(any())).thenReturn(10)

        val result = importer.importConfiguration(testUri, contentResolver)

        assertThat(result).isInstanceOf(ConfigImportResult.Success::class.java)
        val success = result as ConfigImportResult.Success
        assertThat(success.optionsImported).isEqualTo(10)
        verify(repository).importConfiguration(any())
    }

    @Test
    fun testInvalidJsonSyntax_ReturnsInvalidJson() = runTest {
        stubFile("{invalid json")

        val result = importer.importConfiguration(testUri, contentResolver)

        assertThat(result).isInstanceOf(ConfigImportResult.InvalidJson::class.java)
        verify(repository, never()).importConfiguration(any())
    }

    @Test
    fun testEmptyExpenseCategory_ReturnsValidationError() = runTest {
        stubFile(
            baseJson(
                expenseBlock = "[]",
                incomeBlock = requiredList("Salary"),
                transferBlock = requiredList("Savings"),
                accountBlock = requiredList("Checking"),
                tagBlock = requiredList("Personal"),
                defaultsBlock = defaultBlock()
            )
        )

        val result = importer.importConfiguration(testUri, contentResolver)

        assertThat(result).isInstanceOf(ConfigImportResult.ValidationError::class.java)
        verify(repository, never()).importConfiguration(any())
    }

    @Test
    fun testBlankLabel_ReturnsValidationError() = runTest {
        stubFile(
            baseJson(
                expenseBlock = "[{\"label\":\"\"}]",
                incomeBlock = requiredList("Salary"),
                transferBlock = requiredList("Savings"),
                accountBlock = requiredList("Checking"),
                tagBlock = requiredList("Personal"),
                defaultsBlock = defaultBlock()
            )
        )

        val result = importer.importConfiguration(testUri, contentResolver)

        assertThat(result).isInstanceOf(ConfigImportResult.ValidationError::class.java)
        verify(repository, never()).importConfiguration(any())
    }

    @Test
    fun testFileSizeExceedsLimit_ReturnsValidationError() = runTest {
        stubFileWithLength(jsonWithDefaults(), ConfigImporter.MAX_FILE_SIZE_BYTES + 1)

        val result = importer.importConfiguration(testUri, contentResolver)

        assertThat(result).isInstanceOf(ConfigImportResult.ValidationError::class.java)
        verify(repository, never()).importConfiguration(any())
    }

    @Test
    fun testValidSchemaWithDefaults_Success() = runTest {
        stubFile(jsonWithDefaults())
        val captor = argumentCaptor<ConfigImportSchema>()
        whenever(repository.importConfiguration(captor.capture())).thenReturn(6)

        val result = importer.importConfiguration(testUri, contentResolver)

        assertThat(result).isInstanceOf(ConfigImportResult.Success::class.java)
        val schema = captor.firstValue
        assertThat(schema.defaults?.defaultAccount).isEqualTo("Checking")
        assertThat(schema.incomeCategories?.first()?.label).isEqualTo("Salary")
    }

    @Test
    fun testDatabaseError_ReturnsDatabaseError() = runTest {
        stubFile(jsonWithDefaults())
        whenever(repository.importConfiguration(any())).thenThrow(RuntimeException("db failure"))

        val result = importer.importConfiguration(testUri, contentResolver)

        assertThat(result).isInstanceOf(ConfigImportResult.DatabaseError::class.java)
    }

    private fun stubFile(content: String) {
        whenever(contentResolver.openAssetFileDescriptor(testUri, "r")).thenReturn(null)
        whenever(contentResolver.openInputStream(testUri)).thenReturn(content.toStream())
    }

    private fun stubFileWithLength(content: String, declaredLength: Long) {
        val descriptor = mock(AssetFileDescriptor::class.java)
        doReturn(declaredLength).whenever(descriptor).length
        whenever(contentResolver.openAssetFileDescriptor(testUri, "r")).thenReturn(descriptor)
        whenever(contentResolver.openInputStream(testUri)).thenReturn(content.toStream())
    }

    private fun String.toStream(): InputStream =
        ByteArrayInputStream(toByteArray(StandardCharsets.UTF_8))

    private fun jsonWithDefaults(): String = baseJson(
        expenseBlock = requiredList("Food"),
        incomeBlock = requiredList("Salary"),
        transferBlock = requiredList("Savings"),
        accountBlock = requiredList("Checking"),
        tagBlock = requiredList("Personal"),
        defaultsBlock = defaultBlock()
    )

    private fun requiredList(vararg labels: String): String = labels.joinToString(
        prefix = "[",
        postfix = "]"
    ) { label ->
        "{\"label\":\"$label\",\"position\":0,\"active\":true}"
    }

    private fun defaultBlock(): String = "\"defaults\":{\"defaultExpenseCategory\":\"Food\",\"defaultIncomeCategory\":\"Salary\",\"defaultTransferCategory\":\"Savings\",\"defaultAccount\":\"Checking\"}"

    private fun baseJson(
        expenseBlock: String,
        incomeBlock: String,
        transferBlock: String,
        accountBlock: String,
        tagBlock: String,
        defaultsBlock: String
    ): String =
        "{\"ExpenseCategory\":$expenseBlock,\"IncomeCategory\":$incomeBlock,\"TransferCategory\":$transferBlock,\"Account\":$accountBlock,\"Tag\":$tagBlock,$defaultsBlock}"
}
