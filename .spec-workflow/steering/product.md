# Product Overview

## Product Purpose
Build an Android app that lets a user verbally log financial transactions into a Google Spreadsheet using only on-device AI processing. The app removes the friction of manual entry (currently via a Google Form) by parsing natural speech into a structured transaction, confirming with the user in a voice-first loop, and posting to Google Sheets. AI runs fully on-device for privacy; network is only required to sync the final transaction to the sheet.

## Target Users
- Single primary user (initially) on a Google Pixel 7a who manages personal expenses.
- Secondary: Power users comfortable with Google Sheets who want voice-first capture with full data ownership and privacy.

## Target User Flow
1. User taps an Android home screen widget.
2. Widget starts voice capture and records a short utterance (e.g., "I spent $23 at Starbucks this morning for coffee").
3. On-device ASR transcribes audio to text; on-device LLM (Gemini Nano via ML Kit GenAI) parses text into structured transaction data.
4. App shows a compact confirmation UI (widget or activity) with parsed fields.
5. User provides verbal corrections (e.g., "actually twenty-five", "tags: Auto-Paid, Subscription", or "overall charged one twenty on my card"); the loop updates the draft transaction.
6. Loop continues until user says "yes" or "looks good".
7. App posts transaction to Google Sheets via the Sheets API. If offline, it queues and syncs automatically when online.

## Technical Constraints
- Device: Google Pixel 7a (Android 14+ recommended).
- AI Processing: On-device only. Use Gemini Nano via ML Kit GenAI APIs for NLU/structuring. Use offline speech recognition; no cloud AI calls.
- Privacy: No cloud-based AI processing; user owns data. Minimal permissions.
- Connectivity: Work offline for capture/parsing and confirmation; require network only to post to Google Sheets.
- Currency: USD-only for v1 (no multi-currency handling).
- Data Destination: Existing user-owned Google Spreadsheet (configurable spreadsheet and tab).

## Spreadsheet Columns Mapping (Exact Sheet)
Use the existing columns as the contract. The app will append rows with the following mapping for Expense/Income/Transfer types.

Columns (left-to-right as provided):
- Timestamp | Date | Amount? (No $ sign) | Description | Type | Expense Category | Tags | Income Category | [empty] | Account / Credit Card | [If splitwise] how much overall charged to my card? | Transfer Category | Account transfer is going into | [remaining columns left blank]

Field mapping rules:
- Timestamp: UTC timestamp when saved (e.g., 2025-07-15 04:21:47)
- Date: User-local date of the expense (parsed or "today")
- Amount? (No $ sign): For Expense/Income, the amount in USD without symbol
  - Expense: user's personal share amount
  - Income: income amount
  - Transfer: blank
- Description: Merchant or description; may include short note
- Type: one of Expense | Income | Transfer
- Expense Category: set for Expense; blank for Income/Transfer
- Tags: comma-separated list (e.g., "Auto-Paid, Splitwise, Subscription")
- Income Category: set for Income; blank otherwise
- [empty]: left blank
- Account / Credit Card: Card matching will be handled through a preset list of user cards that can be matched based on spoken input
- [If splitwise] how much overall charged to my card?: total amount charged to card in split cases; blank otherwise
  - Example: You paid $120 for dinner, user's share is $60 → Amount=60, Overall Charged=120, Tags includes "Splitwise"
- Transfer Category: set only for Type=Transfer
- Account transfer is going into: set only for Type=Transfer
- Remaining trailing columns: left blank

## Data Models and Transaction Schema
- Transaction fields:
  - id (UUID), createdAt (UTC ISO), userLocalDate, amountUsd (decimal), merchant (string), description (string), type ("Expense"|"Income"|"Transfer"), expenseCategory? (string), incomeCategory? (string), tags (string[]), account (string), splitOverallChargedUsd? (decimal), note? (string), confidence (0–1), correctionsCount (int), source ("voice"), status ("draft"|"confirmed"|"queued"|"posted"|"failed"), sheetRef (spreadsheetId/sheetId/row if posted).
- Parsing intent: support capturing both the user share amount and, when present, the overall charged amount for split expenses. If only one amount is spoken, treat it as the Amount field and leave Overall Charged blank.

## API Contracts (Structured Parsing)
- Input: Recognized text from ASR, optional context (recent merchants/categories/tags, default account, user locale, time).
- Output (JSON): { amountUsd, merchant, description?, type, expenseCategory?, incomeCategory?, tags[], userLocalDate, account?, splitOverallChargedUsd?, note?, confidence }
- Constraints: Strict JSON schema; enforce USD; reject currency tokens; request clarification on invalid/ambiguous fields.

