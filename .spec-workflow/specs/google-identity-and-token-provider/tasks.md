# Tasks Document

- [x] 1. Add GoogleIdentityTokenProvider implementation
  - File: app/src/main/java/com/voiceexpense/auth/GoogleIdentityTokenProvider.kt
  - Implement `TokenProvider` using Google Identity/Play Services to fetch an OAuth2 access token for scope `https://www.googleapis.com/auth/spreadsheets`.
  - Methods: `getAccessToken(accountEmail, scope)`, `invalidateToken(accountEmail, scope)`.
  - Use EncryptedSharedPreferences (via AuthRepository) for optional short-lived token caching (token + expiry) to avoid redundant fetches.
  - Purpose: Provide production token acquisition with invalidation support.
  - _Leverage: AuthRepository, play-services-auth dependency_
  - _Requirements: 1, 2, 3, 6_

- [x] 2. DI: Bind TokenProvider in Hilt
  - File: app/src/main/java/com/voiceexpense/di/AppModule.kt (modify)
  - Replace `provideTokenProvider()` to return `GoogleIdentityTokenProvider` implementation.
  - Inject Android Context/AuthRepository as needed.
  - Purpose: Make TokenProvider available app-wide.
  - _Leverage: Existing Hilt setup_
  - _Requirements: 3_

- [x] 3. Update TransactionRepository to use TokenProvider
  - File: app/src/main/java/com/voiceexpense/data/repository/TransactionRepository.kt (modify)
  - Add `tokenProvider: TokenProvider` dependency (constructor injection) and require signed-in account email from `AuthRepository`.
  - Before posting, call `getAccessToken(email, SheetsScope)`; on HTTP 401, call `invalidateToken(...)` then fetch and retry once.
  - If no account or config missing, skip post and count as failure without crashing.
  - Purpose: Reliable sync with proper auth handling.
  - _Leverage: existing 401 retry scaffold, SheetsClient, AuthRepository_
  - _Requirements: 3, 4, 7_

- [x] 4. SettingsActivity: persist account + warm token
  - File: app/src/main/java/com/voiceexpense/ui/common/SettingsActivity.kt (modify)
  - On sign-in success, save account email via `AuthRepository.setAccount(...)` and optionally call `TokenProvider.getAccessToken(...)` in background.
  - On sign-out, call `AuthRepository.signOut()` and (optional) `TokenProvider.invalidateToken(...)`.
  - Ensure gating text updates immediately on sign-in/out.
  - Purpose: Clear UX and readiness indication.
  - _Leverage: existing UI and Play Services Sign-In code_
  - _Requirements: 1, 5, 7_

- [x] 5. ProGuard/consumer rules check (release readiness)
  - File: app/proguard-rules.pro (modify)
  - Add keep rules if required for Google Sign-In token APIs to avoid stripping (documented minimal keep rules).
  - Purpose: Ensure release builds keep necessary classes.
  - _Leverage: existing proguard config_
  - _Requirements: 7 (stability)

- [x] 6. Unit test: TransactionRepository auth paths
  - File: app/src/test/java/com/voiceexpense/data/repository/TransactionRepositoryAuthTest.kt (new)
  - Use FakeTokenProvider and FakeSheets to verify:
    - 401 then success triggers exactly one invalidate + retry and results in POSTED.
    - Missing account email results in attempts=queued.size, failed>=1, and no crash.
  - Purpose: Guard core sync behavior.
  - _Leverage: existing tests (TransactionRepositoryTest), InMemoryStore, Fake DAOs_
  - _Requirements: 3, 4, 7_

- [x] 7. Unit test: TokenProvider caching/invalidation (interface-level)
  - File: app/src/test/java/com/voiceexpense/auth/TokenProviderTest.kt (new)
  - Test Fake/Static provider behavior and verify invalidate → subsequent get returns updated token.
  - Purpose: Ensure contract correctness for DI swaps.
  - _Leverage: StaticTokenProvider_
  - _Requirements: 3

- [x] 8. Robolectric test: Settings gating updates
  - File: app/src/test/java/com/voiceexpense/ui/common/SettingsActivityAuthTest.kt (new)
  - Simulate sign-in result by stubbing GoogleSignIn account and calling internal handlers; verify auth status text and gating message transitions.
  - Purpose: Validate UX responsiveness.
  - _Leverage: existing SettingsActivity, strings_
  - _Requirements: 1, 5

- [x] 9. Worker integration test with TokenProvider
  - File: app/src/test/java/com/voiceexpense/worker/SyncWorkerAuthTest.kt (new)
  - Inject TransactionRepository configured with FakeTokenProvider and FakeSheets; verify queued → posted when token valid.
  - Purpose: Verify background path remains functional with new auth flow.
  - _Leverage: existing SyncWorkerTest pattern_
  - _Requirements: 4, 7

- [x] 10. README update: Sign-In and Sync requirements
  - File: README.md (modify)
  - Document Google Sign-In requirement, Sheets scope, and Settings steps to configure Spreadsheet ID/Sheet Name.
  - Purpose: Developer/user guidance for enabling posting.
  - _Leverage: existing README structure_
  - _Requirements: 5, 6

- [x] 11. Strings and messages audit
  - File: app/src/main/res/values/strings.xml (modify if needed)
  - Add/update concise error/info messages for sign-in required, token fetch failures, and sync ready.
  - Purpose: Clear user-facing communication.
  - _Leverage: existing string resources_
  - _Requirements: 5, 7
