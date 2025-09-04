# Requirements Document

## Introduction

This specification defines the requirements for integrating ML Kit GenAI APIs (Gemini Nano) and Android SpeechRecognizer API to replace the current placeholder implementations for voice-to-structured-data processing in the Voice Expense Tracker app. This integration is critical for enabling the core functionality that transforms voice input into structured financial transaction data entirely on-device.

The current implementation contains only placeholder methods that return mock data. This feature will implement real on-device AI processing to achieve the app's privacy-first, offline-capable voice expense tracking vision.

## Alignment with Product Vision

This feature directly enables the core product vision outlined in product.md:
- **Privacy-first**: All AI processing occurs on-device with no cloud AI calls
- **Voice-first capture**: Natural speech input parsed into structured transaction data  
- **Offline-capable**: Full functionality without network dependency for AI processing
- **Speed-first**: Target <3 seconds parsing latency for common transactions
- **>90% accuracy**: Reliable parsing for expense/income/transfer transactions

## Requirements

### Requirement 1: Speech-to-Text Processing

**User Story:** As a user, I want my voice utterance to be transcribed to text accurately so that the system can parse it into transaction data.

#### Acceptance Criteria

1. WHEN user speaks into microphone THEN SpeechRecognitionService SHALL transcribe audio to text using Android's built-in SpeechRecognizer API
2. WHEN transcription completes THEN system SHALL return transcribed text with confidence score
3. WHEN background noise interferes THEN system SHALL handle recognition errors gracefully and request retry
4. WHEN device is offline THEN system SHALL still perform speech recognition using on-device models
5. WHEN recognition fails after 3 attempts THEN system SHALL fall back to manual text input option

### Requirement 2: Text-to-Structured-Data Processing  

**User Story:** As a user, I want my spoken transaction description to be automatically parsed into structured fields (amount, merchant, category, etc.) so that I don't need to manually fill forms.

#### Acceptance Criteria

1. WHEN transcribed text is received THEN TransactionParser SHALL use ML Kit GenAI APIs to extract structured transaction data
2. WHEN parsing completes THEN system SHALL return JSON with fields: amountUsd, merchant, description, type, expenseCategory, incomeCategory, tags, userLocalDate, account, splitOverallChargedUsd, note, confidence
3. WHEN amount is ambiguous ("twenty-three fifty") THEN system SHALL default to lower confidence and allow voice correction
4. WHEN split expense is detected THEN system SHALL capture both user share (Amount) and total charged (Overall Charged) amounts
5. WHEN parsing confidence < 0.7 THEN system SHALL flag fields for user verification

### Requirement 3: Transaction Type Classification

**User Story:** As a user, I want the system to automatically determine if my transaction is an Expense, Income, or Transfer so that it's categorized correctly.

#### Acceptance Criteria

1. WHEN utterance contains "income", "paycheck", "salary" keywords THEN system SHALL classify as Income type
2. WHEN utterance contains "transfer", "move money" keywords THEN system SHALL classify as Transfer type  
3. WHEN utterance describes a purchase or payment THEN system SHALL classify as Expense type
4. WHEN type is uncertain THEN system SHALL default to Expense with lower confidence
5. WHEN type is Transfer THEN system SHALL leave amountUsd field null per steering docs

### Requirement 4: Category and Account Parsing

**User Story:** As a user, I want the system to identify expense categories and payment accounts from my natural speech so that transactions are properly categorized.

#### Acceptance Criteria

1. WHEN expense category is mentioned THEN system SHALL map to predefined categories (Groceries, Dining, Personal, etc.)
2. WHEN account/card is mentioned THEN system SHALL match against known account list from settings  
3. WHEN "Bilt Card five two one seven" is spoken THEN system SHALL map to "Bilt Card (5217)" format
4. WHEN category is unknown THEN system SHALL default to "Uncategorized" 
5. WHEN account is unrecognized THEN system SHALL leave account field null

### Requirement 5: Tag and Split Amount Processing

**User Story:** As a user, I want to include tags and split expense information in my voice input so that complex transactions are captured accurately.

#### Acceptance Criteria

1. WHEN "tags: Auto-Paid, Subscription" is spoken THEN system SHALL parse into tags array ["Auto-Paid", "Subscription"]
2. WHEN "my share is twenty, overall charged thirty" THEN system SHALL set amountUsd=20, splitOverallChargedUsd=30
3. WHEN "tag Splitwise" mentioned with two amounts THEN system SHALL include "Splitwise" in tags array
4. WHEN tags format is non-standard THEN system SHALL attempt best-effort parsing with lower confidence
5. WHEN split amounts are inconsistent THEN system SHALL flag for user verification

## Non-Functional Requirements

### Code Architecture and Modularity
- **Single Responsibility Principle**: SpeechRecognitionService handles only ASR, TransactionParser handles only NLU
- **Modular Design**: AI components isolated from UI and data layers with clear interfaces
- **Dependency Management**: ML Kit dependencies properly declared, graceful degradation if models unavailable
- **Clear Interfaces**: Well-defined contracts between ASR→Parser→Repository with proper error handling

### Performance
- **Parsing Latency**: <3 seconds median for speech-to-structured-data pipeline
- **Memory Usage**: Efficient model loading/unloading, avoid memory leaks from AI components
- **Battery Impact**: Minimize foreground service duration, optimize model inference parameters
- **Cold Start**: First-run model download handled gracefully with user feedback

### Security  
- **On-Device Processing**: No audio or text data sent to cloud AI services
- **Data Privacy**: Transcribed text and parsed results stored securely, cleared after processing
- **Permissions**: Only request microphone permission, no network permission for AI processing
- **Model Security**: Use official Google ML Kit APIs, validate model signatures

### Reliability
- **Error Recovery**: Graceful handling of ASR failures, model unavailability, parsing errors
- **Offline Capability**: Full functionality when device has no network connectivity  
- **Model Updates**: Handle ML Kit model updates automatically without breaking functionality
- **Accuracy Validation**: Confidence scoring and user correction loop for low-confidence results

### Usability
- **Response Time**: Visual feedback during processing, no silent failures
- **Error Messages**: Clear user guidance when ASR fails or parsing needs correction
- **Accessibility**: Voice-first interface compatible with screen readers and accessibility services
- **Language Support**: Initial English-only with extensible architecture for future languages