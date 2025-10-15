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
import com.voiceexpense.ui.common.setupEdgeToEdge
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
        setupEdgeToEdge()
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

            // Validate minimum file size (at least 10MB for a valid model)
            val fileSize = getFileSize(uri)
            if (fileSize < MIN_MODEL_SIZE_BYTES) {
                throw IllegalArgumentException("File too small (${fileSize / (1024 * 1024)}MB). Model files should be at least ${MIN_MODEL_SIZE_BYTES / (1024 * 1024)}MB.")
            }

            // Log file header for debugging (MediaPipe will validate the format when loading)
            logFileHeader(uri, "IMPORT_${extension.uppercase()}")

            // Delete any existing model files to ensure only one model at a time
            val llmDir = File(filesDir, com.voiceexpense.ai.model.ModelManager.MODEL_DIRECTORY)
            if (llmDir.exists()) {
                llmDir.listFiles { file ->
                    file.isFile && (file.name.endsWith(".task") || file.name.endsWith(".litertlm"))
                }?.forEach { existingModel ->
                    android.util.Log.i("SetupGuide", "Removing existing model: ${existingModel.name}")
                    existingModel.delete()
                }
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
            android.util.Log.i("SetupGuide", "Successfully imported $fileName (${fileSize / (1024 * 1024)}MB)")
            true
        } catch (t: Throwable) {
            android.util.Log.e("SetupGuide", "Import failed: ${t.message}", t)
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

    private fun logFileHeader(uri: Uri, tag: String) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(16)
                val bytesRead = input.read(header)
                val hexString = header.take(bytesRead).joinToString(" ") {
                    String.format("%02X", it)
                }
                android.util.Log.i("SetupGuide", "$tag - File header (first $bytesRead bytes): $hexString")
            }
        } catch (e: Exception) {
            android.util.Log.w("SetupGuide", "$tag - Failed to read file header: ${e.message}")
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
