package com.voiceexpense.data.remote

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class AppsScriptClientTest {
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
    fun postExpense_success_makesProperRequest() = runBlocking {
        val json = """
            {"result":"success","message":"ok","data":{"rowNumber":2}}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val url = server.url("/exec").toString()
        val client = AppsScriptClient(OkHttpClient(), Moshi.Builder().build())

        val req = AppsScriptRequest(
            token = "t",
            date = "12/28/2024",
            amount = "10.00",
            description = "M — desc",
            type = "Expense",
            expenseCategory = "Cat",
            account = "Card",
            tags = "x,y",
            incomeCategory = null,
            splitwiseAmount = null,
            transferCategory = null,
            transferAccount = null
        )
        val result = client.postExpense(url, req)
        assertThat(result.isSuccess).isTrue()

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/exec")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"token\":\"t\"")
        assertThat(body).contains("\"type\":\"Expense\"")
    }
}

