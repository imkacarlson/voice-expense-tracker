# Tasks Document

## Implementation Tasks for Config Import Feature

- [x] 1. Create JSON data models and result types
  - Files:
    - `app/src/main/java/com/voiceexpense/data/config/ConfigImportModels.kt` (new)
  - Define ConfigImportSchema, ConfigOptionJson, ConfigDefaultsJson data classes
  - Define ConfigImportResult sealed class with Success/Error states
  - Add Moshi @JsonClass annotations for type-safe parsing
  - Purpose: Establish type-safe JSON schema and result handling
  - _Leverage: Existing Moshi setup in AppsScriptClient.kt, ConfigOption.kt structure_
  - _Requirements: Requirement 6 (JSON Schema Definition), Requirement 2 (JSON Format Validation)_
  - _Prompt:
```
Implement the task for spec config-import, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Android Developer specializing in Kotlin data classes and JSON serialization with Moshi

Task: Create ConfigImportModels.kt containing all JSON data models (ConfigImportSchema, ConfigOptionJson, ConfigDefaultsJson) and ConfigImportResult sealed class following Requirements 6 and 2. Add proper Moshi @JsonClass(generateAdapter = true) annotations and @Json name mappings to match the exact JSON schema structure defined in the requirements. The ConfigImportResult sealed class should have Success(optionsImported: Int), InvalidJson(message: String), ValidationError(message: String), DatabaseError(throwable: Throwable), and FileReadError(throwable: Throwable) states.

Context: This is the foundation for type-safe JSON parsing. The JSON schema must support all 5 ConfigTypes (ExpenseCategory, IncomeCategory, TransferCategory, Account, Tag) plus an optional defaults object. Leverage the existing ConfigOption.kt entity structure and Moshi setup patterns from AppsScriptClient.kt.

Restrictions:
- Do not modify existing ConfigOption.kt or ConfigType enum
- Use exact JSON key names from requirements (capitalized ConfigType names)
- All JSON fields must have default values where appropriate (position, active)
- Do not add business logic to data models (pure data classes only)

Success:
- All data classes compile without errors
- Moshi annotations are correctly applied
- JSON schema matches requirements exactly
- ConfigImportResult sealed class covers all error scenarios from requirements
- Code follows Kotlin naming conventions and project structure

Instructions:
1. First, mark this task as in-progress in .spec-workflow/specs/config-import/tasks.md by changing [ ] to [-]
2. Implement the data models as specified
3. When complete, mark this task as completed by changing [-] to [x] in tasks.md
```
  - _

- [x] 2. Create ConfigImporter utility class
  - Files:
    - `app/src/main/java/com/voiceexpense/data/config/ConfigImporter.kt` (new)
  - Implement importConfiguration() method with file reading, JSON parsing, validation
  - Add schema validation logic (non-empty lists, non-blank labels)
  - Add file size limit check (10MB max)
  - Purpose: Orchestrate the import process with error handling
  - _Leverage: ConfigRepository methods, Moshi adapter patterns from AppsScriptClient.kt_
  - _Requirements: Requirement 1 (JSON File Selection), Requirement 2 (JSON Format Validation), Requirement 4 (Configuration Replacement)_
  - _Prompt:
```
Implement the task for spec config-import, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Android Developer with expertise in file I/O, JSON parsing, and coroutines

Task: Create ConfigImporter.kt with dependency injection via Hilt @Inject constructor accepting ConfigRepository and Moshi. Implement the main importConfiguration(fileUri: Uri, contentResolver: ContentResolver): ConfigImportResult suspend function following Requirements 1, 2, and 4. The function should: (1) read file content with 10MB size limit check, (2) parse JSON using Moshi adapter, (3) validate schema (all lists non-empty, all labels non-blank), (4) call ConfigRepository.importConfiguration() on success. Include private helper functions validateSchema() and MAX_FILE_SIZE_BYTES constant.

Context: This is the core business logic layer. It reads the user-selected JSON file, validates it comprehensively, and delegates database operations to ConfigRepository. Leverage Moshi adapter patterns from AppsScriptClient.kt and use Dispatchers.IO for all file/network operations.

Restrictions:
- Must use withContext(Dispatchers.IO) for file operations
- Do not perform database operations directly (delegate to ConfigRepository)
- Catch all exceptions and return appropriate ConfigImportResult error states
- Do not trust user input; validate thoroughly before database operations
- File size check must happen before reading entire file into memory

Success:
- importConfiguration() handles all error scenarios from requirements
- Schema validation catches empty lists and blank labels
- File reading respects 10MB size limit
- All exceptions are caught and converted to ConfigImportResult states
- Code uses proper coroutine patterns with Dispatchers.IO
- Follows Hilt dependency injection patterns

Instructions:
1. First, mark this task as in-progress in .spec-workflow/specs/config-import/tasks.md by changing [ ] to [-]
2. Implement ConfigImporter with all validation logic
3. When complete, mark this task as completed by changing [-] to [x] in tasks.md
```
  - _

