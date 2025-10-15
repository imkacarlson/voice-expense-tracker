package com.voiceexpense.ui.common

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voiceexpense.R
import com.voiceexpense.ai.parsing.ParsedResult
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.ai.parsing.logging.ParsingRunLogBuilder
import com.voiceexpense.ai.parsing.logging.ParsingRunLogEntryType
import com.voiceexpense.ai.parsing.logging.ParsingRunLogStore
import com.voiceexpense.data.repository.TransactionRepository
import com.voiceexpense.ui.confirmation.TransactionConfirmationActivity
import com.voiceexpense.ai.parsing.hybrid.ProcessingMethod
import com.voiceexpense.ai.parsing.hybrid.StagedRefinementDispatcher
import com.voiceexpense.ui.common.SettingsKeys
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()
    @javax.inject.Inject lateinit var parser: TransactionParser
    @javax.inject.Inject lateinit var repo: TransactionRepository
    @javax.inject.Inject lateinit var configRepo: com.voiceexpense.data.config.ConfigRepository

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
            Log.i(TRACE_TAG, "MainActivity.submit() captured='${text.take(120)}'")
            create.isEnabled = false
            create.text = getString(R.string.creating_draft)
            // Hide keyboard
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(input.windowToken, 0)

            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                var runLogBuilderRef: ParsingRunLogBuilder? = null
                try {
                    // Load allowed options from settings to guide the model
                    val expenseCats = configRepo.options(com.voiceexpense.data.config.ConfigType.ExpenseCategory).first().sortedBy { it.position }.map { it.label }
                    val incomeCats = configRepo.options(com.voiceexpense.data.config.ConfigType.IncomeCategory).first().sortedBy { it.position }.map { it.label }
                    val tags = configRepo.options(com.voiceexpense.data.config.ConfigType.Tag).first().sortedBy { it.position }.map { it.label }
                    val accounts = configRepo.options(com.voiceexpense.data.config.ConfigType.Account).first().sortedBy { it.position }.map { it.label }
                    val ctx = ParsingContext(
                        defaultDate = LocalDate.now(),
                        allowedExpenseCategories = expenseCats,
                        allowedIncomeCategories = incomeCats,
                        allowedTags = tags,
                        allowedAccounts = accounts,
                        knownAccounts = accounts
                    )

                    val stage1 = parser.prepareStage1(text, ctx)
                    val parsed = stage1.parsedResult
                    Log.i(TRACE_TAG, "MainActivity.submit() heuristics finished merchant='${parsed.merchant}' type=${parsed.type}")

                    val txn = toDraftTransaction(parsed)
                    repo.saveDraft(txn)

                    val runLogBuilder = ParsingRunLogBuilder(transactionId = txn.id, capturedInput = text)
                    runLogBuilderRef = runLogBuilder
                    ParsingRunLogStore.put(runLogBuilder)
                    val stage1Snapshot = stage1.snapshot
                    val heuristicDraft = stage1Snapshot.heuristicDraft
                    val heuristicsSummary = buildString {
                        appendLine("duration=${stage1Snapshot.stage1DurationMs}ms")
                        appendLine("merchant=${heuristicDraft.merchant ?: "null"}")
                        appendLine("description=${heuristicDraft.description ?: "null"}")
                        appendLine("expenseCategory=${heuristicDraft.expenseCategory ?: "null"}")
                        appendLine("incomeCategory=${heuristicDraft.incomeCategory ?: "null"}")
                        appendLine("account=${heuristicDraft.account ?: "null"}")
                        appendLine("tags=${heuristicDraft.tags}")
                        appendLine("note=${heuristicDraft.note ?: "null"}")
                        appendLine("confidences=${heuristicDraft.confidences}")
                        appendLine("coverageScore=${heuristicDraft.coverageScore}")
                    }
                    runLogBuilder.addEntry(
                        type = ParsingRunLogEntryType.HEURISTIC,
                        title = "Stage1 heuristics summary",
                        detail = heuristicsSummary
                    )

                    val prefs = getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
                    val debug = prefs.getBoolean(SettingsKeys.DEBUG_LOGS, false)
                    val loadingFields = stage1.targetFields.toSet()

                    if (loadingFields.isNotEmpty()) {
                        runLogBuilder.addEntry(
                            type = ParsingRunLogEntryType.SUMMARY,
                            title = "Fields queued for refinement",
                            detail = loadingFields.joinToString()
                        )
                    } else {
                        runLogBuilder.addEntry(
                            type = ParsingRunLogEntryType.SUMMARY,
                            title = "Draft ready from heuristics",
                            detail = "method=HEURISTIC confidence=${parsed.confidence} coverage=${heuristicDraft.coverageScore}"
                        )
                    }

                    runOnUiThread {
                        create.isEnabled = true
                        create.text = getString(R.string.create_draft)
                        input.text?.clear()
                        val intent = Intent(this@MainActivity, TransactionConfirmationActivity::class.java)
                            .putExtra(TransactionConfirmationActivity.EXTRA_TRANSACTION_ID, txn.id)
                        if (loadingFields.isNotEmpty()) {
                            val extras = ArrayList<String>(loadingFields.size)
                            loadingFields.forEach { field -> extras.add(field.name) }
                            intent.putStringArrayListExtra(TransactionConfirmationActivity.EXTRA_LOADING_FIELDS, extras)
                        }
                        startActivity(intent)
                        if (debug && loadingFields.isEmpty()) {
                            Log.d(TRACE_TAG, "Draft created via heuristics only; no staged refinement requested")
                        }
                    }

                    if (loadingFields.isEmpty()) {
                        Log.i(TRACE_TAG, "MainActivity.submit() heuristics satisfied; no staged refinement needed")
                        return@launch
                    }

                    val transactionId = txn.id
                    val ctxWithLogger = ctx.copy(runLogBuilder = runLogBuilder)
                    launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val detailed = parser.runStagedRefinement(
                                text = text,
                                context = ctxWithLogger,
                                stage1Snapshot = stage1Snapshot,
                                onFieldRefined = { update ->
                                    val refined = if (update.error == null) {
                                        mapOf(update.field to update.value)
                                    } else emptyMap()
                                    val errors = update.error?.let { listOf(it) } ?: emptyList()
                                    StagedRefinementDispatcher.emit(
                                        StagedRefinementDispatcher.RefinementEvent(
                                            transactionId = transactionId,
                                            refinedFields = refined,
                                            targetFields = setOf(update.field),
                                            errors = errors,
                                            stage1DurationMs = stage1Snapshot.stage1DurationMs,
                                            stage2DurationMs = update.durationMs,
                                            confidence = null
                                        )
                                    )
                                }
                            )
                            val staged = detailed.staged
                            if (staged != null) {
                                Log.d(
                                    TRACE_TAG,
                                    "Staged refinement completed tx=$transactionId refined=${staged.fieldsRefined.joinToString()} target=${staged.targetFields.joinToString()}"
                                )
                                runLogBuilder.addEntry(
                                    type = ParsingRunLogEntryType.SUMMARY,
                                    title = "Staged parsing completed",
                                    detail = buildString {
                                        appendLine("method=${detailed.method}")
                                        appendLine("validated=${detailed.validated}")
                                        appendLine("confidence=${detailed.result.confidence}")
                                        appendLine("refined=${staged.fieldsRefined}")
                                        appendLine("errors=${staged.refinementErrors}")
                                        appendLine("stage1=${staged.stage1DurationMs}ms stage2=${staged.stage2DurationMs}ms")
                                    }
                                )
                                StagedRefinementDispatcher.emit(
                                    StagedRefinementDispatcher.RefinementEvent(
                                        transactionId = transactionId,
                                        refinedFields = staged.refinedFields,
                                        targetFields = staged.targetFields,
                                        errors = staged.refinementErrors,
                                        stage1DurationMs = staged.stage1DurationMs,
                                        stage2DurationMs = staged.stage2DurationMs,
                                        confidence = detailed.result.confidence
                                    )
                                )
                                if (debug) {
                                    val method = if (detailed.method == ProcessingMethod.AI) "AI" else "Heuristic"
                                    Log.d(TRACE_TAG, "Staged refinement completed using $method")
                                }
                            } else {
                                // Emit completion signal even if staged parsing disabled downstream
                                StagedRefinementDispatcher.emit(
                                    StagedRefinementDispatcher.RefinementEvent(
                                        transactionId = transactionId,
                                        refinedFields = emptyMap(),
                                        targetFields = loadingFields.toSet(),
                                        errors = emptyList(),
                                        stage1DurationMs = stage1Snapshot.stage1DurationMs,
                                        stage2DurationMs = 0L,
                                        confidence = detailed.result.confidence
                                    )
                                )
                                if (debug) {
                                    Log.d(TRACE_TAG, "Staged refinement skipped; heuristics already satisfied")
                                }
                                runLogBuilder.addEntry(
                                    type = ParsingRunLogEntryType.SUMMARY,
                                    title = "Staged parsing skipped",
                                    detail = buildString {
                                        appendLine("method=${detailed.method}")
                                        appendLine("validated=${detailed.validated}")
                                        appendLine("confidence=${detailed.result.confidence}")
                                    }
                                )
                            }
                        } catch (t: Throwable) {
                            Log.e(TRACE_TAG, "MainActivity.runStagedRefinement() failed: ${t.message}", t)
                            runLogBuilder.addEntry(
                                type = ParsingRunLogEntryType.ERROR,
                                title = "Staged refinement failed",
                                detail = t.stackTraceToString()
                            )
                            StagedRefinementDispatcher.emit(
                                StagedRefinementDispatcher.RefinementEvent(
                                    transactionId = transactionId,
                                    refinedFields = emptyMap(),
                                    targetFields = loadingFields.toSet(),
                                    errors = listOfNotNull(t.message),
                                    stage1DurationMs = stage1Snapshot.stage1DurationMs,
                                    stage2DurationMs = 0L,
                                    confidence = null
                                )
                            )
                            if (debug) {
                                Log.d(TRACE_TAG, "Staged refinement failed; heuristics result preserved")
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TRACE_TAG, "MainActivity.submit() failed: ${t.message}", t)
                    runLogBuilderRef?.addEntry(
                        type = ParsingRunLogEntryType.ERROR,
                        title = "Draft creation failed",
                        detail = t.stackTraceToString()
                    )
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
                    .putExtra(TransactionConfirmationActivity.EXTRA_TRANSACTION_ID, t.id)
                startActivity(intent)
            }
            TransactionStatus.CONFIRMED, TransactionStatus.POSTED, TransactionStatus.QUEUED, TransactionStatus.FAILED -> {
                val intent = Intent(this, TransactionDetailsActivity::class.java)
                    .putExtra(TransactionDetailsActivity.EXTRA_ID, t.id)
                startActivity(intent)
            }
        }
    }

    companion object {
        private const val TRACE_TAG = "AI.Trace"
    }

    private fun toDraftTransaction(parsed: ParsedResult): Transaction {
        val type = when (parsed.type) {
            "Income" -> TransactionType.Income
            "Transfer" -> TransactionType.Transfer
            else -> TransactionType.Expense
        }
        return Transaction(
            userLocalDate = parsed.userLocalDate,
            amountUsd = parsed.amountUsd ?: BigDecimal("0.00"),
            merchant = parsed.merchant.ifBlank { "Unknown" },
            description = parsed.description,
            type = type,
            expenseCategory = parsed.expenseCategory,
            incomeCategory = parsed.incomeCategory,
            tags = parsed.tags,
            account = parsed.account,
            splitOverallChargedUsd = parsed.splitOverallChargedUsd,
            note = parsed.note,
            confidence = parsed.confidence,
            status = TransactionStatus.DRAFT
        )
    }
}
