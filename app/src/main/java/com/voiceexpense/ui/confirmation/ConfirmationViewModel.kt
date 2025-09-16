package com.voiceexpense.ui.confirmation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

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
        _transaction.value = draft
        _selectedType.value = draft.type
        recomputeVisibility(draft.type)
        updateConfidence(draft.confidence)
        recomputeValidation()
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
    }

    // Apply manual edits from UI and persist as draft before confirmation
    fun applyManualEdits(updated: Transaction) {
        _transaction.value = updated
        updateConfidence(updated.confidence)
        recomputeValidation()
        viewModelScope.launch { repo.saveDraft(updated) }
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
}
