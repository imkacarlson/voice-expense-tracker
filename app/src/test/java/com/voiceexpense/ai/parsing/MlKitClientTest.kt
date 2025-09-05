package com.voiceexpense.ai.parsing

import com.voiceexpense.ai.model.ModelManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MlKitClientTest {
    @Test
    fun `ensureReady returns Available and isAvailable true`() = runTest {
        val mm = ModelManager()
        val client = MlKitClient(mm)

        val status = client.ensureReady()
        assertTrue(status is MlKitClient.Status.Available)
        assertTrue(client.isAvailable())
    }

    @Test
    fun `rewrite fails when not ready`() = runTest {
        val mm = ModelManager()
        val client = MlKitClient(mm)

        client.close() // ensure not ready
        val res = client.rewrite("hello")
        assertTrue(res.isFailure)
        assertEquals("GenAI model not ready", res.exceptionOrNull()?.message)
        assertFalse(client.isAvailable())
    }

    @Test
    fun `rewrite currently unsupported when ready`() = runTest {
        val mm = ModelManager()
        val client = MlKitClient(mm)

        client.ensureReady()
        val res = client.rewrite("hi")
        assertTrue(res.isFailure)
        // Placeholder wiring not implemented yet
        assertEquals("ML Kit GenAI rewriting not wired yet", res.exceptionOrNull()?.message)
    }
}

