# Design Document

## Overview

This design specifies the MVP implementation of a voice-first Android app that captures expenses/income/transfers via a home screen widget, performs on-device ASR and structured parsing, confirms via a compact UI with voice corrections, and appends rows to a user-owned Google Sheet. The architecture follows MVVM with clear separation between UI, domain (voice/parse/confirm rules), and data (Room + Sheets). Background sync is handled by WorkManager; authentication uses Google Sign-In with secure token storage.

## Steering Document Alignment

### Technical Standards (tech.md)
- Kotlin, Android 14+, Gradle build; apply detekt/ktlint and Android Lint.
- On-device AI only: ML Kit Speech Recognition and ML Kit GenAI (Gemini Nano) for parsing. No cloud AI calls.
- OAuth 2.0 via Google Sign-In, Sheets API v4 via OkHttp/Retrofit; tokens stored in EncryptedSharedPreferences with hardware-backed keystore.
- Offline-first: Room persistence + WorkManager retries with backoff for sync. Value input option USER_ENTERED.

### Project Structure (structure.md)
- Packages under `com.voiceexpense.*` with the documented layout: `ui/`, `service/`, `ai/`, `data/`, `auth/`, `worker/`, `util/`.
- Widget launches a foreground `voice` service; confirmation UI in `ui/confirmation`; sync in `service/sync` and `worker/`.
- Naming conventions, file size limits, and dependency direction enforced (UI → Domain → Data).

## Code Reuse Analysis
- New project: no internal components to reuse yet; leverage platform libraries (WorkManager, Room, Google Sign-In, ML Kit, Retrofit/OkHttp).
- Reuse steering docs’ API contracts and spreadsheet column mapping verbatim to ensure compatibility.
- Introduce `scripts/build_apk.py` per tech.md to standardize builds (outside app code).

### Existing Components to Leverage
- Android Widget framework: Quick entrypoint for capture.
- ML Kit SpeechRecognizer (on-device) and ML Kit GenAI (Gemini Nano): ASR + NLU.
- WorkManager and Room: durable queue + background sync.

### Integration Points
- Google Sheets API v4: Append rows to configured spreadsheet/tab following exact column order.
- Google Sign-In / Play Services Auth: Obtain and refresh tokens; minimal scopes.
- Android OS permissions: Microphone, Internet (for sync), Foreground service during recording.

## Architecture

- MVVM with repositories. Foreground `VoiceRecordingService` orchestrates ASR → parsing.
- `TransactionParser` produces strict JSON; `StructuredOutputValidator` enforces schema and business rules.
- `TransactionRepository` persists drafts/confirmed/queued transactions; `SheetsClient` handles append.
- `SyncWorker` drains queue with exponential backoff; `AuthRepository` manages tokens.

```mermaid
flowchart LR
    W[Home Screen Widget] -->|Intent| VS(VoiceRecordingService)
    VS --> ASR[On-device ASR]
    ASR --> PARSE[TransactionParser (Gemini Nano)]
    PARSE --> VAL[StructuredOutputValidator]
    VAL --> DRAFT[TransactionRepository (Room)]
    DRAFT --> UI[ConfirmationActivity/ViewModel]
    UI -->|confirm| QUEUE[Queue (Room)]
    QUEUE --> WM[WorkManager SyncWorker]
    WM --> AUTH[AuthRepository]
    AUTH --> SHEETS[SheetsClient]
    SHEETS --> GS[Google Sheets]
```

### Modular Design Principles
- Single responsibility per class; clear interfaces for AI, data, auth, and sync layers.
- Isolation of ASR vs parsing; parsing vs validation; repository vs API client.
- Dependency injection via Hilt for testability and clear boundaries.

## Components and Interfaces

### Widget and Entry
- `ui/widget/ExpenseWidgetProvider` (AppWidgetProvider)
  - Purpose: Launch capture flow.
  - Interface: Receives widget tap; sends intent to `VoiceRecordingService`.
- `ui/widget/WidgetConfigActivity` (optional for settings)

### Voice Capture and Processing
- `service/voice/VoiceRecordingService`
  - Purpose: Foreground service managing audio capture lifecycle.
  - Interfaces:
    - `startCapture()` / `stopCapture()`
    - Emits partial/final transcription to UI via local broadcast/Flow.
  - Dependencies: `ai/speech/AudioRecordingManager`, `ai/speech/SpeechRecognitionService`.
- `ai/speech/AudioRecordingManager`
  - Purpose: Microphone session, silence detection.
- `ai/speech/SpeechRecognitionService`
  - Purpose: On-device ASR integration; exposes `transcribe(audio): Flow<TranscriptSegment>`.

### Parsing and Validation
- `ai/parsing/TransactionParser`
  - Purpose: On-device Gemini Nano prompt → structured JSON.
  - Interface: `suspend fun parse(text: String, context: ParsingContext): ParsedResult`.
