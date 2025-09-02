package com.voiceexpense.ui.confirmation.voice

import com.voiceexpense.ai.parsing.StructuredOutputValidator
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Orchestrates voice correction loop: speak → listen → parse → apply → repeat.
 * UI/ViewModel should observe [prompts] to speak via TTS and feed transcripts via [onTranscript].
 */
class VoiceCorrectionController(
    private val tts: TtsEngine,
    private val parser: CorrectionIntentParser,
    private val renderer: PromptRenderer,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private var debug: Boolean = false
    private var current: Transaction? = null
    private var speakJob: Job? = null
    private var timeoutJob: Job? = null
    private var reprompted: Boolean = false

    private val _state = MutableStateFlow(LoopState())
    val state: StateFlow<LoopState> = _state

    private val _prompts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val prompts: SharedFlow<String> = _prompts

    private val _confirmed = MutableSharedFlow<Transaction>(extraBufferCapacity = 1)
    val confirmed: SharedFlow<Transaction> = _confirmed

    private val _cancelled = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cancelled: SharedFlow<Unit> = _cancelled

    private val _updates = MutableSharedFlow<Transaction>(extraBufferCapacity = 8)
    val updates: SharedFlow<Transaction> = _updates

    fun interrupt() {
        tts.stop()
        _state.value = _state.value.copy(isSpeaking = false)
    }

    fun setDebug(enabled: Boolean) { this.debug = enabled }

    fun start(initial: Transaction) {
        current = initial
        resetTimeout()
        speakSummary()
    }

    fun onTranscript(text: String) {
        val txn = current ?: return
        resetTimeout()
        val intent = parser.parse(text, txn)
        if (debug) println("[VoiceLoop] transcript='$text' intent=${intent::class.simpleName}")
        when (intent) {
            is CorrectionIntent.SetAmount -> update(txn.copy(amountUsd = intent.amount, correctionsCount = txn.correctionsCount + 1))
            is CorrectionIntent.SetMerchant -> update(txn.copy(merchant = intent.name, correctionsCount = txn.correctionsCount + 1))
            is CorrectionIntent.SetDescription -> update(txn.copy(description = intent.text, correctionsCount = txn.correctionsCount + 1))
            is CorrectionIntent.SetType -> {
                val t = intent.type
                val cleared = when (t) {
                    TransactionType.Expense -> txn.copy(type = t, incomeCategory = null, correctionsCount = txn.correctionsCount + 1)
                    TransactionType.Income -> txn.copy(type = t, expenseCategory = null, amountUsd = txn.amountUsd, correctionsCount = txn.correctionsCount + 1)
                    TransactionType.Transfer -> txn.copy(type = t, amountUsd = null, expenseCategory = null, incomeCategory = null, correctionsCount = txn.correctionsCount + 1)
                }
                update(cleared)
            }
            is CorrectionIntent.SetExpenseCategory -> update(txn.copy(expenseCategory = intent.name, incomeCategory = null, correctionsCount = txn.correctionsCount + 1))
            is CorrectionIntent.SetIncomeCategory -> update(txn.copy(incomeCategory = intent.name, expenseCategory = null, correctionsCount = txn.correctionsCount + 1))
            is CorrectionIntent.SetTags -> {
                val newTags = if (intent.replace) intent.tags else (txn.tags + intent.tags).distinct()
                update(txn.copy(tags = newTags, correctionsCount = txn.correctionsCount + 1))
            }
            is CorrectionIntent.SetAccount -> update(txn.copy(account = intent.name, correctionsCount = txn.correctionsCount + 1))
            is CorrectionIntent.SetOverallCharged -> update(txn.copy(splitOverallChargedUsd = intent.amount, correctionsCount = txn.correctionsCount + 1))
            is CorrectionIntent.SetDate -> update(txn.copy(userLocalDate = intent.date, correctionsCount = txn.correctionsCount + 1))
            CorrectionIntent.Confirm -> {
                // Emit confirm; VM should handle repository updates and navigation
                scope.launch { current?.let { _confirmed.emit(it) } }
                return
            }
            CorrectionIntent.Cancel -> {
                scope.launch { _cancelled.emit(Unit) }
                return
            }
            CorrectionIntent.Repeat -> speakSummary()
            is CorrectionIntent.Unknown -> clarifyUnknown()
        }
    }

    private fun clarifyUnknown() {
        scope.launch {
            val text = renderer.clarify(Ambiguity.UnknownType)
            speak(text, PromptKind.Clarify)
        }
    }

    private fun update(next: Transaction) {
        val prev = current ?: return
        // Validate core constraints
        val validation = StructuredOutputValidator.validate(
            com.voiceexpense.ai.parsing.ParsedResult(
                amountUsd = next.amountUsd,
                merchant = next.merchant,
                description = next.description,
                type = when (next.type) {
                    TransactionType.Expense -> "Expense"
                    TransactionType.Income -> "Income"
                    TransactionType.Transfer -> "Transfer"
                },
                expenseCategory = next.expenseCategory,
                incomeCategory = next.incomeCategory,
                tags = next.tags,
                userLocalDate = next.userLocalDate,
                account = next.account,
                splitOverallChargedUsd = next.splitOverallChargedUsd,
                note = next.note,
                confidence = next.confidence
            )
        )
        if (!validation.valid) {
            val text = when (validation.error) {
                "share exceeds overall" -> renderer.clarify(Ambiguity.AmountVsOverall)
                "invalid type" -> renderer.clarify(Ambiguity.UnknownType)
                else -> renderer.clarify(Ambiguity.ConflictFields)
            }
            scope.launch { speak(text, PromptKind.Clarify) }
            return
        }
        current = next
        scope.launch { _updates.emit(next) }
        speakSummary()
    }

    private fun speakSummary() {
        val txn = current ?: return
        val missing = computeMissing(txn)
        val text = if (missing.isEmpty()) renderer.summary(txn) else renderer.askMissing(missing)
        scope.launch { speak(text, if (missing.isEmpty()) PromptKind.Summary else PromptKind.AskMissing) }
        resetTimeout()
    }

    private suspend fun speak(text: String, kind: PromptKind) {
        _state.value = _state.value.copy(isSpeaking = true, lastPrompt = text, awaiting = kind)
        speakJob?.cancel()
        speakJob = scope.launch { tts.speak(text) }
        speakJob?.invokeOnCompletion {
            _state.value = _state.value.copy(isSpeaking = false)
        }
        _prompts.emit(text)
    }

    private fun resetTimeout() {
        timeoutJob?.cancel()
        reprompted = false
        val secs = _state.value.timeouts.initialSeconds.toLong()
        timeoutJob = scope.launch {
            kotlinx.coroutines.delay(secs * 1000)
            if (!reprompted) {
                reprompted = true
                val prompt = renderer.summary(current ?: return@launch)
                speak(prompt + " Still there? Say yes to save or a field to change.", PromptKind.Clarify)
                // second timeout
                kotlinx.coroutines.delay(_state.value.timeouts.repromptSeconds * 1000L)
                _cancelled.emit(Unit)
            }
        }
    }

    private fun computeMissing(t: Transaction): Set<Field> {
        val missing = mutableSetOf<Field>()
        when (t.type) {
            TransactionType.Expense -> if (t.amountUsd == null) missing += Field.Amount
            TransactionType.Income -> if (t.amountUsd == null) missing += Field.Amount
            TransactionType.Transfer -> { /* amount is not required */ }
        }
        return missing
    }
}
