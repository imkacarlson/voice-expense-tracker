package com.voiceexpense.ui.confirmation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.ai.parsing.heuristic.FieldKey
import com.voiceexpense.ai.parsing.hybrid.FieldRefinementTracker
import com.voiceexpense.ai.parsing.hybrid.FieldSelectionStrategy
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val _fieldLoadingStates = MutableStateFlow(emptyLoadingMap())
    val fieldLoadingStates: StateFlow<Map<FieldKey, Boolean>> = _fieldLoadingStates
    val refinementState get() = refinementTracker.refinementState

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

    fun setDraft(draft: Transaction) {
        setHeuristicDraft(draft, emptySet())
    }

    fun setHeuristicDraft(draft: Transaction, loadingFields: Set<FieldKey>) {
        refinementTracker = FieldRefinementTracker()
        if (loadingFields.isNotEmpty()) {
            refinementTracker.markRefining(loadingFields)
        }
        updateLoadingStates(loadingFields)
        applyDraftState(draft)
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
    }

    // Apply manual edits from UI and persist as draft before confirmation
    fun applyManualEdits(updated: Transaction) {
        _transaction.value = updated
        updateConfidence(updated.confidence)
        recomputeValidation()
        viewModelScope.launch { repo.saveDraft(updated) }
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
            FieldKey.TAGS -> {
                val tags = when (value) {
                    is List<*> -> value.filterIsInstance<String>().mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                    is String -> listOfNotNull(value.trim().takeIf(String::isNotEmpty))
                    else -> emptyList()
                }
                if (tags == transaction.tags) transaction else transaction.copy(tags = tags)
            }
            FieldKey.NOTE -> {
                val note = (value as? String)?.trim()
                val normalized = note.takeUnless { it.isNullOrEmpty() }
                if (normalized == transaction.note) transaction else transaction.copy(note = normalized)
            }
            else -> transaction
        }
    }

    private fun updateLoadingStates(loadingFields: Set<FieldKey>) {
        _fieldLoadingStates.value = FieldSelectionStrategy.AI_REFINABLE_FIELDS.associateWith { loadingFields.contains(it) }
    }

    private fun setFieldLoading(field: FieldKey, isLoading: Boolean) {
        _fieldLoadingStates.update { current ->
            val updated = if (current.isEmpty()) emptyLoadingMap() else current.toMutableMap()
            updated[field] = isLoading
            updated
        }
    }

    private fun emptyLoadingMap(): MutableMap<FieldKey, Boolean> =
        FieldSelectionStrategy.AI_REFINABLE_FIELDS.associateWith { false }.toMutableMap()
}
