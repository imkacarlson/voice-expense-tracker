package com.voiceexpense.ai.parsing.hybrid

import com.google.common.truth.Truth.assertThat
import com.voiceexpense.ai.parsing.ParsingContext
import org.junit.Test

class PromptBuilderTest {
    private val builder = PromptBuilder()

    @Test
    fun includes_system_and_constraints() {
        val p = builder.build("coffee 4.75 at starbucks")
        assertThat(p).contains("Additional constraints:")
        assertThat(p.lowercase()).contains("return only json")
        assertThat(p).contains("Examples:")
        assertThat(p).contains("Input: ")
    }

    @Test
    fun selects_transfer_examples_for_transfer_input() {
        val p = builder.build("transfer 100 from checking to savings")
        val examplesBlock = p.substringAfter("Examples:")
        assertThat(examplesBlock.lowercase()).contains("transfer")
    }

    @Test
    fun selects_split_examples_for_split_like_input() {
        val p = builder.build("dinner 60, my share 20, overall charged 60")
        val examplesBlock = p.substringAfter("Examples:")
        assertThat(examplesBlock.lowercase()).contains("overall")
    }

    @Test
    fun includes_context_hints_when_available() {
        val ctx = ParsingContext(
            recentMerchants = listOf("Starbucks", "Chipotle"),
            knownAccounts = listOf("Checking", "Savings")
        )
        val p = builder.build("coffee 4.75", ctx)
        assertThat(p).contains("recentMerchants:")
        assertThat(p).contains("knownAccounts:")
    }
}

