package com.voiceexpense.ai.speech

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SpeechRecognitionServiceTest {
    @Test
    fun transcribeDebug_emitsSingleSegment() = runBlocking {
        val svc = SpeechRecognitionService()
        svc.transcribeDebug("hello world").test {
            assertThat(awaitItem()).isEqualTo("hello world")
            awaitComplete()
        }
    }
}

