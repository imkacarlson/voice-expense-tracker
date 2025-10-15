package com.voiceexpense.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "transactions")
@TypeConverters(Converters::class)
data class Transaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val createdAt: Instant = Instant.now(),
    val userLocalDate: LocalDate,
    val amountUsd: BigDecimal?,
    val merchant: String,
    val description: String?,
    val type: TransactionType,
    val expenseCategory: String?,
    val incomeCategory: String?,
    // For Transfer type transactions
    val transferCategory: String? = null,
    val transferDestination: String? = null,
    val tags: List<String> = emptyList(),
    val account: String?,
    val splitOverallChargedUsd: BigDecimal?,
    val confidence: Float,
    val correctionsCount: Int = 0,
    val source: String = "voice",
    val status: TransactionStatus = TransactionStatus.DRAFT,
    val sheetRef: SheetReference? = null
)