- [x] 3. Add ConfigDao batch delete methods
  - Files:
    - `app/src/main/java/com/voiceexpense/data/config/ConfigDao.kt` (modify)
  - Add @Query("DELETE FROM config_options") deleteAllOptions() method
  - Add @Query("DELETE FROM default_values") clearAllDefaults() method
  - Purpose: Enable atomic clearing of all configurations before import
  - _Leverage: Existing Room DAO patterns in ConfigDao.kt_
  - _Requirements: Requirement 4 (Configuration Replacement)_
  - _Prompt:
```
Implement the task for spec config-import, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Android Developer specializing in Room database and SQL

Task: Add two new methods to ConfigDao.kt following Requirement 4: (1) deleteAllOptions(): Int - deletes all rows from config_options table, (2) clearAllDefaults(): Int - deletes all rows from default_values table. Both should be suspend functions with @Query annotations and return Int (number of rows deleted).

Context: These methods enable the "replace all configurations" import behavior. They will be called within a @Transaction method in ConfigRepository to ensure atomicity. Leverage existing Room DAO patterns in ConfigDao.kt.

Restrictions:
- Do not modify existing DAO methods
- Use proper Room @Query annotation syntax
- Both methods must be suspend functions
- Return type must be Int (affected row count)
- Do not add any business logic (pure SQL queries only)

Success:
- Both methods compile and follow Room conventions
- SQL queries are syntactically correct
- Methods are properly annotated with @Query
- Return types match Room requirements (Int)
- Code follows existing DAO patterns in the file

Instructions:
1. First, mark this task as in-progress in .spec-workflow/specs/config-import/tasks.md by changing [ ] to [-]
2. Add the two delete methods to ConfigDao interface
3. When complete, mark this task as completed by changing [-] to [x] in tasks.md
```
  - _

- [x] 4. Add ConfigRepository import transaction method
  - Files:
    - `app/src/main/java/com/voiceexpense/data/config/ConfigRepository.kt` (modify)
  - Add deleteAllOptions() and clearAllDefaults() wrapper methods
  - Add @Transaction suspend fun importConfiguration(schema: ConfigImportSchema)
  - Implement conversion from ConfigOptionJson to ConfigOption entities with UUID generation
  - Handle default value mapping from labels to generated option IDs
  - Purpose: Provide atomic database transaction for import operation
  - _Leverage: Existing ConfigRepository methods (upsertAll, setDefault), UUID generation from Transaction.kt_
  - _Requirements: Requirement 4 (Configuration Replacement), Requirement 6 (JSON Schema Definition)_
  - _Prompt:
```
Implement the task for spec config-import, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Android Developer with expertise in Room transactions and data mapping

Task: Extend ConfigRepository.kt with three new methods following Requirement 4: (1) deleteAllOptions() - calls dao.deleteAllOptions(), (2) clearAllDefaults() - calls dao.clearAllDefaults(), (3) @Transaction suspend fun importConfiguration(schema: ConfigImportSchema) - orchestrates the full import. The transaction method should: delete all options, clear all defaults, convert each ConfigOptionJson to ConfigOption with UUID.randomUUID().toString() for id, call upsertAll(), then map default labels to option IDs and call setDefault() for each. Add a private extension function ConfigOptionJson.toEntity(type: ConfigType, index: Int): ConfigOption for conversion.

Context: This is the atomic database transaction that ensures all-or-nothing import behavior. Room's @Transaction annotation will automatically rollback on any exception. Leverage existing upsertAll() and setDefault() methods. Use UUID generation pattern from Transaction.kt entity.

Restrictions:
- Must use @Transaction annotation for atomicity
- Do not skip the delete operations (must clear before import)
- Use UUID.randomUUID().toString() for generating option IDs
- Handle missing defaults gracefully (skip if label not found)
- Do not perform validation here (validation happens in ConfigImporter)
- Use ConfigOptionJson.position if present, otherwise use index parameter

Success:
- importConfiguration() is atomic (rollback on error)
- All ConfigTypes are converted and inserted correctly
- Default values are mapped from labels to option IDs correctly
- Missing default labels are handled gracefully (no crash)
- UUID generation follows existing project patterns
- Code compiles and follows Room transaction conventions

Instructions:
1. First, mark this task as in-progress in .spec-workflow/specs/config-import/tasks.md by changing [ ] to [-]
2. Add the wrapper methods and transaction method to ConfigRepository
3. When complete, mark this task as completed by changing [-] to [x] in tasks.md
```
  - _

