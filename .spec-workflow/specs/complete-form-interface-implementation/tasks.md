# Tasks Document

- [x] 1. Add transfer fields to Transaction entity and migration
  - File: `app/src/main/java/com/voiceexpense/data/model/Transaction.kt`
  - File: `app/src/main/java/com/voiceexpense/data/local/AppDatabase.kt` (add Room migration N→N+1)
  - File: `app/src/main/java/com/voiceexpense/data/model/Converters.kt` (ensure list/decimal converters remain compatible)
  - Purpose: Support `transferCategory` and `transferDestination` with safe DB upgrade
  - _Leverage: existing Room setup in AppDatabase.kt_
  - _Requirements: Requirement 1, Requirement 6_
  - _Prompt: Role: Android Room/SQLite Engineer | Task: Add nullable `transferCategory` and `transferDestination` to Transaction entity and implement a Room migration that adds these columns without data loss. Update DAO if needed. | Restrictions: Do not drop the transactions table; migration must be additive and idempotent; maintain existing converters. | Success: App builds, migration test passes, existing transactions load with null defaults._

- [x] 2. Create configuration storage (Room) for categories/accounts/tags
  - File: `app/src/main/java/com/voiceexpense/data/config/ConfigOption.kt` (entity + enums)
  - File: `app/src/main/java/com/voiceexpense/data/config/DefaultValue.kt` (entity)
  - File: `app/src/main/java/com/voiceexpense/data/config/ConfigDao.kt`
  - File: `app/src/main/java/com/voiceexpense/data/config/ConfigRepository.kt`
  - File: `app/src/main/java/com/voiceexpense/data/local/AppDatabase.kt` (register new entities + migration N+1→N+2)
  - Purpose: Persist and observe option sets and defaults
  - _Leverage: existing Room patterns in data/local_
  - _Requirements: Requirement 4, Requirement 6_
  - _Prompt: Role: Android Data Engineer (Room) | Task: Define Room entities/DAO/repository for configurable option sets (ExpenseCategory, IncomeCategory, TransferCategory, Account, Tag) and default selections; add migration to create new tables. | Restrictions: Use Flow for observers; keep schema additive; ensure indices for lookup/order. | Success: CRUD works, flows emit updates, migration succeeds._

- [x] 3. Extend Settings UI for dropdown configuration
  - File: `app/src/main/res/layout/activity_settings.xml` (add configuration section)
  - File: `app/src/main/java/com/voiceexpense/ui/common/SettingsActivity.kt` (list, add, edit, delete, reorder; set defaults)
  - Purpose: Allow users to manage options and defaults
  - _Leverage: existing SettingsActivity structure and SharedPreferences access_
  - _Requirements: Requirement 4_
  - _Prompt: Role: Android UI Developer (Material) | Task: Add UI in Settings to manage option lists and defaults per type; persist via ConfigRepository. | Restrictions: Follow Material patterns; keep screens simple; immediate reflection in form. | Success: Users can add/edit/delete/reorder options and set defaults; changes persist and are observable._

- [x] 4. Replace free-text controls with proper inputs in confirmation form
  - File: `app/src/main/res/layout/activity_transaction_confirmation.xml` (swap `EditText` to Material DatePicker trigger, dropdowns, multi-select tags, chips)
  - File: `app/src/main/java/com/voiceexpense/ui/confirmation/TransactionConfirmationActivity.kt` (wire pickers; show MM/DD display, ISO storage)
  - Purpose: Correct input types and better UX
  - _Leverage: MaterialComponents, existing fields, current binding logic_
  - _Requirements: Requirement 2, Requirement 3_
  - _Prompt: Role: Android UI/UX Engineer | Task: Implement DatePicker, dropdowns bound to ConfigRepository, and a multi-select dialog for tags; display selected tags as chips; keep voice/AI prefill. | Restrictions: Maintain current navigation and buttons; avoid regressions to speech correction; ensure accessibility. | Success: Inputs use correct controls; interactions are smooth; TalkBack labels present._

