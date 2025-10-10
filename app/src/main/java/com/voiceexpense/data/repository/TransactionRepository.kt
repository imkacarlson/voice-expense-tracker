package com.voiceexpense.data.repository

import com.voiceexpense.data.local.TransactionDao
import com.voiceexpense.auth.AuthRepository
import com.voiceexpense.auth.TokenProvider
import com.voiceexpense.data.model.SheetReference
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.data.model.TransactionStatus
import com.voiceexpense.data.model.TransactionType
import com.voiceexpense.data.remote.AppsScriptClient
import com.voiceexpense.data.remote.AppsScriptRequest
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class SyncResult(val attempted: Int, val posted: Int, val failed: Int)

class TransactionRepository(
    private val dao: TransactionDao,
    private val apps: AppsScriptClient? = null,
    private val auth: AuthRepository? = null,
    private val tokenProvider: TokenProvider? = null
) {
    private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC)
    // Apps Script example uses MM/dd/yyyy format
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")

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

    // Lookup a transaction by id (used by confirmation Activity to load the draft)
    suspend fun getById(tId: String): Transaction? = dao.getById(tId)

    // Apps Script configuration (to be set by caller, e.g., Worker/Settings)
    var webAppUrl: String = ""

    // Process queued transactions: post via Apps Script with single 401-aware retry.
    suspend fun syncPending(): SyncResult {
        val appsClient = apps ?: error("AppsScriptClient not provided")
        val authRepo = auth ?: error("AuthRepository not provided")
        val tokens = tokenProvider ?: error("TokenProvider not provided")
        require(webAppUrl.isNotBlank()) { "Web App URL missing" }

        val queued = dao.getByStatus(TransactionStatus.QUEUED)
        var posted = 0
        var failed = 0
        val accountEmail = authRepo.getAccountEmail() ?: ""
        val scope = "https://www.googleapis.com/auth/userinfo.email"
        var token = tokens.getAccessToken(accountEmail, scope)
        for (t in queued) {
            val req = mapToAppsScriptRequest(t, token)
            val first = appsClient.postExpense(webAppUrl, req)
            val res = if (first.isSuccess) first else {
                val ex = first.exceptionOrNull()
                val is401 = ex?.message?.contains("HTTP 401") == true || ex?.message?.contains("invalid_token", ignoreCase = true) == true
                if (is401) {
                    tokens.invalidateToken(accountEmail, scope)
                    token = tokens.getAccessToken(accountEmail, scope)
                    val retry = appsClient.postExpense(webAppUrl, mapToAppsScriptRequest(t, token))
                    retry
                } else first
            }
            if (res.isSuccess) {
                // We may optionally store row number; no spreadsheet ID anymore
                val row = res.getOrNull()?.data?.rowNumber
                val ref = SheetReference(spreadsheetId = "apps-script", sheetId = "sheet", rowIndex = row)
                dao.setPosted(t.id, ref, TransactionStatus.POSTED)
                posted++
            } else {
                failed++
            }
        }
        return SyncResult(attempted = queued.size, posted = posted, failed = failed)
    }

    // Map a transaction to Apps Script request payload
    fun mapToAppsScriptRequest(t: Transaction, token: String): AppsScriptRequest {
        val date = dateFormatter.format(t.userLocalDate)
        val amount = when (t.type) {
            TransactionType.Transfer -> null
            else -> t.amountUsd?.toPlainString()
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
        val expenseCategory = if (t.type == TransactionType.Expense) t.expenseCategory else null
        val incomeCategory = if (t.type == TransactionType.Income) t.incomeCategory else null
        val tags = if (t.tags.isNotEmpty()) t.tags.joinToString(", ") else null
        val overall = t.splitOverallChargedUsd?.toPlainString()
        val account = t.account

        return AppsScriptRequest(
            token = token,
            date = date,
            amount = amount,
            description = desc,
            type = type,
            expenseCategory = expenseCategory,
            account = account,
            tags = tags,
            incomeCategory = incomeCategory,
            splitwiseAmount = overall,
            transferCategory = null,
            transferAccount = null
        )
    }
}
