# Tasks Document

- [x] 1. Add Google Sign-In dependency and scope
  - File: app/build.gradle.kts
  - Add Play Services Auth dependency; ensure Hilt Work integration dependency present.
  - Purpose: Enable Google Sign-In with Sheets scope.
  - _Leverage: tech.md Core Technologies_
  - _Requirements: 1

- [x] 2. Create Sign-In UI in Settings
  - File: app/src/main/java/com/voiceexpense/ui/common/SettingsActivity.kt; app/src/main/res/layout/activity_settings.xml
  - Add “Sign in” and “Sign out” buttons; show current email/account if signed in; show gating messages when not signed in or config missing.
  - Purpose: Minimal UX to acquire consent and manage auth state.
  - _Leverage: Settings structure
  - _Requirements: 1, 6, 7

- [x] 3. Configure GoogleSignInClient with Sheets scope
  - File: app/src/main/java/com/voiceexpense/ui/common/SettingsActivity.kt
  - Request scope: https://www.googleapis.com/auth/spreadsheets; launch sign-in intent; handle Activity Result.
  - Purpose: Obtain consent for Sheets access.
  - _Leverage: Google Play Services Auth
  - _Requirements: 1

- [x] 4. Extend AuthRepository for account + token lifecycle
  - File: app/src/main/java/com/voiceexpense/auth/AuthRepository.kt
  - Store accountName/email; provide `getAccessToken()` that fetches/refreshes a valid token for the Sheets scope; provide `signOut()` to clear tokens and identity.
  - Purpose: Centralize token retrieval and secure storage.
  - _Leverage: EncryptedSharedPreferences
  - _Requirements: 2, 7

- [x] 5. Add TokenProvider abstraction for testing
  - File: app/src/main/java/com/voiceexpense/auth/TokenProvider.kt (new)
  - Interface to retrieve/refresh access tokens for a given account + scope; production impl uses Play Services APIs; test impl returns preset tokens.
  - Purpose: Make auth logic testable without real Google services.
  - _Leverage: existing test patterns
  - _Requirements: 2

- [x] 6. Wire TokenProvider via Hilt
  - File: app/src/main/java/com/voiceexpense/di/AppModule.kt
  - Provide `TokenProvider` implementation; inject into `AuthRepository`.
  - Purpose: Dependency injection for token retrieval.
  - _Leverage: Hilt modules
  - _Requirements: Non-functional: Architecture

- [x] 7. Surface updatedRange in SheetsClient
  - File: app/src/main/java/com/voiceexpense/data/remote/SheetsClient.kt
  - Ensure `AppendResponse.updates.updatedRange` is propagated; add helper to parse sheet name and row index when feasible.
  - Purpose: Allow storing `sheetRef` on success.
  - _Leverage: existing AppendResponse model
  - _Requirements: 4

- [x] 8. Implement TransactionRepository.syncPending()
  - File: app/src/main/java/com/voiceexpense/data/repository/TransactionRepository.kt
  - Iterate QUEUED; for each: fetch token via AuthRepository; append row; on 401, refresh token once and retry; on success mark POSTED and set `sheetRef` if derivable; otherwise leave in QUEUED.
  - Purpose: Core reliable sync logic.
  - _Leverage: DAO, SheetsClient, mapToSheetRow
  - _Requirements: 3, 4, 5

- [x] 9. Migrate SyncWorker to Hilt injection
  - File: app/src/main/java/com/voiceexpense/worker/SyncWorker.kt; app/src/main/java/com/voiceexpense/App.kt; app/build.gradle.kts
  - Add Hilt Work dependency; annotate worker with @HiltWorker and inject DAO/Repo/Auth/Sheets; remove AppServices locator; set WorkManager configuration for Hilt.
  - Purpose: Remove service locator and align with DI.
  - _Leverage: Hilt + WorkManager integration
  - _Requirements: Non-functional: Architecture, 5

- [x] 10. Add network constraint and enqueue triggers
  - File: wherever Work is scheduled (e.g., after confirm/enqueue); worker setup
  - Ensure WorkManager job runs with `NetworkType.CONNECTED` and backoff policy; trigger after enqueueing transactions.
  - Purpose: Battery-friendly, reliable sync.
  - _Leverage: WorkManager
  - _Requirements: 5

- [x] 11. Settings gating and messages
  - File: app/src/main/java/com/voiceexpense/ui/common/SettingsActivity.kt
  - Disable/enable sync depending on spreadsheet ID/sheet name presence and signed-in state; show concise messages.
  - Purpose: Clear setup pathway.
  - _Leverage: existing Settings keys
  - _Requirements: 6

- [x] 12. Add unit tests for repository sync and 401 retry
  - File: app/src/test/java/com/voiceexpense/data/repository/TransactionRepositoryTest.kt (extend)
  - Fake SheetsClient to return 401 then success; ensure single retry and POSTED status; test sheetRef parsing.
  - Purpose: Guard reliability.
  - _Leverage: existing test scaffolding
  - _Requirements: 3, 4

- [x] 13. Add unit tests for AuthRepository with TokenProvider
  - File: app/src/test/java/com/voiceexpense/auth/AuthRepositoryTest.kt (extend)
  - Test token persistence, retrieval, refresh-failure path, and sign-out.
  - Purpose: Ensure secure, reliable token handling.
  - _Leverage: InMemoryStore
  - _Requirements: 2, 7

- [x] 14. Update SyncWorker tests for DI
  - File: app/src/test/java/com/voiceexpense/worker/SyncWorkerTest.kt
  - Use fakes with injected worker; verify queued → posted and backoff paths.
  - Purpose: Validate worker behavior after DI migration.
  - _Leverage: existing tests
  - _Requirements: 5

- [x] 15. Minimal user-visible error messaging
  - File: app/src/main/java/com/voiceexpense/ui/common/SettingsActivity.kt (and/or confirmation UI)
  - Show concise errors for: sign-in required, spreadsheet config missing, network retry pending.
  - Purpose: Actionable UX without noise.
  - _Leverage: current UI components
  - _Requirements: 6, 8
