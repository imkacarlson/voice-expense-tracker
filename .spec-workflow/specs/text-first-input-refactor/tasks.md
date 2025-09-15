# Tasks Document

- [x] 1. Remove voice recording service components
  - Files: app/src/main/java/com/voiceexpense/service/voice/VoiceRecordingService.kt, app/src/main/java/com/voiceexpense/ui/voice/StartVoiceActivity.kt, app/src/main/java/com/voiceexpense/ui/voice/ListeningActivity.kt
  - Delete voice recording service and associated activities
  - Remove service declarations from AndroidManifest.xml
  - Purpose: Eliminate custom voice recording infrastructure
  - _Leverage: AndroidManifest.xml for service declaration cleanup_
  - _Requirements: 1.1, 1.4_
  - _Prompt: Implement the task for spec text-first-input-refactor, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Android Developer specializing in service management and manifest configuration | Task: Remove VoiceRecordingService, StartVoiceActivity, and ListeningActivity files, plus clean up service declarations from AndroidManifest.xml following requirements 1.1 and 1.4 | Restrictions: Do not break existing text processing functionality, ensure all voice service references are removed, maintain app compilation | _Leverage: AndroidManifest.xml for proper service cleanup_ | _Requirements: 1.1, 1.4_ | Success: All voice service files deleted, manifest cleaned up, app compiles without voice service references | Instructions: Mark this task as in progress in tasks.md by changing [ ] to [-], then mark as complete [x] when finished_

- [x] 2. Remove speech recognition components
  - Files: app/src/main/java/com/voiceexpense/ai/speech/SpeechRecognitionService.kt, app/src/main/java/com/voiceexpense/ai/speech/AudioRecordingManager.kt, app/src/main/java/com/voiceexpense/ai/speech/RecognitionConfig.kt
  - Delete speech recognition and audio recording components
  - Remove ML Kit Speech Recognition dependencies from build.gradle
  - Purpose: Eliminate custom speech processing pipeline
  - _Leverage: app/build.gradle.kts for dependency cleanup_
  - _Requirements: 1.1, 1.2_
  - _Prompt: Implement the task for spec text-first-input-refactor, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Android Developer with expertise in ML Kit integration and Gradle dependency management | Task: Delete SpeechRecognitionService, AudioRecordingManager, and RecognitionConfig files, plus remove ML Kit Speech Recognition dependencies from build.gradle following requirements 1.1 and 1.2 | Restrictions: Preserve MediaPipe GenAI dependencies for text parsing, do not break existing AI text processing, ensure clean dependency removal | _Leverage: app/build.gradle.kts for dependency management_ | _Requirements: 1.1, 1.2_ | Success: Speech recognition files deleted, dependencies removed, text parsing still works, app builds successfully | Instructions: Mark this task as in progress in tasks.md by changing [ ] to [-], then mark as complete [x] when finished_

- [x] 3. Remove voice correction system from confirmation screen
  - Files: app/src/main/java/com/voiceexpense/ui/confirmation/voice/VoiceCorrectionController.kt, app/src/main/java/com/voiceexpense/ui/confirmation/voice/TtsEngine.kt, app/src/main/java/com/voiceexpense/ui/confirmation/voice/CorrectionIntentParser.kt, app/src/main/java/com/voiceexpense/ui/confirmation/voice/PromptRenderer.kt
  - Delete voice correction components and remove "Speak Correction" button
  - Update TransactionConfirmationActivity to remove voice correction imports and logic
  - Purpose: Simplify confirmation screen to manual editing only
  - _Leverage: app/src/main/java/com/voiceexpense/ui/confirmation/TransactionConfirmationActivity.kt_
  - _Requirements: 4.1, 4.2_
  - _Prompt: Implement the task for spec text-first-input-refactor, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Android UI Developer with expertise in Activity refactoring and form management | Task: Delete all voice correction components and update TransactionConfirmationActivity to remove voice correction logic and "Speak Correction" button following requirements 4.1 and 4.2 | Restrictions: Preserve all manual form editing capabilities, maintain form validation, do not break confirmation workflow | _Leverage: TransactionConfirmationActivity.kt for proper cleanup_ | _Requirements: 4.1, 4.2_ | Success: Voice correction files deleted, confirmation activity updated, manual editing still works, speak correction button removed | Instructions: Mark this task as in progress in tasks.md by changing [ ] to [-], then mark as complete [x] when finished_

