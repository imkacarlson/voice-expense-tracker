package com.voiceexpense.ai.parsing.hybrid

/** Minimal abstraction over GenAI client for testability. */
interface GenAiGateway {
    fun isAvailable(): Boolean
    suspend fun structured(prompt: String): Result<String>
}

