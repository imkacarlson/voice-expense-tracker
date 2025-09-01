package com.voiceexpense.ui.confirmation

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.voiceexpense.R
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.ai.speech.SpeechRecognitionService
import com.voiceexpense.data.repository.TransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TransactionConfirmationActivity : AppCompatActivity() {
    @Inject lateinit var repo: TransactionRepository
    private lateinit var viewModel: ConfirmationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_confirmation)

        // Basic wiring without DI; replaced by Hilt later
        viewModel = ConfirmationViewModel(repo, TransactionParser())

        val title: TextView = findViewById(R.id.txn_title)
        val confirm: Button = findViewById(R.id.btn_confirm)
        val cancel: Button = findViewById(R.id.btn_cancel)
        val speak: Button = findViewById(R.id.btn_speak)

        title.text = getString(R.string.app_name)

        confirm.setOnClickListener { viewModel.confirm() }
        cancel.setOnClickListener { viewModel.cancel(); finish() }

        // Placeholder voice correction using ASR debug
        val asr = SpeechRecognitionService()
        speak.setOnClickListener {
            // In real app, start listening and feed transcript; here we demo a sample correction
            asr.transcribeDebug("actually 25.00").collect(this) { text ->
                viewModel.applyCorrection(text)
            }
        }
    }
}
