package com.voiceexpense.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

open class SheetsClient(
    private val baseUrl: String = "https://sheets.googleapis.com",
    client: OkHttpClient? = null,
    moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
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

    /**
     * Extracts sheet name and the last updated row index (1-based) from an AppendResponse updates.updatedRange.
     * Example: "Sheet1!A5:M5" -> ("Sheet1", 5)
     */
    fun extractSheetNameAndLastRow(updates: AppendResponse.Updates?): Pair<String, Long?>? {
        val range = updates?.updatedRange ?: return null
        val exclParts = range.split("!")
        if (exclParts.size != 2) return null
        val sheet = exclParts[0]
        val a1 = exclParts[1]
        val endRef = a1.substringAfter(":", a1)
        val rowDigits = endRef.dropWhile { it.isLetter() }
        val row = rowDigits.toLongOrNull()
        return sheet to row
    }
}