- [x] 5. Add Hilt dependency injection module
  - Files:
    - `app/src/main/java/com/voiceexpense/di/DataModule.kt` (modify, if exists) OR
    - `app/src/main/java/com/voiceexpense/di/ConfigModule.kt` (new)
  - Add @Provides method for ConfigImporter
  - Inject ConfigRepository and Moshi dependencies
  - Purpose: Enable ConfigImporter injection in SettingsActivity
  - _Leverage: Existing Hilt module patterns in di/ directory_
  - _Requirements: Non-functional (Code Architecture)_
  - _Prompt:
```
Implement the task for spec config-import, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Android Developer with expertise in Hilt dependency injection

Task: Locate the existing Hilt module for data layer dependencies (likely in app/src/main/java/com/voiceexpense/di/). If a DataModule or ConfigModule exists, add a @Provides method for ConfigImporter. If not, create a new @Module @InstallIn(SingletonComponent::class) annotated object. The provider method should accept ConfigRepository and Moshi as parameters and return ConfigImporter instance. Use @Singleton scope.

Context: ConfigImporter needs to be injected into SettingsActivity via @Inject lateinit var. Follow existing Hilt patterns in the project's di/ directory. Moshi should already be provided in an existing module (check NetworkModule or AppModule).

Restrictions:
- Do not create duplicate Moshi providers (reuse existing)
- Use @Singleton scope for ConfigImporter
- Follow existing module naming conventions
- Do not modify unrelated provider methods
- Ensure @InstallIn(SingletonComponent::class) is used

Success:
- ConfigImporter can be injected via @Inject lateinit var
- No duplicate Moshi providers are created
- Module follows existing Hilt patterns in the project
- Code compiles and Hilt graph resolves correctly

Instructions:
1. First, mark this task as in-progress in .spec-workflow/specs/config-import/tasks.md by changing [ ] to [-]
2. Add or update the Hilt module for ConfigImporter
3. When complete, mark this task as completed by changing [-] to [x] in tasks.md
```
  - _

- [x] 6. Add string resources for import feature
  - Files:
    - `app/src/main/res/values/strings.xml` (modify)
  - Add button label: import_configuration
  - Add confirmation dialog strings: import_confirm_title, import_confirm_message, replace, cancel
  - Add success/error toast messages: import_success, import_error_invalid_json, import_error_validation, import_error_database, import_error_file_read
  - Purpose: Provide user-facing text for import UI
  - _Leverage: Existing strings.xml patterns_
  - _Requirements: Requirement 3 (Import Confirmation Dialog), Requirement 2 (JSON Format Validation), Requirement 4 (Configuration Replacement)_
  - _Prompt:
```
Implement the task for spec config-import, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Android Developer familiar with Android resources and localization

Task: Add all string resources needed for the config import feature to app/src/main/res/values/strings.xml following Requirements 2, 3, and 4. Required strings: (1) import_configuration = "Import Configuration", (2) import_confirm_title = "Replace all configurations?", (3) import_confirm_message = "This will delete all existing dropdown options and defaults. Continue?", (4) replace = "Replace", (5) cancel = "Cancel", (6) import_success = "Configuration imported successfully (%d options)", (7) import_error_invalid_json = "Invalid JSON format: %s", (8) import_error_validation = "Validation error: %s", (9) import_error_database = "Import failed: %s", (10) import_error_file_read = "Failed to read file: %s". Use proper Android string formatting placeholders where needed.

Context: These strings will be used in SettingsActivity for the import button, confirmation dialog, and toast messages. Follow existing string naming conventions in strings.xml.

Restrictions:
- Do not modify existing strings
- Use %s for string placeholders and %d for integer placeholders
- Follow alphabetical ordering if that's the project convention
- Maintain consistent naming with existing settings strings
- Keep messages concise and user-friendly

Success:
- All required strings are added
- String formatting placeholders are correctly specified
- Strings follow existing naming conventions
- Messages are clear and actionable for users

Instructions:
1. First, mark this task as in-progress in .spec-workflow/specs/config-import/tasks.md by changing [ ] to [-]
2. Add all string resources to strings.xml
3. When complete, mark this task as completed by changing [-] to [x] in tasks.md
```
  - _

