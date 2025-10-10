package com.voiceexpense.ai.parsing.logging

import com.voiceexpense.ai.parsing.heuristic.FieldKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class ParsingRunLogEntryType {
    INPUT,
    HEURISTIC,
    PROMPT,
    RESPONSE,
    VALIDATION,
    SUMMARY,
    ERROR
}

data class ParsingRunLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val type: ParsingRunLogEntryType,
    val title: String,
    val detail: String? = null,
    val field: FieldKey? = null
)

data class ParsingRunLog(
    val transactionId: String,
    val createdAt: Instant,
    val entries: List<ParsingRunLogEntry>
) {
    fun toMarkdown(note: String?): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withLocale(Locale.US)
            .withZone(ZoneId.systemDefault())
        val builder = StringBuilder()
        builder.appendLine("# Voice Expense Diagnostics")
        builder.appendLine()
        builder.appendLine("- Transaction ID: $transactionId")
        builder.appendLine("- Generated: ${formatter.format(createdAt)}")
        builder.appendLine("- Entry count: ${entries.size}")
        builder.appendLine()
        if (!note.isNullOrBlank()) {
            builder.appendLine("## User Note")
            builder.appendLine()
            builder.appendLine(note.trim())
            builder.appendLine()
        }
        builder.appendLine("## Run Details")
        builder.appendLine()
        entries.forEach { entry ->
            builder.appendLine("### ${entry.title}")
            builder.appendLine("* Type: ${entry.type}")
            builder.appendLine("* Logged: ${formatter.format(Instant.ofEpochMilli(entry.timestamp))}")
            entry.field?.let { builder.appendLine("* Field: ${it.name}") }
            builder.appendLine()
            entry.detail?.let { detail ->
                builder.appendLine("```")
                builder.appendLine(detail)
                builder.appendLine("```")
                builder.appendLine()
            }
        }
        return builder.toString()
    }
}

class ParsingRunLogBuilder(
    val transactionId: String,
    capturedInput: String
) {
    private val createdAt: Instant = Instant.now()
    private val entries = Collections.synchronizedList(mutableListOf<ParsingRunLogEntry>())

    init {
        addEntry(
            type = ParsingRunLogEntryType.INPUT,
            title = "Captured input",
            detail = capturedInput
        )
    }

    fun addEntry(
        type: ParsingRunLogEntryType,
        title: String,
        detail: String? = null,
        field: FieldKey? = null
    ) {
        entries += ParsingRunLogEntry(
            type = type,
            title = title,
            detail = detail,
            field = field
        )
    }

    fun snapshot(): ParsingRunLog = ParsingRunLog(
        transactionId = transactionId,
        createdAt = createdAt,
        entries = synchronized(entries) { entries.toList() }
    )
}

object ParsingRunLogStore {
    private val builders = ConcurrentHashMap<String, ParsingRunLogBuilder>()

    fun put(builder: ParsingRunLogBuilder) {
        builders[builder.transactionId] = builder
    }

    fun snapshot(transactionId: String): ParsingRunLog? =
        builders[transactionId]?.snapshot()

    fun builder(transactionId: String): ParsingRunLogBuilder? = builders[transactionId]

    fun remove(transactionId: String) {
        builders.remove(transactionId)
    }
}
