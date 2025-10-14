package com.voiceexpense.auth

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google Identity-backed TokenProvider.
 *
 * Uses GoogleAuthUtil to acquire an OAuth2 access token for the requested scope
 * (expects full scope string, e.g., "https://www.googleapis.com/auth/userinfo.email").
 * Stores/clears the token in AuthRepository for local caching.
 */
class GoogleIdentityTokenProvider(
    private val appContext: Context,
    private val authRepository: AuthRepository
) : TokenProvider {

    override suspend fun getAccessToken(accountEmail: String, scope: String): String = withContext(Dispatchers.IO) {
        // Build OAuth2 scope string expected by GoogleAuthUtil
        val oauth2Scope = if (scope.startsWith("oauth2:")) scope else "oauth2: $scope"

        // GoogleAuthUtil handles caching and automatic token refresh internally.
        // It uses refresh tokens (stored securely by Google Play Services) to
        // get fresh access tokens when needed, without requiring re-authentication.
        require(accountEmail.isNotBlank()) { "Account email required for token acquisition" }
        val account = Account(accountEmail, "com.google")
        val token = GoogleAuthUtil.getToken(appContext, account, oauth2Scope)

        // Store token in AuthRepository for potential debugging/local reference,
        // but don't rely on this cache for subsequent requests
        authRepository.setAccessToken(token)
        token
    }

    override suspend fun invalidateToken(accountEmail: String, scope: String) = withContext(Dispatchers.IO) {
        val token = authRepository.getAccessToken()
        if (!token.isNullOrEmpty()) {
            runCatching { GoogleAuthUtil.clearToken(appContext, token) }
        }
        authRepository.setAccessToken("")
    }
}
