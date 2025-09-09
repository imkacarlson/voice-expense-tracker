# Complete Form Interface Implementation - Requirements

## Introduction

Transform the current basic EditText-based transaction confirmation form into a comprehensive, production-ready UI that fully aligns with the CSV schema used for Google Sheets sync. The form must provide correct input types, excellent UX with smart behavior, real-time validation, and a user-configurable catalog for dropdowns and tags. The implementation must preserve compatibility with existing transactions and voice/AI parsing flows.

Current context (codebase):
- UI: `TransactionConfirmationActivity` uses `EditText` fields for amount, overall, merchant, description, type, category, tags, account, date, and note; conditional logic is minimal; date is free text (YYYY-MM-DD); tags are comma-separated.
- Data model: `Transaction` includes `userLocalDate`, `amountUsd`, `merchant`, `description`, `type` (Expense | Income | Transfer), `expenseCategory`, `incomeCategory`, `tags`, `account`, `splitOverallChargedUsd`, `note`, `confidence`, etc. No transfer-specific fields exist yet.
- Settings: Known accounts stored as a comma-separated string (`SettingsKeys.KNOWN_ACCOUNTS`); no structured, user-editable configuration store for categories/tags.
- Parsing: AI pre-fills draft transactions; users can manually edit in the confirmation screen.

## Alignment with Product Vision

This feature advances the voice-first expense tracker by making the manual confirmation/edit step fast, accurate, and configurable. It reduces friction after AI parsing, ensures the app can represent all transaction types (Expense, Income, Transfer) faithfully to the CSV, and enables power users to adapt dropdown options without code changes. It preserves the on-device-first architecture and lightweight sync model described in README, improving usability and reliability without expanding network scope.

## Requirements

### Requirement 1: Complete CSV Field Coverage

User Story: As a user, I want the form to cover every column of my actual CSV so that all transaction types (Expense, Income, Transfer) can be captured correctly without losing information.

Acceptance Criteria
1. WHEN editing a transaction THEN the form SHALL expose fields for: Date, Amount (personal share), Description, Type, Expense Category, Tags, Income Category, Account/Credit Card, Overall Charged, Transfer Category, Transfer Destination.
2. IF Type is Expense THEN the form SHALL require Expense Category and Amount.
3. IF Type is Income THEN the form SHALL require Income Category and Amount.
4. IF Type is Transfer THEN the form SHALL require Transfer Category and Transfer Destination and SHALL not require Amount.
5. IF existing transactions are opened THEN new fields SHALL display empty/defaults without breaking load.

Notes
- Add new model fields for transfer metadata (e.g., `transferCategory`, `transferDestination`) and handle DB migration.

### Requirement 2: Proper Input Types

User Story: As a user, I want each field to use the correct UI control so that input is clear, fast, and validated at the source.

Acceptance Criteria
1. WHEN user taps Date THEN the system SHALL show a native DatePicker and write MM/DD display with ISO storage (YYYY-MM-DD) under the hood.
2. WHEN user edits Amount or Overall Charged THEN the system SHALL use numeric decimal input and validate positive numbers only.
3. WHEN selecting categories/accounts THEN the system SHALL use dropdowns (Spinner or Material exposed menu) populated from user-configured options.
4. WHEN editing Tags THEN the system SHALL use a multi-select control and render selected items as chips.
5. WHEN entering text fields (Description, Note) THEN the system SHALL use standard text input with appropriate capitalization.

### Requirement 3: Smart Form Behavior

User Story: As a user, I want the form to react to my choices so that I only see relevant fields and get smart defaults.

Acceptance Criteria
1. WHEN Type = Expense THEN the system SHALL show Amount, Expense Category, Tags, Account, Overall Charged and hide Income/Transfer fields.
2. WHEN Type = Income THEN the system SHALL show Amount, Income Category, Tags, Account and hide Expense/Overall/Transfer fields.
3. WHEN Type = Transfer THEN the system SHALL show Transfer Category, Transfer Destination, Tags and hide Amount, Expense, Income, Account, Overall fields.
4. WHEN opening a new draft THEN the system SHALL default Date to today and pre-select default categories/accounts where configured.
5. WHEN user edits fields THEN the system SHALL show inline validation messages in real time and disable Confirm until valid.

### Requirement 4: Configuration Management

User Story: As a user, I want to manage dropdown options and defaults in Settings so that I can tailor categories, accounts, and tags to my workflow.

Acceptance Criteria
1. WHEN opening Settings → Dropdown Configuration THEN the system SHALL list current options for Expense Categories, Income Categories, Transfer Categories, Accounts, and Tags with add/edit/delete/reorder capabilities.
2. WHEN user sets a default option for a field type THEN the system SHALL pre-select it for new transactions (without overriding AI-parsed values).
3. WHEN user adds/edits/removes options THEN the system SHALL persist changes locally and reflect them immediately in the form selectors.
4. WHEN user imports/exports configurations (future) THEN the system MAY provide backup/restore of option sets.

Notes
- Introduce a `ConfigRepository` and Room-backed entities for option sets; use SharedPreferences only for small default selectors if needed; prefer Room for structured data and migration safety.

### Requirement 5: Validation Rules

User Story: As a user, I want the app to prevent invalid submissions and explain problems clearly so that my data stays consistent.

Acceptance Criteria
1. WHEN Amount is required THEN the system SHALL enforce positive decimal values.
2. WHEN Overall Charged is present AND Type = Expense THEN the system SHALL enforce Overall Charged ≥ Amount.
3. WHEN Type = Transfer THEN the system SHALL require both Transfer Category and Transfer Destination.
4. WHEN required Category is missing for Expense/Income THEN the system SHALL block submission and show a clear error.
5. WHEN Date is invalid THEN the system SHALL show an inline error and block submission.

### Requirement 6: Backwards Compatibility and Data Migration

User Story: As an existing user, I want my previous transactions to continue working after the upgrade so that I can edit and view them safely.

Acceptance Criteria
1. WHEN upgrading the schema to add transfer fields and configuration tables THEN the system SHALL provide a Room migration that preserves existing rows.
2. WHEN loading an existing transaction without transfer fields THEN the system SHALL treat new fields as null/empty defaults.
3. WHEN editing and saving existing transactions THEN the system SHALL succeed without data loss.

## Non-Functional Requirements

### Code Architecture and Modularity
- Single Responsibility Principle: Split UI (views/adapters), state (ViewModel), config repository, and validation engine.
- Modular Design: Reusable input components (DatePicker field, Dropdown field, Tag chooser) reference shared validation.
- Clear Interfaces: Define contracts for `ConfigRepository` (fetch/update options, observe changes) and `ValidationEngine` (validate by type and rule set).
- Dependency Management: Avoid coupling UI to storage; ViewModel mediates between UI and repositories.

### Performance
- Form opens and renders within 500 ms on mid-range devices.
- Dropdown configuration changes persist within 200 ms.
- Real-time validation reacts within 100 ms to input changes.
- Tag selector handles 20+ tags without noticeable lag; long lists use efficient adapters.

### Security
- Store configuration and defaults locally; no additional network scopes introduced.
- Respect existing encrypted storage for auth; do not log PII from transactions in release builds.

### Reliability
- Provide comprehensive Room migrations for new tables/columns; include fallback tests for upgrade/downgrade.
- Ensure form state survives configuration changes (rotation) via ViewModel and saved state.

### Usability
- Logical field order matches mental model by Type.
- Required fields clearly indicated; errors are actionable and specific.
- New users can configure basic categories within 2 minutes; power users can efficiently tag with 20+ tags.
- Accessible: Support TalkBack labels, larger text, and touch targets.

