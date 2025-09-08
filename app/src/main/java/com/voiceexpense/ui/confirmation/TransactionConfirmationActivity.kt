package com.voiceexpense.ui.confirmation

import android.os.Bundle
import android.widget.Toast
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
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
    @Inject lateinit var parser: TransactionParser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_confirmation)
        // Back/up navigation on toolbar
        findViewById<MaterialToolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Basic wiring without DI; replaced by Hilt later
        val controller = VoiceCorrectionController(
            tts = TtsEngine(),
            parser = CorrectionIntentParser(),
            renderer = PromptRenderer()
        )
        viewModel = ConfirmationViewModel(repo, parser, controller)
        // Enable debug logs if developer toggle is set
        val prefs = getSharedPreferences(com.voiceexpense.ui.common.SettingsKeys.PREFS, android.content.Context.MODE_PRIVATE)
        controller.setDebug(prefs.getBoolean(com.voiceexpense.ui.common.SettingsKeys.DEBUG_LOGS, false))

        val title: TextView = findViewById(R.id.txn_title)
        val confirm: Button = findViewById(R.id.btn_confirm)
        val cancel: Button = findViewById(R.id.btn_cancel)
        val speak: Button = findViewById(R.id.btn_speak)
        val inputCorrection: android.widget.EditText = findViewById(R.id.input_correction)
        val applyCorrection: Button = findViewById(R.id.btn_apply_correction)
        val amountView: TextView = findViewById(R.id.field_amount)
        val overallView: TextView = findViewById(R.id.field_overall)
        val merchantView: TextView = findViewById(R.id.field_merchant)
        val descView: TextView = findViewById(R.id.field_description)
        val typeView: TextView = findViewById(R.id.field_type)
        val categoryView: TextView = findViewById(R.id.field_category)
        val tagsView: TextView = findViewById(R.id.field_tags)
        val accountView: TextView = findViewById(R.id.field_account)
        val dateView: TextView = findViewById(R.id.field_date)
        val noteView: TextView = findViewById(R.id.field_note)

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
            if (draft.confidence < 0.7f) {
                title.text = getString(R.string.app_name) + "  •  Verify highlighted fields"
                Toast.makeText(this@TransactionConfirmationActivity, "Low confidence — please verify fields.", Toast.LENGTH_LONG).show()
            }
            // Observe transaction updates and render fields
            lifecycleScope.launch {
                viewModel.transaction.collect { t ->
                    if (t == null) return@collect
                    amountView.text = "Amount: ${t.amountUsd?.toPlainString() ?: "—"}"
                    overallView.text = "Overall charged: ${t.splitOverallChargedUsd?.toPlainString() ?: "—"}"
                    merchantView.text = "Merchant: ${t.merchant}"
                    descView.text = "Description: ${t.description ?: "—"}"
                    typeView.text = "Type: ${t.type}"
                    val category = when (t.type) {
                        com.voiceexpense.data.model.TransactionType.Expense -> t.expenseCategory
                        com.voiceexpense.data.model.TransactionType.Income -> t.incomeCategory
                        com.voiceexpense.data.model.TransactionType.Transfer -> null
                    }
                    categoryView.text = "Category: ${category ?: "—"}"
                    tagsView.text = "Tags: ${if (t.tags.isNotEmpty()) t.tags.joinToString(", ") else "—"}"
                    accountView.text = "Account: ${t.account ?: "—"}"
                    dateView.text = "Date: ${t.userLocalDate}"
                    noteView.text = "Note: ${t.note ?: "—"}"

                    // Simple highlighting for missing key fields
                    fun TextView.markMissing(missing: Boolean) {
                        setTextColor(if (missing) android.graphics.Color.parseColor("#E65100") else android.graphics.Color.BLACK)
                    }
                    amountView.markMissing(t.amountUsd == null)
                    merchantView.markMissing(t.merchant.isBlank())
                    if (t.type == com.voiceexpense.data.model.TransactionType.Expense) {
                        categoryView.markMissing(t.expenseCategory.isNullOrBlank())
                    }
                }
            }
        }

        confirm.setOnClickListener {
            viewModel.confirm()
            enqueueSyncNow(this)
        }
        cancel.setOnClickListener { viewModel.cancel(); finish() }

        // Placeholder voice correction using ASR debug
        val asr = SpeechRecognitionService(this)
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

        // Typed corrections
        fun submitCorrection() {
            val text = inputCorrection.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) {
                Toast.makeText(this, R.string.error_empty_input, Toast.LENGTH_SHORT).show()
                return
            }
            viewModel.interruptTts()
            viewModel.applyCorrection(text)
            inputCorrection.text?.clear()
        }
        applyCorrection.setOnClickListener { submitCorrection() }
        inputCorrection.setOnEditorActionListener { _, _, _ -> submitCorrection(); true }
    }
}
