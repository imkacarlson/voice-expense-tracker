# Tasks Document

- [x] 1. Create few-shot prompt example repository
  - File: app/src/main/java/com/voiceexpense/ai/parsing/hybrid/FewShotExampleRepository.kt (new)
  - Define comprehensive prompt examples for each transaction type with realistic patterns
  - Include examples for expense, income, transfer, split transactions, and edge cases
  - Purpose: Provide research-validated examples to guide ML Kit toward structured output
  - _Leverage: existing sample utterances from steering documents and requirements_
  - _Requirements: 1.1, 3.1_

- [x] 2. Create structured prompt builder with template system
  - File: app/src/main/java/com/voiceexpense/ai/parsing/hybrid/PromptBuilder.kt (new)
  - Implement sophisticated prompt construction with schema templates and constraint language
  - Add few-shot example selection based on input characteristics and context
  - Purpose: Build research-backed prompts that coerce ML Kit into JSON output format
  - _Leverage: existing ParsingContext, transaction schema from ParsedResult model_
  - _Requirements: 1.1, 3.1_

- [x] 3. Create comprehensive validation pipeline for AI responses
  - File: app/src/main/java/com/voiceexpense/ai/parsing/hybrid/ValidationPipeline.kt (new)
  - Implement JSON syntax validation, schema validation, and business rule checking
  - Add confidence scoring based on field completeness and validation success
  - Purpose: Ensure AI-generated JSON meets transaction schema requirements and business constraints
  - _Leverage: existing StructuredOutputValidator, Transaction model validation rules_
  - _Requirements: 5.1, 5.2_

- [x] 4. Enhance MlKitClient with structured prompt processing
  - File: app/src/main/java/com/voiceexpense/ai/parsing/MlKitClient.kt (modify existing)
  - Replace simple rewrite method with structured prompt processing capability
  - Add prompt-specific error handling and response validation
  - Purpose: Enable sophisticated prompt engineering through existing ML Kit integration
  - _Leverage: existing MlKitClient infrastructure, resource lifecycle management_
  - _Requirements: 1.1, 4.1_

- [x] 5. Implement hybrid transaction parser orchestrator
  - File: app/src/main/java/com/voiceexpense/ai/parsing/hybrid/HybridTransactionParser.kt (new)
  - Create main orchestrator that coordinates AI attempt → validation → fallback pipeline
  - Add processing method tracking and performance metrics collection
  - Purpose: Provide single entry point for hybrid processing with intelligent fallback logic
  - _Leverage: existing TransactionParser patterns, heuristic parsing logic_
  - _Requirements: 2.1, 2.2, 2.3_

- [x] 6. Create processing result models with metadata
  - File: app/src/main/java/com/voiceexpense/ai/parsing/hybrid/ProcessingModels.kt (new)
  - Define HybridParsingResult, ValidationResult, ProcessingStatistics models
  - Add processing method enumeration and confidence scoring structures
  - Purpose: Provide rich metadata about processing pipeline for debugging and optimization
  - _Leverage: existing ParsedResult model, error handling patterns_
  - _Requirements: 2.1, 5.1_

- [x] 7. Integrate hybrid parser into existing TransactionParser
  - File: app/src/main/java/com/voiceexpense/ai/parsing/TransactionParser.kt (modify existing)
  - Replace current ML Kit integration with hybrid processing pipeline
  - Maintain existing interface while adding hybrid processing capabilities
  - Purpose: Seamlessly integrate hybrid processing into existing transaction pipeline
  - _Leverage: existing TransactionParser interface, runGenAi method structure_
  - _Requirements: 2.1, 2.2_

- [x] 8. Add schema template configuration system
  - File: app/src/main/java/com/voiceexpense/ai/parsing/hybrid/SchemaTemplates.kt (new)
  - Define JSON schema templates with field definitions and constraints
  - Add configurable template selection based on transaction complexity
  - Purpose: Provide consistent schema definitions for prompt construction
  - _Leverage: existing Transaction model schema, ParsedResult field definitions_
  - _Requirements: 3.1_

- [x] 9. Create confidence scoring algorithm
  - File: app/src/main/java/com/voiceexpense/ai/parsing/hybrid/ConfidenceScorer.kt (new)
  - Implement multi-factor confidence scoring based on validation results and processing method
  - Add weighted scoring for field completeness, validation success, and AI vs heuristic processing
  - Purpose: Provide reliable confidence metrics for processing quality assessment
  - _Leverage: existing validation logic, processing method tracking_
  - _Requirements: 5.1, 5.2_

- [x] 10. Add hybrid processing to dependency injection
  - File: app/src/main/java/com/voiceexpense/di/AiModule.kt (modify existing)
  - Register new hybrid processing components with proper scoping and dependencies
  - Configure component graph for PromptBuilder → ValidationPipeline → HybridParser
  - Purpose: Enable dependency injection for hybrid processing components
  - _Leverage: existing Hilt module patterns, component registration_
  - _Requirements: 2.1_

