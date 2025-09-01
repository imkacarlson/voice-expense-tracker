package com.voiceexpense.data.remote

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

open class SheetsClient(
    private val baseUrl: String = "https://sheets.googleapis.com",
    client: OkHttpClient? = null,
    moshi: Moshi = Moshi.Builder().build()
) {
    private val httpClient: OkHttpClient = client ?: OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val api: SheetsApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(SheetsApi::class.java)

    open suspend fun appendRow(
        accessToken: String,
        spreadsheetId: String,
        sheetName: String,
        values: List<String>
    ): Result<AppendResponse> = runCatching {
        val authHeader = "Bearer $accessToken"
        val range = sheetName
        val body = AppendRequest(range = range, values = listOf(values))
        val resp = api.appendValues(authHeader, spreadsheetId, range, "USER_ENTERED", body)
        if (resp.isSuccessful) {
            resp.body() ?: AppendResponse(spreadsheetId = spreadsheetId, tableRange = null, updates = null)
        } else {
            error("Sheets append failed: HTTP ${'$'}{resp.code()} ${'$'}{resp.message()}")
        }
    }
}
