package com.voiceexpense.ui.common

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.voiceexpense.R
import com.voiceexpense.data.local.TransactionDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class TransactionDetailsActivity : AppCompatActivity() {
    @Inject lateinit var dao: TransactionDao

    override fun onCreate(savedInstanceState: Bundle?) {
        setupEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_details)

        val id = intent.getStringExtra(EXTRA_ID) ?: run { finish(); return }
        val title: TextView = findViewById(R.id.detail_title)
        val body: TextView = findViewById(R.id.detail_body)

        lifecycleScope.launch(Dispatchers.IO) {
            val t = dao.getById(id)
            withContext(Dispatchers.Main) {
                if (t == null) { finish(); return@withContext }
                title.text = t.merchant
                val sb = StringBuilder()
                sb.appendLine("Amount: ${t.amountUsd?.toPlainString() ?: "—"}")
                sb.appendLine("Date: ${t.userLocalDate}")
                sb.appendLine("Type: ${t.type}")
                sb.appendLine("Status: ${t.status}")
                t.sheetRef?.let { sb.appendLine("Row: ${it.rowIndex ?: "—"}") }
                t.description?.let { sb.appendLine("Description: $it") }
                if (t.tags.isNotEmpty()) sb.appendLine("Tags: ${t.tags.joinToString(", ")}")
                t.account?.let { sb.appendLine("Account: $it") }
                body.text = sb.toString()
            }
        }
    }

    companion object {
        const val EXTRA_ID = "id"
    }
}

