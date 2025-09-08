package com.voiceexpense.ai.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay

class SpeechRecognitionService(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(config: RecognitionConfig = RecognitionConfig()): Flow<RecognitionResult> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(RecognitionResult.Error(RecognitionError.Unavailable("Speech recognition not available on device")))
            close()
            return@callbackFlow
        }

        // Create and start SpeechRecognizer on the main thread as required by the platform
        mainHandler.post {
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
                        trySend(RecognitionResult.Complete)
                        // Close the flow so collectors can proceed (e.g., to retry online)
                        close()
                    }

                    override fun onResults(results: Bundle) {
                        val texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        val confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                        if (texts.isNotEmpty()) {
                            val text = texts.first()
                            val conf = confidences?.firstOrNull() ?: 0f
                            trySend(RecognitionResult.Success(text, conf))
                        } else {
                            // No recognized text — treat like a timeout so UI can inform the user
                            trySend(RecognitionResult.Error(RecognitionError.Timeout))
                        }
                        trySend(RecognitionResult.Complete)
                        close()
                    }

                    override fun onPartialResults(partialResults: Bundle) {
                        if (config.partialResults) {
                            val texts = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                            if (texts.isNotEmpty()) trySend(RecognitionResult.Partial(texts.first()))
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.languageCode)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, config.maxResults)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, config.partialResults)
                    // Prefer offline if available
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, config.offlineMode)
                }
                sr.startListening(intent)
            }
        }

        awaitClose {
            stopListening()
        }
    }

    /**
     * Debug helper used by the confirmation screen to simulate a transcript.
     * Emits the provided text once and completes.
     */
    fun transcribeDebug(text: String): Flow<String> = flow {
        // small delay to simulate async behavior
        delay(50)
        emit(text)
    }

    fun stopListening() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            recognizer?.apply {
                try {
                    stopListening()
                    cancel()
                } finally {
                    destroy()
                }
            }
            recognizer = null
        } else {
            mainHandler.post { stopListening() }
        }
    }
}
