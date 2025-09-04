package com.voiceexpense.ai.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SpeechRecognitionService(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    suspend fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(config: RecognitionConfig = RecognitionConfig()): Flow<RecognitionResult> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(RecognitionResult.Error(RecognitionError.Unavailable("Speech recognition not available on device")))
            close()
            return@callbackFlow
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    trySend(RecognitionResult.Listening)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val mapped = when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> RecognitionError.NoPermission
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> RecognitionError.Timeout
                        else -> RecognitionError.Api(error, "ASR error $error")
                    }
                    trySend(RecognitionResult.Error(mapped))
                }

                override fun onResults(results: Bundle) {
                    val texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                    if (texts.isNotEmpty()) {
                        val text = texts.first()
                        val conf = confidences?.firstOrNull() ?: 0f
                        if (conf >= config.confidenceThreshold) {
                            trySend(RecognitionResult.Success(text, conf))
                        } else {
                            trySend(RecognitionResult.Success(text, conf))
                        }
                    }
                    trySend(RecognitionResult.Complete)
                }

                override fun onPartialResults(partialResults: Bundle) {
                    if (config.partialResults) {
                        val texts = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        if (texts.isNotEmpty()) trySend(RecognitionResult.Partial(texts.first()))
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.languageCode)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, config.maxResults)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, config.partialResults)
            // Prefer offline if available
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, config.offlineMode)
        }

        recognizer?.startListening(intent)

        awaitClose {
            stopListening()
        }
    }

    fun stopListening() {
        recognizer?.apply {
            try {
                stopListening()
                cancel()
            } finally {
                destroy()
            }
        }
        recognizer = null
    }
}
