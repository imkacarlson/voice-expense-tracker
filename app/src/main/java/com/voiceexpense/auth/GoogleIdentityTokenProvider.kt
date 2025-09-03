package com.voiceexpense.auth

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google Identity-backed TokenProvider skeleton.
 * NOTE: For now this delegates to AuthRepository's stored token. The production
 * implementation should integrate Google Identity/Play Services to fetch a fresh
 * OAuth2 token for the Sheets scope and cache it securely.
 */
class GoogleIdentityTokenProvider(
    private val appContext: Context,
    private val authRepository: AuthRepository
) : TokenProvider {

    override suspend fun getAccessToken(accountEmail: String, scope: String): String = withContext(Dispatchers.IO) {
        // Placeholder: read whatever token is stored. In a full implementation,
        // use Google Identity to request a token for [scope] and persist it.
        authRepository.getAccessToken()
    }

    override suspend fun invalidateToken(accountEmail: String, scope: String) {
        // Clear stored token; next getAccessToken() should acquire a fresh one
        authRepository.setAccessToken("")
    }
}

