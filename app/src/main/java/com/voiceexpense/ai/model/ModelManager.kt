package com.voiceexpense.ai.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ModelManager {
    private var loadJob: Job? = null
    private var isLoaded: Boolean = false
    private val scope = CoroutineScope(Dispatchers.Default)

    fun ensureLoaded() {
        if (isLoaded) return
        loadJob?.cancel()
        loadJob = scope.launch {
            // Simulate small warmup; real impl would prepare on-device models
            delay(50)
            isLoaded = true
        }
    }

    fun unload() {
        isLoaded = false
    }
}

