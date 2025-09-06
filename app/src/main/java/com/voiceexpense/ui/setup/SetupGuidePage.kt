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
            val dest = File(filesDir, com.voiceexpense.ai.model.ModelManager.DEFAULT_RELATIVE_MODEL_PATH)
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
