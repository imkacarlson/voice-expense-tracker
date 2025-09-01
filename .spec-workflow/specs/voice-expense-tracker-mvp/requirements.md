# Requirements Document

## Introduction

This specification defines the MVP requirements for a privacy‑first Android app that lets a user verbally log expenses, income, and transfers into a Google Spreadsheet using only on‑device AI for transcription and parsing. It covers voice capture via a home‑screen widget, on‑device ASR and structured parsing, a concise confirmation loop with voice corrections, offline queueing, and Google Sheets sync, aligned to the product and technical steering documents.

## Alignment with Product Vision

- Privacy-first: All AI processing (ASR + NLU) runs on-device; the network is used only to post the confirmed transaction to Google Sheets.
- Speed-first capture: One-tap widget start, short utterances, silence detection, and a compact confirmation UI enable logging within seconds.
- Offline-first: Transactions can be captured and confirmed without connectivity; queued posts sync automatically when online.
- Data ownership: Transactions append to a user-owned Google Spreadsheet with an explicit, stable column mapping.
- Accessibility: Voice-first operation with large controls and screen-reader support.

## Requirements

### Requirement 1: Widget-initiated Voice Capture

**User Story:** As a user, I want to start logging a transaction from a home-screen widget so that I can capture expenses quickly without opening the full app.

#### Acceptance Criteria

1. WHEN the user taps the widget THEN the system SHALL start a foreground recording flow and listen for speech.
2. IF silence is detected or the user taps stop THEN the system SHALL end recording and proceed to on-device transcription.
3. WHEN microphone permission is missing THEN the system SHALL prompt for permission with a clear rationale and fallback to a tappable entry point in the main app.

### Requirement 2: On-Device Speech Recognition (ASR)

**User Story:** As a user, I want my speech transcribed on-device so that my data remains private and the app works offline.

#### Acceptance Criteria

1. WHEN recording completes THEN the system SHALL produce an ASR transcript on-device without cloud calls.
2. IF transcription confidence is low THEN the system SHALL request a brief rephrase with a concise voice/text prompt.
3. WHEN the device is offline THEN the system SHALL still transcribe using pre-downloaded language models (English).

### Requirement 3: Structured Parsing to Transaction JSON

**User Story:** As a user, I want my speech parsed into a structured transaction so that fields like amount, type, category, tags, and account are pre-filled.

#### Acceptance Criteria

1. WHEN transcript text is available THEN the system SHALL parse on-device (Gemini Nano via ML Kit GenAI) into a strict JSON schema with fields: amountUsd, merchant, description?, type (Expense|Income|Transfer), expenseCategory?, incomeCategory?, tags[], userLocalDate, account?, splitOverallChargedUsd?, note?, confidence.
2. IF multiple amounts are present for a split expense THEN the system SHALL map user share to amountUsd and total charged to splitOverallChargedUsd, and ensure amountUsd ≤ splitOverallChargedUsd.
3. IF parsing is ambiguous (e.g., unclear amounts or type) THEN the system SHALL set lower confidence and request targeted clarification in the confirmation loop.

### Requirement 4: Confirmation UI and Voice Correction Loop

**User Story:** As a user, I want to review and correct the parsed transaction using my voice so that I can fix errors without typing.

#### Acceptance Criteria

1. WHEN parsing completes THEN the system SHALL present a compact confirmation UI showing key fields and offer a large mic control for corrections.
2. WHEN the user states corrections (e.g., “actually twenty-five”, “tags Auto-Paid, Subscription”, “overall charged one twenty”, “Bilt card five two one seven”) THEN the system SHALL update the draft transaction accordingly and reflect changes immediately.
3. WHEN the user says “yes”, “looks good”, or “save” THEN the system SHALL mark the transaction as confirmed and enqueue for posting.
4. WHEN the user says “cancel” THEN the system SHALL discard the draft and end the flow with no data posted.

### Requirement 5: Spreadsheet Column Mapping and Append

**User Story:** As a user, I want transactions appended to my existing Google Sheet with the exact column mapping so that my data remains consistent with my current workflow.

#### Acceptance Criteria

1. WHEN a transaction is confirmed THEN the system SHALL append a row using the exact mapping: Timestamp | Date | Amount? (No $ sign) | Description | Type | Expense Category | Tags | Income Category | [empty] | Account / Credit Card | [If splitwise] how much overall charged to my card? | Transfer Category | Account transfer is going into | [remaining blank].
2. WHEN Type is Expense THEN the system SHALL populate Amount and Expense Category and leave Income/Transfer columns blank.
3. WHEN Type is Income THEN the system SHALL populate Amount and Income Category and leave Expense/Transfer columns blank.
4. WHEN Type is Transfer THEN the system SHALL populate Transfer Category and destination Account and leave Amount blank.

