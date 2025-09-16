# Requirements Document

## Introduction

Introduce heuristic-first parsing for transaction utterances so the app fills obvious fields without waiting for the Gemma 3 1B model. Tighten the fallback prompt so it mirrors the real Google Sheet output and only requests the fields heuristics could not derive. This keeps on-device processing fast on the Pixel 7a and reduces confirmation edits for the user.

## Alignment with Product Vision

The product vision emphasizes privacy-first, low-friction capture with on-device AI. By extracting high-confidence data locally and minimizing LLM involvement, we deliver quicker confirmations, reduce hallucinations, and stay aligned with the spreadsheet mapping defined in `product.md`.

## Requirements

### Requirement 1

**User Story:** As a user logging an expense, I want the app to fill transaction fields immediately from obvious cues so that the confirmation form is accurate without waiting on AI.

#### Acceptance Criteria

1. WHEN an utterance contains a recognizable month/day phrase (e.g., "on September 11th") THEN the system SHALL map it to `userLocalDate` via heuristics before invoking AI.
2. WHEN an utterance includes a dollar amount and merchant-like name THEN the system SHALL set `amountUsd` and `merchant` heuristically with ≥0.8 confidence.
3. IF the heuristics populate all mandatory fields (date, amount, type, merchant or description, account) with confidence ≥ threshold THEN the system SHALL bypass the LLM and return the heuristic result as the parsed output.

### Requirement 2

**User Story:** As the same user, I want AI fallback to only address uncertain fields so that ambiguous information is resolved without undoing the heuristic work.

#### Acceptance Criteria

1. WHEN heuristics leave any required field null THEN the system SHALL call the LLM with a prompt containing the known field values and marking empty fields as `null` for completion.
2. IF the LLM returns values that do not match configured options (accounts, categories, tags) THEN the system SHALL normalize to the closest allowed option or leave the field null for manual confirmation.
3. WHEN the LLM output fails validation (schema or share-vs-overall rules) THEN the system SHALL retry once with a reduced prompt and, if still invalid, fall back to the heuristic result while marking the parsing confidence as low.

## Non-Functional Requirements

### Code Architecture and Modularity
- **Single Responsibility Principle**: Introduce a heuristic extraction component separate from prompt generation, MediaPipe invocation, and validation.
- **Modular Design**: Provide clear interfaces for passing heuristic drafts and known-field maps into the prompt builder without coupling to UI classes.
- **Dependency Management**: Implement heuristics in plain Kotlin utilities; avoid new heavy dependencies.
- **Clear Interfaces**: Define a `HeuristicDraft` data structure that reports field values and confidence scores.

### Performance
- Heuristic extraction SHALL complete within 10 ms on a Pixel 7a for average-length utterances.
- Prompt text sent to Gemma SHALL stay under 800 tokens, reducing average inference latency by at least 20% versus the current baseline.

### Security
- All parsing remains on-device; no new permissions or external services SHALL be introduced.

### Reliability
- Record heuristic vs AI usage in `ProcessingMonitor` so failures can be observed.
- Add automated tests covering heuristic-only success, hybrid success, and hybrid failure fallback pathways.

### Usability
- Surface a low-confidence indicator in the confirmation UI whenever heuristics or AI cannot fill a required field confidently, guiding manual review without overwhelming the user.
