package com.voiceexpense.data.remote

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@JsonClass(generateAdapter = true)
data class AppsScriptRequest(
    val token: String,
    val date: String,
    val amount: String?,
    val description: String?,
    val type: String,
    val expenseCategory: String?,
    val account: String?,
    val tags: String?,
    val incomeCategory: String?,
    val splitwiseAmount: String?,
    val transferCategory: String?,
    val transferAccount: String?
)

@JsonClass(generateAdapter = true)
data class AppsScriptResponseData(
    val timestamp: String?,
    val description: String?,
    val amount: Double?,
    val rowNumber: Long?
)

@JsonClass(generateAdapter = true)
data class AppsScriptResponse(
    val result: String,
    val message: String?,
    val timestamp: String?,
    val data: AppsScriptResponseData?
)

open class AppsScriptClient(
    private val client: OkHttpClient,
    private val moshi: Moshi
) {
    private val jsonAdapter = moshi.adapter(AppsScriptResponse::class.java)
    private val reqAdapter = moshi.adapter(AppsScriptRequest::class.java)
    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun postExpense(url: String, request: AppsScriptRequest): Result<AppsScriptResponse> = runCatching {
        val body = reqAdapter.toJson(request).toRequestBody(json)
        val httpRequest = Request.Builder()
            .url(url)
            .post(body)
            .build()
        client.newCall(httpRequest).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("AppsScript post failed: HTTP ${resp.code} $bodyStr")
            val parsed = jsonAdapter.fromJson(bodyStr) ?: error("Empty response")
            if (parsed.result != "success") error("AppsScript error: ${parsed.message ?: "unknown"}")
            parsed
        }
    }
}