- [x] 7. Update activity_settings.xml layout
  - Files:
    - `app/src/main/res/layout/activity_settings.xml` (modify)
  - Add Button with id btn_import_config below btn_set_default
  - Set text to @string/import_configuration
  - Add 16dp top margin for spacing
  - Purpose: Provide UI button for triggering import
  - _Leverage: Existing button patterns in activity_settings.xml_
  - _Requirements: Requirement 1 (JSON File Selection)_
  - _Prompt:
```
Implement the task for spec config-import, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Android UI Developer with expertise in XML layouts and Material Design

Task: Add an "Import Configuration" button to activity_settings.xml following Requirement 1. Place it directly after the btn_set_default button (around line 205) within the existing dropdown configuration section. The button should have: id="@+id/btn_import_config", layout_width="match_parent", layout_height="wrap_content", minHeight="48dp", text="@string/import_configuration", layout_marginTop="16dp" for visual separation.

Context: This button will trigger the file picker when clicked. It should be placed logically within the existing "Dropdown Configuration" section after all the existing config management buttons. Follow existing button styling patterns in the layout.

Restrictions:
- Do not modify existing UI elements
- Maintain consistent button styling with other buttons in the section
- Use proper accessibility attributes (minHeight 48dp minimum)
- Place button in logical location (after Set Default button)
- Do not change existing layout structure or IDs

Success:
- Button is visible in the dropdown configuration section
- Button follows existing Material Design button patterns
- Spacing is consistent with surrounding elements (16dp top margin)
- Layout still compiles and renders correctly
- Button meets accessibility requirements (48dp min touch target)

Instructions:
1. First, mark this task as in-progress in .spec-workflow/specs/config-import/tasks.md by changing [ ] to [-]
2. Add the button to activity_settings.xml
3. When complete, mark this task as completed by changing [-] to [x] in tasks.md
```
  - _

- [x] 8. Implement import logic in SettingsActivity
  - Files:
    - `app/src/main/java/com/voiceexpense/ui/common/SettingsActivity.kt` (modify)
  - Inject ConfigImporter via @Inject lateinit var
  - Add REQUEST_CODE_IMPORT_CONFIG constant
  - Wire up btn_import_config click listener to launch file picker
  - Override onActivityResult to handle file selection
  - Implement showImportConfirmationDialog() method
  - Implement executeImport() method with coroutine and result handling
  - Purpose: Complete the import feature UI integration
  - _Leverage: Existing SettingsActivity patterns, lifecycleScope for coroutines_
  - _Requirements: Requirement 1 (JSON File Selection), Requirement 3 (Import Confirmation Dialog), Requirement 4 (Configuration Replacement), Requirement 5 (UI Refresh After Import)_
  - _Prompt:
