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
import com.voiceexpense.data.config.ConfigOption
import com.voiceexpense.data.config.ConfigRepository
import com.voiceexpense.data.config.ConfigType
import com.voiceexpense.data.config.DefaultField
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import java.util.UUID

object SettingsKeys {
    const val PREFS = "settings"
    const val WEB_APP_URL = "web_app_url"
    const val BACKUP_AUTH_TOKEN = "backup_auth_token"
    const val KNOWN_ACCOUNTS = "known_accounts" // comma-separated labels
    const val DEBUG_LOGS = "debug_logs" // developer toggle for verbose local logs
    const val ASR_ONLINE_FALLBACK = "asr_online_fallback" // allow online ASR when offline model missing (dev aid)
}

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var tokenProvider: TokenProvider
    @Inject lateinit var configRepository: ConfigRepository
    private val emailScope = Scope("https://www.googleapis.com/auth/userinfo.email")

    // No special request codes required; Google Sign-In handled via ActivityResult API.
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var gatingView: android.widget.TextView
    private fun prefsOrInit(): android.content.SharedPreferences =
        if (::prefs.isInitialized) prefs else getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE).also { prefs = it }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        // Back/up navigation on toolbar
        findViewById<MaterialToolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Views
        val webUrl: EditText = findViewById(R.id.input_web_url)
        val backup: EditText = findViewById(R.id.input_backup_token)
        val accounts: EditText = findViewById(R.id.input_accounts)
        val save: Button = findViewById(R.id.btn_save)
        val signIn: Button = findViewById(R.id.btn_sign_in)
        val signOut: Button = findViewById(R.id.btn_sign_out)
        val authStatus: android.widget.TextView = findViewById(R.id.text_auth_status)
        gatingView = findViewById(R.id.text_sync_gating)
        val aiStatus: android.widget.TextView = findViewById(R.id.text_ai_status)
        val openSetup: Button = findViewById(R.id.btn_open_setup_guide)
        val modelManager = ModelManager()
        val asrFallback: androidx.appcompat.widget.SwitchCompat = findViewById(R.id.switch_asr_online_fallback)
        // Dropdown configuration views
        val typeSpinner: android.widget.Spinner = findViewById(R.id.spinner_option_type)
        val listView: android.widget.ListView = findViewById(R.id.list_options)
        val inputNew: EditText = findViewById(R.id.input_new_option)
        val addBtn: Button = findViewById(R.id.btn_add_option)
        val delBtn: Button = findViewById(R.id.btn_delete_option)
        val upBtn: Button = findViewById(R.id.btn_move_up)
        val downBtn: Button = findViewById(R.id.btn_move_down)
        val defaultSpinner: android.widget.Spinner = findViewById(R.id.spinner_default_option)
        val setDefaultBtn: Button = findViewById(R.id.btn_set_default)

        fun updateAuthStatus(account: GoogleSignInAccount?) {
            if (account != null) {
                authStatus.text = getString(R.string.auth_status_signed_in, account.email ?: account.displayName ?: "")
            } else {
                authStatus.text = getString(R.string.auth_status_signed_out)
            }
        }

        // Preload prefs and account off main thread, then update UI
        lifecycleScope.launch(Dispatchers.IO) {
            // Touch prefs on IO thread to avoid StrictMode disk read on main
            val p = prefsOrInit()
            var url = p.getString(SettingsKeys.WEB_APP_URL, "")
            if (url.isNullOrBlank()) {
                // Pre-fill default Web App URL for convenience
                val def = getString(R.string.default_web_app_url)
                if (def.isNotBlank()) {
                    p.edit().putString(SettingsKeys.WEB_APP_URL, def).apply()
                    url = def
                }
            }
            val bt = p.getString(SettingsKeys.BACKUP_AUTH_TOKEN, "")
            val ka = p.getString(SettingsKeys.KNOWN_ACCOUNTS, "")
            val asrOnline = p.getBoolean(SettingsKeys.ASR_ONLINE_FALLBACK, (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            val existing = GoogleSignIn.getLastSignedInAccount(this@SettingsActivity)
            val hasConfig = !url.isNullOrBlank()
            withContext(Dispatchers.Main) {
                webUrl.setText(url)
                backup.setText(bt)
                accounts.setText(ka)
                asrFallback.isChecked = asrOnline
                // Populate type spinner and list/default adapters
                val types = ConfigType.values()
                val typeAdapter = android.widget.ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, types)
                typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                typeSpinner.adapter = typeAdapter
                typeSpinner.setSelection(0)
                val listAdapter = android.widget.ArrayAdapter<String>(this@SettingsActivity, android.R.layout.simple_list_item_activated_1, mutableListOf())
                listView.adapter = listAdapter
                listView.choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE
                fun observeType(sel: ConfigType) {
                    lifecycleScope.launch {
                        configRepository.options(sel).collectLatest { opts ->
                            val sorted = opts.sortedBy { it.position }
                            listAdapter.clear()
                            listAdapter.addAll(sorted.map { it.label })
                            listAdapter.notifyDataSetChanged()
                            val defAdapter = android.widget.ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_item, sorted.map { it.label })
                            defAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            defaultSpinner.adapter = defAdapter
                            val df = mapDefaultField(sel)
                            if (df != null) {
                                lifecycleScope.launch {
                                    configRepository.defaultFor(df).collectLatest { defId ->
                                        val idx = sorted.indexOfFirst { it.id == defId }
                                        if (idx >= 0 && idx < defaultSpinner.count) defaultSpinner.setSelection(idx)
                                    }
                                }
                            }
                        }
                    }
                }
                typeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                        observeType(types[position])
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) { }
                }
                observeType(types[0])
                addBtn.setOnClickListener {
                    val sel = typeSpinner.selectedItem as ConfigType
                    val label = inputNew.text?.toString()?.trim().orEmpty()
                    if (label.isBlank()) {
                        android.widget.Toast.makeText(this@SettingsActivity, "Label required", android.widget.Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    lifecycleScope.launch(Dispatchers.IO) {
                        val list = configRepository.options(sel).first()
                        val nextPos = (list.maxOfOrNull { it.position } ?: -1) + 1
                        val option = ConfigOption(id = UUID.randomUUID().toString(), type = sel, label = label, position = nextPos, active = true)
                        configRepository.upsert(option)
                    }
                    inputNew.setText("")
                }
                delBtn.setOnClickListener {
                    val selType = typeSpinner.selectedItem as ConfigType
                    val pos = listView.checkedItemPosition
                    if (pos == android.widget.AdapterView.INVALID_POSITION) {
                        android.widget.Toast.makeText(this@SettingsActivity, "Select an option to delete", android.widget.Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    lifecycleScope.launch(Dispatchers.IO) {
                        val opts = configRepository.options(selType).first()
                        val sorted = opts.sortedBy { it.position }
                        val target = sorted.getOrNull(pos)
                        target?.let { configRepository.delete(it.id) }
                    }
                }
                fun move(delta: Int) {
                    val selType = typeSpinner.selectedItem as ConfigType
                    val pos = listView.checkedItemPosition
                    if (pos == android.widget.AdapterView.INVALID_POSITION) return
                    lifecycleScope.launch(Dispatchers.IO) {
                        val opts = configRepository.options(selType).first()
                        val sorted = opts.sortedBy { it.position }.toMutableList()
                        val newPos = (pos + delta).coerceIn(0, sorted.lastIndex)
                        if (newPos != pos) {
                            java.util.Collections.swap(sorted, pos, newPos)
                            sorted.forEachIndexed { index, option ->
                                if (option.position != index) {
                                    configRepository.upsert(option.copy(position = index))
                                }
                            }
                        }
                    }
                }
                upBtn.setOnClickListener { move(-1) }
                downBtn.setOnClickListener { move(1) }
                setDefaultBtn.setOnClickListener {
                    val selType = typeSpinner.selectedItem as ConfigType
                    val df = mapDefaultField(selType)
                    if (df == null) {
                        android.widget.Toast.makeText(this@SettingsActivity, "No default for this type", android.widget.Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    lifecycleScope.launch(Dispatchers.IO) {
                        val opts = configRepository.options(selType).first()
                        val sorted = opts.sortedBy { it.position }
                        val idx = defaultSpinner.selectedItemPosition
                        val optionId = sorted.getOrNull(idx)?.id
                        configRepository.setDefault(df, optionId)
                    }
                }
                updateAuthStatus(existing)
                gatingView.text = when {
                    !hasConfig -> getString(R.string.sync_gating_message_default)
                    existing == null -> getString(R.string.sync_gating_message_need_sign_in)
                    else -> getString(R.string.sync_gating_message_ready_web)
                }
            }
        }

        save.setOnClickListener {
            val url = webUrl.text.toString().trim()
            if (url.isEmpty()) {
                android.widget.Toast.makeText(this, R.string.error_missing_web_url, android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefsOrInit().edit()
                .putString(SettingsKeys.WEB_APP_URL, url)
                .putString(SettingsKeys.BACKUP_AUTH_TOKEN, backup.text.toString())
                .putString(SettingsKeys.KNOWN_ACCOUNTS, accounts.text.toString())
                .apply()
            android.widget.Toast.makeText(this, R.string.info_settings_saved, android.widget.Toast.LENGTH_SHORT).show()
            updateGatingMessage()
        }

        openSetup.setOnClickListener {
            startActivity(android.content.Intent(this, com.voiceexpense.ui.setup.SetupGuidePage::class.java))
        }

        // Configure Google Sign-In (email only). We use userinfo.email access tokens for Apps Script auth.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val signInClient: GoogleSignInClient = GoogleSignIn.getClient(this, gso)

        // Auth status is set in the pre-warm block above

        val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                Log.w("SettingsActivity", "Google sign-in result canceled: code=${result.resultCode}")
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
                    // Attempt to warm a token for userinfo.email
                    account?.email?.let { email ->
                        runCatching { tokenProvider.getAccessToken(email, emailScope.scopeUri) }
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
                        runCatching { tokenProvider.invalidateToken(email, "https://www.googleapis.com/auth/userinfo.email") }
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

        // Persist ASR fallback toggle
        asrFallback.setOnCheckedChangeListener { _, isChecked ->
            prefsOrInit().edit().putBoolean(SettingsKeys.ASR_ONLINE_FALLBACK, isChecked).apply()
            val msg = if (isChecked) R.string.asr_fallback_enabled else R.string.asr_fallback_disabled
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
        }

        // Probe AI status lazily (ensureModelAvailable does IO internally, but keep off main)
        lifecycleScope.launch(Dispatchers.Main) {
            when (val s = modelManager.ensureModelAvailable(applicationContext)) {
                is ModelManager.ModelStatus.Ready -> aiStatus.text = getString(R.string.setup_guide_status_ready)
                is ModelManager.ModelStatus.Unavailable -> aiStatus.text = getString(R.string.setup_guide_status_unavailable, s.reason)
                is ModelManager.ModelStatus.Error -> aiStatus.text = getString(R.string.setup_guide_status_error, s.throwable.message ?: "unknown")
            }
        }
    }

    private fun updateGatingMessage() {
        // Compute gating message off main to avoid disk/account reads on UI thread
        lifecycleScope.launch(Dispatchers.IO) {
            val p = prefsOrInit()
            val hasConfig = !p.getString(SettingsKeys.WEB_APP_URL, "").isNullOrBlank()
            val acct = GoogleSignIn.getLastSignedInAccount(this@SettingsActivity)
            val text = when {
                !hasConfig -> getString(R.string.sync_gating_message_default)
                acct == null -> getString(R.string.sync_gating_message_need_sign_in)
                else -> getString(R.string.sync_gating_message_ready_web)
            }
            withContext(Dispatchers.Main) { gatingView.text = text }
        }
    }

    private fun mapDefaultField(type: ConfigType): DefaultField? = when (type) {
        ConfigType.ExpenseCategory -> DefaultField.DefaultExpenseCategory
        ConfigType.IncomeCategory -> DefaultField.DefaultIncomeCategory
        ConfigType.TransferCategory -> DefaultField.DefaultTransferCategory
        ConfigType.Account -> DefaultField.DefaultAccount
        ConfigType.Tag -> null
    }

}
