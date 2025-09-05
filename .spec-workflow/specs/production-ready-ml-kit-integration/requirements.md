# Requirements Document

## Introduction

This specification defines the requirements for replacing placeholder ML Kit GenAI integration with production-ready implementation in the Voice Expense Tracker app. Currently, the `TransactionParser.runGenAi()` method returns empty strings, causing the system to fall back to basic heuristic parsing. This feature will implement real ML Kit GenAI Rewriting API integration to enable natural language parsing of voice input into structured financial transaction data entirely on-device.

This implementation assumes that users have already set up AICore and Gemini Nano on their device as a prerequisite. The app will focus on using the AI APIs rather than managing model lifecycle.

## Alignment with Product Vision

This feature directly enables the core product vision outlined in product.md:

- **Privacy-first**: All AI processing occurs on-device using ML Kit GenAI APIs with no cloud calls
- **Voice-first capture**: Natural speech input parsed into structured transaction data using Gemini Nano
- **Offline-capable**: Full functionality without network dependency for AI processing
- **Speed-first**: Leverage optimized on-device inference for <3 seconds parsing latency
- **>90% accuracy**: Production-ready parsing using Google's Gemini Nano foundation model

The current placeholder implementation prevents users from experiencing the app's core value proposition. This integration completes the voice-to-data pipeline that makes manual expense entry obsolete.

## Requirements

### Requirement 1: ML Kit GenAI API Integration

**User Story:** As a user, I want my voice input to be parsed using Google's production AI models so that I get accurate, reliable transaction data extraction.

#### Acceptance Criteria

1. WHEN user speaks a transaction description THEN system SHALL use ML Kit GenAI Rewriting API to transform natural language to structured JSON
2. WHEN AI processing completes THEN system SHALL return ParsedResult with proper confidence scores (0.0-1.0)
3. WHEN ML Kit model is unavailable THEN system SHALL fall back to existing heuristic parser gracefully
4. WHEN rewriting request exceeds 256 tokens THEN system SHALL truncate input and log warning
5. WHEN app is not in foreground THEN system SHALL queue AI requests until app becomes active

### Requirement 2: User Setup Guidance

**User Story:** As a user, I want clear instructions on how to set up AI capabilities on my device so that I can enable advanced voice parsing features.

#### Acceptance Criteria

1. WHEN user accesses help/setup page THEN system SHALL display step-by-step instructions for enabling AICore and Gemini Nano
2. WHEN AI model is not available THEN system SHALL show helpful error message with link to setup instructions
3. WHEN user completes setup steps THEN system SHALL provide validation method to confirm AI features are working
4. WHEN device is incompatible THEN system SHALL clearly explain device requirements and limitations
5. WHEN setup fails THEN system SHALL provide troubleshooting steps and fallback options

### Requirement 3: Structured Prompt Engineering

**User Story:** As a user, I want the AI to understand various ways I describe transactions so that I can speak naturally without following rigid patterns.

#### Acceptance Criteria

1. WHEN user describes expense with amount and merchant THEN AI SHALL extract both fields with >90% accuracy
2. WHEN user mentions categories like "groceries" or "dining" THEN AI SHALL map to predefined expense categories
3. WHEN user specifies split amounts ("my share twenty, total thirty") THEN AI SHALL populate both Amount and splitOverallChargedUsd fields
4. WHEN user includes tags ("tags: Auto-Paid, Subscription") THEN AI SHALL parse into structured tags array
5. WHEN user mentions account/card names THEN AI SHALL match against known accounts from user settings

### Requirement 4: Error Handling and API Resilience

**User Story:** As a user, I want the app to work reliably even when AI processing fails so that I never lose transaction data.

#### Acceptance Criteria

1. WHEN ML Kit returns error response THEN system SHALL log error details and fall back to heuristic parser
2. WHEN AICore quota is exceeded THEN system SHALL implement exponential backoff retry with user notification  
3. WHEN AI service is temporarily unavailable THEN system SHALL gracefully degrade to fallback parsing
4. WHEN AI processing takes >5 seconds THEN system SHALL timeout and use fallback with warning message
5. WHEN device bootloader is unlocked THEN system SHALL detect incompatibility and permanently use fallback parsing

### Requirement 5: Performance Optimization

**User Story:** As a user, I want expense capture to be fast and responsive so that voice logging doesn't disrupt my workflow.

#### Acceptance Criteria

1. WHEN AI model is available THEN parsing SHALL complete within 2 seconds for typical transactions
2. WHEN multiple requests are made rapidly THEN system SHALL batch or queue to avoid quota errors
3. WHEN app is backgrounded during processing THEN system SHALL pause AI requests and resume when foregrounded
4. WHEN memory pressure occurs THEN system SHALL handle resource constraints gracefully
5. WHEN AI processing is delayed THEN system SHALL provide immediate feedback to user about processing status

## Non-Functional Requirements

### Code Architecture and Modularity

- **Single Responsibility Principle**: TransactionParser handles only text-to-JSON conversion; setup guidance is separate UI component
- **Modular Design**: AI components remain isolated in ai/ package with clear interfaces to service layer
- **Dependency Management**: ML Kit dependencies properly declared with version constraints and conflict resolution  
- **Clear Interfaces**: Well-defined contracts between VoiceRecordingService, TransactionParser with proper error propagation

### Performance

- **Parsing Latency**: <3 seconds median for voice-to-structured-data pipeline including ASR + AI parsing
- **Memory Efficiency**: Minimal memory footprint since model lifecycle is handled by system
- **Battery Impact**: Minimize inference time and handle API calls efficiently
- **Responsiveness**: Immediate fallback to heuristic parsing when AI is unavailable

### Security  

- **On-Device Processing**: All text processing occurs locally via ML Kit APIs, no data sent to Google servers
- **Data Privacy**: Transcribed text cleared from memory after parsing, no persistent storage of voice data
- **Permissions**: No additional permissions required beyond existing microphone access
- **Model Integrity**: Use official Google ML Kit APIs with built-in model signature validation

### Reliability

- **Graceful Degradation**: Always fall back to existing heuristic parser when ML Kit is unavailable
- **Error Recovery**: Robust handling of API failures, quota limits, and service unavailability
- **State Management**: Proper cleanup of AI resources on app lifecycle events  
- **Consistency**: Deterministic parsing behavior across app restarts and device reboots

### Usability

- **Transparent Operation**: AI processing happens seamlessly without requiring user understanding of technical details
- **Setup Guidance**: Clear, actionable instructions for enabling AI features on user's device
- **Error Communication**: User-friendly messages when AI features are unavailable with link to help page
- **Accessibility**: Voice-first interface remains fully functional for users with visual impairments