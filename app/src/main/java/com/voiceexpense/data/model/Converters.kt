package com.voiceexpense.data.model

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Converters {
    private const val SEP: Char = '\u001F' // Unit Separator, unlikely in user strings

    // Instant <-> Long (epoch millis)
    @TypeConverter
    @JvmStatic
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    @JvmStatic
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    // LocalDate <-> ISO-8601 String
    @TypeConverter
    @JvmStatic
    fun fromLocalDate(value: LocalDate?): String? = value?.format(DateTimeFormatter.ISO_LOCAL_DATE)

    @TypeConverter
    @JvmStatic
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }

    // BigDecimal <-> String
    @TypeConverter
    @JvmStatic
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    @JvmStatic
    fun toBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    // List<String> <-> String
    @TypeConverter
    @JvmStatic
    fun fromStringList(value: List<String>?): String? = value?.joinToString(separator = SEP.toString())

    @TypeConverter
    @JvmStatic
    fun toStringList(value: String?): List<String>? = value?.split(SEP)?.filter { it.isNotEmpty() }

    // TransactionType <-> String
    @TypeConverter
    @JvmStatic
    fun fromTransactionType(value: TransactionType?): String? = value?.name

    @TypeConverter
    @JvmStatic
    fun toTransactionType(value: String?): TransactionType? = value?.let { TransactionType.valueOf(it) }

    // TransactionStatus <-> String
    @TypeConverter
    @JvmStatic
    fun fromTransactionStatus(value: TransactionStatus?): String? = value?.name

    @TypeConverter
    @JvmStatic
    fun toTransactionStatus(value: String?): TransactionStatus? = value?.let { TransactionStatus.valueOf(it) }

    // SheetReference <-> String (SEP separated)
    @TypeConverter
    @JvmStatic
    fun fromSheetReference(value: SheetReference?): String? = value?.let {
        val row = it.rowIndex?.toString() ?: ""
        listOf(it.spreadsheetId, it.sheetId, row).joinToString(separator = SEP.toString())
    }

    @TypeConverter
    @JvmStatic
    fun toSheetReference(value: String?): SheetReference? = value?.let {
        val parts = it.split(SEP)
        if (parts.size >= 2) {
            val row = parts.getOrNull(2)?.takeIf { s -> s.isNotEmpty() }?.toLongOrNull()
            SheetReference(parts[0], parts[1], row)
        } else null
    }
}

