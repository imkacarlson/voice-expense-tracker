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

    @Test
    fun accountPersistence_andIsSignedIn() = runBlocking {
        val repo = AuthRepository(InMemoryStore())
        assertThat(repo.isSignedIn()).isFalse()
        repo.setAccount(accountName = "user@gmail.com", email = "user@gmail.com")
        assertThat(repo.isSignedIn()).isTrue()
        assertThat(repo.getAccountEmail()).isEqualTo("user@gmail.com")
        repo.signOut()
        assertThat(repo.isSignedIn()).isFalse()
        assertThat(repo.getAccountEmail()).isNull()
    }
}
