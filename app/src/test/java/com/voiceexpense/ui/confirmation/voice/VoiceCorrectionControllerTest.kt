package com.voiceexpense.ui.confirmation.voice

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class InstantTts : TtsEngine() {
    override suspend fun speak(text: String) { /* no-op */ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCorrectionControllerTest {
    private fun draft() = Transaction(
        userLocalDate = LocalDate.of(2025, 1, 1),
        amountUsd = BigDecimal("10.00"),
        merchant = "Test",
        description = null,
        type = TransactionType.Expense,
        expenseCategory = "Food",
        incomeCategory = null,
        tags = emptyList(),
        account = null,
        splitOverallChargedUsd = null,
        note = null,
        confidence = 1f,
        status = TransactionStatus.DRAFT
    )

    @Test fun loop_correction_then_confirm() = runBlocking {
        val controller = VoiceCorrectionController(
            tts = InstantTts(), parser = CorrectionIntentParser(), renderer = PromptRenderer()
        )
        controller.start(draft())
        controller.onTranscript("merchant Starbucks")
        val updated = controller.updates.first()
        assertThat(updated.merchant).isEqualTo("Starbucks")

        controller.onTranscript("yes")
        val confirmed = controller.confirmed.first()
        assertThat(confirmed.merchant).isEqualTo("Starbucks")
    }
}

