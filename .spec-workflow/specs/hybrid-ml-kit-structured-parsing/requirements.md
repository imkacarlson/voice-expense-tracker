# Requirements Document

## Introduction

This specification defines the requirements for implementing a hybrid ML Kit + structured parsing approach to achieve production-ready AI-powered transaction extraction. Based on comprehensive research findings, this feature will replace the current placeholder ML Kit integration with advanced prompt engineering techniques that coerce structured JSON output from ML Kit GenAI Rewriting API, while maintaining the existing excellent heuristic parser as an intelligent fallback system.

This hybrid approach addresses the fundamental limitation that ML Kit GenAI APIs don't provide native structured output by using sophisticated prompting techniques validated through industry research, achieving 75-90% AI accuracy for common transaction patterns while ensuring 100% reliability through seamless fallback mechanisms.

## Alignment with Product Vision

This feature directly enables the core product vision outlined in product.md:

- **Privacy-first**: All AI processing occurs on-device using ML Kit GenAI APIs with no cloud calls
- **Voice-first capture**: Advanced AI parsing transforms natural speech into structured transaction data with high accuracy
- **Offline-capable**: Full functionality without network dependency for AI processing after model availability
- **Speed-first**: Target 2-4 seconds parsing latency using optimized prompt engineering techniques
- **>90% accuracy**: Hybrid approach ensures high reliability through AI + heuristic combination

The current placeholder implementation prevents users from experiencing genuine AI-powered parsing. This enhancement delivers the intelligent voice-to-data transformation that makes manual expense entry obsolete while maintaining bulletproof reliability.

## Requirements

### Requirement 1: Advanced Prompt Engineering for Structured Output

**User Story:** As a user, I want my voice input to be processed by sophisticated AI prompts so that I get structured JSON transaction data instead of natural language responses.

#### Acceptance Criteria

1. WHEN user speaks a transaction description THEN system SHALL use few-shot prompting with 3-4 complete input-output examples to guide ML Kit output format
2. WHEN constructing prompts THEN system SHALL include explicit JSON schema definition with exact field requirements and constraints
3. WHEN ML Kit returns response THEN system SHALL validate against expected JSON structure before accepting result
4. WHEN prompt construction occurs THEN system SHALL use constraint language specifying "output must be parseable with JSON.parse() and nothing else"
5. WHEN AI processing completes THEN system SHALL assign confidence scores based on JSON validity and field completeness (0.0-1.0 scale)

### Requirement 2: Hybrid Processing Pipeline with Intelligent Fallback

**User Story:** As a user, I want the system to seamlessly combine AI and heuristic parsing so that I always get accurate transaction data regardless of AI performance.

#### Acceptance Criteria

1. WHEN voice input is received THEN system SHALL attempt ML Kit structured parsing as primary method
2. WHEN ML Kit returns valid JSON with confidence >0.7 THEN system SHALL use AI result directly
3. WHEN ML Kit returns invalid JSON OR confidence ≤0.7 THEN system SHALL fallback to heuristic parser with ML Kit context
4. WHEN heuristic fallback occurs THEN system SHALL use any valid fields extracted from AI attempt to enhance heuristic parsing accuracy
5. WHEN processing completes THEN system SHALL clearly indicate extraction method used (AI, hybrid, or heuristic) for debugging and optimization

### Requirement 3: Template-Based Prompt Construction

**User Story:** As a developer, I want sophisticated prompt templates so that the AI consistently produces structured output matching our transaction schema.

#### Acceptance Criteria

1. WHEN building prompts THEN system SHALL include complete transaction schema with all required fields: amountUsd, merchant, type, expenseCategory, incomeCategory, tags, account, splitOverallChargedUsd, confidence
2. WHEN providing examples THEN system SHALL include representative samples for each transaction type (Expense, Income, Transfer) with realistic merchant names and amounts
3. WHEN processing complex scenarios THEN system SHALL include examples for split transactions, tag parsing, and account matching patterns
4. WHEN handling edge cases THEN system SHALL provide examples for ambiguous amounts, unknown merchants, and missing information scenarios
5. WHEN prompt templates are used THEN system SHALL achieve target 75-90% accuracy for common transaction patterns as validated through research

