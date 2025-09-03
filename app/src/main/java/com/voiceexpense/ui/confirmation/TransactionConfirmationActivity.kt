package com.voiceexpense.ui.confirmation

import android.os.Bundle
import android.widget.Toast
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.voiceexpense.R
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.ai.speech.SpeechRecognitionService
import com.voiceexpense.ui.confirmation.voice.CorrectionIntentParser
import com.voiceexpense.ui.confirmation.voice.PromptRenderer
import com.voiceexpense.ui.confirmation.voice.TtsEngine
import com.voiceexpense.ui.confirmation.voice.VoiceCorrectionController
import com.voiceexpense.data.repository.TransactionRepository
import com.voiceexpense.worker.enqueueSyncNow
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TransactionConfirmationActivity : AppCompatActivity() {
    @Inject lateinit var repo: TransactionRepository
    private lateinit var viewModel: ConfirmationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_confirmation)

        // Basic wiring without DI; replaced by Hilt later
        val controller = VoiceCorrectionController(
            tts = TtsEngine(),
            parser = CorrectionIntentParser(),
            renderer = PromptRenderer()
        )
        viewModel = ConfirmationViewModel(repo, TransactionParser(), controller)
        // Enable debug logs if developer toggle is set
        val prefs = getSharedPreferences(com.voiceexpense.ui.common.SettingsKeys.PREFS, android.content.Context.MODE_PRIVATE)
        controller.setDebug(prefs.getBoolean(com.voiceexpense.ui.common.SettingsKeys.DEBUG_LOGS, false))

        val title: TextView = findViewById(R.id.txn_title)
        val confirm: Button = findViewById(R.id.btn_confirm)
        val cancel: Button = findViewById(R.id.btn_cancel)
        val speak: Button = findViewById(R.id.btn_speak)

        title.text = getString(R.string.app_name)

        // Load draft by id if provided
        val id = intent?.getStringExtra(com.voiceexpense.service.voice.VoiceRecordingService.EXTRA_TRANSACTION_ID)
        if (id.isNullOrBlank()) {
            Toast.makeText(this, R.string.error_open_draft_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            val draft = repo.getById(id)
            if (draft == null) {
                Toast.makeText(this@TransactionConfirmationActivity, R.string.error_open_draft_failed, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            viewModel.setDraft(draft)
        }

        confirm.setOnClickListener {
            viewModel.confirm()
            enqueueSyncNow(this)
        }
        cancel.setOnClickListener { viewModel.cancel(); finish() }

        // Placeholder voice correction using ASR debug
        val asr = SpeechRecognitionService()
        // Subscribe to TTS prompt events (no-op here; real app would speak)
        lifecycleScope.launch { viewModel.ttsEvents.collect { /* hook TTS */ } }
        speak.setOnClickListener {
            viewModel.interruptTts()
            // In real app, start listening and feed transcript; here we demo a sample correction
            lifecycleScope.launch {
                asr.transcribeDebug("actually 25.00").collect { text ->
                    viewModel.applyCorrection(text)
                }
            }
        }
    }
}
