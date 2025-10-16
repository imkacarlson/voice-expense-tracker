package com.voiceexpense.eval

import com.voiceexpense.ai.parsing.hybrid.GenAiGateway
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * GenAiGateway implementation that defers real AI calls to the Python orchestrator.
 *
 * On the first CLI invocation (without injected model responses) each structured()
 * call records the focused prompt that the parser wants to ask and returns a
 * failure Result tagged with [NeedsAiException]. The CLI can inspect the collected
 * prompts and surface a `needs_ai` status back to Python. Once Python provides
 * responses via [injectResponses], the next CLI invocation will reuse the same
 * gateway instance and structured() will return the supplied response strings.
 */
class PythonGenAiGateway : GenAiGateway {

    class NeedsAiException(val fieldKey: String?, message: String) : RuntimeException(message)

    private val promptPattern = Pattern.compile("key \"(?<jsonKey>[a-zA-Z0-9_]+)\"")
    private val prompts = LinkedHashMap<String, String>()
    private val responses = ConcurrentHashMap<String, String>()

    override fun isAvailable(): Boolean = true

    override suspend fun structured(prompt: String): Result<String> {
        val key = extractFieldKey(prompt)
        val response = key?.let { responses[it] }
        return if (response != null) {
            Result.success(response)
        } else {
            if (key != null && !prompts.containsKey(key)) {
                prompts[key] = prompt
            }
            Result.failure(NeedsAiException(key, "AI response required for ${key ?: "unknown"}"))
        }
    }

    fun injectResponses(modelResponses: Map<String, String>) {
        responses.clear()
        responses.putAll(modelResponses)
        prompts.clear()
    }

    fun consumePrompts(): Map<String, String> {
        val snapshot = prompts.toMap()
        prompts.clear()
        return snapshot
    }

    private fun extractFieldKey(prompt: String): String? {
        val matcher = promptPattern.matcher(prompt)
        if (matcher.find()) {
            return matcher.group("jsonKey")
        }
        return null
    }
}
