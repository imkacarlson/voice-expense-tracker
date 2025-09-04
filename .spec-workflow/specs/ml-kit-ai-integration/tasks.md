# Tasks Document

- [x] 1. Enable ML Kit dependencies in build.gradle.kts
  - File: app/build.gradle.kts
  - Uncomment and update ML Kit GenAI and Speech Recognition dependencies
  - Add proper version constraints and conflict resolution
  - Purpose: Enable ML Kit APIs for on-device AI processing
  - _Leverage: existing dependency management patterns_
  - _Requirements: 1.1_

- [x] 2. Create model lifecycle management in ModelManager.kt
  - File: app/src/main/java/com/voiceexpense/ai/model/ModelManager.kt (modify existing)
  - Replace placeholder with ML Kit AICore integration
  - Implement model download, availability checking, and lifecycle management
  - Purpose: Manage Gemini Nano model availability and downloads
  - _Leverage: existing error handling patterns, Hilt DI_
  - _Requirements: 2.2_

- [x] 3. Add speech recognition configuration models
  - File: app/src/main/java/com/voiceexpense/ai/speech/RecognitionConfig.kt (new)
  - Define RecognitionConfig, RecognitionResult sealed classes
  - Add proper serialization and validation
  - Purpose: Provide type-safe configuration for speech recognition
  - _Leverage: existing data model patterns from data/model/_
  - _Requirements: 1.1_

- [x] 4. Implement real speech recognition in SpeechRecognitionService.kt
  - File: app/src/main/java/com/voiceexpense/ai/speech/SpeechRecognitionService.kt (modify existing)
  - Replace placeholder with Android SpeechRecognizer API integration
  - Implement Flow-based result streaming and error handling
  - Purpose: Convert audio to text using on-device speech recognition
  - _Leverage: existing coroutines patterns, Flow usage_
  - _Requirements: 1.1, 1.2_

- [x] 5. Create structured output validation in StructuredOutputValidator.kt
  - File: app/src/main/java/com/voiceexpense/ai/parsing/StructuredOutputValidator.kt (new)
  - Implement JSON schema validation for GenAI output
  - Add sanitization methods for amounts and fields
  - Purpose: Ensure GenAI output matches Transaction schema requirements
  - _Leverage: existing Transaction model, validation patterns_
  - _Requirements: 2.1, 2.2_

- [x] 6. Implement GenAI parsing in TransactionParser.kt
  - File: app/src/main/java/com/voiceexpense/ai/parsing/TransactionParser.kt (modify existing)
  - Replace naive regex with ML Kit GenAI API integration
  - Add proper prompt engineering and structured output handling
  - Purpose: Parse natural language into structured transaction data
  - _Leverage: existing ParsedResult model, ParsingContext_
  - _Requirements: 2.1, 2.2, 2.3_

- [x] 7. Update parsing prompts with transaction examples
  - File: app/src/main/java/com/voiceexpense/ai/parsing/ParsingPrompts.kt (modify existing)
  - Add the 5 sample utterances from steering docs as examples
  - Implement prompt templates for different transaction types
  - Purpose: Improve GenAI parsing accuracy with proper examples
  - _Leverage: existing prompt structure, steering doc examples_
  - _Requirements: 2.3, 2.4_

- [x] 8. Add audio recording implementation in AudioRecordingManager.kt
  - File: app/src/main/java/com/voiceexpense/ai/speech/AudioRecordingManager.kt (modify existing)
  - Implement real audio capture using Android AudioRecord
  - Add proper permission handling and audio format configuration
  - Purpose: Capture audio input for speech recognition processing
  - _Leverage: existing service lifecycle patterns_
  - _Requirements: 1.1_

- [x] 9. Integrate AI pipeline in VoiceRecordingService.kt
  - File: app/src/main/java/com/voiceexpense/service/voice/VoiceRecordingService.kt (modify existing)
  - Connect real AudioRecordingManager → SpeechRecognitionService → TransactionParser pipeline
  - Replace placeholder orchestration with real AI component coordination
  - Purpose: Orchestrate complete voice-to-data processing pipeline
  - _Leverage: existing service structure, repository integration_
  - _Requirements: 1.1, 2.1_

