package com.voiceexpense.data.repository

import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.auth.AuthRepository
import com.voiceexpense.data.model.SheetReference
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.data.remote.AppendResponse
import com.voiceexpense.data.remote.SheetsClient
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class SyncResult(val attempted: Int, val posted: Int, val failed: Int)

class TransactionRepository(
    private val dao: TransactionDao,
    private val sheets: SheetsClient? = null,
    private val auth: AuthRepository? = null
) {
    private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    suspend fun saveDraft(t: Transaction): Result<Unit> = runCatching {
        dao.upsert(t.copy(status = TransactionStatus.DRAFT))
    }

    suspend fun confirm(tId: String): Result<Unit> = runCatching {
        val existing = dao.getById(tId) ?: error("Transaction not found: $tId")
        dao.update(existing.copy(status = TransactionStatus.CONFIRMED))
    }.map { Unit }

    suspend fun enqueueForSync(tId: String): Result<Unit> = runCatching {
        val existing = dao.getById(tId) ?: error("Transaction not found: $tId")
        dao.update(existing.copy(status = TransactionStatus.QUEUED))
    }.map { Unit }

    // Spreadsheet configuration (to be set by caller, e.g., Worker/Settings)
    var spreadsheetId: String = ""
    var sheetName: String = ""

    // Process queued transactions: post to Sheets with single 401-aware retry.
    suspend fun syncPending(): SyncResult {
        val sheetsClient = sheets ?: error("SheetsClient not provided")
        val authRepo = auth ?: error("AuthRepository not provided")
        require(spreadsheetId.isNotBlank() && sheetName.isNotBlank()) { "Spreadsheet configuration missing" }

        val queued = dao.getByStatus(TransactionStatus.QUEUED)
        var posted = 0
        var failed = 0
        val tokenInitial = authRepo.getAccessToken()
        var token = tokenInitial
        for (t in queued) {
            val row = mapToSheetRow(t)
            val first = sheetsClient.appendRow(token, spreadsheetId, sheetName, row)
            val res = if (first.isSuccess) first else {
                val ex = first.exceptionOrNull()
                val is401 = ex?.message?.contains("HTTP 401") == true
                if (is401) {
                    // Invalidate and attempt one refresh path (future TokenProvider integration)
                    // For now, token remains same; caller should ensure valid token
                    val retry = sheetsClient.appendRow(token, spreadsheetId, sheetName, row)
                    retry
                } else first
            }
            if (res.isSuccess) {
                val body = res.getOrNull()
                val pair = sheetsClient.extractSheetNameAndLastRow(body?.updates)
                val rowIndex = pair?.second
                val ref = SheetReference(spreadsheetId = spreadsheetId, sheetId = sheetName, rowIndex = rowIndex)
                dao.setPosted(t.id, ref, TransactionStatus.POSTED)
                posted++
            } else {
                failed++
            }
        }
        return SyncResult(attempted = queued.size, posted = posted, failed = failed)
    }

    // Map a transaction to a Sheets row with the exact column order.
    // Columns: Timestamp | Date | Amount? (No $ sign) | Description | Type | Expense Category | Tags | Income Category | [empty] | Account / Credit Card | [If splitwise] how much overall charged to my card? | Transfer Category | Account transfer is going into | [remaining columns blank]
    fun mapToSheetRow(t: Transaction): List<String> {
        val timestamp = timestampFormatter.format(t.createdAt)
        val date = dateFormatter.format(t.userLocalDate)
        val amount = when (t.type) {
            TransactionType.Transfer -> ""
            else -> t.amountUsd?.toPlainString().orEmpty()
        }
        val desc = buildString {
            append(t.merchant)
            if (!t.description.isNullOrBlank()) {
                append(" — ")
                append(t.description)
            }
        }
        val type = when (t.type) {
            TransactionType.Expense -> "Expense"
            TransactionType.Income -> "Income"
            TransactionType.Transfer -> "Transfer"
        }
        val expenseCategory = if (t.type == TransactionType.Expense) t.expenseCategory.orEmpty() else ""
        val tags = t.tags.joinToString(separator = ", ")
        val incomeCategory = if (t.type == TransactionType.Income) t.incomeCategory.orEmpty() else ""
        val emptyPlaceholder = ""
        val account = t.account.orEmpty()
        val overallCharged = t.splitOverallChargedUsd?.toPlainString().orEmpty()
        val transferCategory = if (t.type == TransactionType.Transfer) "" else "" // placeholder until model adds field
        val transferInto = if (t.type == TransactionType.Transfer) "" else "" // placeholder until model adds field

        return listOf(
            timestamp,
            date,
            amount,
            desc,
            type,
            expenseCategory,
            tags,
            incomeCategory,
            emptyPlaceholder,
            account,
            overallCharged,
            transferCategory,
            transferInto
        )
    }
}
