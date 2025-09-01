package com.voiceexpense.data.remote

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class SheetsClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun appendRow_success_makesProperRequest() = runBlocking {
        val json = """
            {"updates":{"updatedRange":"Sheet1!A1:M1","updatedRows":1,"updatedColumns":13,"updatedCells":13}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val baseUrl = server.url("/").toString()
        val client = SheetsClient(baseUrl = baseUrl, client = OkHttpClient())

        val result = client.appendRow(
            accessToken = "token123",
            spreadsheetId = "sheetId",
            sheetName = "Sheet1",
            values = listOf("t","d","10.00","desc","Expense","Cat","tags","","","Card","","","")
        )

        assertThat(result.isSuccess).isTrue()

        val request = server.takeRequest()
        assertThat(request.path).contains("/v4/spreadsheets/sheetId/values/Sheet1:append")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer token123")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"values\":[[")
        assertThat(body).contains("\"majorDimension\":\"ROWS\"")
    }

    @Test
    fun appendRow_error_mapsFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        val baseUrl = server.url("/").toString()
        val client = SheetsClient(baseUrl = baseUrl, client = OkHttpClient())

        val result = client.appendRow(
            accessToken = "bad",
            spreadsheetId = "sheetId",
            sheetName = "Sheet1",
            values = listOf("")
        )

        assertThat(result.isFailure).isTrue()
    }
}

