package com.voiceexpense.ai.parsing.heuristic

import com.voiceexpense.ai.parsing.ParsingContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Deterministic extractor that derives obvious transaction fields from a natural
 * language utterance before invoking the on-device LLM. The extractor assigns
 * per-field confidence scores so the hybrid pipeline can decide whether AI work
 * is still required.
 */
class HeuristicExtractor(
    private val thresholds: FieldConfidenceThresholds = FieldConfidenceThresholds.DEFAULT
) {
    fun extract(input: String, context: ParsingContext): HeuristicDraft {
        val normalized = normalizeNumbers(input)
        val lower = normalized.lowercase(Locale.US)

        val confidences = mutableMapOf<FieldKey, Float>()
        val tags = mutableSetOf<String>()

        val date = parseDate(lower, context)?.also {
            confidences[FieldKey.USER_LOCAL_DATE] = 0.85f
        }

        // Collect all date number ranges to exclude from amount parsing
        val dateNumberRanges = mutableListOf<IntRange>()

        // Extract ranges from "Month Day" pattern
        DATE_REGEX.findAll(normalized).forEach { match ->
            match.groups[2]?.range?.let { dateNumberRanges += it }
            match.groups[3]?.range?.let { dateNumberRanges += it }
        }

        // Extract ranges from "Day of Month" pattern (e.g., "the 18th of October")
        DATE_DAY_OF_MONTH_REGEX.findAll(normalized).forEach { match ->
            match.groups[1]?.range?.let { dateNumberRanges += it }
            match.groups[3]?.range?.let { dateNumberRanges += it }
        }

        val amountInfo = parseAmounts(normalized, dateNumberRanges)
        val amount = amountInfo.share?.also {
            confidences[FieldKey.AMOUNT_USD] = amountInfo.shareConfidence
        }
        val overall = amountInfo.overall?.also {
            confidences[FieldKey.SPLIT_OVERALL_CHARGED_USD] = amountInfo.overallConfidence
        }

        val type = inferType(lower)?.also {
            confidences[FieldKey.TYPE] = it.second
        }?.first ?: "Expense"

        val merchant = inferMerchant(normalized, lower, context)?.also {
            confidences[FieldKey.MERCHANT] = it.second
        }?.first

        val account = inferAccount(lower, context)?.also {
            confidences[FieldKey.ACCOUNT] = it.second
        }?.first

        if (lower.contains("splitwise") || lower.contains(" split") || lower.contains(" my share")) {
            tags += "splitwise"
        }
        if (lower.contains("auto-paid") || lower.contains("auto paid") || lower.contains("auto-pay") || lower.contains("auto pay") || lower.contains("auto charged")) {
            tags += "auto-paid"
        }
        if (lower.contains("subscription") || lower.contains("recurring")) {
            tags += "subscription"
        }
        if (tags.isNotEmpty()) {
            confidences[FieldKey.TAGS] = 0.6f
        }

        val draft = HeuristicDraft(
            amountUsd = amount,
            merchant = merchant,
            description = null,
            type = type,
            expenseCategory = null,
            incomeCategory = null,
            tags = tags.toList(),
            userLocalDate = date,
            account = account,
            splitOverallChargedUsd = overall,
            confidences = confidences
        )

        return draft.copy(
            tags = draft.tags.distinct(),
            confidences = draft.confidences.toMap()
        )
    }

    private fun normalizeNumbers(text: String): String {
        var updated = text

        // Convert spelled-out numbers to digits (common voice patterns)
        updated = normalizeSpelledOutNumbers(updated)

        // Convert patterns like "17 50" into "17.50"
        updated = COMBINED_DECIMAL_REGEX.replace(updated) { matchResult ->
            val whole = matchResult.groupValues[1]
            val cents = matchResult.groupValues[2]
            "$whole.$cents"
        }
        // Replace spoken "point" usage with decimal points when surrounded by digits
        updated = SPOKEN_POINT_REGEX.replace(updated) { matchResult ->
            "${matchResult.groupValues[1]}.${matchResult.groupValues[2]}"
        }
        return updated
    }

    private fun normalizeSpelledOutNumbers(text: String): String {
        var result = text.lowercase(Locale.US)

        // Handle "X dollar(s) and Y cents" pattern first (most specific)
        // Example: "two dollars and thirty nine cents" -> "2.39"
        result = SPELLED_DOLLARS_AND_CENTS_REGEX.replace(result) { match ->
            val dollars = wordToNumber(match.groupValues[1]) ?: match.groupValues[1]
            val cents = wordToNumber(match.groupValues[2]) ?: match.groupValues[2]
            "$dollars.${cents.toString().padStart(2, '0')}"
        }

        // Handle "X dollar(s) Y cents" (with or without "and")
        // Example: "two dollars thirty nine cents" -> "2.39"
        result = SPELLED_DOLLARS_CENTS_REGEX.replace(result) { match ->
            val dollars = wordToNumber(match.groupValues[1]) ?: match.groupValues[1]
            val cents = wordToNumber(match.groupValues[2]) ?: match.groupValues[2]
            "$dollars.${cents.toString().padStart(2, '0')}"
        }

        // Handle standalone spelled amounts like "twenty three dollars"
        result = SPELLED_DOLLARS_ONLY_REGEX.replace(result) { match ->
            val amount = wordToNumber(match.groupValues[1]) ?: match.groupValues[1]
            "$amount"
        }

        return result
    }

    private fun wordToNumber(word: String): String? {
        val normalized = word.trim().lowercase(Locale.US)
        return WORD_TO_NUMBER[normalized]?.toString()
    }

    private fun parseDate(lower: String, context: ParsingContext): LocalDate? {
        // Try "Month Day" pattern first (e.g., "September 7th")
        val monthDayMatch = DATE_REGEX.find(lower)
        if (monthDayMatch != null) {
            val monthName = monthDayMatch.groupValues[1]
            val day = monthDayMatch.groupValues[2].toIntOrNull() ?: return null
            val year = monthDayMatch.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toIntOrNull()
            val month = MONTHS[monthName] ?: return null
            val baseYear = year ?: context.defaultDate.year
            var candidate = LocalDate.of(baseYear, month, day)
            // If the candidate is more than ~90 days in the future relative to the default date, assume it referred to last year.
            val diff = candidate.toEpochDay() - context.defaultDate.toEpochDay()
            if (year == null && diff > 90) {
                candidate = candidate.minusYears(1)
            }
            return candidate
        }

        // Try "Day of Month" pattern (e.g., "the 18th of October")
        val dayOfMonthMatch = DATE_DAY_OF_MONTH_REGEX.find(lower)
        if (dayOfMonthMatch != null) {
            val day = dayOfMonthMatch.groupValues[1].toIntOrNull() ?: return null
            val monthName = dayOfMonthMatch.groupValues[2]
            val year = dayOfMonthMatch.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toIntOrNull()
            val month = MONTHS[monthName] ?: return null
            val baseYear = year ?: context.defaultDate.year
            var candidate = LocalDate.of(baseYear, month, day)
            val diff = candidate.toEpochDay() - context.defaultDate.toEpochDay()
            if (year == null && diff > 90) {
                candidate = candidate.minusYears(1)
            }
            return candidate
        }

        return null
    }

    private fun parseAmounts(text: String, excludeRanges: List<IntRange> = emptyList()): AmountParseResult {
        val matches = NUMBER_REGEX.findAll(text)
            .mapNotNull { match ->
                if (excludeRanges.any { rangesOverlap(it, match.range) }) return@mapNotNull null
                val value = match.value.replace(",", "")
                val decimal = value.toBigDecimalOrNull() ?: return@mapNotNull null
                AmountCandidate(decimal, match.range.first, match.range.last)
            }
            .toList()
        if (matches.isEmpty()) return AmountParseResult()

        val shareCandidate = matches.mapNotNull { candidate ->
            val windowStart = max(0, candidate.start - HINT_WINDOW_RADIUS)
            val windowEnd = kotlin.math.min(text.length, candidate.end + HINT_WINDOW_RADIUS)
            val window = text.substring(windowStart, windowEnd)
            val bestScore = SHARE_HINT_REGEX.findAll(window)
                .map { match ->
                    val hintStart = windowStart + match.range.first
                    val distance = abs(hintStart - candidate.start)
                    val hintAfterValue = hintStart >= candidate.end
                    distance + if (hintAfterValue) SHARE_HINT_AFTER_PENALTY else 0
                }
                .minOrNull()
            bestScore?.let { candidate to it }
        }.minByOrNull { it.second }?.first ?: matches.first()

        val split = SPLIT_HINT_REGEX.containsMatchIn(text.lowercase(Locale.US))
        val overallCandidate = if (split) {
            matches.firstOrNull { candidate ->
                candidate != shareCandidate && OVERALL_HINT_REGEX.containsMatchIn(text.windowAround(candidate))
            } ?: matches.maxByOrNull { it.value }
        } else null

        var share = shareCandidate.value
        val shareConfidence = if (shareCandidate == matches.first()) 0.85f else 0.9f
        var overall = overallCandidate?.value?.takeIf { it >= share }
        val overallConfidence = overallCandidate?.let { 0.8f } ?: 0f

        // If split detected and we have 2 distinct amounts, ensure smaller = share, larger = overall
        if (split && overall != null && share != overall && share > overall) {
            val temp = share
            share = overall
            overall = temp
        }

        // Don't populate overall if it equals share (only one distinct amount found)
        if (overall != null && share == overall) {
            overall = null
        }

        return AmountParseResult(share, overall, shareConfidence, overallConfidence)
    }

    private fun rangesOverlap(a: IntRange, b: IntRange): Boolean = a.first <= b.last && b.first <= a.last

    private fun stripAccountMentions(candidate: String): String {
        val lower = candidate.lowercase(Locale.US)
        val cues = listOf(" card", " account", " visa", " mastercard", " debit", " checking", " savings")
        val patterns = listOf(" on my ", " using my ", " with my ")
        var cutIndex = candidate.length
        for (pattern in patterns) {
            val idx = lower.indexOf(pattern)
            if (idx >= 0) {
                val tail = lower.substring(idx)
                if (cues.any { tail.contains(it) }) {
                    cutIndex = minOf(cutIndex, idx)
                }
            }
        }
        return candidate.substring(0, cutIndex).trimEnd()
    }

    private fun stripTrailingStopwords(merchant: String): String {
        var result = merchant.trim()
        val lower = result.lowercase(Locale.US)

        // Find the earliest position where a trailing stopword pattern appears
        var cutIndex = result.length

        for (stopword in MERCHANT_TRAILING_STOPWORDS) {
            // Look for the stopword as a word boundary (preceded by space)
            val pattern = " $stopword"
            val idx = lower.indexOf(pattern)
            if (idx >= 0) {
                cutIndex = minOf(cutIndex, idx)
            }
        }

        // Also check for amount patterns like " $XX" or " and $"
        val amountPattern = Regex("""\s+(?:and\s+)?\$""")
        amountPattern.find(lower)?.let { match ->
            cutIndex = minOf(cutIndex, match.range.first)
        }

        return result.substring(0, cutIndex).trim()
    }

    private fun inferType(lower: String): Pair<String, Float>? {
        return when {
            lower.contains("transfer") || lower.contains("moved") -> "Transfer" to 0.85f
            lower.contains("paycheck") || lower.contains("deposit") || lower.contains("income") || lower.contains("refund") -> "Income" to 0.75f
            else -> "Expense" to 0.6f
        }
    }

    private fun inferMerchant(original: String, lower: String, context: ParsingContext): Pair<String, Float>? {
        context.recentMerchants.firstOrNull { merchant ->
            lower.contains(merchant.lowercase(Locale.US))
        }?.let { return it to 0.9f }

        val atMatch = MERCHANT_REGEX.find(original)
        val merchant = atMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        if (merchant != null) {
            val withoutAccount = stripAccountMentions(merchant)
            val withoutTrailing = stripTrailingStopwords(withoutAccount)
            val normalized = withoutTrailing.ifBlank { merchant }
            val suspicious = looksLikeVerboseMerchant(normalized)
            val confidence = if (suspicious) 0f else 0.65f
            val value = normalized.takeIf { it.isNotBlank() } ?: merchant
            if (value.isNotBlank()) {
                return value to confidence
            }
            return merchant to confidence
        }
        return null
    }

    private fun looksLikeVerboseMerchant(value: String): Boolean {
        if (value.length <= 3) return false
        val trimmed = value.trim()
        if (trimmed.length > 30) return true
        val lower = trimmed.lowercase(Locale.US)
        val tokens = lower.split(Regex("\\s+")).filter { it.isNotBlank() }
        val firstWord = tokens.firstOrNull()

        val verbHits = VERB_CUES.count { lower.contains(it) }
        if (verbHits >= 2) return true

        if (firstWord != null && firstWord in SUSPICIOUS_MERCHANT_PREFIXES) return true
        if (tokens.any { it in DEBT_CUES }) return true
        if (FILLER_PHRASES.any { lower.contains(it) }) return true

        val lacksCapitalization = trimmed.any { it.isLetter() } &&
            trimmed.none { it.isUpperCase() || it.isDigit() }
        if (lacksCapitalization) return true

        if (verbHits >= 1 && trimmed.length > 20) return true

        return false
    }

    private fun inferAccount(lower: String, context: ParsingContext): Pair<String, Float>? {
        val accounts = (context.allowedAccounts.takeIf { it.isNotEmpty() } ?: context.knownAccounts)
        if (accounts.isEmpty()) return null

        // Strategy 1: Check for 4-digit account numbers (highest confidence)
        accounts.forEach { accountName ->
            val digits = FOUR_DIGIT_REGEX.find(accountName)?.value
            if (digits != null && lower.contains(digits)) {
                return accountName to 0.9f
            }
        }

        // Strategy 2: Keyword-based fuzzy matching (handles "Chase Sapphire Preferred Card" → "Chase Sapphire Preferred (1234)")
        accounts.forEach { accountName ->
            val keywords = extractAccountKeywords(accountName)
            val matchCount = keywords.count { keyword -> lower.contains(keyword) }

            // If 2+ keywords match, it's probably this account
            if (matchCount >= 2 && keywords.isNotEmpty()) {
                val confidence = when {
                    matchCount == keywords.size -> 0.85f  // All keywords match
                    matchCount >= keywords.size / 2 -> 0.7f  // Half or more match
                    else -> 0.5f
                }
                return accountName to confidence
            }
        }

        // Strategy 3: Fallback to exact substring match
        accounts.forEach { accountName ->
            if (lower.contains(accountName.lowercase(Locale.US))) {
                return accountName to 0.7f
            }
        }

        return null
    }

    private fun extractAccountKeywords(accountName: String): List<String> {
        // Remove parenthetical info and numbers, split into significant words
        val cleaned = accountName
            .replace(Regex("""\([^)]*\)"""), "")  // Remove (1234), (Savings), etc.
            .lowercase(Locale.US)

        return cleaned.split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.length >= 3 }  // Ignore short words like "a", "of"
            .filter { !it.matches(Regex("""\d+""")) }  // Ignore pure numbers
    }

    private fun String.windowAround(candidate: AmountCandidate, radius: Int = HINT_WINDOW_RADIUS): String {
        val start = max(0, candidate.start - radius)
        val end = kotlin.math.min(this.length, candidate.end + radius)
        return this.substring(start, end)
    }

    private data class AmountCandidate(val value: BigDecimal, val start: Int, val end: Int)

    private data class AmountParseResult(
        val share: BigDecimal? = null,
        val overall: BigDecimal? = null,
        val shareConfidence: Float = 0f,
        val overallConfidence: Float = 0f
    )

    companion object {
        private val NUMBER_REGEX = Regex("""\d+(?:[.\,]\d+)?""")
        private val COMBINED_DECIMAL_REGEX = Regex("""(\d+)\s+(\d{2})(?!\d)""")
        private val SPOKEN_POINT_REGEX = Regex("""(\d+)\s+point\s+(\d+)""", RegexOption.IGNORE_CASE)

        // Spelled-out number patterns
        private val WORD_PATTERN = """(zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred)"""
        private val SPELLED_DOLLARS_AND_CENTS_REGEX = Regex("""$WORD_PATTERN\s*dollars?\s+and\s+$WORD_PATTERN\s*cents?""", RegexOption.IGNORE_CASE)
        private val SPELLED_DOLLARS_CENTS_REGEX = Regex("""$WORD_PATTERN\s*dollars?\s+$WORD_PATTERN\s*cents?""", RegexOption.IGNORE_CASE)
        private val SPELLED_DOLLARS_ONLY_REGEX = Regex("""$WORD_PATTERN\s*dollars?""", RegexOption.IGNORE_CASE)

        private val WORD_TO_NUMBER = mapOf(
            "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
            "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
            "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
            "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
            "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
            "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
            "hundred" to 100
        )
        private const val HINT_WINDOW_RADIUS = 60
        private val SHARE_HINT_REGEX = Regex("(?i)(my share|owe|i owe|i will owe)")
        private val SPLIT_HINT_REGEX = Regex("(?i)(splitwise|my share|split|overall)")
        private val OVERALL_HINT_REGEX = Regex("(?i)(overall|total|charged|to my card|overall charged)")
        private val MERCHANT_REGEX = Regex("""(?i)(?:at|from)\s+([A-Za-z0-9&' ]{2,40})""")
        private val FOUR_DIGIT_REGEX = Regex("""(\d{4})""")
        private val VERB_CUES = listOf(
            " got ",
            " get ",
            " went ",
            " buy ",
            " bought ",
            " charged ",
            " spent ",
            " paying ",
            " pay ",
            " grabbing ",
            " taking ",
            " owe "
        )
        private val FILLER_PHRASES = listOf(
            " i just ",
            " i got ",
            " couple of ",
            " charged to my ",
            " on my ",
            " for $"
        )
        private val SUSPICIOUS_MERCHANT_PREFIXES = setOf(
            "that",
            "this",
            "it",
            "then",
            "so",
            "and",
            "but",
            "because",
            "if",
            "after",
            "when",
            "while",
            "my",
            "our",
            "their",
            "his",
            "her",
            "i"
        )
        private val DEBT_CUES = setOf("owe", "owed", "owing", "due")

        private val MERCHANT_TRAILING_STOPWORDS = listOf(
            "and", "it", "for", "on", "using", "with", "to", "costed", "cost",
            "costs", "charged", "the", "was", "is"
        )

        private val DATE_REGEX = Regex(
            "(?i)(january|february|march|april|may|june|july|august|september|october|november|december)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s*(\\d{4}))?"
        )

        // Matches "the 18th of October" or "18th of October"
        private val DATE_DAY_OF_MONTH_REGEX = Regex(
            "(?i)(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?(january|february|march|april|may|june|july|august|september|october|november|december)(?:,?\\s*(\\d{4}))?"
        )

        private val MONTHS: Map<String, Month> = Month.values().associateBy { it.name.lowercase(Locale.US) }

        private const val SHARE_HINT_AFTER_PENALTY = 200
    }
}
