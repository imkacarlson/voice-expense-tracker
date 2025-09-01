package com.voiceexpense.auth

/**
 * Abstraction to fetch/refresh OAuth access tokens for Google APIs (Sheets scope).
 * Production implementation will use Google Play Services / Identity APIs.
 */
interface TokenProvider {
    /** Returns a valid access token for the given account email and OAuth scope. */
    suspend fun getAccessToken(accountEmail: String, scope: String): String

    /** Invalidates any cached token for the given account and scope (force refresh on next call). */
    suspend fun invalidateToken(accountEmail: String, scope: String)
}

/** Simple test/dummy implementation that always returns a preset token. */
class StaticTokenProvider(private var token: String = "") : TokenProvider {
    override suspend fun getAccessToken(accountEmail: String, scope: String): String = token
    override suspend fun invalidateToken(accountEmail: String, scope: String) { /* no-op */ }
    fun setToken(value: String) { token = value }
}

