package com.voiceexpense.ui.common

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.voiceexpense.auth.AuthRepository
import com.voiceexpense.auth.TokenProvider
import com.voiceexpense.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.voiceexpense.ai.model.ModelManager
import com.google.android.material.appbar.MaterialToolbar

object SettingsKeys {
    const val PREFS = "settings"
    const val SPREADSHEET_ID = "spreadsheet_id"
    const val SHEET_NAME = "sheet_name"
    const val KNOWN_ACCOUNTS = "known_accounts" // comma-separated labels
    const val DEBUG_LOGS = "debug_logs" // developer toggle for verbose local logs
}

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var tokenProvider: TokenProvider
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        // Back/up navigation on toolbar
        findViewById<MaterialToolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Lazy so we can pre-warm on a background thread before main-thread access
        val prefs by lazy { getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE) }
        val spreadsheet: EditText = findViewById(R.id.input_spreadsheet)
        val sheet: EditText = findViewById(R.id.input_sheet)
        val accounts: EditText = findViewById(R.id.input_accounts)
        val save: Button = findViewById(R.id.btn_save)
        val signIn: Button = findViewById(R.id.btn_sign_in)
        val signOut: Button = findViewById(R.id.btn_sign_out)
        val authStatus: android.widget.TextView = findViewById(R.id.text_auth_status)
        val gating: android.widget.TextView = findViewById(R.id.text_sync_gating)
        val aiStatus: android.widget.TextView = findViewById(R.id.text_ai_status)
        val openSetup: Button = findViewById(R.id.btn_open_setup_guide)
        val modelManager = ModelManager()

        // Preload prefs and account off main thread, then update UI
        lifecycleScope.launch(Dispatchers.IO) {
            // Touch prefs on IO thread to avoid StrictMode disk read on main
            val ss = prefs.getString(SettingsKeys.SPREADSHEET_ID, "")
            val sh = prefs.getString(SettingsKeys.SHEET_NAME, "Sheet1")
            val ka = prefs.getString(SettingsKeys.KNOWN_ACCOUNTS, "")
            val existing = GoogleSignIn.getLastSignedInAccount(this@SettingsActivity)
            val hasSheetConfig = !ss.isNullOrBlank() && !sh.isNullOrBlank()
            withContext(Dispatchers.Main) {
                spreadsheet.setText(ss)
                sheet.setText(sh)
                accounts.setText(ka)
                updateAuthStatus(existing)
                gating.text = when {
                    !hasSheetConfig -> getString(R.string.sync_gating_message_default)
                    existing == null -> getString(R.string.sync_gating_message_need_sign_in)
                    else -> getString(R.string.sync_gating_message_ready, sh ?: "")
                }
            }
        }

        fun updateGatingMessage() {
            // Compute gating message off main to avoid disk/account reads on UI thread
            lifecycleScope.launch(Dispatchers.IO) {
                val hasSheetConfig = !prefs.getString(SettingsKeys.SPREADSHEET_ID, "").isNullOrBlank() &&
                        !prefs.getString(SettingsKeys.SHEET_NAME, "").isNullOrBlank()
                val acct = GoogleSignIn.getLastSignedInAccount(this@SettingsActivity)
                val text = when {
                    !hasSheetConfig -> getString(R.string.sync_gating_message_default)
                    acct == null -> getString(R.string.sync_gating_message_need_sign_in)
                    else -> getString(R.string.sync_gating_message_ready, prefs.getString(SettingsKeys.SHEET_NAME, "") ?: "")
                }
                withContext(Dispatchers.Main) { gating.text = text }
            }
        }

        save.setOnClickListener {
            val ss = spreadsheet.text.toString().trim()
            val sh = sheet.text.toString().trim()
            if (ss.isEmpty() || sh.isEmpty()) {
                android.widget.Toast.makeText(this, R.string.error_missing_sheet_config, android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit()
                .putString(SettingsKeys.SPREADSHEET_ID, ss)
                .putString(SettingsKeys.SHEET_NAME, sh)
                .putString(SettingsKeys.KNOWN_ACCOUNTS, accounts.text.toString())
                .apply()
            android.widget.Toast.makeText(this, R.string.info_settings_saved, android.widget.Toast.LENGTH_SHORT).show()
            updateGatingMessage()
        }

        openSetup.setOnClickListener {
            startActivity(android.content.Intent(this, com.voiceexpense.ui.setup.SetupGuidePage::class.java))
        }

        // Configure Google Sign-In for Sheets scope (consent UI only; token handling added later)
        val sheetsScope = Scope("https://www.googleapis.com/auth/spreadsheets")
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(sheetsScope)
            .build()
        val signInClient: GoogleSignInClient = GoogleSignIn.getClient(this, gso)

        fun updateAuthStatus(account: GoogleSignInAccount?) {
            if (account != null) {
                authStatus.text = getString(R.string.auth_status_signed_in, account.email ?: account.displayName ?: "")
            } else {
                authStatus.text = getString(R.string.auth_status_signed_out)
            }
        }

        // Auth status is set in the pre-warm block above

        val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                android.widget.Toast.makeText(this, "Sign-in canceled", android.widget.Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                updateAuthStatus(account)
                // Persist account and optionally warm token in background
                lifecycleScope.launch {
                    authRepository.setAccount(accountName = account?.displayName, email = account?.email)
                    account?.email?.let { email ->
                        runCatching { tokenProvider.getAccessToken(email, "https://www.googleapis.com/auth/spreadsheets") }
                    }
                    updateGatingMessage()
                }
            } catch (e: ApiException) {
                val code = e.statusCode
                Log.w("SettingsActivity", "Google sign-in failed: code=$code", e)
                val msg = if (code == 10) {
                    "Sign-in configuration error (code 10). See setup tips."
                } else {
                    "Sign-in failed: ${e.statusCode}"
                }
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
            }
        }

        signIn.setOnClickListener {
            signInLauncher.launch(signInClient.signInIntent)
        }
        signOut.setOnClickListener {
            signInClient.signOut().addOnCompleteListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    // Clear stored credentials and invalidate any cached token
                    authRepository.signOut()
                    val last = GoogleSignIn.getLastSignedInAccount(this@SettingsActivity)
                    last?.email?.let { email ->
                        runCatching { tokenProvider.invalidateToken(email, "https://www.googleapis.com/auth/spreadsheets") }
                    }
                    withContext(Dispatchers.Main) {
                        updateAuthStatus(null)
                        android.widget.Toast.makeText(this@SettingsActivity, R.string.info_sign_in_required, android.widget.Toast.LENGTH_SHORT).show()
                        updateGatingMessage()
                    }
                }
            }
        }

        updateGatingMessage()

        // Probe AI status lazily (ensureModelAvailable does IO internally, but keep off main)
        lifecycleScope.launch(Dispatchers.Main) {
            when (val s = modelManager.ensureModelAvailable(applicationContext)) {
                is ModelManager.ModelStatus.Ready -> aiStatus.text = getString(R.string.setup_guide_status_ready)
                is ModelManager.ModelStatus.Unavailable -> aiStatus.text = getString(R.string.setup_guide_status_unavailable, s.reason)
                is ModelManager.ModelStatus.Error -> aiStatus.text = getString(R.string.setup_guide_status_error, s.throwable.message ?: "unknown")
            }
        }
    }
}
