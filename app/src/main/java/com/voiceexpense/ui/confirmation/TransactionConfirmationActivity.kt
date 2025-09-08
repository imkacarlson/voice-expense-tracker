package com.voiceexpense.ui.confirmation

import android.os.Bundle
import android.widget.Toast
import android.widget.Button
import android.widget.TextView
import android.widget.EditText
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
        val amountView: EditText = findViewById(R.id.field_amount)
        val overallView: EditText = findViewById(R.id.field_overall)
        val merchantView: EditText = findViewById(R.id.field_merchant)
        val descView: EditText = findViewById(R.id.field_description)
        val typeView: EditText = findViewById(R.id.field_type)
        val categoryView: EditText = findViewById(R.id.field_category)
        val tagsView: EditText = findViewById(R.id.field_tags)
        val accountView: EditText = findViewById(R.id.field_account)
        val dateView: EditText = findViewById(R.id.field_date)
        val noteView: EditText = findViewById(R.id.field_note)

        title.text = getString(R.string.app_name)
        // Disable actions until draft loads
        confirm.isEnabled = false
        speak.isEnabled = false

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
                    // Enable actions once draft is available
                    confirm.isEnabled = true
                    speak.isEnabled = true
                    amountView.setText(t.amountUsd?.toPlainString() ?: "")
                    overallView.setText(t.splitOverallChargedUsd?.toPlainString() ?: "")
                    merchantView.setText(t.merchant)
                    descView.setText(t.description ?: "")
                    typeView.setText(t.type.name)
                    val category = when (t.type) {
                        com.voiceexpense.data.model.TransactionType.Expense -> t.expenseCategory
                        com.voiceexpense.data.model.TransactionType.Income -> t.incomeCategory
                        com.voiceexpense.data.model.TransactionType.Transfer -> null
                    }
                    categoryView.setText(category ?: "")
                    tagsView.setText(if (t.tags.isNotEmpty()) t.tags.joinToString(", ") else "")
                    accountView.setText(t.account ?: "")
                    dateView.setText(t.userLocalDate.toString())
                    noteView.setText(t.note ?: "")

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
            // Gather manual edits from inputs and persist before confirming
            val current = viewModel.transaction.value ?: return@setOnClickListener
            // Parse and validate inputs
            fun parseAmount(text: String): java.math.BigDecimal? =
                text.trim().takeIf { it.isNotEmpty() }?.let { runCatching { java.math.BigDecimal(it) }.getOrNull() }

            val newAmount = parseAmount(amountView.text?.toString() ?: "")
            val newOverall = parseAmount(overallView.text?.toString() ?: "")
            val newMerchant = (merchantView.text?.toString() ?: "").trim()
            if (newMerchant.isBlank()) {
                Toast.makeText(this, "Merchant is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val newDescription = (descView.text?.toString() ?: "").trim().ifEmpty { null }
            val newTypeStr = (typeView.text?.toString() ?: "").trim()
            val newType = when {
                newTypeStr.equals("expense", true) -> com.voiceexpense.data.model.TransactionType.Expense
                newTypeStr.equals("income", true) -> com.voiceexpense.data.model.TransactionType.Income
                newTypeStr.equals("transfer", true) -> com.voiceexpense.data.model.TransactionType.Transfer
                newTypeStr.isBlank() -> current.type
                else -> {
                    Toast.makeText(this, "Type must be Expense, Income, or Transfer", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }
            val categoryText = (categoryView.text?.toString() ?: "").trim().ifEmpty { null }
            val newExpenseCategory = if (newType == com.voiceexpense.data.model.TransactionType.Expense) categoryText else null
            val newIncomeCategory = if (newType == com.voiceexpense.data.model.TransactionType.Income) categoryText else null
            val newTags = (tagsView.text?.toString() ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val newAccount = (accountView.text?.toString() ?: "").trim().ifEmpty { null }
            val newDateStr = (dateView.text?.toString() ?: "").trim()
            val newDate = runCatching { java.time.LocalDate.parse(newDateStr.ifEmpty { current.userLocalDate.toString() }) }.getOrElse {
                Toast.makeText(this, "Date must be YYYY-MM-DD", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val newNote = (noteView.text?.toString() ?: "").trim().ifEmpty { null }

            val updated = current.copy(
                amountUsd = newAmount,
                splitOverallChargedUsd = newOverall,
                merchant = newMerchant,
                description = newDescription,
                type = newType,
                expenseCategory = newExpenseCategory,
                incomeCategory = newIncomeCategory,
                tags = newTags,
                account = newAccount,
                userLocalDate = newDate,
                note = newNote
            )
            viewModel.applyManualEdits(updated)
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

        // Removed typed correction row per UX change
    }
}