- [x] 11. Create comprehensive unit tests for prompt engineering
  - File: app/src/test/java/com/voiceexpense/ai/parsing/hybrid/PromptBuilderTest.kt (new)
  - Test prompt construction with different transaction types and complexity levels
  - Validate few-shot example selection and schema template integration
  - Purpose: Ensure prompt engineering produces consistent, effective prompts for ML Kit
  - _Leverage: existing test patterns, sample transaction fixtures_
  - _Requirements: 3.1_

- [x] 12. Create validation pipeline unit tests
  - File: app/src/test/java/com/voiceexpense/ai/parsing/hybrid/ValidationPipelineTest.kt (new)
  - Test JSON validation with valid and malformed AI responses
  - Verify business rule validation and confidence scoring accuracy
  - Purpose: Ensure validation pipeline correctly identifies valid vs invalid AI output
  - _Leverage: existing validation test patterns, mock AI responses_
  - _Requirements: 5.1, 5.2_

- [x] 13. Create hybrid processing integration tests
  - File: app/src/test/java/com/voiceexpense/ai/parsing/hybrid/HybridTransactionParserTest.kt (new)
  - Test complete pipeline: prompt construction → AI processing → validation → fallback
  - Mock ML Kit responses to test both success and failure scenarios
  - Purpose: Validate end-to-end hybrid processing pipeline functionality
  - _Leverage: existing integration test patterns, mock strategies_
  - _Requirements: 2.1, 2.2, 2.3_

- [x] 14. Update existing TransactionParser tests for hybrid integration
  - File: app/src/test/java/com/voiceexpense/ai/parsing/TransactionParserTest.kt (modify existing)
  - Add test cases for hybrid processing success and fallback scenarios
  - Verify existing heuristic parsing continues to work as fallback
  - Purpose: Ensure hybrid integration doesn't break existing parsing functionality
  - _Leverage: existing test fixtures, sample utterances, mock patterns_
  - _Requirements: 2.1, 2.2_

- [x] 15. Create few-shot example effectiveness tests
  - File: app/src/test/java/com/voiceexpense/ai/parsing/hybrid/FewShotExampleRepositoryTest.kt (new)
  - Test example selection algorithms for different transaction patterns
  - Validate example quality and relevance scoring
  - Purpose: Ensure few-shot examples effectively guide AI toward structured output
  - _Leverage: existing sample transaction patterns, test utilities_
  - _Requirements: 3.1_

- [x] 16. Add performance benchmarking for hybrid processing
  - File: app/src/androidTest/java/com/voiceexpense/ai/parsing/hybrid/HybridPerformanceTest.kt (new)
  - Benchmark processing time for AI success vs fallback scenarios
  - Measure accuracy improvements over heuristic-only processing
  - Purpose: Validate performance meets research targets (2-4 seconds, 75-90% accuracy)
  - _Leverage: existing performance test utilities, timing measurement patterns_
  - _Requirements: 4.1, 4.2_

- [x] 17. Create end-to-end accuracy validation tests
  - File: app/src/androidTest/java/com/voiceexpense/ai/parsing/hybrid/AccuracyValidationTest.kt (new)
  - Test hybrid processing against comprehensive sample utterances from requirements
  - Measure accuracy rates for different transaction types and complexity levels
  - Purpose: Validate hybrid approach achieves research-backed accuracy targets
  - _Leverage: sample utterances from requirements document, accuracy measurement utilities_
  - _Requirements: All_

- [x] 18. Add processing statistics and monitoring
  - File: app/src/main/java/com/voiceexpense/ai/parsing/hybrid/ProcessingMonitor.kt (new)
  - Implement statistics collection for AI success rates, processing times, fallback rates
  - Add performance monitoring and optimization recommendations
  - Purpose: Provide visibility into hybrid processing performance for optimization
  - _Leverage: existing logging patterns, performance measurement utilities_
  - _Requirements: 4.1, 5.1_

- [x] 19. Create error handling integration for hybrid failures
  - File: app/src/main/java/com/voiceexpense/ai/error/AiErrorHandler.kt (modify existing)
  - Add hybrid-specific error handling for prompt construction, validation, and processing failures
  - Implement circuit breaker patterns for repeated AI failures
  - Purpose: Provide robust error recovery for complex hybrid processing pipeline
  - _Leverage: existing error handling infrastructure, circuit breaker patterns_
  - _Requirements: 4.1, 5.2_

- [x] 20. Add hybrid processing documentation and examples
  - File: docs/hybrid-ml-kit-integration.md (new)
  - Document hybrid processing architecture, prompt engineering techniques, and usage examples
  - Include troubleshooting guide for AI processing issues and performance optimization tips
  - Purpose: Provide comprehensive documentation for hybrid processing implementation
  - _Leverage: existing documentation patterns, architectural decision records_
  - _Requirements: All_