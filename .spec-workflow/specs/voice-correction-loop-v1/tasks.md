# Tasks Document

- [x] 1. Define loop domain models
  - File: app/src/main/java/com/voiceexpense/ui/confirmation/voice/LoopModels.kt
  - Add sealed class `CorrectionIntent`, enums `Field`, `Ambiguity`, and data `LoopState`, `PromptKind`, `Timeouts`.
  - Purpose: Establish strongly-typed intents and loop state for the controller and VM.
  - Leverage: `com.voiceexpense.data.model.Transaction`, `TransactionType`.
  - Requirements: 2.1, 3.4, Non-Functional: Code Architecture

- [x] 2. Implement `CorrectionIntentParser`
  - File: app/src/main/java/com/voiceexpense/ui/confirmation/voice/CorrectionIntentParser.kt
  - Parse utterances into intents: amount, merchant/description, type, categories, tags (append/replace), account, overall charged, date, confirm/cancel/repeat.
  - Purpose: Map free-form speech to actionable updates.
  - Leverage: `TransactionType` for type mapping.
  - Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8; 4.1 (confirm/cancel)

- [x] 3. Implement `PromptRenderer`
  - File: app/src/main/java/com/voiceexpense/ui/confirmation/voice/PromptRenderer.kt
  - Generate: `summary(t)`, `askMissing(missing)`, `confirm()`, `clarify(kind, candidates)` with ≤120 chars typical.
  - Purpose: Consistent, concise TTS prompts and summaries.
  - Leverage: `Transaction` fields; string utils.
  - Requirements: 1.1, 1.2, 4.3; Usability

- [x] 4. Add `TtsEngine` wrapper
  - File: app/src/main/java/com/voiceexpense/ui/confirmation/voice/TtsEngine.kt
  - Provide `suspend fun speak(text)` and `fun stop()`; no-op or stub for unit tests.
  - Purpose: Interruptible TTS with a simple interface.
  - Leverage: Android TTS later; start with stub.
  - Requirements: 1.3 (interruption), Usability

- [x] 5. Build `VoiceCorrectionController`
  - File: app/src/main/java/com/voiceexpense/ui/confirmation/voice/VoiceCorrectionController.kt
  - Orchestrate speak → listen → parse → apply → validate; handle ambiguities and missing fields; emit next prompts.
  - Purpose: Central loop engine; testable without Android UI.
  - Leverage: `SpeechRecognitionService`, `PromptRenderer`, `CorrectionIntentParser`, `StructuredOutputValidator`.
  - Requirements: 1.1, 1.2, 1.3, 3.x, 4.x, 6.1

- [x] 6. Extend `ConfirmationViewModel` for loop
  - File: app/src/main/java/com/voiceexpense/ui/confirmation/ConfirmationViewModel.kt (modify)
  - Add `loop: StateFlow<LoopState>`, `startLoop()`, `interruptTts()`, route `applyCorrection(text)` to controller, and integrate validator responses.
  - Purpose: Expose state/effects to UI; central app-facing API.
  - Leverage: `TransactionRepository`, `StructuredOutputValidator`.
  - Requirements: 4.1, 6.1, Non-Functional: Architecture/Performance

- [x] 7. Wire Activity to loop events
  - File: app/src/main/java/com/voiceexpense/ui/confirmation/TransactionConfirmationActivity.kt (modify)
  - Subscribe to VM effects; trigger `startLoop()`; connect mic button to `interruptTts()` and listening.
  - Purpose: Provide hands-free flow and demo path.
  - Leverage: existing buttons and `SpeechRecognitionService`.
  - Requirements: 1.1, 4.1, Usability

- [x] 8. Add DI bindings
  - File: app/src/main/java/com/voiceexpense/di/AppModule.kt (modify)
  - Provide singletons/factories for `CorrectionIntentParser`, `PromptRenderer`, `TtsEngine`, and `VoiceCorrectionController`.
  - Purpose: Compose loop components via Hilt.
  - Leverage: existing DI patterns in `AppModule.kt`.
  - Requirements: Non-Functional: Modularity

- [x] 9. Integrate validation and conflict resolution
  - File: app/src/main/java/com/voiceexpense/ui/confirmation/voice/VoiceCorrectionController.kt (continue)
  - Enforce: type validity, clear incompatible fields, amount ≤ overall; produce clarify prompts for conflicts.
  - Purpose: Reliability and correctness.
  - Leverage: `StructuredOutputValidator`.
  - Requirements: 3.3, 3.4; Non-Functional: Reliability

- [x] 10. Update repository usage on confirm
  - File: app/src/main/java/com/voiceexpense/ui/confirmation/ConfirmationViewModel.kt (modify)
  - Ensure confirm → mark CONFIRMED then QUEUED; increment metrics `correctionsCount` on edits.
  - Purpose: Persist correct states and metrics.
  - Leverage: `TransactionRepository.confirm`, `enqueueForSync`.
  - Requirements: 4.1, 6.1

- [x] 11. Unit tests: intent parser
  - File: app/src/test/java/com/voiceexpense/ui/confirmation/voice/CorrectionIntentParserTest.kt
  - Cover numeric phrases, type mapping, merchant/description, tags append/replace, account matching stub, overall charged, dates.
  - Purpose: Ensure robust utterance handling.
  - Leverage: JUnit, truth/kotlin test utilities present in project.
  - Requirements: 2.1–2.8

- [x] 12. Unit tests: prompt renderer
  - File: app/src/test/java/com/voiceexpense/ui/confirmation/voice/PromptRendererTest.kt
  - Verify summary length/contents; missing-field prompts; clarify strings.
  - Purpose: Consistent UX prompts.
  - Requirements: 1.1, 1.2, 3.x

- [x] 13. Integration tests: controller loop
  - File: app/src/test/java/com/voiceexpense/ui/confirmation/voice/VoiceCorrectionControllerTest.kt
  - Simulate transcripts: correction → confirm, ambiguity → clarify → resolve, silence → reprompt → end.
  - Purpose: Validate loop behavior end-to-end without UI.
  - Requirements: 1.x, 3.x, 4.x

- [x] 14. ViewModel E2E test
  - File: app/src/test/java/com/voiceexpense/ui/confirmation/ConfirmationViewModelTest.kt (extend)
  - Validate pipeline: applyCorrection → transaction updated and `correctionsCount` increments; confirm enqueues.
  - Purpose: Regressions coverage across VM and repo.
  - Requirements: 6.1, 4.1

- [x] 15. Add developer toggle for verbose local logs
  - File: app/src/main/java/com/voiceexpense/ui/common/SettingsKeys.kt (modify) and usage in controller
  - Gate local debug logs behind a setting; avoid sensitive content unless enabled.
  - Purpose: Privacy-friendly observability.
  - Leverage: existing settings patterns.
  - Requirements: 6.2, 6.3 (no sensitive logs unless enabled)

- [x] 16. Timeouts and reprompts
  - File: app/src/main/java/com/voiceexpense/ui/confirmation/voice/VoiceCorrectionController.kt (continue)
  - Implement silence timeout (default 8s) with single reprompt then end session.
  - Purpose: Clear exit on inactivity.
  - Requirements: 4.3

- [x] 17. Documentation
  - File: docs/voice-correction-loop-v1.md (new)
  - Brief developer notes on components, flows, and testing commands.
  - Purpose: Speed up onboarding and maintenance.
  - Leverage: design.md content.
  - Requirements: Non-Functional: Modularity/Usability
