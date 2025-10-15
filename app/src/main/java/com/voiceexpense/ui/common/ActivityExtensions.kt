package com.voiceexpense.ui.common

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Configures the activity for edge-to-edge display with proper status bar handling.
 *
 * This extension function:
 * - Enables edge-to-edge mode (required for Android 15+)
 * - Sets status bar icons to dark for visibility on light backgrounds
 * - Automatically switches to light icons in dark mode
 * - Applies window insets to prevent content from being hidden behind system bars
 *
 * Usage: Call this in onCreate() before setContentView()
 */
fun ComponentActivity.setupEdgeToEdge() {
    enableEdgeToEdge()

    // Set status bar icons to dark for light backgrounds
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = true
    }

    // Apply window insets to prevent content from being hidden behind system bars
    window.decorView.post {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}
