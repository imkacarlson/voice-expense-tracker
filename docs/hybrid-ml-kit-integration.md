# Hybrid ML Kit Structured Parsing

This document describes the hybrid transaction parsing pipeline that combines on-device GenAI (ML Kit) with a robust heuristic fallback.

## Overview
- Components:
  - `FocusedPromptBuilder`: builds compact, single-field prompts for the staged refinement loop.
  - `ValidationPipeline`: normalizes ML Kit output and validates schema/business rules.
  - `HybridTransactionParser`: orchestrates AI-first → validation → fallback.
  - `ConfidenceScorer`: multi-factor confidence scoring.
  - `ProcessingMonitor`: aggregate statistics (AI rate, fallback rate, avg time).
  - `GenAiGateway`: abstraction for ML Kit client to enable testing/mocking.

## Usage
Injected via Hilt:

```kotlin
@Inject lateinit var hybrid: HybridTransactionParser

val ctx = ParsingContext(
  recentMerchants = listOf("Starbucks"),
  knownAccounts = listOf("Checking", "Savings")
)
val res = hybrid.parse("spent 4.75 at starbucks", ctx)
when (res.method) {
  ProcessingMethod.AI -> { /* validated AI result */ }
  ProcessingMethod.HEURISTIC -> { /* fallback result */ }
}
```

`TransactionParser` internally uses the hybrid path when the model is ready, preserving its public API.

## Prompt Engineering
- Staged refinement prompts focus on one low-confidence field at a time, keeping instructions short and targeted.
- `FocusedPromptBuilder` supplies field-specific guidelines (e.g., merchant vs. category) while reusing the user utterance verbatim.
- Heuristic confidences determine which fields need prompting, avoiding unnecessary AI calls.

## Validation
- `ValidationPipeline`:
  - Removes markdown fences and extracts first JSON object.
  - Validates `tags` array shape and key business rules (type set, split constraints, transfer categories null).
  - Produces `ValidationOutcome` with normalized JSON, errors, and confidence hint.

## Confidence Scoring
- Factors: method weight (AI > heuristic), validation success, field completeness, existing confidence.
- Scores clamped to [0,1].

## Monitoring
- `ProcessingMonitor.snapshot()` exposes:
  - totals, AI vs fallback rates, validation rate, and avg time.

## Error Handling
- `AiErrorHandler` adds hybrid-specific APIs:
  - `recordHybridFailure`, `resetHybridFailures`, `isHybridCircuitOpen`, and `fromHybridErrors`.
  - Simple circuit breaker after 3 consecutive failures, encouraging fallback and backoff.

## Testing
- Unit tests cover the staged orchestrator, validation pipeline, and end-to-end hybrid parser integration.
- Android tests benchmark performance and synthetic accuracy.

## Troubleshooting
- Invalid `tags` type: ensure array of strings, not a single string.
- Split rule failures: ensure `amountUsd <= splitOverallChargedUsd`.
- Transfer category errors: `expenseCategory` and `incomeCategory` must be null for Transfer.
- Repeated AI failures: circuit breaker opens; check model readiness and prompt size; fallback remains available.