- `ai/parsing/StructuredOutputValidator`
  - Purpose: Validate schema, USD-only, split constraints (share ≤ overall), required fields.
  - Interface: `fun validate(result: ParsedResult): ValidationResult`.
- `ai/parsing/ParsingPrompts`
  - Purpose: Prompt templates + few-shot examples.

### Confirmation UI
- `ui/confirmation/TransactionConfirmationActivity`
  - Purpose: Display draft, accept voice corrections, confirm/cancel.
  - Interface: Binds `ConfirmationViewModel` state; triggers mic and save.
- `ui/confirmation/ConfirmationViewModel`
  - Purpose: Hold draft `Transaction`, apply corrections.
  - Interface:
    - `applyCorrection(utterance: String)`
    - `confirm()` / `cancel()`
  - Dependencies: `TransactionRepository`, `TransactionParser` (for correction intents), `SpeechRecognitionService` for inline corrections.

### Data Layer
- `data/model/Transaction` (Room @Entity)
- `data/model/SheetReference`, `data/model/TransactionType`, `data/model/TransactionStatus`
- `data/local/TransactionDao` (CRUD + queue queries)
- `data/repository/TransactionRepository`
  - Interface:
    - `suspend fun saveDraft(t: Transaction): Result<Unit>`
    - `suspend fun confirm(tId: String): Result<Unit>`
    - `suspend fun enqueueForSync(tId: String): Result<Unit>`
    - `suspend fun syncPending(): SyncResult`
- `data/remote/SheetsClient` (Retrofit/OkHttp)
  - Interface: `suspend fun appendRow(values: List<String>): Result<SheetAppendResponse>`
  - Mapping: Implements exact column order from steering docs.

### Auth
- `auth/AuthRepository`
  - Purpose: Google Sign-In, token storage/refresh.
  - Interface: `suspend fun getAccessToken(): String`, `suspend fun signOut()`.

### Sync
- `worker/SyncWorker`
  - Purpose: Drain queue; retry with exponential backoff; mark statuses.
  - Triggers: Connectivity and charging constraints; manual trigger after confirm.

### Utilities
- `util/DateUtils`, `util/CurrencyFormatter`, `util/Result`, `util/Network.kt`

## Data Models

### Transaction (Room)
- id: String (UUID)
- createdAt: Instant
- userLocalDate: LocalDate
- amountUsd: BigDecimal?
- merchant: String
- description: String?
- type: TransactionType (Expense|Income|Transfer)
- expenseCategory: String?
- incomeCategory: String?
- tags: List<String>
- account: String?
- splitOverallChargedUsd: BigDecimal?
- note: String?
- confidence: Float
- correctionsCount: Int
- source: String = "voice"
- status: TransactionStatus = DRAFT|CONFIRMED|QUEUED|POSTED|FAILED
- sheetRef: SheetReference?

### ParsedResult
- amountUsd: BigDecimal?
- merchant: String
- description: String?
- type: TransactionType
- expenseCategory: String?
- incomeCategory: String?
- tags: List<String>
- userLocalDate: LocalDate
- account: String?
- splitOverallChargedUsd: BigDecimal?
- note: String?
- confidence: Float

### SheetReference
- spreadsheetId: String
- sheetId: String
- rowIndex: Long?

## Error Handling

### Error Scenarios
1. ASR failure or low confidence
   - Handling: Prompt concise rephrase; limit attempts; fall back to manual edit in UI.
   - User Impact: Short TTS/text hint; retry option.
2. Parsing invalid/ambiguous JSON
   - Handling: Re-run parser with stricter schema; request targeted clarification.
   - User Impact: Highlight fields needing confirmation.
3. Split amounts inconsistent (share > overall)
   - Handling: Block confirm; ask for correction.
   - User Impact: Error message and voice prompt.
4. OAuth errors (expired/invalid)
   - Handling: Refresh token automatically; if refresh fails, prompt re-auth without losing queue.
   - User Impact: Brief sign-in prompt; queued items preserved.
5. Network failures when posting
   - Handling: Exponential backoff via WorkManager; mark failed after max retries; keep for manual retry.
   - User Impact: Status shown as queued/failed; retries transparent.
6. Sheets append conflict or API errors
   - Handling: Log structured error; retry safe; never duplicate rows (idempotency via local id + sheetRef).

## Testing Strategy

### Unit Testing
- Parser and validator: fixtures for 5 steering examples and edge cases.
- Repository mapping: Transaction → row mapping order and conditional blanks.
- Date/currency utils; Sync backoff logic.

### Integration Testing
- Widget → Service → ASR → Parser → UI (mock ASR/Parser for determinism).
- Offline queue: confirm while offline, then simulate online and verify append.
- Auth refresh path during sync.

### End-to-End Testing
- Happy path expense with tags and account.
- Split expense with overall charged.
- Income and transfer flows.
- Error recoveries: ASR retry, parsing clarification, network retry.
