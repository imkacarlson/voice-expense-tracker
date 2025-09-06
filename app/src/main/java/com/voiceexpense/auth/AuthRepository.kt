package com.voiceexpense.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/**
 * Lazily initializes EncryptedSharedPreferences on first use.
 * Heavy keystore/disk work runs on the calling thread, so pair usage
 * with Dispatchers.IO in repository methods to avoid main-thread stalls.
 */
class LazyEncryptedPrefsStore(private val context: Context) : KeyValueStore {
    @Volatile private var prefs: SharedPreferences? = null

    private fun ensure() {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    prefs = EncryptedSharedPreferences.create(
                        context,
                        "auth_store",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                }
            }
        }
    }

    override fun getString(key: String): String? {
        ensure()
        return prefs!!.getString(key, null)
    }

    override fun putString(key: String, value: String) {
        ensure()
        prefs!!.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        ensure()
        prefs!!.edit().remove(key).apply()
    }

    override fun clear() {
        ensure()
        prefs!!.edit().clear().apply()
    }
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

    suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        store.getString(KEY_ACCESS_TOKEN) ?: ""
    }

    suspend fun setAccessToken(token: String) = withContext(Dispatchers.IO) {
        store.putString(KEY_ACCESS_TOKEN, token)
    }

    suspend fun setAccount(accountName: String?, email: String?) = withContext(Dispatchers.IO) {
        if (accountName != null) store.putString(KEY_ACCOUNT_NAME, accountName) else store.remove(KEY_ACCOUNT_NAME)
        if (email != null) store.putString(KEY_ACCOUNT_EMAIL, email) else store.remove(KEY_ACCOUNT_EMAIL)
    }

    suspend fun getAccountEmail(): String? = withContext(Dispatchers.IO) { store.getString(KEY_ACCOUNT_EMAIL) }

    suspend fun isSignedIn(): Boolean = withContext(Dispatchers.IO) { store.getString(KEY_ACCOUNT_EMAIL)?.isNotBlank() == true }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        store.remove(KEY_ACCESS_TOKEN)
        store.remove(KEY_ACCOUNT_NAME)
        store.remove(KEY_ACCOUNT_EMAIL)
    }
}
