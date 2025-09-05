# Tasks Document

- [x] 1. Add ML Kit GenAI dependency to build.gradle.kts
  - File: app/build.gradle.kts
  - Update ML_KIT_GENAI_COORDINATE property with com.google.mlkit:genai-rewriting:1.0.0-beta1
  - Verify dependency resolution and version constraints
  - Purpose: Enable ML Kit GenAI Rewriting API for structured text transformation
  - _Leverage: existing dependency management patterns in build.gradle.kts_
  - _Requirements: 1.1_

- [x] 2. Create MlKitClient wrapper for Rewriting API
  - File: app/src/main/java/com/voiceexpense/ai/parsing/MlKitClient.kt (new)
  - Implement wrapper around ML Kit GenAI Rewriting API with proper lifecycle management
  - Add feature status checking, error handling, and resource cleanup
  - Purpose: Isolate ML Kit API interactions from business logic
  - _Leverage: existing AI component patterns from ai/ package_
  - _Requirements: 1.1, 4.1_

- [x] 3. Create structured prompt templates for transaction parsing
  - File: app/src/main/java/com/voiceexpense/ai/parsing/TransactionPrompts.kt (new)
  - Design prompts that transform natural language into structured JSON transaction data
  - Include examples for expense, income, and transfer transaction types
  - Purpose: Guide ML Kit to produce consistent structured output
  - _Leverage: existing ParsingPrompts.kt structure and sample utterances_
  - _Requirements: 3.1, 3.2_

- [x] 4. Replace placeholder runGenAi() method in TransactionParser
  - File: app/src/main/java/com/voiceexpense/ai/parsing/TransactionParser.kt (modify existing)
  - Replace empty stub with real MlKitClient integration
  - Maintain existing fallback logic and interface compatibility
  - Purpose: Enable production AI parsing while preserving existing architecture
  - _Leverage: existing TransactionParser structure, ParsedResult model, validation logic_
  - _Requirements: 1.1, 1.2_

- [x] 5. Enhance StructuredOutputValidator for ML Kit responses
  - File: app/src/main/java/com/voiceexpense/ai/parsing/StructuredOutputValidator.kt (modify existing)
  - Add validation methods specific to ML Kit JSON response format
  - Implement error recovery for malformed AI output
  - Purpose: Ensure ML Kit responses match expected transaction schema
  - _Leverage: existing JSON validation logic and Transaction model schema_
  - _Requirements: 4.1, 4.2_

- [x] 6. Create SetupGuidePage for user AI configuration help
  - File: app/src/main/java/com/voiceexpense/ui/setup/SetupGuidePage.kt (new)
  - Implement help page with step-by-step AICore and Gemini Nano setup instructions
  - Add validation tools to test user's AI setup
  - Purpose: Guide users through one-time device AI configuration
  - _Leverage: existing Activity patterns, navigation flows, WebView integration_
  - _Requirements: 2.1, 2.2_

- [x] 7. Add setup guide navigation to SettingsActivity
  - File: app/src/main/java/com/voiceexpense/ui/common/SettingsActivity.kt (modify existing)
  - Add menu item and navigation to setup guide page
  - Display AI status indicator (available/unavailable/not configured)
  - Purpose: Provide easy access to AI setup guidance from app settings
  - _Leverage: existing SettingsActivity structure, menu patterns_
  - _Requirements: 2.1, 2.3_

- [x] 8. Update ModelManager with simplified AI status checking
  - File: app/src/main/java/com/voiceexpense/ai/model/ModelManager.kt (modify existing)
  - Replace complex model lifecycle with simple availability checking
  - Remove download management, focus on status reporting
  - Purpose: Simplify model management to assume user has configured device
  - _Leverage: existing ModelManager structure, status reporting patterns_
  - _Requirements: 2.1_

- [x] 9. Register MlKitClient in dependency injection
  - File: app/src/main/java/com/voiceexpense/di/AiModule.kt (modify existing)
  - Add MlKitClient provider with proper scoping and lifecycle management
  - Configure dependencies between parser, client, and validator components
  - Purpose: Enable dependency injection for ML Kit components
  - _Leverage: existing Hilt module patterns in AiModule_
  - _Requirements: 1.1_

