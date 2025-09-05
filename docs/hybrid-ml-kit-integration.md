# Hybrid ML Kit Structured Parsing

This document describes the hybrid transaction parsing pipeline that combines on-device GenAI (ML Kit) with a robust heuristic fallback.

## Overview
- Components:
  - `FewShotExampleRepository`: curated utterances for few-shot prompting.
  - `PromptBuilder`: composes system constraints, schema templates, context hints, and examples.
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
- System instruction enforces strict JSON and field constraints.
- Schema templates adjust guidance for Basic, Split, and Transfer contexts.
- Few-shot selection adapts to input (transfer, split cues, income keywords) and app context.

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
- Unit tests cover prompt building, validation pipeline, repository quality, and integration.
- Android tests benchmark performance and synthetic accuracy.

## Troubleshooting
- Invalid `tags` type: ensure array of strings, not a single string.
- Split rule failures: ensure `amountUsd <= splitOverallChargedUsd`.
- Transfer category errors: `expenseCategory` and `incomeCategory` must be null for Transfer.
- Repeated AI failures: circuit breaker opens; check model readiness and prompt size; fallback remains available.

