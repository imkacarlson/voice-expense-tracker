package com.voiceexpense.ai.parsing.heuristic

import com.voiceexpense.ai.parsing.ParsingContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import java.util.Locale
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

        val dateNumberRanges = mutableListOf<IntRange>()
        DATE_REGEX.findAll(normalized).forEach { match ->
            match.groups[2]?.range?.let { dateNumberRanges += it }
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
            note = null,
            confidences = confidences
        )

        return draft.copy(
            tags = draft.tags.distinct(),
            confidences = draft.confidences.toMap()
        )
    }

    private fun normalizeNumbers(text: String): String {
        var updated = text
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

    private fun parseDate(lower: String, context: ParsingContext): LocalDate? {
        val match = DATE_REGEX.find(lower) ?: return null
        val monthName = match.groupValues[1]
        val day = match.groupValues[2].toIntOrNull() ?: return null
        val year = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toIntOrNull()
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

        val shareCandidate = matches.firstOrNull { candidate ->
            val window = text.windowAround(candidate)
            SHARE_HINT_REGEX.containsMatchIn(window)
        } ?: matches.first()

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
            val cleaned = stripAccountMentions(merchant)
            val normalized = cleaned.ifBlank { merchant }
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
        val verbHits = VERB_CUES.count { lower.contains(it) }
        if (verbHits >= 2) return true
        if (FILLER_PHRASES.any { lower.contains(it) }) return true
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

    private fun String.windowAround(candidate: AmountCandidate, radius: Int = 18): String {
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
            " taking "
        )
        private val FILLER_PHRASES = listOf(
            " i just ",
            " i got ",
            " couple of ",
            " charged to my ",
            " on my ",
            " for $"
        )

        private val DATE_REGEX = Regex(
            "(?i)(january|february|march|april|may|june|july|august|september|october|november|december)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s*(\\d{4}))?"
        )

        private val MONTHS: Map<String, Month> = Month.values().associateBy { it.name.lowercase(Locale.US) }
    }
}
