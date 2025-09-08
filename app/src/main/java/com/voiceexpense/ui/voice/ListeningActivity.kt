package com.voiceexpense.ui.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.voiceexpense.R
import com.voiceexpense.service.voice.VoiceRecordingService

/**
 * Minimal overlay UI indicating the app is actively listening, with a Stop control.
 */
class ListeningActivity : AppCompatActivity() {
    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                VoiceRecordingService.ACTION_LISTENING_COMPLETE,
                VoiceRecordingService.ACTION_DRAFT_READY -> finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen on briefly; this is a transient overlay
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_listening)

        findViewById<TextView>(R.id.text_state)?.text = getString(R.string.listening_now)
        findViewById<Button>(R.id.btn_stop)?.setOnClickListener {
            val stop = Intent(this, VoiceRecordingService::class.java).apply { action = VoiceRecordingService.ACTION_STOP }
            ContextCompat.startForegroundService(this, stop)
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(
            finishReceiver,
            IntentFilter().apply {
                addAction(VoiceRecordingService.ACTION_LISTENING_COMPLETE)
                addAction(VoiceRecordingService.ACTION_DRAFT_READY)
            },
            RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        runCatching { unregisterReceiver(finishReceiver) }
    }
}

