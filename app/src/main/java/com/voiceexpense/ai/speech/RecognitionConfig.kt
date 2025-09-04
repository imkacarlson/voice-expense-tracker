package com.voiceexpense.ai.speech

/** Speech recognition configuration and result models. */
data class RecognitionConfig(
    val languageCode: String = "en-US",
    val maxResults: Int = 1,
    val partialResults: Boolean = false,
    val offlineMode: Boolean = true,
    val confidenceThreshold: Float = 0.5f
) {
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (languageCode.isBlank()) errors += "languageCode must not be blank"
        if (maxResults !in 1..5) errors += "maxResults must be between 1 and 5"
        if (confidenceThreshold !in 0f..1f) errors += "confidenceThreshold must be between 0.0 and 1.0"
        return errors
    }
}

sealed class RecognitionResult {
    data class Success(val text: String, val confidence: Float) : RecognitionResult()
    data class Partial(val text: String) : RecognitionResult()
    data class Error(val error: RecognitionError) : RecognitionResult()
    data object Listening : RecognitionResult()
    data object Complete : RecognitionResult()
}

sealed class RecognitionError {
    data class Api(val code: Int, val message: String) : RecognitionError()
    data object Timeout : RecognitionError()
    data object NoPermission : RecognitionError()
    data class Other(val message: String) : RecognitionError()
}

