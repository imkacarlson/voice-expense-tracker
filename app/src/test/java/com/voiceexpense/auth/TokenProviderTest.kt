package com.voiceexpense.auth

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class TokenProviderTest {
    @Test
    fun staticProvider_invalidate_noopGetStillReturnsLatest() = runBlocking {
        val p = StaticTokenProvider("t1")
        assertThat(p.getAccessToken("user@example.com", "scope")).isEqualTo("t1")
        p.setToken("t2")
        p.invalidateToken("user@example.com", "scope")
        assertThat(p.getAccessToken("user@example.com", "scope")).isEqualTo("t2")
    }
}

