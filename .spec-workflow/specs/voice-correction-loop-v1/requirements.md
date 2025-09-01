# Requirements Document

## Introduction

Implement a voice-first correction loop that confirms a parsed transaction via concise TTS prompts and lets the user make verbal corrections (amount, merchant/description, type, category, tags, account, overall charged, date). The loop resolves ambiguities by asking targeted follow-ups and ends when the user confirms or cancels. The feature runs fully on-device except final Google Sheets sync.

## Alignment with Product Vision

- Privacy-first: All ASR and parsing stay on-device (Gemini Nano / ML Kit). Only the final confirmed transaction syncs to Google Sheets.
- Speed-first: Minimize taps; enable hands-free corrections with short prompts and quick updates.
- Offline-first: Capture, parse, confirm, and queue offline; sync later when online.
- Reliability: Structured validation and clarification reduce errors; clear exit states.

## Requirements

### Requirement 1: TTS Confirmation + Prompting

**User Story:** As a user, I want the app to read back the parsed transaction and ask short, clear questions so I can correct mistakes quickly by voice.

#### Acceptance Criteria

1. WHEN a draft transaction is available THEN the system SHALL generate a concise TTS summary (amount, merchant/description, type, key tags) and a follow-up prompt (e.g., “Say yes to save or say a field to change”).
2. IF critical fields are missing (amount for Expense/Income or type) THEN the system SHALL prioritize a targeted question requesting only the missing field.
3. WHEN the user interrupts during TTS THEN the system SHALL stop TTS and accept new voice input.

### Requirement 2: Voice Corrections for Key Fields

**User Story:** As a user, I want to correct individual fields by speaking so that I never have to type.

#### Acceptance Criteria

1. WHEN the user says a numeric phrase (e.g., “actually twenty-five” or “25.00”) THEN the system SHALL update the amount with proper decimal parsing.
2. WHEN the user says a merchant/description update (e.g., “merchant Starbucks” or “description coffee mugs”) THEN the system SHALL update the corresponding field.
3. WHEN the user specifies a type (Expense | Income | Transfer) THEN the system SHALL update the type, enforcing consistent fields (e.g., clear incomeCategory when Expense).
4. WHEN the user provides a category THEN the system SHALL set `expenseCategory` for Expense or `incomeCategory` for Income accordingly.
5. WHEN the user says “tags: …” THEN the system SHALL set tags as a comma-separated list, preserving earlier tags unless user says “replace tags: …”.
6. WHEN the user specifies an account (e.g., “Bilt Card five two one seven”) THEN the system SHALL attempt to match against known accounts from Settings.
7. WHEN the user mentions “overall charged …” THEN the system SHALL set `splitOverallChargedUsd` and validate Amount ≤ Overall.
8. WHEN the user says a date (e.g., “yesterday”, “July 3rd”) THEN the system SHALL set `userLocalDate` appropriately.

### Requirement 3: Ambiguity Detection and Clarification

**User Story:** As a user, I want the app to notice ambiguities and ask me focused questions so corrections remain fast and accurate.

#### Acceptance Criteria

1. IF two numbers are spoken without labels AND type is Expense THEN the system SHALL ask “Is the larger amount overall charged and the smaller your share?”
2. IF the type cannot be inferred THEN the system SHALL ask “Is this an expense, income, or transfer?”
3. IF the amount is malformed (e.g., “twenty-three fifty” with no clear decimal) THEN the system SHALL either pick the likely value with low confidence or ask for repetition.
4. WHEN a correction conflicts with existing fields (e.g., Income with expenseCategory) THEN the system SHALL resolve by clearing incompatible fields and confirming the change via TTS.

### Requirement 4: Confirmation, Cancel, and Loop Termination

**User Story:** As a user, I want to confirm, save, or cancel by voice so I can finish quickly.

#### Acceptance Criteria

1. WHEN the user says “yes”, “looks good”, or “save” THEN the system SHALL mark the transaction CONFIRMED and enqueue it for sync.
2. WHEN the user says “cancel” THEN the system SHALL discard the draft and exit the screen.
3. WHEN the user is silent for a configurable timeout (e.g., 8s) THEN the system SHALL reprompt once; after another timeout it SHALL end the session without saving.

### Requirement 5: Offline-First Operation

**User Story:** As a user, I want to use the correction loop without internet so I can log expenses anywhere.

#### Acceptance Criteria

1. GIVEN the device is offline WHEN the user confirms THEN the system SHALL queue the transaction for later sync and show queued status in UI.
2. GIVEN the device is offline WHEN the loop needs parsing or validation THEN the system SHALL operate fully on-device without cloud calls.

### Requirement 6: Metrics and Observability (Local Only)

**User Story:** As a developer, I want lightweight metrics to gauge UX quality without compromising privacy.

#### Acceptance Criteria

1. WHEN a correction is applied THEN the system SHALL increment `correctionsCount` on the draft.
2. The system SHALL record parse latency and number of clarification prompts per session in memory or local logs (no remote telemetry).
3. The system SHALL not log sensitive data (amounts, merchants) beyond local debug logs gated by a developer setting.

## Non-Functional Requirements

### Code Architecture and Modularity
- Single Responsibility: Keep ASR, parsing, validation, TTS, and ViewModel state isolated.
- Modular Design: Introduce dedicated correction intent parser; reuse `StructuredOutputValidator` for schema checks.
- Clear Interfaces: ViewModel exposes intents like `applyCorrection(text)` and effects for TTS prompts.

### Performance
- Parsing and applying a correction: under 1.0s median on Pixel 7a.
- TTS prompt response (time to speak after update): under 500ms to start.
- Entire correction loop typical completion: under 30s end-to-end in the common case.

### Security
- On-device only for ASR/parsing; no cloud AI calls.
- No sensitive data in logs unless developer debug is explicitly enabled.

### Reliability
- The loop SHALL resolve common ambiguities within two targeted prompts.
- Confirmed transactions SHALL be enqueued reliably even when offline.

### Usability
- Voice-first with minimal taps; clear, concise prompts (≤120 characters per prompt).
- Accessibility-friendly: readable on-screen summaries; works with screen readers; interruptible TTS.
