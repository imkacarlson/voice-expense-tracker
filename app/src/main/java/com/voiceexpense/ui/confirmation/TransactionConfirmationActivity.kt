package com.voiceexpense.ui.confirmation

import android.os.Bundle
import android.widget.Toast
import android.content.Intent
import android.widget.Button
import android.widget.TextView
import android.widget.EditText
import android.widget.Spinner
import android.app.DatePickerDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import androidx.lifecycle.lifecycleScope
import com.voiceexpense.R
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.data.repository.TransactionRepository
import com.voiceexpense.data.config.ConfigRepository
import com.voiceexpense.data.config.ConfigType
import kotlinx.coroutines.flow.first
import com.voiceexpense.data.config.DefaultField
import com.voiceexpense.worker.enqueueSyncNow
import com.voiceexpense.ui.common.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class TransactionConfirmationActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_TRANSACTION_ID = "transaction_id"
    }
    @Inject lateinit var repo: TransactionRepository
    private lateinit var viewModel: ConfirmationViewModel
    @Inject lateinit var parser: TransactionParser
    @Inject lateinit var configRepo: ConfigRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_confirmation)
        // Back/up navigation on toolbar
        findViewById<MaterialToolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Basic wiring without DI; replaced by Hilt later
        viewModel = ConfirmationViewModel(repo, parser)
        // Voice correction removed; no controller debug configuration needed

        val title: TextView = findViewById(R.id.txn_title)
        val confirm: Button = findViewById(R.id.btn_confirm)
        val cancel: Button = findViewById(R.id.btn_cancel)
        val amountView: EditText = findViewById(R.id.field_amount)
        val overallView: EditText = findViewById(R.id.field_overall)
        val merchantView: EditText = findViewById(R.id.field_merchant)
        val descView: EditText = findViewById(R.id.field_description)
        val typeSpinner: Spinner = findViewById(R.id.spinner_type)
        val categorySpinner: Spinner = findViewById(R.id.spinner_category)
        val tagsView: EditText = findViewById(R.id.field_tags)
        val accountSpinner: Spinner = findViewById(R.id.spinner_account)
        val dateView: TextView = findViewById(R.id.field_date)
        val noteView: EditText = findViewById(R.id.field_note)

        title.text = getString(R.string.app_name)
        // Disable actions until draft loads
        confirm.isEnabled = false

        // Load draft by id if provided
        val id = intent?.getStringExtra(EXTRA_TRANSACTION_ID)
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
                    amountView.setText(t.amountUsd?.toPlainString() ?: "")
                    overallView.setText(t.splitOverallChargedUsd?.toPlainString() ?: "")
                    merchantView.setText(t.merchant)
                    descView.setText(t.description ?: "")
                    // Setup type spinner values and selection
                    val types = arrayOf("Expense", "Income", "Transfer")
                    val typeAdapter = android.widget.ArrayAdapter(this@TransactionConfirmationActivity, android.R.layout.simple_spinner_item, types)
                    typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    typeSpinner.adapter = typeAdapter
                    val selIdx =
                        when (t.type) {
                            com.voiceexpense.data.model.TransactionType.Expense -> 0
                            com.voiceexpense.data.model.TransactionType.Income -> 1
                            com.voiceexpense.data.model.TransactionType.Transfer -> 2
                        }
                    typeSpinner.setSelection(selIdx)
                    viewModel.setSelectedType(
                        when (selIdx) {
                            0 -> com.voiceexpense.data.model.TransactionType.Expense
                            1 -> com.voiceexpense.data.model.TransactionType.Income
                            else -> com.voiceexpense.data.model.TransactionType.Transfer
                        }
                    )
                    val category = when (t.type) {
                        com.voiceexpense.data.model.TransactionType.Expense -> t.expenseCategory
                        com.voiceexpense.data.model.TransactionType.Income -> t.incomeCategory
                        com.voiceexpense.data.model.TransactionType.Transfer -> null
                    }
                    // Bind category options by type
                    fun bindCategoriesFor(type: com.voiceexpense.data.model.TransactionType) {
                        val cfgType = when (type) {
                            com.voiceexpense.data.model.TransactionType.Expense -> ConfigType.ExpenseCategory
                            com.voiceexpense.data.model.TransactionType.Income -> ConfigType.IncomeCategory
                            com.voiceexpense.data.model.TransactionType.Transfer -> ConfigType.TransferCategory
                        }
                        lifecycleScope.launch {
                            configRepo.options(cfgType).collect { opts ->
                                val labels = opts.sortedBy { it.position }.map { it.label }
                                val adapter = android.widget.ArrayAdapter(this@TransactionConfirmationActivity, android.R.layout.simple_spinner_item, labels)
                                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                                categorySpinner.adapter = adapter
                                val idx = labels.indexOf(category ?: "")
                                if (idx >= 0) {
                                    categorySpinner.setSelection(idx)
                                } else {
                                    // apply default if available
                                    val df = when (type) {
                                        com.voiceexpense.data.model.TransactionType.Expense -> DefaultField.DefaultExpenseCategory
                                        com.voiceexpense.data.model.TransactionType.Income -> DefaultField.DefaultIncomeCategory
                                        com.voiceexpense.data.model.TransactionType.Transfer -> DefaultField.DefaultTransferCategory
                                    }
                                    lifecycleScope.launch {
                                        val defId = configRepo.defaultFor(df).first()
                                        val sorted = opts.sortedBy { it.position }
                                        val defIdx = sorted.indexOfFirst { it.id == defId }
                                        if (defIdx >= 0) categorySpinner.setSelection(defIdx)
                                    }
                                }
                            }
                        }
                    }
                    bindCategoriesFor(t.type)
                    // Bind account options
                    lifecycleScope.launch {
                        configRepo.options(ConfigType.Account).collect { opts ->
                            val labels = opts.sortedBy { it.position }.map { it.label }
                            val adapter = android.widget.ArrayAdapter(this@TransactionConfirmationActivity, android.R.layout.simple_spinner_item, labels)
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            accountSpinner.adapter = adapter
                            val accIdx = labels.indexOf(t.account ?: "")
                            if (accIdx >= 0) accountSpinner.setSelection(accIdx)
                        }
                    }
                    tagsView.setText(if (t.tags.isNotEmpty()) t.tags.joinToString(", ") else "")
                    // Date display as MM/dd for UX; keep ISO in model
                    val mmdd = java.time.format.DateTimeFormatter.ofPattern("MM/dd").format(t.userLocalDate)
                    dateView.text = mmdd
                    noteView.setText(t.note ?: "")

                    // Simple highlighting for missing key fields
                    fun TextView.markMissing(missing: Boolean) {
                        setTextColor(if (missing) android.graphics.Color.parseColor("#E65100") else android.graphics.Color.BLACK)
                    }
                    amountView.markMissing(t.amountUsd == null)
                    merchantView.markMissing(t.merchant.isBlank())
                    // Category highlighting skipped for Spinner control
                }
            }

            // Observe validation and gate Confirm button; show basic field errors
            lifecycleScope.launch {
                viewModel.validation.collect { res ->
                    if (res == null) return@collect
                    confirm.isEnabled = res.formValid
                    fun setError(view: EditText, key: String) {
                        view.error = res.fieldErrors[key]
                    }
                    setError(amountView, "amount")
                    setError(overallView, "overall")
                    // Category errors shown via toast on submit for now
                }
            }
        }

        // Update visibility on type changes
        typeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val type = when (position) {
                    0 -> com.voiceexpense.data.model.TransactionType.Expense
                    1 -> com.voiceexpense.data.model.TransactionType.Income
                    else -> com.voiceexpense.data.model.TransactionType.Transfer
                }
                viewModel.setSelectedType(type)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Observe and apply visibility
        lifecycleScope.launch {
            viewModel.visibility.collect { vis ->
                val v = if (vis.showAmount) android.view.View.VISIBLE else android.view.View.GONE
                findViewById<android.view.View>(R.id.field_amount).visibility = v
                findViewById<android.view.View>(R.id.label_amount).visibility = v

                val vOverall = if (vis.showOverall) android.view.View.VISIBLE else android.view.View.GONE
                findViewById<android.view.View>(R.id.field_overall).visibility = vOverall
                findViewById<android.view.View>(R.id.label_overall).visibility = vOverall

                val vCat = if (vis.showExpenseCategory || vis.showIncomeCategory) android.view.View.VISIBLE else android.view.View.GONE
                findViewById<android.view.View>(R.id.spinner_category).visibility = vCat
                findViewById<android.view.View>(R.id.label_category).visibility = vCat

                val vAcc = if (vis.showAccount) android.view.View.VISIBLE else android.view.View.GONE
                findViewById<android.view.View>(R.id.spinner_account).visibility = vAcc
                findViewById<android.view.View>(R.id.label_account).visibility = vAcc
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
            val newType = when (typeSpinner.selectedItemPosition) {
                0 -> com.voiceexpense.data.model.TransactionType.Expense
                1 -> com.voiceexpense.data.model.TransactionType.Income
                2 -> com.voiceexpense.data.model.TransactionType.Transfer
                else -> current.type
            }
            val categoryText = (categorySpinner.selectedItem?.toString() ?: "").trim().ifEmpty { null }
            val newExpenseCategory = if (newType == com.voiceexpense.data.model.TransactionType.Expense) categoryText else null
            val newIncomeCategory = if (newType == com.voiceexpense.data.model.TransactionType.Income) categoryText else null
            val newTags = (tagsView.text?.toString() ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val newAccount = (accountSpinner.selectedItem?.toString() ?: "").trim().ifEmpty { null }
            val newDateStr = (dateView.text?.toString() ?: "").trim()
            val newDate = runCatching {
                if (newDateStr.contains('/')) {
                    val fmt = java.time.format.DateTimeFormatter.ofPattern("MM/dd")
                    val parsed = java.time.LocalDate.parse(newDateStr, fmt)
                    parsed.withYear(java.time.LocalDate.now().year)
                } else java.time.LocalDate.parse(newDateStr.ifEmpty { current.userLocalDate.toString() })
            }.getOrElse {
                Toast.makeText(this, "Invalid date", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Saved. Syncing in background.", Toast.LENGTH_SHORT).show()
            navigateHome()
        }
        cancel.setOnClickListener {
            viewModel.cancel()
            navigateHome()
        }

        // Date picker for date field
        dateView.setOnClickListener {
            val today = viewModel.transaction.value?.userLocalDate ?: java.time.LocalDate.now()
            DatePickerDialog(this, { _, y, m, d ->
                val chosen = java.time.LocalDate.of(y, m + 1, d)
                val mmdd = java.time.format.DateTimeFormatter.ofPattern("MM/dd").format(chosen)
                dateView.text = mmdd
            }, today.year, today.monthValue - 1, today.dayOfMonth).show()
        }

        // Tags multi-select dialog
        tagsView.setOnClickListener {
            lifecycleScope.launch {
                val opts = configRepo.options(ConfigType.Tag).first()
                val labels = opts.sortedBy { it.position }.map { it.label }
                val initial = (tagsView.text?.toString() ?: "").split(',').map { it.trim() }
                val checked = labels.map { initial.contains(it) }.toBooleanArray()
                android.app.AlertDialog.Builder(this@TransactionConfirmationActivity)
                    .setTitle("Select tags")
                    .setMultiChoiceItems(labels.toTypedArray(), checked) { _, which, isChecked ->
                        checked[which] = isChecked
                    }
                    .setPositiveButton("OK") { _, _ ->
                        val selected = labels.filterIndexed { index, _ -> checked[index] }
                        tagsView.setText(selected.joinToString(", "))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        // Placeholder voice correction using ASR debug
        // Voice correction removed in text-first refactor

        // Removed typed correction row per UX change
    }

    private fun navigateHome() {
        // Bring MainActivity to front if it exists, otherwise create it
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }
}
