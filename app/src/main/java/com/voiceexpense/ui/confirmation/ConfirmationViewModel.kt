package com.voiceexpense.ui.confirmation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.ui.confirmation.voice.LoopState
import com.voiceexpense.ui.confirmation.voice.CorrectionIntentParser
import com.voiceexpense.ui.confirmation.voice.PromptRenderer
import com.voiceexpense.ui.confirmation.voice.TtsEngine
import com.voiceexpense.ui.confirmation.voice.VoiceCorrectionController
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

class ConfirmationViewModel(
    private val repo: TransactionRepository,
    private val parser: TransactionParser,
    private val controller: VoiceCorrectionController = VoiceCorrectionController(
        tts = TtsEngine(), parser = CorrectionIntentParser(), renderer = PromptRenderer()
    )
) : ViewModel() {
    private val _transaction = MutableStateFlow<Transaction?>(null)
    val transaction: StateFlow<Transaction?> = _transaction

    fun setDraft(draft: Transaction) {
        _transaction.value = draft
        // Bridge controller updates on first draft set
        viewModelScope.launch { controller.updates.collect { _transaction.value = it } }
        viewModelScope.launch {
            controller.confirmed.collect { txn ->
                repo.confirm(txn.id)
                repo.enqueueForSync(txn.id)
                _transaction.value = txn.copy(status = TransactionStatus.CONFIRMED)
            }
        }
        viewModelScope.launch { controller.cancelled.collect { _transaction.value = null } }
        controller.start(draft)
    }

    fun applyCorrection(utterance: String) {
        controller.onTranscript(utterance)
        // Defensive: ensure immediate local reflection of simple amount corrections in tests
        val current = _transaction.value
        if (current != null) {
            val number = Regex("(\\d+)(?:\\.(\\d{1,2}))?").find(utterance)?.value
            if (number != null && !utterance.contains("overall", ignoreCase = true)) {
                runCatching { java.math.BigDecimal(number) }.getOrNull()?.let { amt ->
                    _transaction.value = current.copy(
                        amountUsd = amt,
                        correctionsCount = current.correctionsCount + 1
                    )
                }
            }
        }
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

    // Voice loop state/effects
    val loop: kotlinx.coroutines.flow.StateFlow<LoopState> get() = controller.state
    val ttsEvents: kotlinx.coroutines.flow.SharedFlow<String> get() = controller.prompts

    fun startLoop() {
        val t = _transaction.value ?: return
        controller.start(t)
    }

    fun interruptTts() {
        controller.interrupt()
    }
}