- [x] 4. Remove home screen widget components
  - Files: app/src/main/java/com/voiceexpense/ui/widget/ExpenseWidgetProvider.kt, app/src/main/res/layout/widget_expense.xml, app/src/main/res/xml/expense_widget_info.xml
  - Delete widget provider and all widget-related layouts and configurations
  - Remove widget declarations from AndroidManifest.xml
  - Purpose: Eliminate home screen widget infrastructure
  - _Leverage: AndroidManifest.xml for widget declaration cleanup_
  - _Requirements: 3.1, 3.2_
  - _Prompt: Implement the task for spec text-first-input-refactor, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Android Developer specializing in widget management and manifest configuration | Task: Delete ExpenseWidgetProvider, widget layouts, and XML configurations, plus remove widget declarations from AndroidManifest.xml following requirements 3.1 and 3.2 | Restrictions: Do not break main app functionality, ensure complete widget removal, maintain app compilation | _Leverage: AndroidManifest.xml for proper widget cleanup_ | _Requirements: 3.1, 3.2_ | Success: All widget files deleted, manifest cleaned up, app compiles without widget references, main app still accessible | Instructions: Mark this task as in progress in tasks.md by changing [ ] to [-], then mark as complete [x] when finished_

- [x] 5. Remove voice-related permissions and manifest entries
  - File: app/src/main/AndroidManifest.xml
  - Remove RECORD_AUDIO permission and any other voice-related permissions
  - Clean up any remaining voice service or widget declarations
  - Purpose: Complete manifest cleanup and permission reduction
  - _Leverage: AndroidManifest.xml for permission management_
  - _Requirements: 5.1, 5.2_
  - _Prompt: Implement the task for spec text-first-input-refactor, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Android Developer with expertise in manifest configuration and permission management | Task: Remove RECORD_AUDIO and other voice-related permissions from AndroidManifest.xml, clean up any remaining voice/widget declarations following requirements 5.1 and 5.2 | Restrictions: Preserve all necessary permissions for text processing and Google Sheets sync, do not break authentication or network functionality | _Leverage: AndroidManifest.xml for permission and declaration management_ | _Requirements: 5.1, 5.2_ | Success: Voice permissions removed, manifest is clean, app still has necessary permissions for core functionality | Instructions: Mark this task as in progress in tasks.md by changing [ ] to [-], then mark as complete [x] when finished_

- [x] 6. Remove voice-related tests and update test suite
  - Files: app/src/test/java/com/voiceexpense/ai/speech/SpeechRecognitionServiceTest.kt, app/src/test/java/com/voiceexpense/ui/confirmation/voice/VoiceCorrectionControllerTest.kt, app/src/test/java/com/voiceexpense/service/voice/VoiceRecordingServiceAiTest.kt, and other voice-related test files
  - Delete all test files related to voice processing and correction
  - Update test configurations to remove voice test dependencies
  - Purpose: Clean up test suite and remove voice test infrastructure
  - _Leverage: existing test structure in app/src/test/ and app/src/androidTest/_
  - _Requirements: 1.5, 4.5_
  - _Prompt: Implement the task for spec text-first-input-refactor, first run spec-workflow-guide to get the workflow guide then implement the task: Role: QA Engineer with expertise in Android testing and test suite management | Task: Delete all voice-related test files and update test configurations following requirements 1.5 and 4.5 | Restrictions: Preserve all text processing and form validation tests, maintain test suite integrity, do not break existing test infrastructure | _Leverage: existing test directory structure_ | _Requirements: 1.5, 4.5_ | Success: Voice tests deleted, test suite runs successfully, text processing tests preserved, no broken test references | Instructions: Mark this task as in progress in tasks.md by changing [ ] to [-], then mark as complete [x] when finished_

