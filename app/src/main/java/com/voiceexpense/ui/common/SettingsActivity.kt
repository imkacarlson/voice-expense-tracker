package com.voiceexpense.ui.common

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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

        val prefs = getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
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

        spreadsheet.setText(prefs.getString(SettingsKeys.SPREADSHEET_ID, ""))
        sheet.setText(prefs.getString(SettingsKeys.SHEET_NAME, "Sheet1"))
        accounts.setText(prefs.getString(SettingsKeys.KNOWN_ACCOUNTS, ""))

        fun updateGatingMessage() {
            val hasSheetConfig = !prefs.getString(SettingsKeys.SPREADSHEET_ID, "").isNullOrBlank() &&
                    !prefs.getString(SettingsKeys.SHEET_NAME, "").isNullOrBlank()
            val acct = GoogleSignIn.getLastSignedInAccount(this)
            gating.text = when {
                !hasSheetConfig -> getString(R.string.sync_gating_message_default)
                acct == null -> getString(R.string.sync_gating_message_need_sign_in)
                else -> getString(R.string.sync_gating_message_ready, prefs.getString(SettingsKeys.SHEET_NAME, "") ?: "")
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

        val existing = GoogleSignIn.getLastSignedInAccount(this)
        updateAuthStatus(existing)

        val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.result
            updateAuthStatus(account)
            // Persist account and optionally warm token in background
            lifecycleScope.launch {
                authRepository.setAccount(accountName = account?.displayName, email = account?.email)
                account?.email?.let { email ->
                    // Warm token for Sheets scope (provider handles actual fetching/caching)
                    runCatching {
                        tokenProvider.getAccessToken(email, "https://www.googleapis.com/auth/spreadsheets")
                    }
                }
                updateGatingMessage()
            }
        }

        signIn.setOnClickListener {
            signInLauncher.launch(signInClient.signInIntent)
        }
        signOut.setOnClickListener {
            signInClient.signOut().addOnCompleteListener {
                lifecycleScope.launch {
                    // Clear stored credentials and invalidate any cached token
                    authRepository.signOut()
                    existing?.email?.let { email ->
                        runCatching { tokenProvider.invalidateToken(email, "https://www.googleapis.com/auth/spreadsheets") }
                    }
                    updateAuthStatus(null)
                    android.widget.Toast.makeText(this@SettingsActivity, R.string.info_sign_in_required, android.widget.Toast.LENGTH_SHORT).show()
                    updateGatingMessage()
                }
            }
        }

        updateGatingMessage()

        // Probe AI status lazily
        lifecycleScope.launch {
            when (val s = modelManager.ensureModelAvailable(applicationContext)) {
                is ModelManager.ModelStatus.Ready -> aiStatus.text = getString(R.string.setup_guide_status_ready)
                is ModelManager.ModelStatus.Unavailable -> aiStatus.text = getString(R.string.setup_guide_status_unavailable, s.reason)
                is ModelManager.ModelStatus.Error -> aiStatus.text = getString(R.string.setup_guide_status_error, s.throwable.message ?: "unknown")
            }
        }
    }
}