```
Implement the task for spec config-import, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Android Developer with expertise in Activities, Intents, and coroutines

Task: Extend SettingsActivity.kt to implement the complete import workflow following Requirements 1, 3, 4, and 5. Add: (1) @Inject lateinit var configImporter: ConfigImporter, (2) companion object constant REQUEST_CODE_IMPORT_CONFIG = 1001, (3) in onCreate(), wire btn_import_config click listener to launch ACTION_OPEN_DOCUMENT intent with type "application/json", (4) override onActivityResult to handle REQUEST_CODE_IMPORT_CONFIG and call showImportConfirmationDialog(uri), (5) implement showImportConfirmationDialog(fileUri: Uri) using AlertDialog.Builder with title/message from strings.xml and positive button calling executeImport(fileUri), (6) implement executeImport(fileUri: Uri) with lifecycleScope.launch(Dispatchers.IO) calling configImporter.importConfiguration() and handling all ConfigImportResult states with appropriate Toast messages on Dispatchers.Main using withContext.

Context: This completes the user-facing import feature. The existing Flow observers on ConfigRepository.options() will automatically refresh the UI after import succeeds, so no manual refresh is needed. Follow existing Activity patterns for file picker intents and coroutine usage.

Restrictions:
- Use ACTION_OPEN_DOCUMENT (not ACTION_GET_CONTENT) for proper file access
- Use lifecycleScope.launch, not GlobalScope
- Switch to Dispatchers.IO for import, back to Dispatchers.Main for UI updates
- Do not duplicate code; reuse existing patterns
- Handle all ConfigImportResult states (Success, InvalidJson, ValidationError, DatabaseError, FileReadError)
- Toast messages must use string resources with formatting parameters

Success:
- File picker launches correctly and filters to JSON files
- Confirmation dialog shows before import with correct strings
- Import executes on background thread (Dispatchers.IO)
- All result states show appropriate toast messages
- UI refreshes automatically after successful import (via existing Flows)
- No memory leaks (lifecycleScope, not GlobalScope)
- Code follows existing Activity patterns

Instructions:
1. First, mark this task as in-progress in .spec-workflow/specs/config-import/tasks.md by changing [ ] to [-]
2. Implement all import logic in SettingsActivity
3. When complete, mark this task as completed by changing [-] to [x] in tasks.md
```
  - _

- [x] 9. Write unit tests for ConfigImporter
  - Files:
    - `app/src/test/java/com/voiceexpense/data/config/ConfigImporterTest.kt` (new)
  - Test valid JSON parsing → Success result
  - Test invalid JSON syntax → InvalidJson result
  - Test empty ConfigType list → ValidationError result
  - Test blank label → ValidationError result
  - Test file size exceeds limit → ValidationError result
  - Test successful import with defaults → Success result
  - Test database error → DatabaseError result
  - Purpose: Ensure ConfigImporter reliability and error handling
  - _Leverage: Existing test patterns in ConfigRepositoryTest.kt, Mockito for mocking_
  - _Requirements: All requirements (comprehensive test coverage)_
  - _Prompt:
```
Implement the task for spec config-import, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: QA Engineer with expertise in JUnit, Mockito, and Kotlin coroutines testing

Task: Create ConfigImporterTest.kt with comprehensive unit tests covering all ConfigImporter scenarios. Use Mockito to mock ConfigRepository and ContentResolver. Tests should include: (1) testValidJsonParsing_Success - valid JSON returns Success, (2) testInvalidJsonSyntax_ReturnsInvalidJson - malformed JSON returns InvalidJson, (3) testEmptyExpenseCategory_ReturnsValidationError - empty list returns ValidationError, (4) testBlankLabel_ReturnsValidationError - blank label returns ValidationError, (5) testFileSizeExceedsLimit_ReturnsValidationError - 11MB file returns ValidationError, (6) testValidSchemaWithDefaults_Success - full valid JSON with defaults returns Success, (7) testDatabaseError_ReturnsDatabaseError - repository throws exception returns DatabaseError. Use real Moshi instance for JSON parsing.

Context: These tests verify that ConfigImporter handles all error scenarios correctly before they reach the user. Leverage existing test patterns from ConfigRepositoryTest.kt. Use runTest {} for coroutine testing.

Restrictions:
- Mock ConfigRepository (do not test Room database here)
- Mock ContentResolver and InputStream for file operations
- Use real Moshi instance (do not mock JSON parsing)
- Each test should be isolated and independent
- Use descriptive test method names with Given-When-Then pattern
- Do not test Android framework behavior (Intent, Activity, etc.)

Success:
- All 7+ test scenarios are covered
- Tests use proper Mockito mocking patterns
- Coroutines are tested correctly with runTest
- Tests verify both success and error scenarios
- Code coverage for ConfigImporter is high
- Tests run quickly and reliably

Instructions:
1. First, mark this task as in-progress in .spec-workflow/specs/config-import/tasks.md by changing [ ] to [-]
2. Write comprehensive unit tests for ConfigImporter
3. When complete, mark this task as completed by changing [-] to [x] in tasks.md
```
  - _

