package com.voiceexpense.ui.confirmation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

class ConfirmationViewModel(
    private val repo: TransactionRepository,
    private val parser: TransactionParser
) : ViewModel() {
    private val _transaction = MutableStateFlow<Transaction?>(null)
    val transaction: StateFlow<Transaction?> = _transaction

    fun setDraft(draft: Transaction) {
        _transaction.value = draft
    }

    fun applyCorrection(utterance: String) {
        val current = _transaction.value ?: return
        // Naive correction: if number present, update amount
        val num = Regex("(\\d+)(?:\\.(\\d{1,2}))?").find(utterance)?.value
        val updated = if (num != null) {
            current.copy(amountUsd = BigDecimal(num), correctionsCount = current.correctionsCount + 1)
        } else {
            current.copy(correctionsCount = current.correctionsCount + 1)
        }
        _transaction.value = updated
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
    }
}