- [x] 5. Implement conditional field visibility by transaction type
  - File: `app/src/main/java/com/voiceexpense/ui/confirmation/ConfirmationViewModel.kt` (state for visible fields)
  - File: `app/src/main/java/com/voiceexpense/ui/confirmation/TransactionConfirmationActivity.kt` (observe and toggle)
  - Purpose: Show relevant fields per type (Expense/Income/Transfer)
  - _Leverage: existing ViewModel + Flow state_
  - _Requirements: Requirement 3_
  - _Prompt: Role: Android State Management Engineer | Task: Add derived UI state for visibility based on `TransactionType`; update Activity to hide/show fields accordingly. | Restrictions: Avoid heavy recomposition; keep logic in ViewModel. | Success: Switching type updates visibility instantly and consistently._

- [x] 6. Add ValidationEngine and real-time validation
  - File: `app/src/main/java/com/voiceexpense/ui/confirmation/ValidationEngine.kt`
  - File: `app/src/main/java/com/voiceexpense/ui/confirmation/ConfirmationViewModel.kt` (integrate validation; gate Confirm)
  - Purpose: Enforce required fields and business rules with inline errors
  - _Leverage: existing parsing/confirmation flows_
  - _Requirements: Requirement 5_
  - _Prompt: Role: Android/Kotlin Engineer | Task: Implement validation rules (required fields per type, numeric positivity, overall ≥ amount, transfer dependencies) and expose field error states; disable Confirm until valid. | Restrictions: Keep pure functions; no DB I/O in validation. | Success: Inline errors appear on invalid input; confirm enables only when valid._

- [x] 7. Pre-fill smart defaults and remember selections
  - File: `app/src/main/java/com/voiceexpense/ui/confirmation/ConfirmationViewModel.kt` (apply defaults when creating/editing)
  - Purpose: Default date to today; pre-select defaults for categories/accounts without overriding AI values
  - _Leverage: ConfigRepository defaults; existing draft set flow_
  - _Requirements: Requirement 3, Requirement 4_
  - _Prompt: Role: Android UX Engineer | Task: Apply defaults when field empty and AI didn’t supply a value; keep user changes sticky during session. | Restrictions: Do not override non-empty values; keep logic deterministic. | Success: New drafts show sensible defaults; user can override easily._

- [x] 8. Migration and repository tests
  - File: `app/src/test/java/com/voiceexpense/data/local/TransactionDaoTest.kt` (extend)
  - File: `app/src/test/java/com/voiceexpense/data/repository/TransactionRepositoryTest.kt` (extend)
  - File: `app/src/test/java/com/voiceexpense/ui/confirmation/ConfirmationViewModelTest.kt` (validation gating)
  - Purpose: Verify migrations, repository behavior, and validation gating
  - _Leverage: existing test patterns_
  - _Requirements: Requirement 6, Requirement 5_
  - _Prompt: Role: Android Test Engineer | Task: Add tests for Room migration adding transfer fields, ConfigRepository CRUD/defaults, and ViewModel validation gating Confirm. | Restrictions: Keep tests isolated; use in-memory DB for unit tests. | Success: Tests pass and cover key behaviors._

- [x] 9. UI tests for new controls and flows
  - File: `app/src/androidTest/...` (new tests under `ui/confirmation` and `ui/common`)
  - Purpose: Validate date picker, dropdown selection, tags multi-select, and conditional visibility; confirm disabled/enabled transitions
  - _Leverage: existing instrumented test setup_
  - _Requirements: Requirement 2, Requirement 3, Requirement 5_
  - _Prompt: Role: Android QA (Espresso) | Task: Add instrumented tests covering new UI interactions and validation states. | Restrictions: Avoid flaky timing; use idling where needed. | Success: Tests reliably pass on CI and local._

- [x] 10. Performance polish
  - File: `app/src/main/java/com/voiceexpense/ui/confirmation/TransactionConfirmationActivity.kt` and adapters
  - Purpose: Ensure lists scale (20+ tags/accounts) and form opens <500ms
  - _Leverage: Recycler/Adapter efficiencies; avoid heavy observers_
  - _Requirements: Performance NFRs_
  - _Prompt: Role: Android Performance Engineer | Task: Profile and optimize adapters and observers; minimize main-thread work on open. | Restrictions: No premature optimization; measure with systrace/logs. | Success: Meets target open/render/validation timings._
