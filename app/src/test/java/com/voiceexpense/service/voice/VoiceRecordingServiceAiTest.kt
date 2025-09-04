package com.voiceexpense.service.voice

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.TransactionParser
import kotlinx.coroutines.runBlocking
import org.junit.Test

class VoiceRecordingServiceAiTest {
    @Test
    fun parserIntegrationBasicExpense() = runBlocking {
        val parser = TransactionParser()
        val res = parser.parse("I spent 12 at Merchant", ParsingContext())
        assertThat(res.type).isEqualTo("Expense")
    }
}

