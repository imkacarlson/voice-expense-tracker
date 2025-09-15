package com.voiceexpense.ai.model

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ModelManagerTest {
    @Test
    fun ensureModelUnavailableWithoutContext() = runBlocking {
        val mm = ModelManager()
        val status = mm.ensureModelAvailable(null)
        assertThat(status).isInstanceOf(ModelManager.ModelStatus.Unavailable::class.java)
        assertThat(mm.isModelReady()).isFalse()
    }
}