### Requirement 6: OAuth and Secure Token Handling

**User Story:** As a user, I want secure sign-in to my Google account so that the app can post to my sheet with minimal scope and strong protection.

#### Acceptance Criteria

1. WHEN the user authorizes Sheets access THEN the system SHALL store tokens securely using EncryptedSharedPreferences and hardware-backed keystore.
2. IF the token expires THEN the system SHALL refresh it automatically prior to posting without losing queued transactions.
3. WHEN the user switches accounts THEN the system SHALL update the posting target configuration and preserve prior data safely.

### Requirement 7: Offline Queue and Background Sync

**User Story:** As a user, I want confirmed transactions to be saved locally and posted automatically when online so that I never lose data.

#### Acceptance Criteria

1. WHEN connectivity is unavailable THEN the system SHALL persist confirmed transactions locally with status “queued”.
2. WHEN connectivity returns THEN the system SHALL post queued transactions via WorkManager with exponential backoff and update statuses to “posted” or “failed”.
3. IF posting fails due to transient network or auth errors THEN the system SHALL retry according to backoff policy and never drop transactions silently.

### Requirement 8: Account/Card and Tag Handling

**User Story:** As a user, I want to specify accounts/cards and tags via speech so that my entries are categorized and linked correctly.

#### Acceptance Criteria

1. WHEN a known card/account is spoken (e.g., “Bilt Card five two one seven”) THEN the system SHALL match it against a preset list and set the account field.
2. WHEN tags are provided in speech (e.g., “tags Auto-Paid, Subscription”) THEN the system SHALL set a comma-separated list in the Tags column.
3. IF a merchant is unknown THEN the system SHALL still populate Description and allow the user to tag or categorize via the correction loop.

### Requirement 9: Error Handling and Edge Cases

**User Story:** As a user, I want the app to handle ambiguous inputs and errors gracefully so that I can quickly recover.

#### Acceptance Criteria

1. IF the utterance is too noisy or unclear THEN the system SHALL prompt for a concise rephrase, with a cap on attempts to avoid battery drain.
2. IF the parsing JSON is invalid or incomplete THEN the system SHALL re-parse with stricter schema enforcement or ask for specific clarification.
3. IF the user provides conflicting amounts (share > overall) THEN the system SHALL request correction before allowing confirmation.

### Requirement 10: Performance Targets

**User Story:** As a user, I want the app to be responsive so that capturing a transaction is fast.

#### Acceptance Criteria

1. WHEN parsing runs THEN the system SHALL complete structured parsing in under 3 seconds on a Pixel 7a for typical utterances.
2. WHEN starting from the widget THEN the system SHALL complete the end-to-end flow (capture → confirm) in under 30 seconds in common cases.
3. WHEN idle THEN the system SHALL not keep long‑running foreground services active beyond the recording/processing window.

### Requirement 11: Accessibility and Usability

**User Story:** As a user, I want a voice-first and accessible interface so that I can operate the app quickly and inclusively.

#### Acceptance Criteria

1. WHEN the confirmation UI is shown THEN the system SHALL support screen readers and large tap targets for primary actions.
2. WHEN corrections are provided THEN the system SHALL support both voice and minimal-tap alternatives.
3. WHEN errors occur THEN the system SHALL present concise, human-readable messages and optional TTS prompts.

## Non-Functional Requirements

### Code Architecture and Modularity
- Single Responsibility Principle: Each file has one clear purpose (voice, parsing, data, UI, sync).
- Modular Design: Separate packages for UI, services, AI, data, auth, and workers per structure guide.
- Dependency Management: Use DI (Hilt) and clear interfaces between layers (UI → Domain → Data).
- Clear Interfaces: Strong typing for transaction schema and repository boundaries.

### Performance
- Parsing latency: <3s median; end‑to‑end capture→confirm <30s.
- Efficient lifecycle: Stop listening on silence; defer network to WorkManager; avoid unnecessary model reloads.

### Security
- On-device AI only; no cloud AI calls.
- OAuth tokens stored with EncryptedSharedPreferences and hardware-backed keys.
- Minimal scopes (Sheets only); no sensitive data in logs.

### Reliability
- Offline-first with durable local persistence (Room) and reliable sync (WorkManager with backoff).
- Conflict-safe append to Sheets with valueInputOption=USER_ENTERED and error handling for retries.

### Usability
- Voice-first corrections with immediate feedback.
- Clear prompts for clarification and low-confidence cases.
- Consistent terminology matching the Google Sheet columns and app fields.
