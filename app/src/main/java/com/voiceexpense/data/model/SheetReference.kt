package com.voiceexpense.data.model

data class SheetReference(
    val spreadsheetId: String,
    val sheetId: String,
    val rowIndex: Long? = null
)

