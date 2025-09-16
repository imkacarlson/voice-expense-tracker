package com.voiceexpense.ai.parsing.hybrid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FewShotExampleRepositoryTest {
    @Test
    fun categories_have_examples() {
        assertThat(FewShotExampleRepository.defaultExpense()).isNotNull()
        assertThat(FewShotExampleRepository.subscriptionExpense()).isNotNull()
        assertThat(FewShotExampleRepository.splitExpense()).isNotNull()
        assertThat(FewShotExampleRepository.income()).isNotNull()
        assertThat(FewShotExampleRepository.transfer()).isNotNull()
    }

    @Test
    fun all_contains_diverse_examples() {
        val all = FewShotExampleRepository.all()
        val inputs = all.map { it.input.lowercase() }
        assertThat(inputs.any { it.contains("transfer") }).isTrue()
        assertThat(inputs.any { it.contains("split") || it.contains("overall") }).isTrue()
        assertThat(inputs.any { it.contains("paycheck") || it.contains("deposit") }).isTrue()
    }
}