- [x] 10. Write integration tests for Settings import flow
  - Files:
    - `app/src/androidTest/java/com/voiceexpense/ui/common/SettingsConfigImportTest.kt` (new)
  - Test import button launches file picker intent
  - Test confirmation dialog appears after file selection
  - Test successful import updates UI (ListView refresh)
  - Test error handling shows appropriate toast messages
  - Test database transaction rollback on error
  - Purpose: Verify end-to-end import workflow
  - _Leverage: Existing SettingsConfigUiTest.kt patterns, Espresso for UI testing_
  - _Requirements: All requirements (end-to-end validation)_
  - _Prompt:
```
Implement the task for spec config-import, first run spec-workflow-guide to get the workflow guide then implement the task:

Role: Android QA Engineer with expertise in Espresso UI testing and instrumentation tests

Task: Create SettingsConfigImportTest.kt with integration tests covering the full import workflow. Use ActivityScenario<SettingsActivity> and Espresso matchers. Tests should include: (1) testImportButton_LaunchesFilePicker - verify Intent with ACTION_OPEN_DOCUMENT and application/json type is launched, (2) testSuccessfulImport_RefreshesUI - mock successful file selection, verify ListView updates with new options, (3) testInvalidJson_ShowsErrorToast - mock invalid JSON file, verify error toast appears, (4) testConfirmationDialog_ShowsBeforeImport - verify AlertDialog appears with correct title/message, (5) testCancelConfirmation_NoChanges - cancel dialog, verify no database changes. Use Hilt test dependencies for dependency injection and Room in-memory database for testing.

Context: These tests verify the complete user workflow from button click to UI update. Leverage existing UI test patterns from SettingsConfigUiTest.kt. Use IdlingResource or appropriate synchronization for coroutine-based async operations.

Restrictions:
- Use ActivityScenario for Activity lifecycle management
- Mock file picker results using Intents.intending() and hasAction(ACTION_OPEN_DOCUMENT)
- Use in-memory Room database for testing (not production database)
- Synchronize with coroutines properly (use IdlingResource or waitForIdle)
- Do not rely on timing (Thread.sleep); use proper synchronization
- Clean up test data after each test (@After)

Success:
- All integration test scenarios pass reliably
- Tests verify Intent launching, dialog display, UI updates, and error handling
- Database operations are properly isolated (in-memory DB)
- Tests synchronize correctly with async operations
- No flaky tests due to timing issues
- Tests follow existing Espresso patterns in the project

Instructions:
1. First, mark this task as in-progress in .spec-workflow/specs/config-import/tasks.md by changing [ ] to [-]
2. Write integration tests for the import workflow
3. When complete, mark this task as completed by changing [-] to [x] in tasks.md
```
  - _

## Task Summary

**Total Tasks**: 10

**Task Dependencies**:
- Task 1 (Data Models) → Required by Task 2 (ConfigImporter)
- Task 2 (ConfigImporter) → Requires Task 4 (ConfigRepository)
- Task 3 (ConfigDao) → Required by Task 4 (ConfigRepository)
- Task 4 (ConfigRepository) → Required by Task 2 (ConfigImporter)
- Task 5 (Hilt Module) → Required by Task 8 (SettingsActivity)
- Task 6 (Strings) → Required by Task 8 (SettingsActivity)
- Task 7 (Layout) → Required by Task 8 (SettingsActivity)
- Task 8 (SettingsActivity) → Requires Tasks 2, 5, 6, 7
- Task 9 (Unit Tests) → Requires Task 2 (ConfigImporter)
- Task 10 (Integration Tests) → Requires Task 8 (SettingsActivity)

**Recommended Order**:
1. Task 1 (Data Models) - Foundation
2. Task 3 (ConfigDao) - Database layer
3. Task 4 (ConfigRepository) - Data access layer
4. Task 2 (ConfigImporter) - Business logic
5. Task 5 (Hilt Module) - Dependency injection
6. Task 6 (Strings) - Resources
7. Task 7 (Layout) - UI structure
8. Task 8 (SettingsActivity) - UI logic
9. Task 9 (Unit Tests) - Validation
10. Task 10 (Integration Tests) - End-to-end validation

## Implementation Notes

- All tasks follow atomic implementation principles (1-3 files each)
- Each task includes detailed prompts with role, context, restrictions, and success criteria
- Tasks leverage existing codebase patterns and utilities
- Comprehensive test coverage is included (unit + integration)
- Dependencies are clearly documented for proper sequencing