- [x] 10. Create MlKitClient unit tests with mocked API
  - File: app/src/test/java/com/voiceexpense/ai/parsing/MlKitClientTest.kt (new)
  - Mock ML Kit Rewriting API responses for deterministic testing
  - Test error scenarios: service unavailable, quota exceeded, malformed responses
  - Purpose: Ensure ML Kit wrapper handles all API scenarios correctly
  - _Leverage: existing test patterns, mock strategies, test fixtures_
  - _Requirements: 1.1, 4.1_

- [x] 11. Update TransactionParser unit tests for ML Kit integration
  - File: app/src/test/java/com/voiceexpense/ai/parsing/TransactionParserTest.kt (modify existing)
  - Add test cases for ML Kit success path and fallback decision logic
  - Verify existing test fixtures still pass with new integration
  - Purpose: Ensure parser integration works correctly with comprehensive test coverage
  - _Leverage: existing test fixtures, sample utterances, mock patterns_
  - _Requirements: 1.1, 1.2_

- [x] 12. Create structured prompt engineering tests
  - File: app/src/test/java/com/voiceexpense/ai/parsing/TransactionPromptsTest.kt (new)
  - Test prompt templates with the 5 sample utterances from steering docs
  - Validate expected JSON output format and field mapping
  - Purpose: Ensure prompt engineering produces consistent structured output
  - _Leverage: existing test data, sample transactions, JSON validation utilities_
  - _Requirements: 3.1, 3.2_

- [x] 13. Add error handling unit tests for API failures
  - File: app/src/test/java/com/voiceexpense/ai/error/AiErrorHandlerTest.kt (modify existing)
  - Test ML Kit specific error scenarios: quota exceeded, service unavailable
  - Verify graceful fallback behavior and user-friendly error messages
  - Purpose: Ensure robust error handling for all ML Kit failure modes
  - _Leverage: existing error handler test patterns, mock error responses_
  - _Requirements: 4.1, 4.2_

- [x] 14. Create setup guide integration tests
  - File: app/src/androidTest/java/com/voiceexpense/ui/setup/SetupGuideTest.kt (new)
  - Test setup guide navigation, validation, and status reporting
  - Verify help page displays correctly and provides useful guidance
  - Purpose: Ensure setup guidance UI works correctly for users
  - _Leverage: existing UI test patterns, Espresso testing utilities_
  - _Requirements: 2.1, 2.2_

- [x] 15. Add performance benchmarking for ML Kit parsing
  - File: app/src/androidTest/java/com/voiceexpense/ai/performance/MlKitPerformanceTest.kt (new)
  - Benchmark parsing latency with real ML Kit API calls
  - Verify <3 second parsing requirement is met consistently
  - Purpose: Ensure ML Kit integration meets performance requirements
  - _Leverage: existing performance test utilities, timing measurement patterns_
  - _Requirements: 5.1, 5.2_

- [x] 16. Create end-to-end integration test for complete voice flow
  - File: app/src/androidTest/java/com/voiceexpense/integration/VoiceToSheetsFlowTest.kt (modify existing)
  - Test complete pipeline: widget → service → ASR → ML Kit parsing → repository → sync
  - Use mocked ML Kit responses for CI, real API for device testing
  - Purpose: Verify complete voice expense workflow with ML Kit integration
  - _Leverage: existing integration test patterns, service test utilities_
  - _Requirements: All_

- [x] 17. Add ML Kit configuration to gradle.properties
  - File: gradle.properties (modify existing)
  - Set ML_KIT_GENAI_COORDINATE=com.google.mlkit:genai-rewriting:1.0.0-beta1
  - Add any additional configuration properties needed for ML Kit
  - Purpose: Configure build system to include ML Kit dependencies
  - _Leverage: existing ML Kit dependency configuration pattern_
  - _Requirements: 1.1_

- [x] 18. Update documentation with ML Kit setup instructions
  - File: README.md (modify existing)
  - Add section about ML Kit GenAI requirements and device compatibility
  - Document setup process and troubleshooting steps
  - Purpose: Provide developers and users with clear setup guidance
  - _Leverage: existing README structure and documentation patterns_
  - _Requirements: 2.1_