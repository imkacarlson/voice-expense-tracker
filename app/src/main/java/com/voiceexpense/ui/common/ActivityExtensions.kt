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

    ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        // Only add top padding for the status bar, preserve other padding from XML
        view.updatePadding(top = view.paddingTop + systemBars.top)
        insets
    }
}
