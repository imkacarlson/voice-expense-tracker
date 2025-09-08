package com.voiceexpense.ui.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.widget.Toast
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
            if (granted) {
                if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                    Toast.makeText(this, "On-device speech recognition unavailable. Install 'Speech Services by Google' and download offline English.", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    startServiceAndFinish()
                }
            } else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No UI; just permission gate -> start service
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Quick availability check to avoid a no-op without on-device speech services
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Toast.makeText(this, "On-device speech recognition unavailable. Install 'Speech Services by Google' and download offline English.", Toast.LENGTH_LONG).show()
                finish()
                return
            }
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
        // Show overlay while listening so user can stop
        startActivity(Intent(this, ListeningActivity::class.java))
        finish()
    }
}
