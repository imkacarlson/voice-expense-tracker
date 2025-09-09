# Product Overview

## Product Purpose
Build an Android app that lets users log financial transactions into a Google Spreadsheet using voice OR text input with on-device AI processing. The app removes the friction of manual entry by parsing natural speech or text into a structured transaction, allowing manual editing and confirmation in an intuitive form interface, and posting to Google Sheets. AI runs fully on-device for privacy; network is only required to sync the final transaction to the sheet.

## Target Users
- Single primary user (initially) on a Google Pixel 7a who manages personal expenses.
- Secondary: Power users comfortable with Google Sheets who want flexible capture (voice OR text) with full data ownership and privacy.

## Target User Flow
1. User has two options to start:
   - Tap an Android home screen widget for voice capture
   - Open the app and type a transaction description directly
2. **Voice Path**: Widget starts voice capture and records a short utterance (e.g., "I spent $23 at Starbucks this morning for coffee")
3. **Text Path**: User types transaction in the main app input field (e.g., "Starbucks $5 latte charged to my Citi card")
4. On-device ASR transcribes audio to text (voice path) or uses typed text directly; on-device LLM (Gemma 3 1B via MediaPipe Tasks) parses text into structured transaction data.
5. App shows confirmation screen with a **complete form interface** containing all parsed fields as editable inputs.
6. User can:
   - **Edit any field directly** using the form inputs (amounts, merchant, category, tags, account, date, etc.)
   - **Use voice corrections** optionally by tapping "Speak Correction" 
   - **Use dropdowns** for categories, accounts, tags (configurable in settings)
   - **Use date picker** for transaction date
7. User confirms when satisfied; app posts transaction to Google Sheets via Google Apps Script Web App. If offline, it queues and syncs automatically when online.
8. **Home Screen History**: Main screen shows the 10 most recent transactions with status indicators (Draft, Queued, Confirmed, Posted, Failed) that users can click to view/edit.

## Technical Constraints
- Device: Google Pixel 7a (Android 14+ recommended).
- AI Processing: On-device only. Use Gemma 3 1B via MediaPipe Tasks (.task file) for NLU/structuring. Use offline speech recognition; no cloud AI calls.
- Privacy: No cloud-based AI processing; user owns data. Minimal permissions.
- Connectivity: Work offline for capture/parsing and confirmation; require network only to post to Google Sheets.
- Currency: USD-only for v1 (no multi-currency handling).
- Data Destination: Existing user-owned Google Spreadsheet (configurable spreadsheet and tab).

## Spreadsheet Columns Mapping (Exact Sheet)
Based on user's actual transaction data, the app will append rows with the following mapping:

**Column Structure:**
1. **Timestamp** - Auto-filled by Apps Script when transaction is saved
2. **Date** - User-selectable date (can be different from timestamp for backdated entries)
3. **Amount? (No $ sign)** - User's personal share amount (number input)
4. **Description** - Merchant/description (free text)
5. **Type** - Dropdown: Expense | Income | Transfer
6. **Expense Category** - Configurable dropdown (for expenses only)
7. **Tags** - Configurable multi-select dropdown (comma-separated)
8. **Income Category** - Configurable dropdown (for income only)
9. **[empty]** - Left blank
10. **Account / Credit Card** - Configurable dropdown of user's accounts/cards
11. **[If splitwise] how much overall charged to my card?** - Number input (for split expenses)
12. **Transfer Category** - Configurable dropdown (for transfers only)
13. **Account transfer is going into** - Configurable dropdown (for transfers only)

## Form Interface Requirements

### **Input Types by Field:**
- **Date**: Date picker widget (allows logging past transactions)
- **Amount**: Number input with decimal support
- **Description**: Free text input (merchant/description)
- **Type**: Dropdown with 3 options (Expense/Income/Transfer)
- **Expense Category**: Configurable dropdown (user manages options in settings)
- **Tags**: Configurable multi-select dropdown (user manages options in settings)
- **Income Category**: Configurable dropdown (user manages options in settings)
- **Account/Credit Card**: Configurable dropdown (user manages options in settings)
- **Overall Charged**: Number input (for splitwise transactions)
- **Transfer Category**: Configurable dropdown (user manages options in settings)
- **Transfer Destination**: Configurable dropdown (user manages options in settings)

### **Dropdown Configuration:**
All dropdown fields must be configurable in the app settings. Users should be able to:
- Add new options to any dropdown
- Edit existing options
- Remove unused options
- Reorder options by preference

## Data Models and Transaction Schema
- Transaction fields:
  - id (UUID), createdAt (UTC ISO), userLocalDate, amountUsd (decimal), merchant (string), description (string), type ("Expense"|"Income"|"Transfer"), expenseCategory? (string), incomeCategory? (string), tags (string[]), account (string), splitOverallChargedUsd? (decimal), note? (string), confidence (0–1), correctionsCount (int), source ("voice"|"text"), status ("draft"|"confirmed"|"queued"|"posted"|"failed"), sheetRef (spreadsheetId/sheetId/row if posted).
- Parsing intent: support capturing both the user share amount and, when present, the overall charged amount for split expenses. If only one amount is spoken, treat it as the Amount field and leave Overall Charged blank.

