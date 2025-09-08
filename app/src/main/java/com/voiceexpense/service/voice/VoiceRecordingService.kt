package com.voiceexpense.service.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.ai.speech.AudioRecordingManager
import com.voiceexpense.ai.speech.RecognitionConfig
import com.voiceexpense.ai.speech.RecognitionResult
import com.voiceexpense.ai.speech.SpeechRecognitionService
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.data.repository.TransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

@AndroidEntryPoint
class VoiceRecordingService : Service() {
    companion object {
        const val CHANNEL_ID = "voice_recording"
        const val NOTIF_ID = 1001
        const val ACTION_START = "com.voiceexpense.action.START"
        const val ACTION_STOP = "com.voiceexpense.action.STOP"
        const val ACTION_DRAFT_READY = "com.voiceexpense.action.DRAFT_READY"
        const val ACTION_LISTENING_COMPLETE = "com.voiceexpense.action.LISTENING_COMPLETE"
        const val ACTION_LISTENING_STARTED = "com.voiceexpense.action.LISTENING_STARTED"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_ERROR_MESSAGE = "error_message"
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var job: Job? = null
    private var lastLaunchedId: String? = null
    private var lastLaunchAt: Long = 0L

    @Inject lateinit var audio: AudioRecordingManager
    @Inject lateinit var asr: SpeechRecognitionService
    @Inject lateinit var parser: TransactionParser
    @Inject lateinit var repo: TransactionRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        // Dependencies injected by Hilt
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture()
            ACTION_STOP -> stopCapture()
        }
        return START_STICKY
    }

    private fun startCapture() {
        startForeground(NOTIF_ID, notification("Listening…"))
        // Notify UI that listening has started (explicit to our package)
        sendBroadcast(Intent(ACTION_LISTENING_STARTED).setPackage(packageName))
        Log.d("VoiceService", "startCapture: starting ASR with offlineMode=true")
        job?.cancel()
        job = scope.launch {
            // Safety timeout to avoid battery drain
            val timeoutJob = launch { kotlinx.coroutines.delay(20_000); stopCapture() }

            // Do not start a parallel AudioRecord while SpeechRecognizer is active

            val config = RecognitionConfig(
                languageCode = "en-US",
                maxResults = 1,
                partialResults = false,
                // Steering docs: prefer on-device (offline) ASR only
                offlineMode = true,
                confidenceThreshold = 0.5f
            )
            asr.startListening(config).collectLatest { result ->
                when (result) {
                    is RecognitionResult.Success -> {
                        Log.d("VoiceService", "ASR success: '${result.text}' conf=${result.confidence}")
                        handleTranscript(result.text)
                        stopCapture()
                    }
                    is RecognitionResult.Error -> {
                        Log.w("VoiceService", "ASR error: ${result.error}")
                        // Surface a user-friendly message via broadcast before stopping
                        val msg = when (val e = result.error) {
                            is com.voiceexpense.ai.speech.RecognitionError.Unavailable ->
                                "On-device speech recognition unavailable. Install 'Speech Services by Google' and download offline English."
                            is com.voiceexpense.ai.speech.RecognitionError.NoPermission ->
                                "Microphone permission not granted"
                            is com.voiceexpense.ai.speech.RecognitionError.Timeout ->
                                "Didn't catch that — please try again"
                            is com.voiceexpense.ai.speech.RecognitionError.Api -> when (e.code) {
                                // LANGUAGE_NOT_SUPPORTED (12) or LANGUAGE_UNAVAILABLE (13)
                                12, 13 -> "Offline English (US) model not installed. Open Google app > Settings > Voice > Offline speech recognition and download English (US)."
                                8 -> "Speech recognizer busy — please try again"
                                3 -> "Audio error from recognizer — please try again"
                                else -> "Speech recognition error (${e.code})"
                            }
                            else -> "Speech recognition error"
                        }
                        sendBroadcast(
                            Intent(ACTION_LISTENING_COMPLETE)
                                .setPackage(packageName)
                                .putExtra(EXTRA_ERROR_MESSAGE, msg)
                        )
                        stopCapture()
                    }
                    RecognitionResult.Complete -> {
                        Log.d("VoiceService", "ASR complete")
                        stopCapture()
                    }
                    else -> { /* Listening/Partial ignored here */ }
                }
            }
            timeoutJob.cancel()
        }
    }

    private suspend fun handleTranscript(text: String) {
        val parsed = parser.parse(text, ParsingContext(defaultDate = LocalDate.now()))
        val txn = Transaction(
            userLocalDate = parsed.userLocalDate,
            amountUsd = parsed.amountUsd ?: BigDecimal("0.00"),
            merchant = parsed.merchant.ifBlank { "Unknown" },
            description = parsed.description,
            type = when (parsed.type) {
                "Income" -> TransactionType.Income
                "Transfer" -> TransactionType.Transfer
                else -> TransactionType.Expense
            },
            expenseCategory = parsed.expenseCategory,
            incomeCategory = parsed.incomeCategory,
            tags = parsed.tags,
            account = parsed.account,
            splitOverallChargedUsd = parsed.splitOverallChargedUsd,
            note = parsed.note,
            confidence = parsed.confidence,
            status = TransactionStatus.DRAFT
        )
        repo.saveDraft(txn)
        // Notify listeners (optional)
        sendBroadcast(
            Intent(ACTION_DRAFT_READY)
                .setPackage(packageName)
                .putExtra(EXTRA_TRANSACTION_ID, txn.id)
        )
        // Debounce duplicate launches for the same id
        val now = System.currentTimeMillis()
        val shouldLaunch = lastLaunchedId != txn.id || (now - lastLaunchAt) > 2000
        if (shouldLaunch) {
            val confirmIntent = Intent(applicationContext, com.voiceexpense.ui.confirmation.TransactionConfirmationActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_TRANSACTION_ID, txn.id)
            startActivity(confirmIntent)
            lastLaunchedId = txn.id
            lastLaunchAt = now
        }
    }

    private fun stopCapture() {
        job?.cancel()
        Log.d("VoiceService", "stopCapture: stopping service and notifying UI")
        // Inform UI to dismiss any overlay
        sendBroadcast(Intent(ACTION_LISTENING_COMPLETE).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Voice Recording", NotificationManager.IMPORTANCE_LOW)
        mgr.createNotificationChannel(channel)
    }

    private fun notification(text: String): Notification {
        val stopIntent = Intent(this, VoiceRecordingService::class.java).apply { action = ACTION_STOP }
        val stopPending = android.app.PendingIntent.getService(
            this, 0, stopIntent, android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Expense")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(0, "Stop", stopPending)
            .build()
    }
}
