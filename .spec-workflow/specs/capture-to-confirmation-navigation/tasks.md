# Tasks Document

- [x] 1. Add repository lookup by id
  - File: app/src/main/java/com/voiceexpense/data/repository/TransactionRepository.kt (modify)
  - Add `suspend fun getById(tId: String): Transaction? = dao.getById(tId)` to expose draft lookup for the Activity.
  - Purpose: Allow confirmation Activity to load the draft using the id extra.
  - _Leverage: data/local/TransactionDao.getById_
  - _Requirements: Requirement 2.1, Reliability

- [x] 2. Launch confirmation from service with draft id
  - File: app/src/main/java/com/voiceexpense/service/voice/VoiceRecordingService.kt (modify)
  - After saving the draft, start `TransactionConfirmationActivity` with `FLAG_ACTIVITY_NEW_TASK` and put `EXTRA_TRANSACTION_ID`.
  - Keep existing `ACTION_DRAFT_READY` broadcast for optional listeners.
  - Purpose: Seamless capture → confirmation navigation.
  - _Leverage: Existing service, Activity class_
  - _Requirements: Requirement 1.1, 3.1, Performance

- [x] 3. Debounce duplicate launches
  - File: app/src/main/java/com/voiceexpense/service/voice/VoiceRecordingService.kt (modify)
  - Track last launched id and timestamp; if attempting to launch the same id within 2 seconds, skip launch.
  - Purpose: Avoid duplicate confirmation screens.
  - _Leverage: Service scope state
  - _Requirements: Requirement 1.2

- [x] 4. Read id and load draft in Activity
  - File: app/src/main/java/com/voiceexpense/ui/confirmation/TransactionConfirmationActivity.kt (modify)
  - On create: read `EXTRA_TRANSACTION_ID`; if missing, show toast and `finish()`; else use repository to fetch and pass to `viewModel.setDraft(draft)`.
  - Purpose: Initialize confirmation loop with correct draft.
  - _Leverage: ConfirmationViewModel.setDraft, TransactionRepository.getById_
  - _Requirements: Requirement 2.1, 2.2, 2.3

- [x] 5. User feedback on missing/invalid id
  - File: app/src/main/res/values/strings.xml (modify)
  - Add string: `error_open_draft_failed` (e.g., "Could not open draft. Please try again."). Use it in Activity on failure.
  - Purpose: Graceful failure without crash.
  - _Leverage: Android resources
  - _Requirements: Usability, Reliability

- [x] 6. Optional Activity singleTop (duplicate mitigation)
  - File: app/src/main/AndroidManifest.xml (modify)
  - For `TransactionConfirmationActivity`, consider `launchMode="singleTop"` to reduce duplicates from rapid events.
  - Purpose: Secondary guard against duplicate launches.
  - _Leverage: Android manifest config
  - _Requirements: Requirement 1.2

- [x] 7. Tests — repository lookup
  - File: app/src/test/java/com/voiceexpense/data/repository/TransactionRepositoryTest.kt (extend)
  - Add a test that inserts a draft and asserts `getById(id)` returns it; and returns null for missing.
  - Purpose: Ensure basic lookup correctness.
  - _Leverage: existing fake DAO patterns
  - _Requirements: Reliability

- [x] 8. Tests — Activity behavior
  - File: app/src/test/java/com/voiceexpense/ui/confirmation/ConfirmationActivityNavigationTest.kt (new)
  - Robolectric: launch Activity with and without the id extra; verify toast text on missing id and that Activity finishes; with valid id, verify ViewModel receives `setDraft` (use a test VM or expose state).
  - Purpose: Validate navigation + loading logic.
  - _Leverage: Robolectric, existing test utilities
  - _Requirements: Requirement 2.1, 2.2

- [x] 9. Tests — Service intent launch (optional)
  - File: app/src/test/java/com/voiceexpense/service/voice/VoiceRecordingServiceNavigationTest.kt (new)
  - Robolectric: start service, simulate draft save path, and assert an Activity start intent with the correct extra was sent; verify debounce prevents duplicate intents.
  - Purpose: Ensure service emits the correct navigation intent.
  - _Leverage: Robolectric shadows
  - _Requirements: Requirement 1.1, 1.2
