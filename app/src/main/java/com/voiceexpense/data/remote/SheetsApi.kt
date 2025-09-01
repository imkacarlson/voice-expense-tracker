package com.voiceexpense.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

data class AppendRequest(
    val range: String,
    val majorDimension: String = "ROWS",
    val values: List<List<String>>
)

data class AppendResponse(
    val spreadsheetId: String?,
    val tableRange: String?,
    val updates: Updates?
) {
    data class Updates(
        val updatedRange: String?,
        val updatedRows: Int?,
        val updatedColumns: Int?,
        val updatedCells: Int?
    )
}

interface SheetsApi {
    @POST("/v4/spreadsheets/{spreadsheetId}/values/{range}:append")
    suspend fun appendValues(
        @Header("Authorization") authHeader: String,
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") rangeA1: String,
        @Query("valueInputOption") valueInputOption: String = "USER_ENTERED",
        @Body body: AppendRequest
    ): Response<AppendResponse>
}

