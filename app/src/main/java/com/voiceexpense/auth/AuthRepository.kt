package com.voiceexpense.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
    fun clear()
}

class EncryptedPrefsStore(context: Context) : KeyValueStore {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "auth_store",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
    override fun clear() { prefs.edit().clear().apply() }
}

class InMemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
    override fun clear() { map.clear() }
}

class AuthRepository(
    private val store: KeyValueStore
) {
    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_ACCOUNT_NAME = "account_name"
        private const val KEY_ACCOUNT_EMAIL = "account_email"
    }

    suspend fun getAccessToken(): String {
        return store.getString(KEY_ACCESS_TOKEN) ?: ""
    }

    suspend fun setAccessToken(token: String) {
        store.putString(KEY_ACCESS_TOKEN, token)
    }

    suspend fun setAccount(accountName: String?, email: String?) {
        if (accountName != null) store.putString(KEY_ACCOUNT_NAME, accountName) else store.remove(KEY_ACCOUNT_NAME)
        if (email != null) store.putString(KEY_ACCOUNT_EMAIL, email) else store.remove(KEY_ACCOUNT_EMAIL)
    }

    suspend fun getAccountEmail(): String? = store.getString(KEY_ACCOUNT_EMAIL)

    suspend fun isSignedIn(): Boolean = store.getString(KEY_ACCOUNT_EMAIL)?.isNotBlank() == true

    suspend fun signOut() {
        store.remove(KEY_ACCESS_TOKEN)
        store.remove(KEY_ACCOUNT_NAME)
        store.remove(KEY_ACCOUNT_EMAIL)
    }
}
