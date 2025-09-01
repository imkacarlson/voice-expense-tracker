package com.voiceexpense.ai.parsing

object ParsingPrompts {
    const val SYSTEM_PROMPT = """
You are a parser that outputs strict JSON for expense entries.
Return only JSON with fields: amountUsd, merchant, description, type, expenseCategory, incomeCategory,
tags, userLocalDate (YYYY-MM-DD), account, splitOverallChargedUsd, note, confidence.
Type is one of Expense, Income, Transfer. USD only, no currency symbols.
""" 
}

