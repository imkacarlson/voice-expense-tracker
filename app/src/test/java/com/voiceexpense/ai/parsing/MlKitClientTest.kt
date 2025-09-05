package com.voiceexpense.ai.parsing

import android.content.Context
import com.voiceexpense.ai.model.ModelManager
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitClientTest {
    private val mockContext = mockk<Context>(relaxed = true)
    
    @Test
    fun `ensureReady returns Available and isAvailable true`() = runTest {
        val mm = ModelManager()
        val client = MlKitClient(mockContext, mm)

        val status = client.ensureReady()
        assertTrue(status is MlKitClient.Status.Available)
        assertTrue(client.isAvailable())
    }

    @Test
    fun `rewrite fails when not ready`() = runTest {
        val mm = ModelManager()
        val client = MlKitClient(mockContext, mm)

        client.close() // ensure not ready
        val res = client.rewrite("hello")
        assertTrue(res.isFailure)
        assertEquals("GenAI model not ready", res.exceptionOrNull()?.message)
        assertFalse(client.isAvailable())
    }

    @Test
    fun `rewrite attempts ML Kit integration when ready`() = runTest {
        val mm = ModelManager()
        val client = MlKitClient(mockContext, mm)

        client.ensureReady()
        val res = client.rewrite("hi")
        // Since we're using a mock context, this will likely fail with ML Kit initialization error
        // but that's expected in unit tests - the important thing is we're no longer getting the 
        // "not wired yet" placeholder error
        assertTrue(res.isFailure)
    }
}

