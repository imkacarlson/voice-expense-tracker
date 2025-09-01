# Tasks Document

- [x] 0. Establish testing stack and policy
  - File: app/build.gradle.kts; app/src/test/java/com/voiceexpense/testutil/*; app/src/androidTest/java/com/voiceexpense/testutil/*
  - Add test deps: JUnit, Robolectric, Espresso, MockK, Turbine, Truth (or AssertJ), Coroutines test
  - Configure testOptions (unitTests.returnDefaultValues=true), JVM target, and runner for androidTest
  - Add base utils: CoroutineTestRule/MainDispatcherRule, FakeClock, TestDispatchers
  - Purpose: Enable writing tests alongside implementation (TDD cadence)
  - Policy: For each component task (3–16), add/extend unit tests before moving on
  - _Leverage: tech.md Testing Strategy_
  - _Requirements: Non-functional: Reliability/Testing_

- [x] 1. Initialize Android project and module
  - File: project root (Gradle wrappers, settings)
  - Create Kotlin Android app (minSdk 34 target), package `com.voiceexpense`
  - Add dependencies: Hilt, Room, WorkManager, Retrofit/OkHttp, Play Services Auth, ML Kit Speech, ML Kit GenAI, Kotlin coroutines
  - Purpose: Establish buildable baseline
  - _Leverage: tech.md Build & Dependencies_
  - _Requirements: All (foundation)_

- [x] 2. Add manifest, permissions, and baseline resources
  - File: app/src/main/AndroidManifest.xml
  - Declare permissions: RECORD_AUDIO, FOREGROUND_SERVICE, INTERNET; add app widget provider and service declarations; notification channel meta
  - Purpose: Enable voice service, widget, and network sync
  - _Leverage: structure.md manifest placement_
  - _Requirements: 1, 2, 4, 7_

- [ ] 3. Implement Room database and models
  - File: app/src/main/java/com/voiceexpense/data/model/Transaction.kt
  - Define Transaction, TransactionType, TransactionStatus, SheetReference; add TypeConverters for List<String>, BigDecimal
  - Purpose: Persist drafts/queue
  - _Leverage: structure.md Data Models_
  - _Requirements: 3, 4, 7, 9_

- [ ] 4. Create DAO and database
  - File: app/src/main/java/com/voiceexpense/data/local/TransactionDao.kt; AppDatabase.kt
  - CRUD for drafts and queue; queries for pending sync
  - Purpose: Storage layer for transactions
  - _Leverage: Room patterns_
  - _Requirements: 7, 9_

- [ ] 5. Build `TransactionRepository`
  - File: app/src/main/java/com/voiceexpense/data/repository/TransactionRepository.kt
  - Methods: saveDraft, confirm, enqueueForSync, syncPending, mapToSheetRow
  - Purpose: Single entry for data ops and mapping
  - _Leverage: design mapping rules_
  - _Requirements: 5, 7_

- [ ] 6. Implement Google Sheets client
  - File: app/src/main/java/com/voiceexpense/data/remote/SheetsClient.kt
  - Retrofit/OkHttp setup; append row with USER_ENTERED; column order per steering
  - Purpose: Remote posting
  - _Leverage: tech.md Sheets API_
  - _Requirements: 5, 7, 9_

- [ ] 6.1 Sheets client unit tests
  - File: app/src/test/java/com/voiceexpense/data/remote/SheetsClientTest.kt
  - Use MockWebServer; validate request body/order; map errors to Result
  - Purpose: Verify request/response handling
  - Timing: Write alongside Task 6
  - _Leverage: OkHttp MockWebServer_
  - _Requirements: 5, 7, 9

- [ ] 7. Add AuthRepository with secure storage
  - File: app/src/main/java/com/voiceexpense/auth/AuthRepository.kt
  - Google Sign-In; token retrieval/refresh; EncryptedSharedPreferences + keystore
  - Purpose: Minimal-scope OAuth for Sheets
  - _Leverage: tech.md Security & Auth_
  - _Requirements: 6, 7_

- [ ] 7.1 AuthRepository unit tests
  - File: app/src/test/java/com/voiceexpense/auth/AuthRepositoryTest.kt
  - Test token save/load/refresh; expired token path; no token path
  - Purpose: Ensure secure, reliable token handling
  - Timing: Write alongside Task 7
  - _Leverage: EncryptedSharedPreferences test helpers/mocks_
  - _Requirements: 6, 7

- [ ] 8. Create SyncWorker and integration
  - File: app/src/main/java/com/voiceexpense/worker/SyncWorker.kt
  - Drain queue, backoff retries, update statuses; constraints for network
  - Purpose: Reliable background sync
  - _Leverage: WorkManager_
  - _Requirements: 7, 9_

- [ ] 8.1 SyncWorker tests
  - File: app/src/test/java/com/voiceexpense/worker/SyncWorkerTest.kt
  - Backoff, success/failure paths; auth refresh
  - Purpose: Reliable posting
  - Timing: Write alongside Task 8
  - _Leverage: WorkManager test utils
  - _Requirements: 7, 9

- [ ] 9. Implement on-device ASR services
  - File: app/src/main/java/com/voiceexpense/ai/speech/AudioRecordingManager.kt
  - Silence detection; manage microphone lifecycle
  - File: app/src/main/java/com/voiceexpense/ai/speech/SpeechRecognitionService.kt
  - On-device transcription API; expose Flow of segments
  - Purpose: Local transcription
  - _Leverage: ML Kit Speech_
  - _Requirements: 1, 2_

- [ ] 9.1 Speech recognition service tests
  - File: app/src/test/java/com/voiceexpense/ai/speech/SpeechRecognitionServiceTest.kt
  - Mock callbacks; verify Flow emissions and silence stop
  - Purpose: Validate ASR integration contract
  - Timing: Write alongside Task 9
  - _Leverage: Robolectric/Shadow APIs, MockK
  - _Requirements: 1, 2

- [ ] 10. Implement parsing with Gemini Nano
  - File: app/src/main/java/com/voiceexpense/ai/parsing/TransactionParser.kt
  - Convert transcript to structured JSON per schema
  - File: app/src/main/java/com/voiceexpense/ai/parsing/StructuredOutputValidator.kt
  - Enforce schema, USD-only, split constraints
  - File: app/src/main/java/com/voiceexpense/ai/parsing/ParsingPrompts.kt; ParsedResult.kt
  - Purpose: On-device NLU to transaction
  - _Leverage: ML Kit GenAI_
  - _Requirements: 3, 9, 10_

- [ ] 11. Foreground voice recording service
  - File: app/src/main/java/com/voiceexpense/service/voice/VoiceRecordingService.kt
  - Orchestrate ASR → parse; notification channel; broadcast results to UI
  - Purpose: Capture pipeline
  - _Leverage: Android services_
  - _Requirements: 1, 2, 3, 4_

- [ ] 12. Home screen widget
  - File: app/src/main/java/com/voiceexpense/ui/widget/ExpenseWidgetProvider.kt
  - Layout: app/src/main/res/layout/widget_expense.xml; Config XML in res/xml/
  - Tap → start service intent
  - Purpose: One-tap capture
  - _Leverage: Android Widget framework_
  - _Requirements: 1_

- [ ] 13. Confirmation UI and ViewModel
  - File: app/src/main/java/com/voiceexpense/ui/confirmation/TransactionConfirmationActivity.kt
  - File: app/src/main/java/com/voiceexpense/ui/confirmation/ConfirmationViewModel.kt
  - Layout: app/src/main/res/layout/activity_transaction_confirmation.xml
  - Show draft; accept corrections; confirm/cancel
  - Purpose: Voice-first correction loop
  - _Leverage: MVVM patterns_
  - _Requirements: 4, 11_

- [ ] 13.1 ConfirmationViewModel unit tests
  - File: app/src/test/java/com/voiceexpense/ui/confirmation/ConfirmationViewModelTest.kt
  - Test applyCorrection mappings, confirm/cancel transitions, error prompts
  - Purpose: Ensure correction loop logic
  - Timing: Write alongside Task 13–14
  - _Leverage: Coroutines test, Turbine
  - _Requirements: 4, 11

- [ ] 14. Voice correction integration
  - File: use existing SpeechRecognitionService in confirmation UI
  - Apply `applyCorrection(utterance)` to update draft
  - Purpose: Update fields via speech
  - _Leverage: parser + validator_
  - _Requirements: 4, 8, 9

- [ ] 15. Settings for spreadsheet and accounts
  - File: app/src/main/java/com/voiceexpense/ui/common/SettingsActivity.kt
  - Persist spreadsheetId, sheet/tab, known cards/accounts list
  - Purpose: Configure destination and matching
  - _Leverage: SharedPreferences/Room as needed_
  - _Requirements: 5, 8

- [ ] 16. DI setup with Hilt
  - File: app/src/main/java/com/voiceexpense/App.kt
  - Provide modules for Room, Retrofit, Auth, Repos, Workers
  - Purpose: Dependency injection
  - _Leverage: Hilt_
  - _Requirements: Non-functional: Architecture

- [ ] 17. Mapping to sheet row tests
  - File: app/src/test/java/com/voiceexpense/data/repository/TransactionRepositoryTest.kt
  - Verify exact column order and conditional blanks
  - Purpose: Prevent regressions
  - Timing: Write alongside Task 5
  - _Leverage: JUnit
  - _Requirements: 5

- [ ] 18. Parser and validator tests with fixtures
  - File: app/src/test/java/com/voiceexpense/ai/parsing/TransactionParserTest.kt
  - Include 5 steering examples + edge cases (ambiguous amounts, split)
  - Purpose: Ensure parsing correctness
  - Timing: Write alongside Task 10
  - _Leverage: fixtures
  - _Requirements: 3, 9, 10

- [ ] 19. Room DAO tests
  - File: app/src/test/java/com/voiceexpense/data/local/TransactionDaoTest.kt
  - CRUD, queue queries, migrations baseline
  - Purpose: Storage integrity
  - Timing: Write alongside Tasks 3–4
  - _Leverage: Room in-memory DB
  - _Requirements: 7

- [ ] 20. SyncWorker tests
  - File: app/src/test/java/com/voiceexpense/worker/SyncWorkerTest.kt
  - Backoff, success/failure paths; auth refresh
  - Purpose: Reliable posting
  - Timing: Write alongside Task 8
  - _Leverage: WorkManager test utils
  - _Requirements: 7, 9

- [ ] 21. Widget → Service integration test
  - File: app/src/androidTest/java/com/voiceexpense/ui/widget/WidgetIntegrationTest.kt
  - Tap launches service; flow to confirmation
  - Purpose: Entry flow reliability
  - Timing: Write alongside Tasks 12–13
  - _Leverage: Espresso/Robolectric
  - _Requirements: 1, 4

- [ ] 22. Accessibility and usability pass
  - File: res/strings.xml, layouts, contentDescription updates
  - Large tap targets, TalkBack labels, concise prompts
  - Purpose: Inclusive design
  - _Leverage: Android accessibility checklist
  - _Requirements: 11

- [ ] 23. Performance tuning
  - File: ai/model/ModelManager.kt and service lifecycles
  - Optimize model loading, silence thresholds, stop on idle
  - Purpose: Meet <3s parse, <30s E2E targets
  - _Leverage: tech.md Performance
  - _Requirements: 10

- [ ] 24. Build automation script
  - File: scripts/build_apk.py
  - Prereq checks; assembleDebug; optional install; output path
  - Purpose: Consistent packaging
  - _Leverage: tech.md helper script spec
  - _Requirements: Non-functional: DevEx

- [ ] 25. Documentation updates
  - File: README.md and docs/
  - Add setup (SDKs, permissions), model download notes, usage instructions, build script usage
  - Purpose: Smooth onboarding
  - Timing: Update incrementally as features land
  - _Leverage: steering docs
  - _Requirements: Non-functional
