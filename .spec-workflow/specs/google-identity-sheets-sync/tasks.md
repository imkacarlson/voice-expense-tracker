# Tasks Document

- [x] 1. Implement GoogleIdentityTokenProvider token fetch/invalidate
  - File: app/src/main/java/com/voiceexpense/auth/GoogleIdentityTokenProvider.kt
  - Replace placeholder with real Google Identity/Play Services token acquisition for scope "https://www.googleapis.com/auth/spreadsheets"; implement invalidate via clearToken.
  - Purpose: Provide valid OAuth access tokens for Sheets API calls.
  - _Leverage: app/src/main/java/com/voiceexpense/auth/TokenProvider.kt, app/src/main/java/com/voiceexpense/auth/AuthRepository.kt_
  - _Requirements: 1, 4, 5_

- [x] 2. Wire TokenProvider via Hilt DI
  - File: app/src/main/java/com/voiceexpense/di/AppModule.kt
  - Ensure TokenProvider binding provides GoogleIdentityTokenProvider; keep EncryptedPrefs fallback for tests.
  - Purpose: Make token provider injectable across app components.
  - _Leverage: app/src/main/java/com/voiceexpense/di/AppModule.kt_
  - _Requirements: 5_

- [x] 3. SettingsActivity: Sign-in/out and readiness gating
  - File: app/src/main/java/com/voiceexpense/ui/common/SettingsActivity.kt
  - Ensure Sheets scope is requested, account saved, token warmed post sign-in; on sign-out clear creds and invalidate token; update status and gating messages.
  - Purpose: Clear UX for auth state and sync readiness.
  - _Leverage: app/src/main/res/layout/activity_settings.xml, app/src/main/res/values/strings.xml_
  - _Requirements: 1, 2, 6_

- [x] 4. Strings: Add/update auth and gating messages
  - File: app/src/main/res/values/strings.xml
  - Ensure messages exist for signed-in, signed-out, gating states, settings saved, sign-in required, token fetch error.
  - Purpose: Provide concise, actionable user feedback.
  - _Leverage: existing strings entries_
  - _Requirements: 2, 6_

- [x] 5. Repository: Batch sync with 401-aware retry
  - File: app/src/main/java/com/voiceexpense/data/repository/TransactionRepository.kt
  - Fetch token once per run, on HTTP 401: invalidate then refresh once, retry append; on success set POSTED and persist SheetReference.
  - Purpose: Reliable posting with secure token lifecycle.
  - _Leverage: app/src/main/java/com/voiceexpense/data/model/SheetReference.kt, app/src/main/java/com/voiceexpense/data/remote/SheetsClient.kt_
  - _Requirements: 3, 4, 5_

- [x] 6. Repository: Map model to exact Sheets row
  - File: app/src/main/java/com/voiceexpense/data/repository/TransactionRepository.kt (mapToSheetRow)
  - Ensure output columns match: Timestamp | Date | Amount | Description | Type | Expense Category | Tags | Income Category | [empty] | Account/Credit Card | Overall Charged | Transfer Category | Transfer Into.
  - Purpose: Guarantee alignment with the configured spreadsheet.
  - _Leverage: current implementation_
  - _Requirements: 3_

- [x] 7. Sheets client: Append + range parsing utility
  - File: app/src/main/java/com/voiceexpense/data/remote/SheetsClient.kt
  - Keep append call using Retrofit with bearer token; extract sheet name and last row index from updates.updatedRange (e.g., "Sheet1!A5:M5").
  - Purpose: Encapsulate HTTP and response parsing.
  - _Leverage: app/src/main/java/com/voiceexpense/data/remote/SheetsApi.kt_
  - _Requirements: 3_

- [x] 8. SyncWorker: Read config and run with constraints
  - File: app/src/main/java/com/voiceexpense/worker/SyncWorker.kt
  - Read spreadsheet ID and Sheet name from shared prefs; call repo.syncPending(); return success/retry accordingly.
  - Purpose: Background sync under network constraints.
  - _Leverage: app/src/main/java/com/voiceexpense/ui/common/SettingsActivity.kt, app/src/main/java/com/voiceexpense/worker/WorkScheduling.kt_
  - _Requirements: 3_

- [x] 9. Work scheduling: Enqueue on confirm with NetworkType.CONNECTED
  - File: app/src/main/java/com/voiceexpense/worker/WorkScheduling.kt; app/src/main/java/com/voiceexpense/ui/confirmation/TransactionConfirmationActivity.kt
  - Ensure enqueueUniqueWork uses CONNECTED network constraint and exponential backoff; trigger after confirmation.
  - Purpose: Fire-and-forget sync scheduling that avoids duplicate work.
  - _Leverage: current implementation_
  - _Requirements: 3, 6_

- [x] 10. AuthRepository: Secure storage and sign-out
  - File: app/src/main/java/com/voiceexpense/auth/AuthRepository.kt
  - Store account email and token via EncryptedSharedPreferences (fallback to in-memory for tests); clear on sign-out.
  - Purpose: Secure persistence without leaking secrets.
  - _Leverage: app/src/main/java/com/voiceexpense/di/AppModule.kt_
  - _Requirements: 4, 5_

- [x] 11. Unit tests: Repository 401 invalidation + retry
  - File: app/src/test/java/com/voiceexpense/data/repository/TransactionRepositoryAuthTest.kt
  - Cover first-call 401 then success flow; assert invalidate called and row posted.
  - Purpose: Verify retry semantics and state updates.
  - _Leverage: FakeTokenProvider, FakeDao, Sheets client stub_
  - _Requirements: 3, 5_

- [x] 12. Unit tests: TokenProvider contract
  - File: app/src/test/java/com/voiceexpense/auth/TokenProviderTest.kt
  - Test happy path, invalidate behavior, and error propagation (if applicable) using a fake provider.
  - Purpose: Ensure TokenProvider semantics across implementations.
  - _Leverage: app/src/main/java/com/voiceexpense/auth/TokenProvider.kt_
  - _Requirements: 5_

- [x] 13. UI tests: SettingsActivity gating states
  - File: app/src/test/java/com/voiceexpense/ui/common/SettingsActivityAuthTest.kt
  - Verify messages for: no config, needs sign-in, ready state after sign-in and config.
  - Purpose: Prevent regressions in readiness UX.
  - _Leverage: Robolectric, existing strings_
  - _Requirements: 2, 6_

- [x] 14. Worker tests: SyncWorker happy path and retry
  - File: app/src/test/java/com/voiceexpense/worker/SyncWorkerAuthTest.kt; app/src/test/java/com/voiceexpense/worker/SyncWorkerTest.kt
  - Test reading prefs, success posting, and retry path on transient failures.
  - Purpose: Validate background reliability and constraints adherence.
  - _Leverage: WorkManager test utils (if used), FakeDao, fake Sheets client_
  - _Requirements: 3_

- [x] 15. Integration: TransactionConfirmation enqueue
  - File: app/src/test/java/com/voiceexpense/ui/widget/WidgetIntegrationTest.kt (or new test)
  - Confirm that confirm action enqueues work via WorkManager and sets QUEUED status.
  - Purpose: Verify end-to-end wiring from confirm to background sync.
  - _Leverage: TransactionConfirmationActivity, WorkScheduling.enqueueSyncNow_
  - _Requirements: 3, 6_

- [x] 16. Manual E2E checklist
  - Steps: Sign in (grant Sheets), set Spreadsheet ID + Sheet name, create/confirm a transaction, observe posted row in sheet; sign out and verify no posting until sign-in again.
  - Purpose: Validate full user journey and environment configs.
  - _Requirements: All_
