package com.voiceexpense.ui.setup

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.voiceexpense.R
import com.voiceexpense.ai.model.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SetupGuidePage : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val modelManager = ModelManager()

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
                val status = modelManager.ensureModelAvailable()
                val msg = when (status) {
                    is ModelManager.ModelStatus.Ready -> getString(R.string.setup_guide_status_ready)
                    is ModelManager.ModelStatus.Downloading -> getString(R.string.setup_guide_status_downloading)
                    is ModelManager.ModelStatus.Unavailable -> getString(R.string.setup_guide_status_unavailable, status.reason)
                    is ModelManager.ModelStatus.Error -> getString(R.string.setup_guide_status_error, status.throwable.message ?: "unknown")
                }
                Toast.makeText(this@SetupGuidePage, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}