- [x] 10. Add AI component dependency injection
  - File: app/src/main/java/com/voiceexpense/di/AiModule.kt (new)
  - Create Hilt module for AI components (ModelManager, parsers, speech service)
  - Configure component lifetimes and dependencies
  - Purpose: Enable dependency injection for AI components
  - _Leverage: existing Hilt modules pattern from di/ package_
  - _Requirements: 1.1, 2.1_

- [x] 11. Create AI parsing unit tests with fixtures
  - File: app/src/test/java/com/voiceexpense/ai/parsing/TransactionParserTest.kt (modify existing)
  - Add tests using the 5 steering doc utterances as fixtures
  - Mock ML Kit GenAI API responses with deterministic outputs
  - Purpose: Ensure parsing accuracy and catch regressions
  - _Leverage: existing test patterns, fixtures approach_
  - _Requirements: 2.1, 2.2, 2.3_

- [x] 12. Create speech recognition unit tests
  - File: app/src/test/java/com/voiceexpense/ai/speech/SpeechRecognitionServiceTest.kt (modify existing)
  - Mock Android SpeechRecognizer with fake recognition results
  - Test error scenarios and Flow emission patterns
  - Purpose: Ensure speech recognition reliability and error handling
  - _Leverage: existing service test patterns, mock strategies_
  - _Requirements: 1.1, 1.2_

- [x] 13. Add model manager unit tests
  - File: app/src/test/java/com/voiceexpense/ai/model/ModelManagerTest.kt (new)
  - Mock ML Kit AICore APIs for model lifecycle testing
  - Test offline scenarios and graceful degradation
  - Purpose: Ensure proper model management and error handling
  - _Leverage: existing test utilities and mock patterns_
  - _Requirements: 2.2_

- [x] 14. Create integration tests for AI pipeline
  - File: app/src/test/java/com/voiceexpense/service/voice/VoiceRecordingServiceAiTest.kt (new)
  - Test complete pipeline: audio input → transcription → parsing → repository
  - Mock AI components with realistic response timing
  - Purpose: Ensure end-to-end AI processing works correctly
  - _Leverage: existing service test patterns, integration test helpers_
  - _Requirements: 1.1, 2.1_

- [x] 15. Add error handling for AI component failures
  - File: app/src/main/java/com/voiceexpense/ai/error/AiErrorHandler.kt (new)
  - Implement error recovery strategies for model unavailable, parsing failures
  - Add user-friendly error messages and retry logic
  - Purpose: Provide robust error handling for AI processing failures
  - _Leverage: existing error handling patterns from util/ package_
  - _Requirements: 1.3, 2.2_

- [x] 16. Update confirmation UI for low confidence handling
  - File: app/src/main/java/com/voiceexpense/ui/confirmation/TransactionConfirmationActivity.kt (modify existing)
  - Add UI indicators for low-confidence parsed fields
  - Implement field highlighting and voice correction prompts
  - Purpose: Allow user verification and correction of uncertain AI parsing
  - _Leverage: existing confirmation UI, ViewModel patterns_
  - _Requirements: 2.2, 2.3_

- [x] 17. Performance optimization for AI processing
  - File: app/src/main/java/com/voiceexpense/ai/performance/AiPerformanceOptimizer.kt (new)
  - Add model warming, batch processing, and memory management
  - Implement performance monitoring for <3s parsing target
  - Purpose: Ensure AI processing meets performance requirements
  - _Leverage: existing performance patterns, coroutines optimization_
  - _Requirements: 1.1, 2.1_

- [x] 18. Add comprehensive end-to-end AI tests
  - File: app/src/androidTest/java/com/voiceexpense/ai/AiIntegrationTest.kt (new)
  - Test complete user flows with real device AI components
  - Test performance benchmarks and accuracy requirements
  - Purpose: Validate AI integration works correctly on real devices
  - _Leverage: existing instrumentation test patterns_
  - _Requirements: All_