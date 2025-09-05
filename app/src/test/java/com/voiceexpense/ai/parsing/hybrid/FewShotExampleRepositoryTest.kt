package com.voiceexpense.ai.parsing.hybrid

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FewShotExampleRepositoryTest {
    @Test
    fun categories_have_examples() {
        assertThat(FewShotExampleRepository.EXPENSE).isNotEmpty()
        assertThat(FewShotExampleRepository.INCOME).isNotEmpty()
        assertThat(FewShotExampleRepository.TRANSFER).isNotEmpty()
        assertThat(FewShotExampleRepository.SPLIT).isNotEmpty()
    }

    @Test
    fun all_contains_diverse_examples() {
        val all = FewShotExampleRepository.all()
        assertThat(all.any { it.contains("transfer", ignoreCase = true) }).isTrue()
        assertThat(all.any { it.contains("overall", ignoreCase = true) || it.contains("my share", ignoreCase = true) }).isTrue()
        assertThat(all.any { it.contains("paycheck", ignoreCase = true) || it.contains("refund", ignoreCase = true) }).isTrue()
    }
}

