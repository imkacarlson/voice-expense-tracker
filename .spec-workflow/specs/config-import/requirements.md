# Requirements Document

## Introduction

This feature enables users to import dropdown configuration data from a JSON file into the Voice Expense Tracker app. Users can define their expense categories, income categories, transfer categories, accounts, and tags in a structured JSON file and import it through the Settings screen, replacing all existing dropdown configurations. This streamlines initial setup, backup restoration, and configuration sharing scenarios.

## Alignment with Product Vision

This feature supports the Voice Expense Tracker's goal of providing a streamlined, user-friendly transaction logging experience by:

- **Reducing setup friction**: New users or users resetting their device can quickly restore their personalized categories and accounts from a backup file instead of manually re-entering each option
- **Improving configurability**: Power users who manage complex category hierarchies can maintain their configurations as code (JSON) and version control them
- **Maintaining privacy and data ownership**: Configuration files remain entirely under user control, stored locally and imported manually without cloud services
- **Supporting the configurable dropdown architecture**: Extends the existing settings management system documented in product.md ("Dropdown Configuration: All dropdown fields must be configurable")

## Requirements

### Requirement 1: JSON File Selection

**User Story:** As a user, I want to select a JSON file from my device storage, so that I can import my dropdown configurations into the app

#### Acceptance Criteria

1. WHEN the user taps the "Import Configuration" button in Settings THEN the system SHALL launch an Android file picker (ACTION_OPEN_DOCUMENT) filtered to JSON files (MIME type application/json)
2. WHEN the user selects a valid JSON file THEN the system SHALL read the file contents
3. IF the file cannot be read (permissions, not found, corrupted) THEN the system SHALL display an error toast with the message "Failed to read file: [error details]"
4. WHEN the user cancels the file picker THEN the system SHALL return to Settings without changes

### Requirement 2: JSON Format Validation

**User Story:** As a user, I want the app to validate my JSON file before importing, so that I receive clear feedback if my file is malformed

#### Acceptance Criteria

1. WHEN the system reads the JSON file THEN it SHALL parse and validate against the expected schema
2. IF the JSON is syntactically invalid THEN the system SHALL display an error toast "Invalid JSON format" and abort the import
3. IF required top-level keys are missing (ExpenseCategory, IncomeCategory, TransferCategory, Account, Tag) THEN the system SHALL display an error toast "Missing required configuration sections" and abort the import
4. IF any configuration option is missing required fields (label) THEN the system SHALL display an error toast "Invalid option format in [ConfigType]" and abort the import
5. WHEN validation succeeds THEN the system SHALL proceed to import confirmation

### Requirement 3: Import Confirmation Dialog

**User Story:** As a user, I want to confirm before replacing my existing configurations, so that I don't accidentally lose my current settings

#### Acceptance Criteria

1. WHEN JSON validation succeeds THEN the system SHALL display a confirmation dialog with the title "Replace all configurations?" and message "This will delete all existing dropdown options and defaults. Continue?"
2. WHEN the user taps "Cancel" THEN the system SHALL abort the import and return to Settings
3. WHEN the user taps "Replace" THEN the system SHALL proceed to replace all configurations

### Requirement 4: Configuration Replacement

**User Story:** As a user, I want the import to completely replace my existing configurations, so that my app matches exactly what's in the JSON file

#### Acceptance Criteria

1. WHEN the user confirms the import THEN the system SHALL delete all existing ConfigOption records for all ConfigTypes (ExpenseCategory, IncomeCategory, TransferCategory, Account, Tag)
2. WHEN existing options are deleted THEN the system SHALL insert new ConfigOption records from the JSON file, preserving the label, position, and active fields
3. IF the JSON includes default field mappings (defaultExpenseCategory, defaultIncomeCategory, defaultTransferCategory, defaultAccount) THEN the system SHALL update the corresponding DefaultField values in ConfigRepository
4. WHEN the import transaction completes successfully THEN the system SHALL display a success toast "Configuration imported successfully"
5. IF the import fails (database error) THEN the system SHALL rollback all changes and display an error toast "Import failed: [error details]"

### Requirement 5: UI Refresh After Import

**User Story:** As a user, I want the Settings screen to immediately reflect my imported configurations, so that I can verify the import worked correctly

#### Acceptance Criteria

1. WHEN the import completes successfully THEN the system SHALL refresh the dropdown configuration UI to display the newly imported options
2. WHEN the type spinner is showing a ConfigType THEN the list view SHALL update to show the imported options for that type
3. WHEN a default field exists for the selected ConfigType THEN the default spinner SHALL update to show the imported default selection

### Requirement 6: JSON Schema Definition

**User Story:** As a user, I want to know the exact JSON format expected, so that I can create valid configuration files

#### Acceptance Criteria

1. The JSON schema SHALL support the following structure:
```json
{
  "ExpenseCategory": [
    {"label": "Food", "position": 0, "active": true},
    {"label": "Transport", "position": 1, "active": true}
  ],
  "IncomeCategory": [
    {"label": "Salary", "position": 0, "active": true}
  ],
  "TransferCategory": [
    {"label": "Savings", "position": 0, "active": true}
  ],
  "Account": [
    {"label": "Chase Checking", "position": 0, "active": true},
    {"label": "Citi Card (1234)", "position": 1, "active": true}
  ],
  "Tag": [
    {"label": "Work", "position": 0, "active": true},
    {"label": "Personal", "position": 1, "active": true}
  ],
  "defaults": {
    "defaultExpenseCategory": "Food",
    "defaultIncomeCategory": "Salary",
    "defaultTransferCategory": "Savings",
    "defaultAccount": "Chase Checking"
  }
}
```
2. Each option object SHALL contain: `label` (required, non-empty string), `position` (optional, defaults to index), `active` (optional, defaults to true)
3. The `defaults` object SHALL be optional; if present, values SHALL match existing labels in their respective ConfigType arrays
4. IF a default value references a non-existent label THEN the system SHALL ignore that default and continue with the import

## Non-Functional Requirements

### Code Architecture and Modularity
- **Single Responsibility Principle**: Create a dedicated ConfigImporter utility class to handle JSON parsing, validation, and database operations
- **Modular Design**: Separate concerns: file selection (Activity/Intent), JSON parsing (data class models), validation logic (validator), and database transactions (repository)
- **Dependency Management**: Use existing ConfigRepository for database operations; avoid duplicating data access logic
- **Clear Interfaces**: Define a ConfigImportResult sealed class for success/failure states with specific error types

### Performance
- JSON parsing and validation SHALL complete in <500ms for files up to 10KB
- Database replacement transaction SHALL use a single database transaction to ensure atomicity
- UI refresh SHALL occur asynchronously without blocking the main thread

### Security
- File access SHALL use Android Storage Access Framework (ACTION_OPEN_DOCUMENT) with proper permission handling
- JSON parsing SHALL sanitize input to prevent injection attacks
- Database operations SHALL use Room's parameterized queries to prevent SQL injection
- No sensitive data (OAuth tokens, personal transaction data) SHALL be included in the JSON format

### Reliability
- Import operation SHALL be atomic: either all configurations are replaced successfully, or none are changed (rollback on error)
- Validation errors SHALL provide actionable error messages indicating which section or field failed
- File I/O errors SHALL be caught and handled gracefully with user-facing error messages

### Usability
- Import button SHALL be clearly labeled and positioned near the dropdown configuration section
- Error messages SHALL be specific and actionable (not generic "Import failed")
- Success feedback SHALL confirm the import and number of options imported
- The file picker SHALL default to showing JSON files only to reduce confusion
