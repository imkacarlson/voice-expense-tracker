package com.voiceexpense.ai.speech

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SpeechRecognitionService {
    // Placeholder: real on-device ASR integration to be added.
    // For now, provide a debug method to emit a single transcript.
    fun transcribeDebug(input: String): Flow<String> = flowOf(input)
}

