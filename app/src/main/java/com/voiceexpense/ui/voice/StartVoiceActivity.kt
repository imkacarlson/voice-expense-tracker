package com.voiceexpense.ui.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.voiceexpense.service.voice.VoiceRecordingService

/**
 * Lightweight trampoline Activity launched from the home-screen widget.
 * Ensures RECORD_AUDIO permission is granted before starting the microphone FGS.
 * Finishes immediately after starting the service or showing a denial.
 */
class StartVoiceActivity : AppCompatActivity() {
    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startServiceAndFinish() else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No UI; just permission gate -> start service
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startServiceAndFinish()
        } else {
            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startServiceAndFinish() {
        val intent = Intent(this, VoiceRecordingService::class.java).apply {
            action = VoiceRecordingService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        finish()
    }
}

