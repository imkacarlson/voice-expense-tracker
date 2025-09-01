package com.voiceexpense.ui.common

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.voiceexpense.R

object SettingsKeys {
    const val PREFS = "settings"
    const val SPREADSHEET_ID = "spreadsheet_id"
    const val SHEET_NAME = "sheet_name"
    const val KNOWN_ACCOUNTS = "known_accounts" // comma-separated labels
}

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences(SettingsKeys.PREFS, Context.MODE_PRIVATE)
        val spreadsheet: EditText = findViewById(R.id.input_spreadsheet)
        val sheet: EditText = findViewById(R.id.input_sheet)
        val accounts: EditText = findViewById(R.id.input_accounts)
        val save: Button = findViewById(R.id.btn_save)

        spreadsheet.setText(prefs.getString(SettingsKeys.SPREADSHEET_ID, ""))
        sheet.setText(prefs.getString(SettingsKeys.SHEET_NAME, "Sheet1"))
        accounts.setText(prefs.getString(SettingsKeys.KNOWN_ACCOUNTS, ""))

        save.setOnClickListener {
            prefs.edit()
                .putString(SettingsKeys.SPREADSHEET_ID, spreadsheet.text.toString())
                .putString(SettingsKeys.SHEET_NAME, sheet.text.toString())
                .putString(SettingsKeys.KNOWN_ACCOUNTS, accounts.text.toString())
                .apply()
            finish()
        }
    }
}

