package com.voiceexpense.ui.common

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.voiceexpense.R
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.service.voice.VoiceRecordingService
import com.voiceexpense.ui.confirmation.TransactionConfirmationActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_get_started).setOnClickListener {
            startActivity(Intent(this, com.voiceexpense.ui.setup.SetupGuidePage::class.java))
        }
        findViewById<Button>(R.id.btn_open_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val list = findViewById<RecyclerView>(R.id.list_recent)
        val adapter = RecentTransactionsAdapter(onClick = { t -> onTransactionClick(t) })
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        lifecycleScope.launch {
            vm.recent.collectLatest { items ->
                adapter.submitList(items)
            }
        }
    }

    private fun onTransactionClick(t: Transaction) {
        when (t.status) {
            TransactionStatus.DRAFT -> {
                val intent = Intent(this, TransactionConfirmationActivity::class.java)
                    .putExtra(VoiceRecordingService.EXTRA_TRANSACTION_ID, t.id)
                startActivity(intent)
            }
            TransactionStatus.CONFIRMED, TransactionStatus.POSTED, TransactionStatus.QUEUED, TransactionStatus.FAILED -> {
                val intent = Intent(this, TransactionDetailsActivity::class.java)
                    .putExtra(TransactionDetailsActivity.EXTRA_ID, t.id)
                startActivity(intent)
            }
        }
    }
}
