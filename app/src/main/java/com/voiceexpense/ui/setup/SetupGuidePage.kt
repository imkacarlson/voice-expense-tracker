package com.voiceexpense.ui.setup

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.appbar.MaterialToolbar
import com.voiceexpense.R
import com.voiceexpense.ai.model.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class SetupGuidePage : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val modelManager = ModelManager()

    companion object {
        private const val MIN_MODEL_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB minimum
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        scope.launch {
            val ok = importModel(uri)
            if (ok) {
                Toast.makeText(this@SetupGuidePage, getString(R.string.setup_guide_import_success), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_guide)
        // Back/up navigation on toolbar
        findViewById<MaterialToolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<TextView>(R.id.text_setup_title).text = getString(R.string.setup_guide_title)
        findViewById<TextView>(R.id.text_setup_steps).text = getString(R.string.setup_guide_steps)

        findViewById<Button>(R.id.btn_test_ai_setup).setOnClickListener {
            scope.launch {
                val status = modelManager.ensureModelAvailable(applicationContext)
                val msg = when (status) {
                    is ModelManager.ModelStatus.Ready -> getString(R.string.setup_guide_status_ready)
                    is ModelManager.ModelStatus.Unavailable -> getString(R.string.setup_guide_status_unavailable, status.reason)
                    is ModelManager.ModelStatus.Error -> getString(R.string.setup_guide_status_error, status.throwable.message ?: "unknown")
                }
                Toast.makeText(this@SetupGuidePage, msg, Toast.LENGTH_LONG).show()
            }
        }

        findViewById<Button>(R.id.btn_import_model).setOnClickListener {
            // Allow user to pick any file; we recommend .task
            importLauncher.launch(arrayOf("application/octet-stream", "application/*", "*/*"))
        }
    }

    private suspend fun importModel(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            // Get the original filename from the URI
            val originalFileName = getFileName(uri)
            val extension = originalFileName?.substringAfterLast('.', "") ?: ""

            // Validate file extension
            if (extension != "task" && extension != "litertlm") {
                throw IllegalArgumentException("Invalid file type. Please select a .task or .litertlm file.")
            }

            // Validate file is a zip archive (MediaPipe bundles are zip files)
            val isValidZip = validateZipFile(uri)
            if (!isValidZip) {
                throw IllegalArgumentException("Invalid model file. File is not a valid zip archive. Please check the file integrity.")
            }

            // Validate minimum file size (at least 10MB for a valid model)
            val fileSize = getFileSize(uri)
            if (fileSize < MIN_MODEL_SIZE_BYTES) {
                throw IllegalArgumentException("File too small (${fileSize / (1024 * 1024)}MB). Model files should be at least ${MIN_MODEL_SIZE_BYTES / (1024 * 1024)}MB.")
            }

            // Use the original filename to preserve extension
            val fileName = originalFileName ?: "model.$extension"
            val dest = File(filesDir, "${com.voiceexpense.ai.model.ModelManager.MODEL_DIRECTORY}/$fileName")
            dest.parentFile?.mkdirs()

            contentResolver.openInputStream(uri).use { input ->
                if (input == null) throw IllegalStateException("Cannot open selected file")
                dest.outputStream().use { output ->
                    copyStreams(input, output)
                }
            }
            true
        } catch (t: Throwable) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SetupGuidePage, getString(R.string.setup_guide_import_failure, t.message ?: "unknown"), Toast.LENGTH_LONG).show()
            }
            false
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        return result
    }

    private fun getFileSize(uri: Uri): Long {
        var size: Long = 0
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex >= 0) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } else {
            uri.path?.let { path ->
                size = File(path).length()
            }
        }
        return size
    }

    private fun validateZipFile(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                // Check for zip file signature (PK\x03\x04)
                val header = ByteArray(4)
                val bytesRead = input.read(header)
                bytesRead == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                        header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun copyStreams(input: InputStream, output: OutputStream, bufferSize: Int = 256 * 1024) {
        val buf = ByteArray(bufferSize)
        while (true) {
            val r = input.read(buf)
            if (r == -1) break
            output.write(buf, 0, r)
        }
        output.flush()
    }
}
