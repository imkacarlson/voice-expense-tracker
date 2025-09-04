package com.voiceexpense.ai.speech

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            trySend(Event.Error("Invalid audio buffer size"))
            close()
            return@callbackFlow
        }

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBuffer * 2
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            trySend(Event.Error("AudioRecord initialization failed"))
            recorder.release()
            close()
            return@callbackFlow
        }

        val buffer = ShortArray(minBuffer)
        try {
            recorder.startRecording()
            trySend(Event.Started)

            // Basic loop; in a real impl, feed VAD or ASR as needed
            while (!isClosedForSend) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) break
                // Simple silence heuristic could be added here
            }
        } catch (t: Throwable) {
            trySend(Event.Error(t.message ?: "audio error"))
        } finally {
            try {
                recorder.stop()
            } catch (_: Throwable) {}
            recorder.release()
            trySend(Event.Stopped)
        }

        awaitClose {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } catch (_: Throwable) {}
            recorder.release()
        }
    }
}