## API Contracts (Structured Parsing)
- Input: Recognized text from ASR or direct text input, optional context (recent merchants/categories/tags, default account, user locale, time).
- Output (JSON): { amountUsd, merchant, description?, type, expenseCategory?, incomeCategory?, tags[], userLocalDate, account?, splitOverallChargedUsd?, note?, confidence }
- Constraints: Strict JSON schema; enforce USD; reject currency tokens; request clarification on invalid/ambiguous fields.

### Sample Utterances → Expected Parsed Output (5 examples)
1. "YouTube Premium seven dollars, personal, tags Auto-Paid Subscription" → { amountUsd: 7.00, merchant: "YouTube Premium", type: "Expense", expenseCategory: "Personal", tags: ["Auto-Paid","Subscription"] }
2. "Supermarket in Vienna one eighty seven, groceries, tag Europe Trip Summer 2025, Bilt Card five two one seven" → { amountUsd: 1.87, merchant: "Supermarket in Vienna", type: "Expense", expenseCategory: "Groceries", tags: ["Europe Trip Summer 2025"], account: "Bilt Card (5217)" }
3. "Dinner at La Fiesta thirty dollars charged to my card, my share is twenty, tag Splitwise" → { amountUsd: 20.00, splitOverallChargedUsd: 30.00, merchant: "La Fiesta", type: "Expense", expenseCategory: "Dining", tags: ["Splitwise"] }
4. "Income, paycheck two thousand, tag July" → { amountUsd: 2000.00, type: "Income", incomeCategory: "Salary", tags: ["July"] }
5. "Transfer fifty from checking to savings" → { type: "Transfer", amountUsd: null, expenseCategory: null, incomeCategory: null, tags: [], note: "transfer 50 checking→savings" }

## Google Sheets Integration
- Auth: OAuth 2.0 with `userinfo.email` scope using Google Sign-In. Apps Script validates token/email server-side. Store tokens securely (EncryptedSharedPreferences/Hardware-backed Keystore).
- Posting: WorkManager job posts confirmed transactions to the Apps Script Web App; retries with exponential backoff; handles token refresh; server appends to the configured sheet.
- Offline: Queue entries locally; when connectivity returns, batch post.

## Confirmation UI and Form Interface
- **UI**: Full-screen form activity launched from widget or main app with complete transaction editing capabilities.
- **Form Fields**: All transaction fields are directly editable with appropriate input types (text, number, dropdown, date picker, multi-select).
- **Smart Defaults**: AI parsing pre-fills form fields with high confidence; user can edit any field manually.
- **Validation**: Client-side validation ensures required fields (merchant, amount for expenses/income) are filled before saving.
- **Voice Corrections**: Optional "Speak Correction" button allows voice-based edits for users who prefer voice interaction.
- **Termination**: "Confirm" button validates and saves transaction, enqueues sync; "Cancel" discards draft.

## Home Screen Experience
- **Recent Transactions List**: Shows 10 most recent transactions with:
  - Merchant name and date
  - Amount and transaction type
  - Status indicators with color coding:
    - Draft (gray) - incomplete transactions
    - Queued (amber) - waiting for sync
    - Confirmed (blue) - saved locally
    - Posted (green) - successfully synced to Google Sheets
    - Failed (gray) - sync errors requiring attention
- **Click Actions**: Users can click any transaction to view details or edit drafts
- **Auto-truncation**: List automatically maintains only 10 most recent entries

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
   - Apps Script posting (success, auth errors, network failures, retries) with the exact column mapping.
   - Widget → Activity/Service intents and parameters.
3. Performance benchmarks with automated timing assertions (<3s parsing).
4. Mock strategies:
   - Replace Gemma 3 parser with deterministic mock returning fixtures.
   - Stub Apps Script client with local fake server or in-memory adapter.
   - Simulate SpeechRecognizer outputs for CI.
5. Integration tests:
   - Widget lifecycle + foreground service + confirmation UI.
   - Offline queue to online sync transition.
   - Error recovery flows (auth refresh, network backoff).

## Product Principles
1. Privacy-first and on-device AI only; transparent scopes and storage.
2. Flexible input (voice OR text) with intuitive form-based confirmation.
3. Offline-first with reliable sync; never lose a transaction.
4. Accessibility: Multiple input methods, large controls, screen-reader compatible.

## Success Metrics
- Parsing accuracy: >90% for common transactions.
- Parsing latency: <3s median; end-to-end flow <30s.
- Post success rate: >99% within 24h including retries.
- User preference: Track usage split between voice vs text input.
- Adoption proxy: repeat usage per day; queue drain times.

## Out of Scope (Initial)
- Receipt photo processing, bank account aggregation, advanced analytics, multi-user.

## Implementation Phases
- Phase 1: Form interface with manual input, basic AI parsing, home screen history.
- Phase 2: Apps Script posting with offline queue; Room persistence; exact column mapping.
- Phase 3: Voice input path, configurable dropdowns, settings management.
- Phase 4: Polish, battery/perf tuning, accessibility and internationalization pass.

## Risks and Mitigations
- Offline ASR variability → pre-download language models; fall back to concise prompts.
- LLM format drift → enforce strict JSON schema with validators and retry on invalid output.
- OAuth complexity → use proven libraries; narrow scopes; robust token handling.
- Battery spikes → shorten sessions, limit model context, defer work to background.
- Small model accuracy → hybrid prompting strategies, field-by-field parsing fallbacks.