### Sample Utterances → Expected Parsed Output (5 examples)
1. "YouTube Premium seven dollars, personal, tags Auto-Paid Subscription" → { amountUsd: 7.00, merchant: "YouTube Premium", type: "Expense", expenseCategory: "Personal", tags: ["Auto-Paid","Subscription"] }
2. "Supermarket in Vienna one eighty seven, groceries, tag Europe Trip Summer 2025, Bilt Card five two one seven" → { amountUsd: 1.87, merchant: "Supermarket in Vienna", type: "Expense", expenseCategory: "Groceries", tags: ["Europe Trip Summer 2025"], account: "Bilt Card (5217)" }
3. "Dinner at La Fiesta thirty dollars charged to my card, my share is twenty, tag Splitwise" → { amountUsd: 20.00, splitOverallChargedUsd: 30.00, merchant: "La Fiesta", type: "Expense", expenseCategory: "Dining", tags: ["Splitwise"] }
4. "Income, paycheck two thousand, tag July" → { amountUsd: 2000.00, type: "Income", incomeCategory: "Salary", tags: ["July"] }
5. "Transfer fifty from checking to savings" → { type: "Transfer", amountUsd: null, expenseCategory: null, incomeCategory: null, tags: [], note: "transfer 50 checking→savings" }

## Google Sheets Integration
- Auth: OAuth 2.0 with the Sheets scope, using Android Account/Google Sign-In. Store tokens securely (EncryptedSharedPreferences/Hardware-backed Keystore). Request minimal scopes and allow account switch.
- Posting: WorkManager job posts confirmed transactions; retries with exponential backoff; handles token refresh; conflict-safe append (valueInputOption=USER_ENTERED) to a configured tab following the column order above.
- Offline: Queue entries locally; when connectivity returns, batch post.

## Confirmation UI and Voice Feedback Loop
- UI: Compact activity or bottom sheet launched from widget; single screen with fields and a large mic button.
- Flow: Display parsed draft; prompt user with concise TTS; accept verbal corrections (amount, merchant, category, tags, account, overall charged, date). Structured tag phrases: "tags: Auto-Paid, Subscription"; split phrases: "overall charged one twenty".
- Termination: "yes"/"looks good"/"save" confirms and enqueues post; "cancel" discards.

## Error Handling and Edge Cases
- Ambiguous amounts ("twenty-three fifty") → clarify or default with lower confidence.
- Unknown merchants → set Description accordingly and capture in tags/note if needed.
- Split expenses with two amounts (share + overall) → validate consistency; ensure Amount ≤ Overall.
- Background noise/unclear speech → request repeat with brief prompt; cap retries and fail gracefully.
- Offline posting → queue with visible status; notify when synced.
- Auth failures → prompt re-auth; never drop transactions.

## Performance and Battery
- Targets: Parsing <3s per utterance; end-to-end <30s; accuracy >90% for common transactions.
- Techniques: Short recordings; stop listening on silence; run LLM with structured output to reduce compute; batch network posts; use foreground service only while recording; defer heavy work to WorkManager.

## Testing Strategy (Test-Ready)
1. Test scenarios per user story with acceptance criteria (happy paths, edge cases, failure modes).
2. API contract tests:
   - ASR text → structured JSON parsing using the 5 fixtures above plus minimal variants (tags, split amounts, account parsing).
   - Sheets posting (success, auth errors, network failures, retries) with the exact column mapping.
   - Widget → Activity/Service intents and parameters.
3. Performance benchmarks with automated timing assertions (<3s parsing).
4. Mock strategies:
   - Replace Gemini Nano parser with deterministic mock returning fixtures.
   - Stub Sheets API with local fake server or in-memory adapter.
   - Simulate SpeechRecognizer outputs for CI.
5. Integration tests:
   - Widget lifecycle + foreground service + confirmation UI.
   - Offline queue to online sync transition.
   - Error recovery flows (auth refresh, network backoff).

## Product Principles
1. Privacy-first and on-device AI only; transparent scopes and storage.
2. Speed-first capture; voice-first corrections; minimal taps.
3. Offline-first with reliable sync; never lose a transaction.
4. Accessibility: Voice-first, large controls, screen-reader compatible.

## Success Metrics
- Parsing accuracy: >90% for common transactions.
- Parsing latency: <3s median; end-to-end flow <30s.
- Post success rate: >99% within 24h including retries.
- Adoption proxy: repeat widget usage per day; queue drain times.

## Out of Scope (Initial)
- Receipt photo processing, bank account aggregation, advanced analytics, multi-user.

## Implementation Phases
- Phase 1: Prototype widget, ASR, structured parsing mock, basic confirmation UI.
- Phase 2: Sheets OAuth + posting with offline queue; Room persistence; exact column mapping.
- Phase 3: Voice correction loop (tags, account, split overall charged), ambiguity handling, metrics & logging.
- Phase 4: Polish, battery/perf tuning, accessibility and internationalization pass.

## Risks and Mitigations
- Offline ASR variability → pre-download language models; fall back to concise prompts.
- LLM format drift → enforce strict JSON schema with validators and retry on invalid output.
- OAuth complexity → use proven libraries; narrow scopes; robust token handling.
- Battery spikes → shorten sessions, limit model context, defer work to background.