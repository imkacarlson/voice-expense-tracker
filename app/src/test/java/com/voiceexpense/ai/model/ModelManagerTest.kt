package com.voiceexpense.ai.model

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ModelManagerTest {
    @Test
    fun ensureModelAvailableTransitionsToReady() = runBlocking {
        val mm = ModelManager()
        val status = mm.ensureModelAvailable()
        assertThat(status).isInstanceOf(ModelManager.ModelStatus.Downloading::class.java)
        // Allow simulated preparation to complete
        delay(250)
        assertThat(mm.isModelReady()).isTrue()
    }
}

