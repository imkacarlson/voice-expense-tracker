package com.voiceexpense.ai.parsing.hybrid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ValidationPipelineTest {
    @Test
    fun accepts_valid_json_and_normalizes_fences() {
        val raw = """
            ```json
            {"amountUsd":12.5,"merchant":"Starbucks","type":"Expense","tags":["coffee"],"userLocalDate":"2025-01-01","confidence":0.8}
            ```
        """.trimIndent()
        val out = ValidationPipeline.validateRawResponse(raw)
        assertThat(out.valid).isTrue()
        assertThat(out.normalizedJson).isNotNull()
        assertThat(out.errors).isEmpty()
    }

    @Test
    fun rejects_invalid_tags_type() {
        val raw = "{" +
            "\"amountUsd\":10," +
            "\"merchant\":\"Cafe\"," +
            "\"type\":\"Expense\"," +
            "\"tags\":\"coffee\"," + // invalid: not array
            "\"userLocalDate\":\"2025-01-01\"}" 
        val out = ValidationPipeline.validateRawResponse(raw)
        assertThat(out.valid).isFalse()
        assertThat(out.errors.joinToString()).contains("tags")
    }

    @Test
    fun enforces_split_rule_share_le_overall() {
        val raw = "{" +
            "\"amountUsd\":30," +
            "\"splitOverallChargedUsd\":20," +
            "\"merchant\":\"Dinner\"," +
            "\"type\":\"Expense\"," +
            "\"userLocalDate\":\"2025-01-01\"}"
        val out = ValidationPipeline.validateRawResponse(raw)
        assertThat(out.valid).isFalse()
        assertThat(out.errors.joinToString()).contains("share exceeds overall")
    }
}

