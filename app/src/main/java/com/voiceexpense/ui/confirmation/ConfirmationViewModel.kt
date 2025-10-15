package com.voiceexpense.ui.confirmation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.ai.parsing.hybrid.FieldRefinementTracker
import com.voiceexpense.ai.parsing.hybrid.FieldSelectionStrategy
import com.voiceexpense.ai.parsing.hybrid.StagedRefinementDispatcher
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.data.repository.TransactionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConfirmationViewModel(
    private val repo: TransactionRepository,
    private val parser: TransactionParser
) : ViewModel() {
    private val _transaction = MutableStateFlow<Transaction?>(null)
    val transaction: StateFlow<Transaction?> = _transaction
    private val validator = ValidationEngine()
    private val _validation = MutableStateFlow<ValidationResult?>(null)
    val validation: StateFlow<ValidationResult?> = _validation

    data class ConfidenceUiState(
        val confidence: Float,
        val isLowConfidence: Boolean
    )

    private val _confidence = MutableStateFlow(ConfidenceUiState(confidence = 1f, isLowConfidence = false))
    val confidence: StateFlow<ConfidenceUiState> = _confidence

    private val lowConfidenceThreshold = 0.75f

    private var refinementTracker: FieldRefinementTracker = FieldRefinementTracker()
    private val _fieldLoadingStates = MutableStateFlow<Map<FieldKey, Boolean>>(emptyLoadingMap())
    val fieldLoadingStates: StateFlow<Map<FieldKey, Boolean>> = _fieldLoadingStates
    val refinementState get() = refinementTracker.refinementState
    private var refinementJob: Job? = null

    data class UiVisibility(
        val showAmount: Boolean,
        val showExpenseCategory: Boolean,
        val showIncomeCategory: Boolean,
        val showAccount: Boolean,
        val showOverall: Boolean
    )

    private val _selectedType = MutableStateFlow<TransactionType?>(null)
    private val _visibility = MutableStateFlow(UiVisibility(true, true, false, true, true))
    val visibility: StateFlow<UiVisibility> = _visibility

    fun setDraft(draft: Transaction, refinedFields: Set<FieldKey> = emptySet()) {
        setHeuristicDraft(draft, refinedFields)
    }

    fun setHeuristicDraft(draft: Transaction, loadingFields: Set<FieldKey>) {
        refinementTracker = FieldRefinementTracker()
        if (loadingFields.isNotEmpty()) {
            refinementTracker.markRefining(loadingFields)
        }
        updateLoadingStates(loadingFields)
        applyDraftState(draft)
        observeRefinementUpdates(draft.id)
    }

    fun setSelectedType(type: TransactionType) {
        _selectedType.value = type
        recomputeVisibility(type)
    }

    private fun recomputeVisibility(type: TransactionType) {
        val vis = when (type) {
            TransactionType.Expense -> UiVisibility(
                showAmount = true,
                showExpenseCategory = true,
                showIncomeCategory = false,
                showAccount = true,
                showOverall = true
            )
            TransactionType.Income -> UiVisibility(
                showAmount = true,
                showExpenseCategory = false,
                showIncomeCategory = true,
                showAccount = true,
                showOverall = false
            )
            TransactionType.Transfer -> UiVisibility(
                showAmount = false,
                showExpenseCategory = false,
                showIncomeCategory = false,
                showAccount = false,
                showOverall = false
            )
        }
        _visibility.value = vis
    }

    fun confirm() {
        val t = _transaction.value ?: return
        viewModelScope.launch {
            repo.confirm(t.id)
            repo.enqueueForSync(t.id)
            _transaction.value = t.copy(status = TransactionStatus.CONFIRMED)
        }
    }

    fun cancel() {
        _transaction.value = null
        _confidence.value = ConfidenceUiState(confidence = 1f, isLowConfidence = false)
        updateLoadingStates(emptySet())
        refinementJob?.cancel()
    }

    // Apply manual edits from UI and persist as draft before confirmation
    fun applyManualEdits(updated: Transaction) {
        _transaction.value = updated
        updateConfidence(updated.confidence)
        recomputeValidation()
        viewModelScope.launch { repo.saveDraft(updated) }
    }

    // Apply manual edits and confirm in sequence to avoid race condition
    suspend fun applyManualEditsAndConfirm(updated: Transaction) {
        _transaction.value = updated
        updateConfidence(updated.confidence)
        recomputeValidation()
        // Save edits first
        repo.saveDraft(updated)
        // Then confirm (reads from DB and sets status)
        repo.confirm(updated.id)
        // Then enqueue for sync (reads from DB and sets status)
        repo.enqueueForSync(updated.id)
        _transaction.value = updated.copy(status = TransactionStatus.CONFIRMED)
    }

    fun applyAiRefinement(field: FieldKey, value: Any?) {
        applyAiRefinements(mapOf(field to value))
    }

    fun applyAiRefinements(refined: Map<FieldKey, Any?>) {
        val current = _transaction.value ?: return
        var updated = current
        var changed = false

        refined.forEach { (field, value) ->
            if (refinementTracker.isUserModified(field)) {
                setFieldLoading(field, false)
                return@forEach
            }

            val next = updateTransactionField(updated, field, value)
            if (next !== updated) {
                updated = next
                changed = true
            }
            refinementTracker.markCompleted(field, value)
            setFieldLoading(field, false)
        }

        if (changed) {
            _transaction.value = updated
            updateConfidence(updated.confidence)
            recomputeValidation()
            viewModelScope.launch { repo.saveDraft(updated) }
        }
    }

    fun markFieldUserModified(field: FieldKey) {
        refinementTracker.markUserModified(field)
        setFieldLoading(field, false)
    }

    private fun recomputeValidation() {
        val t = _transaction.value ?: return
        _validation.value = validator.validate(t)
    }

    private fun updateConfidence(value: Float) {
        _confidence.value = ConfidenceUiState(
            confidence = value,
            isLowConfidence = value < lowConfidenceThreshold
        )
    }

    private fun applyDraftState(draft: Transaction) {
        _transaction.value = draft
        _selectedType.value = draft.type
        recomputeVisibility(draft.type)
        updateConfidence(draft.confidence)
        recomputeValidation()
    }

    private fun observeRefinementUpdates(transactionId: String) {
        refinementJob?.cancel()
        refinementJob = viewModelScope.launch {
            StagedRefinementDispatcher.updates
                .filter { it.transactionId == transactionId }
                .collect { event -> handleRefinementEvent(event) }
        }
    }

    private fun handleRefinementEvent(event: StagedRefinementDispatcher.RefinementEvent) {
        val current = _transaction.value ?: return
        if (current.id != event.transactionId) return

        Log.d(
            "FieldRefinement",
            "Applying staged update tx=${event.transactionId} refined=${event.refinedFields.keys.joinToString()} errors=${event.errors.size}"
        )

        if (event.refinedFields.isNotEmpty()) {
            applyAiRefinements(event.refinedFields)
        }

        val afterFields = _transaction.value ?: current

        val remaining = event.targetFields - event.refinedFields.keys
        if (remaining.isNotEmpty()) {
            remaining.forEach { field ->
                if (!refinementTracker.isUserModified(field)) {
                    refinementTracker.markCompleted(field, null)
                }
                setFieldLoading(field, false)
            }
        } else if (event.refinedFields.isEmpty()) {
            event.targetFields.forEach { field -> setFieldLoading(field, false) }
        }

        event.confidence?.let { newConfidence ->
            if (afterFields.confidence != newConfidence) {
                val updatedTxn = afterFields.copy(confidence = newConfidence)
                _transaction.value = updatedTxn
                updateConfidence(newConfidence)
                recomputeValidation()
                viewModelScope.launch { repo.saveDraft(updatedTxn) }
            } else {
                updateConfidence(afterFields.confidence)
            }
        }

        if (event.errors.isNotEmpty()) {
            Log.w("FieldRefinement", "Staged parsing errors: ${event.errors.joinToString()}")
        }

        val latest = _transaction.value
        if (latest != null) {
            Log.d(
                "FieldRefinement",
                "Post-update txn merchant='${latest.merchant}' description='${latest.description}' category='${latest.expenseCategory}' tags=${latest.tags} confidence=${latest.confidence}"
            )
        }
    }

    private fun updateTransactionField(
        transaction: Transaction,
        field: FieldKey,
        value: Any?
    ): Transaction {
        return when (field) {
            FieldKey.MERCHANT -> {
                val merchant = (value as? String)?.takeIf { it.isNotBlank() } ?: return transaction
                if (merchant == transaction.merchant) transaction else transaction.copy(merchant = merchant)
            }
            FieldKey.DESCRIPTION -> {
                val description = (value as? String)?.takeIf { it.isNotBlank() }
                if (description == transaction.description) transaction else transaction.copy(description = description)
            }
            FieldKey.EXPENSE_CATEGORY -> {
                val category = (value as? String)?.takeIf { it.isNotBlank() }
                if (category == transaction.expenseCategory) transaction else transaction.copy(expenseCategory = category)
            }
            FieldKey.INCOME_CATEGORY -> {
                val category = (value as? String)?.takeIf { it.isNotBlank() }
                if (category == transaction.incomeCategory) transaction else transaction.copy(incomeCategory = category)
            }
            FieldKey.ACCOUNT -> {
                val account = (value as? String)?.takeIf { it.isNotBlank() }
                if (account == transaction.account) transaction else transaction.copy(account = account)
            }
            FieldKey.TAGS -> {
                val tags = when (value) {
                    is List<*> -> value.filterIsInstance<String>().mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                    is String -> listOfNotNull(value.trim().takeIf(String::isNotEmpty))
                    else -> emptyList()
                }
                if (tags == transaction.tags) transaction else transaction.copy(tags = tags)
            }
            else -> transaction
        }
    }

    private fun updateLoadingStates(loadingFields: Set<FieldKey>) {
        _fieldLoadingStates.value = FieldSelectionStrategy.AI_REFINABLE_FIELDS.associateWith { loadingFields.contains(it) }
    }

    private fun setFieldLoading(field: FieldKey, isLoading: Boolean) {
        _fieldLoadingStates.update { current ->
            val updated = current.toMutableMap()
            updated[field] = isLoading
            updated
        }
    }

    private fun emptyLoadingMap(): Map<FieldKey, Boolean> =
        FieldSelectionStrategy.AI_REFINABLE_FIELDS.associateWith { false }
}
