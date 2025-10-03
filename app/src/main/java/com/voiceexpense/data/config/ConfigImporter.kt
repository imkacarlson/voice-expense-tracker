package com.voiceexpense.data.config

import android.content.ContentResolver
import android.net.Uri
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.DEFAULT_BUFFER_SIZE
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class ConfigImporter @Inject constructor(
    private val repository: ConfigRepository,
    moshi: Moshi
) {
    private val schemaAdapter = moshi.adapter(ConfigImportSchema::class.java)

    suspend fun importConfiguration(
        fileUri: Uri,
        contentResolver: ContentResolver
    ): ConfigImportResult = withContext(Dispatchers.IO) {
        val json = try {
            readJson(fileUri, contentResolver)
        } catch (ex: FileTooLargeException) {
            return@withContext ConfigImportResult.ValidationError(ex.message ?: FILE_TOO_LARGE_MESSAGE)
        } catch (ex: IOException) {
            return@withContext ConfigImportResult.FileReadError(ex)
        }

        val schema = try {
            schemaAdapter.fromJson(json) ?: return@withContext ConfigImportResult.InvalidJson("Empty JSON document")
        } catch (ex: JsonDataException) {
            return@withContext ConfigImportResult.InvalidJson(ex.message ?: "Invalid JSON format")
        } catch (ex: IOException) {
            return@withContext ConfigImportResult.InvalidJson(ex.message ?: "Invalid JSON format")
        }

        validateSchema(schema)?.let { message ->
            return@withContext ConfigImportResult.ValidationError(message)
        }

        return@withContext try {
            val importedCount = repository.importConfiguration(schema)
            ConfigImportResult.Success(importedCount)
        } catch (ex: Exception) {
            ConfigImportResult.DatabaseError(ex)
        }
    }

    @Throws(IOException::class)
    private fun readJson(fileUri: Uri, contentResolver: ContentResolver): String {
        contentResolver.openAssetFileDescriptor(fileUri, "r")?.use { descriptor ->
            val declaredLength = descriptor.length
            if (declaredLength > MAX_FILE_SIZE_BYTES) {
                throw FileTooLargeException(FILE_TOO_LARGE_MESSAGE)
            }
        }

        contentResolver.openInputStream(fileUri)?.use { inputStream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val output = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                total += read
                if (total > MAX_FILE_SIZE_BYTES) {
                    throw FileTooLargeException(FILE_TOO_LARGE_MESSAGE)
                }
                output.write(buffer, 0, read)
            }
            return output.toString(StandardCharsets.UTF_8.name())
        }

        throw IOException("Unable to open input stream for URI: $fileUri")
    }

    private fun validateSchema(schema: ConfigImportSchema): String? {
        val sections = listOf(
            ConfigType.ExpenseCategory to schema.expenseCategories,
            ConfigType.IncomeCategory to schema.incomeCategories,
            ConfigType.TransferCategory to schema.transferCategories,
            ConfigType.Account to schema.accounts,
            ConfigType.Tag to schema.tags
        )

        sections.forEach { (type, options) ->
            if (options == null) {
                return "Missing required configuration sections"
            }
            if (options.isEmpty()) {
                return "${type.name} section must include at least one option"
            }
            if (options.any { it.label.isBlank() }) {
                return "Invalid option format in ${type.name}"
            }
        }

        return null
    }

    private class FileTooLargeException(message: String) : IOException(message)

    companion object {
        private const val FILE_TOO_LARGE_MESSAGE = "File size exceeds 10MB limit"
        const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024
    }
}