### Requirement 4: Performance Optimization and Resource Management

**User Story:** As a user, I want AI processing to be fast and efficient so that voice expense capture doesn't slow down my workflow.

#### Acceptance Criteria

1. WHEN AI processing occurs THEN system SHALL complete within 2-4 seconds target range as established by research benchmarks
2. WHEN ML Kit client is created THEN system SHALL properly manage Rewriter lifecycle with try/finally resource cleanup
3. WHEN multiple transactions are processed rapidly THEN system SHALL handle concurrent requests without performance degradation
4. WHEN AI processing fails THEN system SHALL timeout after maximum 5 seconds and proceed to fallback processing
5. WHEN device memory is constrained THEN system SHALL maintain lightweight operation by sharing Gemini Nano model across app sessions

### Requirement 5: Validation and Quality Assurance Pipeline

**User Story:** As a user, I want the system to validate AI output thoroughly so that I receive accurate, consistent transaction data.

#### Acceptance Criteria

1. WHEN ML Kit returns response THEN system SHALL validate JSON syntax using robust parsing with error handling
2. WHEN JSON is valid THEN system SHALL verify all required fields are present and properly typed (numbers as numbers, strings as strings, arrays as arrays)
3. WHEN business rules apply THEN system SHALL validate constraints like splitOverallChargedUsd ≥ amountUsd for split transactions
4. WHEN validation fails THEN system SHALL log specific validation errors for debugging while proceeding to fallback processing
5. WHEN confidence scoring occurs THEN system SHALL assign scores based on field completeness, validation success, and extraction method reliability

## Non-Functional Requirements

### Code Architecture and Modularity

- **Single Responsibility Principle**: HybridTransactionParser orchestrates flow only; StructuredPromptBuilder handles prompt engineering; ValidationPipeline handles JSON validation
- **Modular Design**: Prompt templates, validation rules, and confidence scoring are separate configurable components
- **Dependency Management**: ML Kit integration isolated from business logic; existing heuristic parser remains unchanged
- **Clear Interfaces**: Well-defined contracts between AI processing, validation, and fallback systems with proper error propagation

### Performance

- **Processing Latency**: 2-4 seconds median for complete hybrid processing pipeline including AI attempt and potential fallback
- **Memory Efficiency**: Lightweight prompt construction and JSON validation with immediate cleanup of processing artifacts
- **Accuracy Targets**: 75-90% accuracy for AI path as validated by research; 100% reliability through hybrid approach
- **Resource Management**: Proper ML Kit Rewriter lifecycle management with automatic resource cleanup and error recovery

### Security

- **On-Device Processing**: All prompt construction and AI processing occurs locally via ML Kit APIs with no cloud dependencies
- **Data Privacy**: Voice transcripts and AI responses processed in memory only with immediate cleanup after JSON extraction
- **Input Validation**: Robust validation of AI output prevents injection or malformed data from entering transaction processing pipeline
- **Error Handling**: Comprehensive error handling prevents sensitive information leakage through logs or crash reports

### Reliability

- **Graceful Degradation**: Seamless fallback to proven heuristic parser ensures zero transaction loss regardless of AI performance
- **Error Recovery**: Robust handling of ML Kit failures, malformed JSON, validation errors, and timeout scenarios
- **Consistency**: Deterministic processing behavior with clear decision points for AI vs heuristic path selection
- **Monitoring**: Comprehensive logging and metrics collection for accuracy tracking and system optimization

### Usability

- **Transparent Operation**: AI processing happens seamlessly with users unaware of hybrid processing complexity
- **Performance Feedback**: Clear processing indicators during AI analysis with graceful timeout handling
- **Quality Indicators**: Confidence scores and extraction method transparency for power users and debugging
- **Backward Compatibility**: Existing transaction processing workflows remain unchanged; hybrid processing is purely additive enhancement