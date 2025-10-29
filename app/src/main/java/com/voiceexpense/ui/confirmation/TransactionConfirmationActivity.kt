package com.voiceexpense.ui.confirmation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import androidx.lifecycle.lifecycleScope
import com.voiceexpense.R
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.ai.parsing.hybrid.FieldRefinementStatus
import com.voiceexpense.data.repository.TransactionRepository
import com.voiceexpense.data.config.ConfigRepository
import com.voiceexpense.data.config.ConfigType
import com.voiceexpense.ui.common.setupEdgeToEdge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import com.voiceexpense.data.config.DefaultField
import com.voiceexpense.worker.enqueueSyncNow
import com.voiceexpense.ui.common.MainActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.voiceexpense.ai.parsing.logging.ParsingRunLogStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.text.Charsets

@AndroidEntryPoint
class TransactionConfirmationActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_LOADING_FIELDS = "loading_fields"
    }
    @Inject lateinit var repo: TransactionRepository
    private lateinit var viewModel: ConfirmationViewModel
    @Inject lateinit var parser: TransactionParser
    @Inject lateinit var configRepo: ConfigRepository
    private lateinit var exportButton: Button
    private var currentTransactionId: String? = null
    private var pendingExportNote: String? = null
    private val loadingDisabledAlpha = 0.45f
    private var lastCategoryOptions: List<String> = emptyList()
    private var lastCategoryType: com.voiceexpense.data.model.TransactionType? = null
    private var lastAccountOptions: List<String> = emptyList()
    private var categoryBindingJob: Job? = null
    private var accountBindingJob: Job? = null
    private val diagnosticsDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri != null) {
            exportDiagnostics(uri)
        } else {
            pendingExportNote = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_confirmation)
        setupEdgeToEdge()

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
        exportButton = findViewById(R.id.btn_export_run_log)
        exportButton.isEnabled = false
        exportButton.setOnClickListener { promptDiagnosticsExport() }
        val amountView: EditText = findViewById(R.id.field_amount)
        val overallView: EditText = findViewById(R.id.field_overall)
        val merchantView: EditText = findViewById(R.id.field_merchant)
        val descView: EditText = findViewById(R.id.field_description)
        val typeSpinner: Spinner = findViewById(R.id.spinner_type)
        val categorySpinner: Spinner = findViewById(R.id.spinner_category)
        val tagsView: EditText = findViewById(R.id.field_tags)
        val accountSpinner: Spinner = findViewById(R.id.spinner_account)
        val dateView: TextView = findViewById(R.id.field_date)

        val types = arrayOf("Expense", "Income", "Transfer")
        val typeAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, types)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = typeAdapter

        val merchantContainer: View = findViewById(R.id.container_field_merchant)
        val descriptionContainer: View = findViewById(R.id.container_field_description)
        val categoryContainer: View = findViewById(R.id.container_field_category)
        val tagsContainer: View = findViewById(R.id.container_field_tags)
        val accountContainer: View = findViewById(R.id.container_field_account)

        val merchantProgress: ProgressBar = findViewById(R.id.loading_merchant)
        val descriptionProgress: ProgressBar = findViewById(R.id.loading_description)
        val categoryProgress: ProgressBar = findViewById(R.id.loading_category)
        val tagsProgress: ProgressBar = findViewById(R.id.loading_tags)
        val accountProgress: ProgressBar = findViewById(R.id.loading_account)

        val loadingIndicators = mapOf(
            FieldKey.MERCHANT to merchantProgress,
            FieldKey.DESCRIPTION to descriptionProgress,
            FieldKey.ACCOUNT to accountProgress,
            FieldKey.TAGS to tagsProgress
        )

        val highlightTargets = mapOf(
            FieldKey.MERCHANT to merchantContainer,
            FieldKey.DESCRIPTION to descriptionContainer,
            FieldKey.EXPENSE_CATEGORY to categoryContainer,
            FieldKey.INCOME_CATEGORY to categoryContainer,
            FieldKey.ACCOUNT to accountContainer,
            FieldKey.TAGS to tagsContainer
        )

        val editableTargets: Map<FieldKey, View> = mapOf(
            FieldKey.MERCHANT to merchantView,
            FieldKey.DESCRIPTION to descView,
            FieldKey.EXPENSE_CATEGORY to categorySpinner,
            FieldKey.INCOME_CATEGORY to categorySpinner,
            FieldKey.ACCOUNT to accountSpinner,
            FieldKey.TAGS to tagsView
        )

        fun EditText.updateTextPreservingFocus(newValue: String) {
            val current = text?.toString() ?: ""
            if (current == newValue || hasFocus()) return
            setText(newValue)
        }

        fun TextView.updateTextIfChanged(newValue: String) {
            if (text.toString() != newValue) {
                text = newValue
            }
        }

        fun TextView.markMissing(missing: Boolean) {
            setTextColor(if (missing) android.graphics.Color.parseColor("#E65100") else android.graphics.Color.BLACK)
        }

        // Helper function to update transaction from current UI state and re-validate
        fun updateTransactionFromUI() {
            val current = viewModel.transaction.value ?: return

            fun parseAmount(text: String): java.math.BigDecimal? =
                text.trim().takeIf { it.isNotEmpty() }?.let { runCatching { java.math.BigDecimal(it) }.getOrNull() }

            val newAmount = parseAmount(amountView.text?.toString() ?: "")
            val newOverall = parseAmount(overallView.text?.toString() ?: "")
            val newMerchant = (merchantView.text?.toString() ?: "").trim()
            val newDescription = (descView.text?.toString() ?: "").trim().ifEmpty { null }
            val categoryText = (categorySpinner.selectedItem?.toString() ?: "").trim().ifEmpty { null }
            val newExpenseCategory = if (current.type == com.voiceexpense.data.model.TransactionType.Expense) categoryText else null
            val newIncomeCategory = if (current.type == com.voiceexpense.data.model.TransactionType.Income) categoryText else null
            val newTags = (tagsView.text?.toString() ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val selectedAccount = (accountSpinner.selectedItem?.toString() ?: "").trim()
            val newAccount = if (selectedAccount == "None" || selectedAccount.isEmpty()) null else selectedAccount

            val updated = current.copy(
                amountUsd = newAmount,
                splitOverallChargedUsd = newOverall,
                merchant = newMerchant,
                description = newDescription,
                expenseCategory = newExpenseCategory,
                incomeCategory = newIncomeCategory,
                tags = newTags,
                account = newAccount
            )

            viewModel.applyManualEdits(updated)
        }

        amountView.doAfterTextChanged {
            if (amountView.hasFocus()) {
                updateTransactionFromUI()
            }
        }
        overallView.doAfterTextChanged {
            if (overallView.hasFocus()) {
                updateTransactionFromUI()
            }
        }
        merchantView.doAfterTextChanged {
            if (merchantView.hasFocus()) {
                viewModel.markFieldUserModified(FieldKey.MERCHANT)
                updateTransactionFromUI()
            }
        }
        descView.doAfterTextChanged {
            if (descView.hasFocus()) {
                viewModel.markFieldUserModified(FieldKey.DESCRIPTION)
                updateTransactionFromUI()
            }
        }
        var categoryUserChange = false
        var accountUserChange = false
        var typeUserChange = false
        categorySpinner.setOnTouchListener { _, _ ->
            categoryUserChange = true
            false
        }
        categorySpinner.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) categoryUserChange = true
        }
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (categoryUserChange) {
                    when (viewModel.transaction.value?.type) {
                        com.voiceexpense.data.model.TransactionType.Expense -> viewModel.markFieldUserModified(FieldKey.EXPENSE_CATEGORY)
                        com.voiceexpense.data.model.TransactionType.Income -> viewModel.markFieldUserModified(FieldKey.INCOME_CATEGORY)
                        else -> {}
                    }
                    updateTransactionFromUI()
                    categoryUserChange = false
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        accountSpinner.setOnTouchListener { _, _ ->
            accountUserChange = true
            false
        }
        accountSpinner.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) accountUserChange = true
        }
        accountSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (accountUserChange) {
                    viewModel.markFieldUserModified(FieldKey.ACCOUNT)
                    updateTransactionFromUI()
                    accountUserChange = false
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        fun bindCategoriesFor(
            type: com.voiceexpense.data.model.TransactionType,
            selectedCategory: String?
        ) {
            categoryBindingJob?.cancel()
            categoryBindingJob = lifecycleScope.launch {
                val cfgType = when (type) {
                    com.voiceexpense.data.model.TransactionType.Expense -> ConfigType.ExpenseCategory
                    com.voiceexpense.data.model.TransactionType.Income -> ConfigType.IncomeCategory
                    com.voiceexpense.data.model.TransactionType.Transfer -> ConfigType.TransferCategory
                }
                val opts = configRepo.options(cfgType).first().sortedBy { it.position }
                val labels = opts.map { it.label }
                if (type != lastCategoryType || labels != lastCategoryOptions) {
                    val adapter = android.widget.ArrayAdapter(this@TransactionConfirmationActivity, android.R.layout.simple_spinner_item, labels)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    categorySpinner.adapter = adapter
                    lastCategoryType = type
                    lastCategoryOptions = labels
                }
                categoryUserChange = false
                val currentIdx = selectedCategory?.let { labels.indexOf(it).takeIf { idx -> idx >= 0 } }
                if (currentIdx != null) {
                    if (categorySpinner.selectedItemPosition != currentIdx) {
                        categorySpinner.setSelection(currentIdx, false)
                    }
                } else {
                    val df = when (type) {
                        com.voiceexpense.data.model.TransactionType.Expense -> DefaultField.DefaultExpenseCategory
                        com.voiceexpense.data.model.TransactionType.Income -> DefaultField.DefaultIncomeCategory
                        com.voiceexpense.data.model.TransactionType.Transfer -> DefaultField.DefaultTransferCategory
                    }
                    val defId = configRepo.defaultFor(df).first()
                    val defIdx = opts.indexOfFirst { it.id == defId }
                    if (defIdx >= 0 && categorySpinner.selectedItemPosition != defIdx) {
                        categorySpinner.setSelection(defIdx, false)
                    }
                }
            }
        }

        fun bindAccountOptions(selectedAccount: String?) {
            accountBindingJob?.cancel()
            accountBindingJob = lifecycleScope.launch {
                val opts = configRepo.options(ConfigType.Account).first().sortedBy { it.position }
                val labels = listOf("None") + opts.map { it.label }
                if (labels != lastAccountOptions) {
                    val adapter = android.widget.ArrayAdapter(this@TransactionConfirmationActivity, android.R.layout.simple_spinner_item, labels)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    accountSpinner.adapter = adapter
                    lastAccountOptions = labels
                }
                accountUserChange = false
                val accIdx = selectedAccount?.let { labels.indexOf(it).takeIf { index -> index >= 0 } } ?: 0
                if (accountSpinner.selectedItemPosition != accIdx) {
                    accountSpinner.setSelection(accIdx, false)
                }
            }
        }

        title.text = getString(R.string.app_name)
        // Disable actions until draft loads
        confirm.isEnabled = false

        // Load draft by id if provided
        val id = intent?.getStringExtra(EXTRA_TRANSACTION_ID)
        val refinedFromIntent: Set<FieldKey> = intent
            ?.getStringArrayListExtra(EXTRA_LOADING_FIELDS)
            ?.mapNotNull { raw -> runCatching { FieldKey.valueOf(raw) }.getOrNull() }
            ?.toSet()
            ?: emptySet()
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
            currentTransactionId = draft.id
            updateExportAvailability()
            viewModel.setHeuristicDraft(draft, refinedFromIntent)
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
                    amountView.updateTextPreservingFocus(t.amountUsd?.toPlainString() ?: "")
                    overallView.updateTextPreservingFocus(t.splitOverallChargedUsd?.toPlainString() ?: "")
                    merchantView.updateTextPreservingFocus(t.merchant)
                    descView.updateTextPreservingFocus(t.description ?: "")
                    val selIdx = when (t.type) {
                        com.voiceexpense.data.model.TransactionType.Expense -> 0
                        com.voiceexpense.data.model.TransactionType.Income -> 1
                        com.voiceexpense.data.model.TransactionType.Transfer -> 2
                    }
                    if (typeSpinner.selectedItemPosition != selIdx) {
                        typeSpinner.setSelection(selIdx, false)
                    }
                    val selectedType = when (selIdx) {
                        0 -> com.voiceexpense.data.model.TransactionType.Expense
                        1 -> com.voiceexpense.data.model.TransactionType.Income
                        else -> com.voiceexpense.data.model.TransactionType.Transfer
                    }
                    viewModel.setSelectedType(selectedType)
                    val categoryValue = when (t.type) {
                        com.voiceexpense.data.model.TransactionType.Expense -> t.expenseCategory
                        com.voiceexpense.data.model.TransactionType.Income -> t.incomeCategory
                        com.voiceexpense.data.model.TransactionType.Transfer -> null
                    }
                    bindCategoriesFor(t.type, categoryValue)
                    bindAccountOptions(t.account)
                    val tagsText = if (t.tags.isNotEmpty()) t.tags.joinToString(", ") else ""
                    tagsView.updateTextPreservingFocus(tagsText)
                    // Date display as YYYY-MM-DD (ISO format)
                    dateView.updateTextIfChanged(t.userLocalDate.toString())

                    amountView.markMissing(t.amountUsd == null)
                    merchantView.markMissing(t.merchant.isBlank())
                    // Category highlighting skipped for Spinner control
                    updateExportAvailability()
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
        typeSpinner.setOnTouchListener { _, _ ->
            typeUserChange = true
            false
        }
        typeSpinner.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) typeUserChange = true
        }
        typeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val type = when (position) {
                    0 -> com.voiceexpense.data.model.TransactionType.Expense
                    1 -> com.voiceexpense.data.model.TransactionType.Income
                    else -> com.voiceexpense.data.model.TransactionType.Transfer
                }
                if (typeUserChange) {
                    viewModel.markFieldUserModified(FieldKey.TYPE)
                    val current = viewModel.transaction.value
                    if (current != null && current.type != type) {
                        val updated = current.copy(type = type)
                        viewModel.applyManualEdits(updated)
                    }
                    typeUserChange = false
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
                findViewById<android.view.View>(R.id.container_field_category).visibility = vCat
                findViewById<android.view.View>(R.id.label_category).visibility = vCat

                val vAcc = if (vis.showAccount) android.view.View.VISIBLE else android.view.View.GONE
                findViewById<android.view.View>(R.id.container_field_account).visibility = vAcc
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
            val selectedAccount = (accountSpinner.selectedItem?.toString() ?: "").trim()
            val newAccount = if (selectedAccount == "None" || selectedAccount.isEmpty()) null else selectedAccount
            val newDateStr = (dateView.text?.toString() ?: "").trim()
            val newDate = runCatching {
                java.time.LocalDate.parse(newDateStr.ifEmpty { current.userLocalDate.toString() })
            }.getOrElse {
                Toast.makeText(this, "Invalid date format. Use YYYY-MM-DD", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
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
                userLocalDate = newDate
            )
            lifecycleScope.launch {
                viewModel.applyManualEditsAndConfirm(updated)
                enqueueSyncNow(this@TransactionConfirmationActivity)
                Toast.makeText(this@TransactionConfirmationActivity, "Saved. Syncing in background.", Toast.LENGTH_SHORT).show()
                navigateHome()
            }
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
                dateView.text = chosen.toString()  // YYYY-MM-DD format
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
                        viewModel.markFieldUserModified(FieldKey.TAGS)
                        updateTransactionFromUI()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        lifecycleScope.launch {
            viewModel.fieldLoadingStates.collect { states ->
                loadingIndicators.forEach { (field, indicator) ->
                    val loading = states[field] == true
                    indicator.isVisible = loading
                }
                val merchantLoading = states[FieldKey.MERCHANT] == true
                val descriptionLoading = states[FieldKey.DESCRIPTION] == true
                val categoryLoading =
                    states[FieldKey.EXPENSE_CATEGORY] == true || states[FieldKey.INCOME_CATEGORY] == true
                val accountLoading = states[FieldKey.ACCOUNT] == true
                val tagsLoading = states[FieldKey.TAGS] == true

                fun applyLoading(field: FieldKey, isLoading: Boolean) {
                    highlightTargets[field]?.alpha = if (isLoading) loadingDisabledAlpha else 1f
                    editableTargets[field]?.let { target ->
                        target.isEnabled = !isLoading
                        if (target === tagsView) {
                            target.isClickable = !isLoading
                        }
                    }
                }

                applyLoading(FieldKey.MERCHANT, merchantLoading)
                applyLoading(FieldKey.DESCRIPTION, descriptionLoading)
                applyLoading(FieldKey.EXPENSE_CATEGORY, categoryLoading)
                applyLoading(FieldKey.INCOME_CATEGORY, categoryLoading)
                applyLoading(FieldKey.ACCOUNT, accountLoading)
                applyLoading(FieldKey.TAGS, tagsLoading)

                categoryProgress.isVisible = categoryLoading
            }
        }

        lifecycleScope.launch {
            var previousStates: Map<FieldKey, FieldRefinementStatus> = emptyMap()
            viewModel.refinementState.collect { currentStates ->
                currentStates.forEach { (field, status) ->
                    val previous = previousStates[field]
                    if (status is FieldRefinementStatus.Completed && previous !is FieldRefinementStatus.Completed) {
                        highlightTargets[field]?.let { animateFieldUpdate(it) }
                    }
                }
                previousStates = currentStates
            }
        }

        // Placeholder voice correction using ASR debug
        // Voice correction removed in text-first refactor

        // Removed typed correction row per UX change
    }

    private fun promptDiagnosticsExport() {
        val txnId = currentTransactionId
        if (txnId == null) {
            Toast.makeText(this, R.string.export_diagnostics_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val logExists = ParsingRunLogStore.snapshot(txnId) != null
        if (!logExists) {
            Toast.makeText(this, R.string.export_diagnostics_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val inputLayout = TextInputLayout(this)
        inputLayout.setPadding(padding, padding, padding, 0)
        inputLayout.hint = getString(R.string.export_diagnostics_note_hint)
        val input = TextInputEditText(inputLayout.context)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.setLines(3)
        inputLayout.addView(input)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_diagnostics_dialog_title)
            .setView(inputLayout)
            .setPositiveButton(R.string.export_diagnostics_positive) { _, _ ->
                pendingExportNote = input.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                diagnosticsDocumentLauncher.launch(defaultDiagnosticsFileName())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun exportDiagnostics(uri: Uri) {
        val txnId = currentTransactionId
        if (txnId == null) {
            Toast.makeText(this, R.string.export_diagnostics_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val log = ParsingRunLogStore.snapshot(txnId)
        if (log == null) {
            Toast.makeText(this, R.string.export_diagnostics_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val note = pendingExportNote
        pendingExportNote = null
        val markdown = log.toMarkdown(note)
        val outcome = runCatching {
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(markdown.toByteArray(Charsets.UTF_8))
                stream.flush()
            } ?: error("Output stream unavailable")
        }
        if (outcome.isSuccess) {
            Toast.makeText(this, R.string.export_diagnostics_success, Toast.LENGTH_SHORT).show()
        } else {
            Log.e("DiagnosticsExport", "Failed to export diagnostics", outcome.exceptionOrNull())
            Toast.makeText(this, R.string.export_diagnostics_failure, Toast.LENGTH_LONG).show()
        }
    }

    private fun defaultDiagnosticsFileName(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())
        return "voice-expense-run-${formatter.format(Instant.now())}.md"
    }

    private fun updateExportAvailability() {
        val hasLog = currentTransactionId?.let { ParsingRunLogStore.snapshot(it) } != null
        exportButton.isEnabled = hasLog
    }

    override fun onDestroy() {
        if (isFinishing) {
            currentTransactionId?.let { ParsingRunLogStore.remove(it) }
        }
        super.onDestroy()
    }

    private fun navigateHome() {
        // Bring MainActivity to front if it exists, otherwise create it
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun animateFieldUpdate(view: View) {
        val highlightColor = Color.parseColor("#FFF9C4")
        view.setBackgroundColor(highlightColor)
        ValueAnimator.ofArgb(highlightColor, Color.TRANSPARENT).apply {
            duration = 600
            addUpdateListener { animator ->
                view.setBackgroundColor(animator.animatedValue as Int)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.setBackgroundColor(Color.TRANSPARENT)
                }
            })
            start()
        }
    }
}
