package com.voiceexpense.ai.speech

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpeechRecognitionServiceTest {
    @Test
    fun configValidation() {
        val ok = RecognitionConfig()
        assertThat(ok.validate()).isEmpty()

        val bad = RecognitionConfig(maxResults = 0, confidenceThreshold = 1.5f, languageCode = "")
        val errs = bad.validate()
        assertThat(errs).containsAtLeast(
            "languageCode must not be blank",
            "maxResults must be between 1 and 5",
            "confidenceThreshold must be between 0.0 and 1.0"
        )
    }
}