- [x] 7. Remove voice-related string resources and assets
  - Files: app/src/main/res/values/strings.xml, app/src/main/res/layout/activity_listening.xml, and other voice-related resource files
  - Delete voice-related string resources, layouts, and drawable assets
  - Clean up any unused resource references
  - Purpose: Complete resource cleanup and reduce app size
  - _Leverage: app/src/main/res/ directory structure_
  - _Requirements: 5.4, 5.5_
  - _Prompt: Implement the task for spec text-first-input-refactor, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Android Developer with expertise in resource management and app optimization | Task: Delete voice-related strings, layouts, and assets from res/ directory following requirements 5.4 and 5.5 | Restrictions: Preserve all text input and form-related resources, do not break existing UI, maintain resource referencing integrity | _Leverage: app/src/main/res/ directory structure_ | _Requirements: 5.4, 5.5_ | Success: Voice resources deleted, app size reduced, no broken resource references, existing UI still works | Instructions: Mark this task as in progress in tasks.md by changing [ ] to [-], then mark as complete [x] when finished_

- [x] 8. Update documentation to reflect text-only approach
  - Files: README.md, docs/voice-correction-loop-v1.md, docs/hybrid-ml-kit-integration.md
  - Update README to reflect simplified text-only approach
  - Archive or remove voice-specific documentation
  - Add note about using Android's built-in voice-to-text via Google Keyboard
  - Purpose: Ensure documentation matches simplified app functionality
  - _Leverage: existing documentation structure in docs/_
  - _Requirements: 5.5, 6.5_
  - _Prompt: Implement the task for spec text-first-input-refactor, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Technical Writer with expertise in Android app documentation and user guides | Task: Update README and documentation to reflect text-only approach following requirements 5.5 and 6.5 | Restrictions: Maintain documentation clarity and usefulness, preserve technical accuracy for remaining features, ensure user guidance is complete | _Leverage: existing docs/ directory structure_ | _Requirements: 5.5, 6.5_ | Success: Documentation updated and accurate, voice references removed, text input workflow clearly explained, users understand how to use Google Keyboard for voice | Instructions: Mark this task as in progress in tasks.md by changing [ ] to [-], then mark as complete [x] when finished_

- [ ] 9. Verify complete functionality and run comprehensive testing
  - Test the complete text input workflow: MainActivity → text parsing → form confirmation → Google Sheets sync
  - Verify all existing functionality works without voice components
  - Run unit tests and integration tests to ensure no regressions
  - Purpose: Validate that removal was successful and core functionality preserved
  - _Leverage: existing test infrastructure and MainActivity text input flow_
  - _Requirements: 6.1, 6.2, 6.3_
  - _Prompt: Implement the task for spec text-first-input-refactor, first run spec-workflow-guide to get the workflow guide then implement the task: Role: QA Engineer with expertise in Android testing and regression testing | Task: Perform comprehensive testing of text input workflow and verify all functionality following requirements 6.1, 6.2, and 6.3 | Restrictions: Do not modify core functionality during testing, focus on validation and verification, ensure thorough test coverage | _Leverage: existing MainActivity text input and test infrastructure_ | _Requirements: 6.1, 6.2, 6.3_ | Success: All tests pass, text workflow works end-to-end, no regressions detected, app is stable and functional | Instructions: Mark this task as in progress in tasks.md by changing [ ] to [-], then mark as complete [x] when finished_

- [ ] 10. Final cleanup and code quality review
  - Review codebase for any remaining voice-related references or imports
  - Clean up any unused imports or dependencies
  - Verify app builds and runs without errors
  - Purpose: Ensure complete removal and code quality
  - _Leverage: IDE analysis tools and build system verification_
  - _Requirements: All requirements_
  - _Prompt: Implement the task for spec text-first-input-refactor, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Senior Android Developer with expertise in code quality and refactoring | Task: Perform final cleanup and quality review covering all requirements | Restrictions: Do not introduce new functionality, focus on cleanup and verification, maintain code quality standards | _Leverage: IDE analysis and build verification tools_ | _Requirements: All requirements_ | Success: Codebase is clean with no voice references, app builds successfully, code quality maintained, removal is complete | Instructions: Mark this task as in progress in tasks.md by changing [ ] to [-], then mark as complete [x] when finished_
