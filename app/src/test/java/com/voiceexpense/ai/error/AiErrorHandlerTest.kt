package com.voiceexpense.ai.error

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.model.ModelManager
import org.junit.Test

class AiErrorHandlerTest {
    @Test
    fun model_unavailable_quota_exceeded_maps_retryable() {
        val action = AiErrorHandler.fromModelStatus(ModelManager.ModelStatus.Unavailable("quota exceeded"))
        assertThat(action.message.lowercase()).contains("quota exceeded")
        assertThat(action.canRetry).isTrue()
        assertThat(action.retryDelayMs).isAtLeast(1000)
    }

    @Test
    fun model_unavailable_service_unavailable_maps_retryable() {
        val action = AiErrorHandler.fromModelStatus(ModelManager.ModelStatus.Unavailable("service unavailable"))
        assertThat(action.message.lowercase()).contains("service unavailable")
        assertThat(action.canRetry).isTrue()
    }
}

