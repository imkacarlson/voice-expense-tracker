package com.voiceexpense.auth

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AuthRepositoryTest {
    @Test
    fun saveLoadAndSignOut() = runBlocking {
        val repo = AuthRepository(InMemoryStore())
        assertThat(repo.getAccessToken()).isEqualTo("")
        repo.setAccessToken("abc")
        assertThat(repo.getAccessToken()).isEqualTo("abc")
        repo.signOut()
        assertThat(repo.getAccessToken()).isEqualTo("")
    }
}

