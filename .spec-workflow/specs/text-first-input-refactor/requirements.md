# Requirements Document

## Introduction

This feature refactors the Android expense-logging app from its current voice-first approach to a text-only approach by removing all custom voice functionality. The goal is to simplify the codebase by eliminating custom voice capture, speech recognition, and voice correction components, while leveraging the existing text input that's already present in MainActivity.

The current app has extensive custom voice functionality including voice recording services, speech recognition, voice correction loops, and voice-based UI interactions. This refactor will remove all of that complexity and rely solely on the existing text input field, with users able to use Android's built-in voice-to-text via Google Keyboard if they choose.

## Alignment with Product Vision

This feature directly supports several key goals from the product vision:

- **Codebase Simplification**: Significantly reduces complexity by removing custom voice functionality that's difficult to maintain
- **Intuitive Form Interface**: Leverages the existing comprehensive form interface as the primary user experience
- **User Choice**: Users can still use voice-to-text via Android's built-in Google Keyboard functionality when desired
- **Privacy-First**: Maintains the on-device AI processing approach for text parsing while removing custom voice processing
- **Reliability**: Improves app reliability by removing complex custom voice components and potential failure points

The refactor maintains the technical constraints of on-device text parsing, USD-only currency, and Google Sheets integration while dramatically simplifying the codebase.

## Requirements

### Requirement 1

**User Story:** As a developer, I want to remove all custom voice functionality from the app, so that the codebase is significantly simplified and easier to maintain.

#### Acceptance Criteria

1. WHEN custom voice code is removed THEN the system SHALL delete all voice recording services, speech recognition components, and voice correction logic
2. WHEN the voice components are removed THEN the system SHALL remove all voice-related dependencies from build.gradle files
3. WHEN voice code is deleted THEN the system SHALL remove all voice-related UI components, activities, and services
4. WHEN the refactor is complete THEN the system SHALL have no references to SpeechRecognizer, AudioRecordingManager, VoiceRecordingService, or voice correction components
5. IF voice-related files are removed THEN the system SHALL ensure no broken references remain in the codebase

### Requirement 2

**User Story:** As a user, I want to use the existing text input as the primary and only method for entering transactions, so that I have a simple and reliable way to log expenses.

#### Acceptance Criteria

1. WHEN the user opens the main app THEN the existing text input field SHALL be the only input method available
2. WHEN the user types a transaction description and submits THEN the system SHALL parse the text using the existing on-device AI and display the confirmation form
3. WHEN the user wants to use voice THEN the user SHALL be able to use Android's built-in voice-to-text via Google Keyboard
4. WHEN the app is simplified THEN the text input workflow SHALL remain unchanged from the current MainActivity implementation
5. IF the user needs voice input THEN the Android keyboard's microphone button SHALL provide voice-to-text functionality

### Requirement 3

**User Story:** As a developer, I want to completely remove the home screen widget from the codebase, so that the app is simplified and can have a widget added back later if needed.

#### Acceptance Criteria

1. WHEN the widget is removed THEN the system SHALL delete all widget-related components including ExpenseWidgetProvider and associated layouts
2. WHEN the widget code is deleted THEN the system SHALL remove widget declarations from AndroidManifest.xml
3. WHEN the widget is removed THEN the system SHALL delete widget configuration activities and related UI components
4. WHEN the widget components are deleted THEN the system SHALL remove all widget-related string resources and drawable assets
5. IF the widget is removed THEN the system SHALL ensure no broken references to widget components remain in the codebase

### Requirement 4

**User Story:** As a developer, I want to remove the voice correction functionality from the confirmation screen, so that the form interface is simplified and more reliable.

#### Acceptance Criteria

1. WHEN the confirmation screen is refactored THEN the system SHALL remove the "Speak Correction" button and all voice correction logic
2. WHEN voice corrections are removed THEN the system SHALL remove VoiceCorrectionController, TtsEngine, CorrectionIntentParser, and PromptRenderer
3. WHEN the form is simplified THEN the system SHALL maintain all existing manual editing capabilities for form fields
4. WHEN voice correction is removed THEN the system SHALL remove all voice-related imports and dependencies from the confirmation activity
5. IF voice correction components are deleted THEN the system SHALL ensure form validation and editing still works correctly

### Requirement 5

**User Story:** As a developer, I want to clean up the codebase by removing unused voice-related configuration and dependencies, so that the project is leaner and more maintainable.

#### Acceptance Criteria

1. WHEN voice components are removed THEN the system SHALL remove voice-related permissions from AndroidManifest.xml
2. WHEN dependencies are cleaned up THEN the system SHALL remove speech recognition and audio recording library dependencies
3. WHEN voice services are removed THEN the system SHALL remove service declarations and intent filters from the manifest
4. WHEN the cleanup is complete THEN the system SHALL remove voice-related string resources, layouts, and drawable assets
5. IF voice functionality is removed THEN the system SHALL update documentation to reflect the text-only approach

### Requirement 6

**User Story:** As a user, I want the app to maintain all existing functionality except voice input, so that I can continue using all features I rely on.

#### Acceptance Criteria

1. WHEN voice components are removed THEN the system SHALL maintain all existing text parsing, form editing, and Google Sheets sync functionality
2. WHEN the refactor is complete THEN the system SHALL preserve all configuration options, dropdown management, and transaction history features
3. WHEN voice is removed THEN the system SHALL maintain the same AI parsing performance and accuracy for text input
4. WHEN the app is simplified THEN the system SHALL preserve all existing database operations, authentication, and background sync
5. IF voice features are removed THEN the system SHALL ensure no other functionality is broken or degraded

## Non-Functional Requirements

### Code Architecture and Modularity

- **Component Removal**: Systematically remove all voice-related components without breaking existing functionality
- **Dependency Cleanup**: Remove unused dependencies and imports related to voice processing
- **Service Simplification**: Eliminate voice recording services and related background processing
- **Clear Separation**: Maintain existing separation between text input, AI parsing, and form presentation layers
- **Codebase Reduction**: Significantly reduce overall codebase size and complexity

### Performance

- **Text Input Responsiveness**: Maintain existing text input performance (<100ms keystroke response)
- **Parsing Performance**: Maintain existing AI parsing performance for text input (<3s requirement)
- **App Startup**: App startup should be faster due to removal of voice service initialization
- **Memory Usage**: Reduced memory footprint due to removal of voice processing components
- **Build Time**: Faster build times due to fewer dependencies and source files

### Security

- **Permission Reduction**: Remove RECORD_AUDIO and other voice-related permissions from app manifest
- **Attack Surface Reduction**: Eliminate potential security risks associated with audio recording and voice processing
- **Data Privacy**: Maintain existing on-device AI processing for text parsing
- **Secure Storage**: No changes to existing secure token storage or authentication mechanisms

### Reliability

- **Error Reduction**: Eliminate voice-related error scenarios and failure points
- **Service Stability**: Remove foreground voice recording service that could cause ANRs or crashes
- **Parsing Robustness**: Maintain existing text parsing reliability without voice processing complexity
- **State Management**: Simplify app state management by removing voice-related states
- **Crash Prevention**: Reduce potential crash scenarios by removing complex voice processing pipeline

### Usability

- **Interface Simplification**: Provide a cleaner, simpler interface focused solely on text input
- **Learning Curve**: Reduce learning curve by eliminating voice-specific UI and interactions
- **Accessibility**: Improve accessibility by focusing on keyboard input and standard Android interactions
- **Voice Via System**: Users can still use voice through Android's built-in voice-to-text in Google Keyboard
- **Consistency**: Provide a more consistent experience without voice/text mode switching