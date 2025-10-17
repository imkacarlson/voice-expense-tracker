package com.voiceexpense.ui.common

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Configures the activity for edge-to-edge display with proper status bar handling.
 *
 * This extension function:
 * - Enables edge-to-edge mode (required for Android 15+)
 * - Sets status bar icons to dark for visibility on light backgrounds
 * - Automatically switches to light icons in dark mode
 * - Applies window insets to prevent content from being hidden behind system bars
 * - Preserves existing padding from layout XML files
 *
 * Usage: Call this in onCreate() AFTER setContentView()
 */
fun ComponentActivity.setupEdgeToEdge() {
    enableEdgeToEdge()

    // Set status bar icons to dark for light backgrounds
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = true
    }

    // Apply window insets to the root content view
    // This adds additional top padding for the status bar while preserving existing padding
    val contentView = findViewById<ViewGroup>(android.R.id.content)
    val rootView = contentView.getChildAt(0)

    // Capture the original padding from XML once, before any insets are applied
    // This prevents accumulation when the listener is called multiple times
    val originalPaddingTop = rootView.paddingTop

    ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        // Always add system insets to the ORIGINAL padding, not the current padding
        // This prevents accumulation on keyboard open/close and navigation events
        view.updatePadding(top = originalPaddingTop + systemBars.top)
        insets
    }
}
