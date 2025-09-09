package com.voiceexpense.ui.common

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voiceexpense.R
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.data.repository.TransactionRepository
import com.voiceexpense.service.voice.VoiceRecordingService
import com.voiceexpense.ui.confirmation.TransactionConfirmationActivity
import com.voiceexpense.ai.parsing.hybrid.ProcessingMonitor
import com.voiceexpense.ui.common.SettingsKeys
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()
    @javax.inject.Inject lateinit var parser: TransactionParser
    @javax.inject.Inject lateinit var repo: TransactionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_get_started).setOnClickListener {
            startActivity(Intent(this, com.voiceexpense.ui.setup.SetupGuidePage::class.java))
        }
        findViewById<Button>(R.id.btn_open_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val list = findViewById<RecyclerView>(R.id.list_recent)
        val adapter = RecentTransactionsAdapter(onClick = { t -> onTransactionClick(t) })
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        lifecycleScope.launch {
            vm.recent.collectLatest { items ->
                adapter.submitList(items)
            }
        }

        val input: EditText = findViewById(R.id.input_utterance)
        val create: Button = findViewById(R.id.btn_create_draft)
        fun submit() {
            val text = input.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) {
                Toast.makeText(this, R.string.error_empty_input, Toast.LENGTH_SHORT).show()
                return
            }
            create.isEnabled = false
            create.text = getString(R.string.creating_draft)
            // Hide keyboard
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(input.windowToken, 0)

            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val before = ProcessingMonitor.snapshot()
                    val parsed = parser.parse(text, ParsingContext(defaultDate = LocalDate.now()))
                    val after = ProcessingMonitor.snapshot()
                    val usedAi = after.ai > before.ai
                    val prefs = getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
                    val debug = prefs.getBoolean(SettingsKeys.DEBUG_LOGS, false)
                    val txn = com.voiceexpense.data.model.Transaction(
                        userLocalDate = parsed.userLocalDate,
                        amountUsd = parsed.amountUsd ?: BigDecimal("0.00"),
                        merchant = parsed.merchant.ifBlank { "Unknown" },
                        description = parsed.description,
                        type = when (parsed.type) {
                            "Income" -> TransactionType.Income
                            "Transfer" -> TransactionType.Transfer
                            else -> TransactionType.Expense
                        },
                        expenseCategory = parsed.expenseCategory,
                        incomeCategory = parsed.incomeCategory,
                        tags = parsed.tags,
                        account = parsed.account,
                        splitOverallChargedUsd = parsed.splitOverallChargedUsd,
                        note = parsed.note,
                        confidence = parsed.confidence,
                        status = com.voiceexpense.data.model.TransactionStatus.DRAFT
                    )
                    repo.saveDraft(txn)
                    with(kotlinx.coroutines.Dispatchers.Main) {
                        // no-op
                    }
                    runOnUiThread {
                        if (debug) {
                            val method = if (usedAi) "AI" else "Heuristic"
                            Toast.makeText(this@MainActivity, "Parsed with: $method", Toast.LENGTH_SHORT).show()
                        }
                        create.isEnabled = true
                        create.text = getString(R.string.create_draft)
                        input.text?.clear()
                        val intent = Intent(this@MainActivity, TransactionConfirmationActivity::class.java)
                            .putExtra(VoiceRecordingService.EXTRA_TRANSACTION_ID, txn.id)
                        startActivity(intent)
                    }
                } catch (t: Throwable) {
                    runOnUiThread {
                        create.isEnabled = true
                        create.text = getString(R.string.create_draft)
                        Toast.makeText(this@MainActivity, "Could not parse. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        create.setOnClickListener { submit() }
        input.setOnEditorActionListener { _, _, _ -> submit(); true }
    }

    private fun onTransactionClick(t: Transaction) {
        when (t.status) {
            TransactionStatus.DRAFT -> {
                val intent = Intent(this, TransactionConfirmationActivity::class.java)
                    .putExtra(VoiceRecordingService.EXTRA_TRANSACTION_ID, t.id)
                startActivity(intent)
            }
            TransactionStatus.CONFIRMED, TransactionStatus.POSTED, TransactionStatus.QUEUED, TransactionStatus.FAILED -> {
                val intent = Intent(this, TransactionDetailsActivity::class.java)
                    .putExtra(TransactionDetailsActivity.EXTRA_ID, t.id)
                startActivity(intent)
            }
        }
    }
}
