package com.voiceexpense.ai.speech

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AudioRecordingManager {
    sealed class Event {
        data object Started : Event()
        data object Stopped : Event()
        data object SilenceDetected : Event()
        data class Error(val message: String) : Event()
    }

    fun events(): Flow<Event> = callbackFlow {
        // Stub: in real impl, hook microphone and voice activity detection
        trySend(Event.Started)
        awaitClose { trySend(Event.Stopped).isSuccess }
    }
}

