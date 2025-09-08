package com.voiceexpense.ui.common

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.voiceexpense.R
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import java.time.format.DateTimeFormatter

class RecentTransactionsAdapter(
    private val onClick: (Transaction) -> Unit
) : ListAdapter<Transaction, RecentTransactionsAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Transaction>() {
            override fun areItemsTheSame(oldItem: Transaction, newItem: Transaction) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Transaction, newItem: Transaction) = oldItem == newItem
        }
        private val DATE = DateTimeFormatter.ofPattern("MM/dd")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return VH(v, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(itemView: View, val onClick: (Transaction) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tx_title)
        private val subtitle: TextView = itemView.findViewById(R.id.tx_subtitle)
        private val amount: TextView = itemView.findViewById(R.id.tx_amount)
        private val status: TextView = itemView.findViewById(R.id.tx_status)

        fun bind(t: Transaction) {
            title.text = t.merchant.ifBlank { itemView.context.getString(R.string.app_name) }
            val dateStr = DATE.format(t.userLocalDate)
            subtitle.text = listOfNotNull(dateStr, t.description).joinToString("  •  ")
            amount.text = t.amountUsd?.toPlainString() ?: "—"

            val (label, bg, fg) = when (t.status) {
                TransactionStatus.DRAFT -> Triple("Draft", R.drawable.bg_chip_gray, android.R.color.black)
                TransactionStatus.QUEUED -> Triple("Queued", R.drawable.bg_chip_amber, android.R.color.black)
                TransactionStatus.CONFIRMED -> Triple("Confirmed", R.drawable.bg_chip_blue, android.R.color.white)
                TransactionStatus.POSTED -> Triple("Posted", R.drawable.bg_chip_green, android.R.color.white)
            }
            status.text = label
            status.background = ContextCompat.getDrawable(itemView.context, bg)
            status.setTextColor(ContextCompat.getColor(itemView.context, fg))

            itemView.setOnClickListener { onClick(t) }
        }
    }
}

