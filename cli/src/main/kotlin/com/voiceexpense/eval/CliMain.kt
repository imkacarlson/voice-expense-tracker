package com.voiceexpense.eval

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.voiceexpense.ai.parsing.ParsedResult
import com.voiceexpense.ai.parsing.ParsingContext
import com.voiceexpense.ai.parsing.TransactionParser
import com.voiceexpense.ai.parsing.heuristic.HeuristicDraft
import com.voiceexpense.ai.parsing.hybrid.HybridTransactionParser
import com.voiceexpense.ai.parsing.hybrid.ProcessingMethod
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate

private val moshi: Moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

private val inputAdapter = moshi.adapter(CliInput::class.java)
private val needsAiAdapter = moshi.adapter(CliOutputNeedsAi::class.java)
private val completeAdapter = moshi.adapter(CliOutputComplete::class.java)
private val errorAdapter = moshi.adapter(CliError::class.java)

fun main() {
    val stdin = readStdin()
    if (stdin.isBlank()) {
        emitError("EMPTY_INPUT", "No input provided on stdin")
        return
    }

    val payload = runCatching { inputAdapter.fromJson(stdin) }.getOrNull()
    if (payload == null) {
        emitError("INVALID_JSON", "Unable to decode CLI input")
        return
    }

    val gateway = PythonGenAiGateway()
    payload.modelResponses?.let { gateway.injectResponses(it) }

    val context = payload.context.toParsingContext()
    val hybrid = HybridTransactionParser(genai = gateway)
    val parser = TransactionParser(hybrid = hybrid)

    runBlocking {
        val stage1 = parser.prepareStage1(payload.utterance, context)
        val staged = runCatching {
            parser.runStagedRefinement(
                text = payload.utterance,
                context = context,
                stage1Snapshot = stage1.snapshot
            )
        }.getOrElse { throwable ->
            emitError("PARSER_ERROR", throwable.message ?: throwable::class.simpleName ?: "Unknown error")
            return@runBlocking
        }

        if (payload.modelResponses == null) {
            val prompts = gateway.consumePrompts()
            if (prompts.isNotEmpty()) {
                val summary = stage1.heuristicDraft.toSummary(stage1.parsedResult)
                val stats = staged.staged?.toStats()
                val output = CliOutputNeedsAi(
                    heuristicResults = summary,
                    promptsNeeded = prompts.map { PromptRequest(field = it.key, prompt = it.value) },
                    stats = stats
                )
                println(needsAiAdapter.toJson(output))
                return@runBlocking
            }
        }

        val output = staged.toCompleteOutput()
        println(completeAdapter.toJson(output))
    }
}

private fun ParsedResult.toSnapshot(): ParsedSnapshot = ParsedSnapshot(
    amountUsd = amountUsd,
    merchant = merchant,
    description = description,
    type = type,
    expenseCategory = expenseCategory,
    incomeCategory = incomeCategory,
    tags = tags,
    userLocalDateIso = userLocalDate.toString(),
    account = account,
    splitOverallChargedUsd = splitOverallChargedUsd,
    confidence = confidence
)

private fun com.voiceexpense.ai.parsing.hybrid.StagedParsingResult.toStats(): CliStats = CliStats(
    stage0Ms = stage1DurationMs,
    stage1Ms = stage2DurationMs.takeIf { it > 0 },
    totalMs = totalDurationMs
)

private fun CliContext?.toParsingContext(): ParsingContext {
    if (this == null) return ParsingContext()
    val defaultDate = runCatching { defaultDate?.let(LocalDate::parse) }.getOrNull() ?: LocalDate.now()
    return ParsingContext(
        recentMerchants = recentMerchants.orEmpty(),
        recentCategories = recentCategories.orEmpty(),
        knownAccounts = knownAccounts.orEmpty(),
        defaultDate = defaultDate,
        allowedExpenseCategories = allowedExpenseCategories.orEmpty(),
        allowedIncomeCategories = allowedIncomeCategories.orEmpty(),
        allowedTags = allowedTags.orEmpty(),
        allowedAccounts = allowedAccounts.orEmpty()
    )
}

private fun HeuristicDraft.toSummary(parsed: ParsedResult): HeuristicSummary = HeuristicSummary(
    amountUsd = amountUsd,
    merchant = merchant,
    description = description,
    type = type,
    expenseCategory = expenseCategory,
    incomeCategory = incomeCategory,
    tags = tags,
    splitOverallChargedUsd = splitOverallChargedUsd,
    account = account,
    confidence = coverageScore
)

private fun HybridParsingResult.toCompleteOutput(): CliOutputComplete {
    val staged = this.staged
    val stats = staged?.toStats()
    return CliOutputComplete(
        parsed = result.toSnapshot(),
        method = method.toCliMethod(),
        stats = stats
    )
}

private fun ProcessingMethod.toCliMethod(): String = when (this) {
    ProcessingMethod.AI -> "AI"
    ProcessingMethod.HEURISTIC -> "HEURISTIC"
}

private fun readStdin(): String = BufferedReader(InputStreamReader(System.`in`)).use { reader ->
    buildString {
        var line = reader.readLine()
        while (line != null) {
            appendLine(line)
            line = reader.readLine()
        }
    }
}.trim()

private fun emitError(code: String, message: String) {
    val json = errorAdapter.toJson(CliError(code = code, message = message))
    println(json)
}

@JsonClass(generateAdapter = true)
private data class CliError(
    val status: String = "error",
    val code: String,
    val message: String
)
