# Project Structure

## Directory Organization

```
voice-expense-tracker/
├── app/                          # Android application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/voiceexpense/
│   │   │   │   ├── ui/           # User interface components
│   │   │   │   │   ├── confirmation/ # Transaction confirmation form activity
│   │   │   │   │   ├── common/   # MainActivity, adapters, ViewModels
│   │   │   │   │   ├── setup/    # Initial app setup screens
│   │   │   │   │   └── settings/ # Configuration management UI
│   │   │   │   ├── service/      # Background services
│   │   │   │   │   └── sync/     # Background sync services
│   │   │   │   ├── data/         # Data layer
│   │   │   │   │   ├── local/    # Room database, DAOs
│   │   │   │   │   ├── remote/   # Google Apps Script Web App integration
│   │   │   │   │   ├── repository/ # Repository pattern implementations
│   │   │   │   │   └── model/    # Data models and entities
│   │   │   │   ├── ai/           # On-device AI processing
│   │   │   │   │   ├── parsing/  # Gemma 3 structured parsing
│   │   │   │   │   │   └── hybrid/ # Hybrid AI + heuristic processing
│   │   │   │   │   ├── model/    # MediaPipe Tasks model management
│   │   │   │   │   ├── mediapipe/ # MediaPipe GenAI client
│   │   │   │   │   ├── performance/ # AI performance optimization
│   │   │   │   │   └── error/    # AI error handling
│   │   │   │   ├── auth/         # Google OAuth implementation
│   │   │   │   ├── worker/       # WorkManager background tasks
│   │   │   │   ├── di/           # Hilt dependency injection modules
│   │   │   │   └── util/         # Utility classes and extensions
│   │   │   ├── res/              # Android resources
│   │   │   │   ├── layout/       # XML layouts (form interface, lists)
│   │   │   │   ├── values/       # Strings, colors, dimensions
│   │   │   │   ├── drawable/     # Icons and graphics
│   │   │   │   └── xml/          # App widget configurations
│   │   │   └── AndroidManifest.xml
│   │   ├── test/                 # Unit tests
│   │   │   └── java/com/voiceexpense/
│   │   │       ├── ai/           # AI parsing tests with fixtures
│   │   │       │   ├── parsing/  # Transaction parser tests
│   │   │       │   └── hybrid/   # Hybrid processing tests
│   │   │       ├── data/         # Repository and model tests
│   │   │       ├── ui/           # UI component tests
│   │   │       └── util/         # Utility testing
│   │   └── androidTest/          # Instrumentation tests
│   │       └── java/com/voiceexpense/
│   │           ├── ui/           # UI and widget integration tests
│   │           ├── service/      # Service lifecycle tests
│   │           ├── data/         # Database integration tests
│   │           └── ai/           # AI integration tests
│   ├── build.gradle.kts          # App module build configuration
│   └── proguard-rules.pro        # Code obfuscation rules
├── gradle/                       # Gradle wrapper and configurations
├── scripts/                      # Build and utility scripts
│   └── build_apk.py             # APK build automation script
├── docs/                        # Project documentation
│   ├── voice-correction-loop-v1.md # Voice interaction documentation
│   └── hybrid-ml-kit-integration.md # AI processing documentation
├── .spec-workflow/              # Specification workflow files
│   └── steering/                # Steering documents (product, tech, structure)
├── build.gradle.kts             # Project-level build configuration
├── gradle.properties            # Gradle configuration properties
├── settings.gradle.kts          # Project settings
├── .gitignore                   # Git ignore patterns
└── README.md                    # Project overview and setup instructions
```

## Naming Conventions

### Files
- **Activities/Fragments**: `PascalCase` (e.g., `TransactionConfirmationActivity`, `WidgetConfigFragment`)
- **Services**: `PascalCase` with suffix (e.g., `SyncService`)
- **ViewModels**: `PascalCase` with suffix (e.g., `TransactionViewModel`, `ConfirmationViewModel`)
- **Repositories**: `PascalCase` with suffix (e.g., `TransactionRepository`, `AuthRepository`)
- **Data Models**: `PascalCase` (e.g., `Transaction`, `ParsedExpense`, `SyncStatus`)
- **Workers**: `PascalCase` with suffix (e.g., `SyncWorker`, `RetryWorker`)
- **Utilities**: `PascalCase` with suffix (e.g., `DateUtils`, `CurrencyFormatter`)
- **Tests**: `PascalCase` with suffix (e.g., `TransactionRepositoryTest`, `ParsingServiceTest`)
- **Form Components**: `PascalCase` with suffix (e.g., `CategoryDropdown`, `DatePickerHelper`)

### Code
- **Classes/Data Classes**: `PascalCase` (e.g., `Transaction`, `AppsScriptClient`)
- **Functions/Methods**: `camelCase` (e.g., `parseTransaction`, `saveToDatabase`, `authenticateUser`)
- **Constants**: `UPPER_SNAKE_CASE` (e.g., `MAX_RETRIES`, `DEFAULT_TIMEOUT_MS`, `SHEETS_SCOPE`)
- **Variables/Properties**: `camelCase` (e.g., `transactionAmount`, `userAccount`, `isOffline`)
- **Package Names**: `lowercase` with dots (e.g., `com.voiceexpense.data.local`, `com.voiceexpense.ai.parsing`)

## Import Patterns

### Import Order
1. Android framework imports
2. External library imports (MediaPipe Tasks, Google APIs, Room, WorkManager)
3. Internal module imports (same package)
4. Internal cross-package imports
5. Static imports

### Module Organization
```kotlin
// Example import structure
import android.content.Context
import androidx.work.WorkManager
import androidx.room.Database
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.voiceexpense.data.model.Transaction
import com.voiceexpense.ai.parsing.TransactionParser
import java.util.UUID
```

- Absolute imports from package root (`com.voiceexpense.`)
- No relative imports within modules
- Dependency injection for cross-module communication
- Clear API boundaries between layers

## Code Structure Patterns

### Activity/Fragment Organization
```kotlin
class TransactionConfirmationActivity : AppCompatActivity() {
    // 1. Companion object and constants
    companion object {
        private const val EXTRA_TRANSACTION_ID = "transaction_id"
    }
    
    // 2. Properties and dependencies
    private lateinit var viewModel: TransactionViewModel
    private lateinit var binding: ActivityTransactionConfirmationBinding
    
    // 3. Lifecycle methods
    override fun onCreate(savedInstanceState: Bundle?) { }
    override fun onResume() { }
    
    // 4. UI setup and event handlers
    private fun setupViews() { }
    private fun setupFormValidation() { }
    
    // 5. Helper methods
    private fun updateUI(transaction: Transaction) { }
    private fun showError(message: String) { }
    private fun validateForm(): Boolean { }
}
```

### Repository/Service Organization
```kotlin
class TransactionRepository @Inject constructor(
    private val localDataSource: TransactionDao,
    private val remoteDataSource: AppsScriptClient
) {
    // 1. Public API methods
    suspend fun saveTransaction(transaction: Transaction): Result<Unit>
    suspend fun getTransactions(): Flow<List<Transaction>>
    
    // 2. Sync operations
    suspend fun syncPendingTransactions(): SyncResult
    
    // 3. Private helper methods
    private suspend fun uploadViaAppsScript(transaction: Transaction): Boolean
    private fun mapToAppsRequest(transaction: Transaction): AppsScriptRequest
}
```

### Form Component Organization
```kotlin
class TransactionFormManager(
    private val context: Context,
    private val configRepository: ConfigRepository
) {
    // 1. Form field management
    fun setupFormFields(binding: ActivityTransactionConfirmationBinding)
    fun populateDropdowns()
    fun validateFields(): ValidationResult
    
    // 2. Data binding
    fun bindTransactionToForm(transaction: Transaction)
    fun extractFormData(): Transaction
    
    // 3. Configuration management
    private fun loadDropdownOptions(fieldType: FieldType): List<String>
    private fun saveDropdownChanges(fieldType: FieldType, options: List<String>)
}
```

### Data Class Organization
```kotlin
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val createdAt: Instant,
    val userLocalDate: LocalDate,
    val amountUsd: BigDecimal?,
    val merchant: String,
    val description: String?,
    val type: TransactionType,
    val expenseCategory: String?,
    val incomeCategory: String?,
    val tags: List<String> = emptyList(),
    val account: String?,
    val splitOverallChargedUsd: BigDecimal?,
    val note: String?,
    val confidence: Float,
    val correctionsCount: Int = 0,
    val source: String = "text", // "text" only
    val status: TransactionStatus = TransactionStatus.DRAFT,
    val sheetRef: SheetReference? = null
)
```

## Code Organization Principles

1. **Single Responsibility**: Each file handles one specific concern (text processing, form management, database operations, UI state)
2. **Layer Separation**: Clear boundaries between UI, business logic, and data layers
3. **Dependency Injection**: Use Hilt for dependency management across the application
4. **Reactive Patterns**: Flow/LiveData for data streams, StateFlow for UI state
5. **Error Handling**: Consistent Result/Either pattern for operation outcomes
6. **Testing**: Each component is testable in isolation with clear mocking points
7. **Configuration Management**: Centralized storage and access for user-configurable options

## Module Boundaries

### Core Application Layers
- **UI Layer**: Activities, Fragments, ViewModels, Widgets, Form Components - handles user interactions
- **Domain Layer**: Use cases, business logic - processes voice input, text input, and transaction rules  
- **Data Layer**: Repositories, data sources, models - manages local and remote data

### Feature Boundaries
- **Input Capture**: MainActivity (text) → AI Processing pipeline
- **Transaction Management**: Repository pattern with Room + Apps Script integration
- **Form Interface**: Comprehensive editing with validation, dropdowns, date pickers
- **Configuration Management**: Settings UI for managing dropdown options
- **Authentication**: OAuth flow isolated with secure token storage
- **Background Sync**: WorkManager tasks independent from main app lifecycle

### Dependencies Direction
```
UI Layer → Domain Layer → Data Layer
Widget/MainActivity → Service/Parser → Repository → DataSource
Form Components → Validation → Configuration Storage
```
- No circular dependencies
- Inner layers don't know about outer layers
- Dependency inversion through interfaces

## Code Size Guidelines

### File Size Limits
- **Activity/Fragment**: Maximum 300 lines (prefer ViewModels for logic, extract form management)
- **ViewModel/Repository**: Maximum 400 lines (split by responsibility)
- **Service/Worker**: Maximum 200 lines (focused single-purpose classes)
- **Form Components**: Maximum 250 lines (separate validation and data binding)
- **Data Classes**: Maximum 100 lines (use composition for complex models)
- **Utility Classes**: Maximum 150 lines (group related functions)

### Function/Method Size
- **Public methods**: Maximum 30 lines (prefer composition)
- **Private helpers**: Maximum 15 lines (single responsibility)
- **Form handlers**: Maximum 20 lines (extract validation logic)
- **Complex operations**: Break into smaller functions with descriptive names

### Class Complexity
- **Maximum cyclomatic complexity**: 10 per method
- **Maximum nesting depth**: 3 levels (early returns, guard clauses)
- **Constructor parameters**: Maximum 5 (use data classes or builders)
- **Form field count**: Group related fields into sections

## AI Processing Structure

### Text Processing Pipeline
```
app/src/main/java/com/voiceexpense/ai/
├── speech/
│   ├── SpeechRecognitionService.kt    # ML Kit ASR integration
│   ├── AudioRecordingManager.kt       # Audio capture and processing
│   └── TranscriptionListener.kt       # ASR result handling
├── parsing/
│   ├── TransactionParser.kt           # Main parsing orchestrator
│   ├── ParsingPrompts.kt              # System instructions and templates
│   ├── TransactionPrompts.kt          # Structured prompt templates
│   ├── ParsedResult.kt                # Parsing result models
│   ├── ParsingContext.kt              # Context for parsing (recent data)
│   ├── StructuredOutputValidator.kt   # JSON schema validation
│   └── hybrid/                        # Hybrid processing strategy
│       ├── HybridTransactionParser.kt # AI + heuristic orchestrator
│       ├── GenAiGateway.kt            # MediaPipe abstraction
│       ├── PromptBuilder.kt           # Intelligent prompt composition
│       ├── FewShotExampleRepository.kt # Example management
│       ├── ValidationPipeline.kt      # Output validation
│       ├── ConfidenceScorer.kt        # Parsing quality assessment
│       ├── ProcessingModels.kt        # Data models for processing
│       ├── ProcessingMonitor.kt       # Performance tracking
│       └── SchemaTemplates.kt         # JSON schema definitions
├── mediapipe/
│   └── MediaPipeGenAiClient.kt        # MediaPipe Tasks integration
├── model/
│   ├── ModelManager.kt                # AI model lifecycle management
│   └── OnDeviceConfig.kt              # Model configuration
├── performance/
│   └── AiPerformanceOptimizer.kt      # Performance monitoring
└── error/
    └── AiErrorHandler.kt              # AI error handling and fallbacks
```

### Form Interface Structure
```
app/src/main/java/com/voiceexpense/ui/
├── confirmation/
│   ├── TransactionConfirmationActivity.kt  # Main form interface
│   ├── ConfirmationViewModel.kt            # Form state management
│   ├── FormValidationManager.kt            # Field validation logic
│   ├── DropdownConfigManager.kt            # Dropdown option management
│   └── voice/                              # Voice correction components
│       ├── VoiceCorrectionController.kt    # Voice interaction handler
│       ├── TtsEngine.kt                    # Text-to-speech
│       ├── CorrectionIntentParser.kt       # Voice command parsing
│       └── PromptRenderer.kt               # Voice prompt generation
├── common/
│   ├── MainActivity.kt                     # Home screen with history
│   ├── MainViewModel.kt                    # Recent transactions
│   ├── RecentTransactionsAdapter.kt        # Transaction list
│   └── TransactionDetailsActivity.kt       # Transaction view/edit
└── settings/
    ├── SettingsActivity.kt                 # Configuration management
    ├── DropdownEditorActivity.kt           # Edit dropdown options
    └── ModelSetupActivity.kt               # AI model configuration
```

### Separation of Concerns
- Speech recognition isolated from parsing logic
- Form management separate from AI processing
- Dropdown configuration independent of transaction logic
- Model management handles loading/unloading efficiently
- Clear error boundaries between ASR failures, parsing failures, and form validation
- Validation separated from data binding and UI updates

## Documentation Standards

- All public APIs documented with KDoc
- Complex business logic includes inline comments explaining "why" not "what"
- Form field validation rules documented in code comments
- README files for major feature modules (ai/, data/, ui/)
- Architecture Decision Records (ADRs) for significant technical choices
- API contract documentation for structured parsing input/output
- Testing documentation with fixture examples for AI parsing scenarios
- Configuration management documentation for dropdown setup

## Testing Structure

### Test Organization
```
app/src/test/java/com/voiceexpense/
├── ai/
│   ├── parsing/
│   │   ├── TransactionParserTest.kt           # Unit tests with fixtures
│   │   ├── TransactionPromptsTest.kt          # Prompt template tests
│   │   └── hybrid/
│   │       ├── HybridTransactionParserTest.kt # Hybrid processing tests
│   │       ├── PromptBuilderTest.kt           # Prompt composition tests
│   │       ├── ValidationPipelineTest.kt      # Validation tests
│   │       └── FewShotExampleRepositoryTest.kt # Example selection tests
│   │   └── fixtures/
│   │       ├── ValidUtterances.kt             # Sample inputs/outputs
│   │       └── EdgeCaseInputs.kt              # Ambiguous/invalid cases
│   └── speech/
│       └── SpeechRecognitionServiceTest.kt    # Mock ASR integration
├── ui/
│   ├── confirmation/
│   │   ├── ConfirmationViewModelTest.kt       # Form state tests
│   │   └── FormValidationManagerTest.kt       # Validation logic tests
│   └── common/
│       └── MainViewModelTest.kt               # Recent transactions tests
├── data/
│   ├── repository/
│   │   └── TransactionRepositoryTest.kt       # Repository behavior tests
│   └── local/
│       └── TransactionDaoTest.kt              # Database operation tests
└── worker/
    └── SyncWorkerTest.kt                      # Background sync tests
```

### Testing Principles
- Use fixture data for consistent AI parsing tests
- Mock external dependencies (Apps Script client, MediaPipe Tasks)
- Integration tests for critical paths (widget → service → confirmation, text input → parsing → form)
- Form validation tests with edge cases
- Performance tests for parsing latency requirements (<3s)
- Error scenario testing for offline/auth failures
- Configuration management tests for dropdown persistence

## Configuration Management Structure

### Settings Storage
```
app/src/main/java/com/voiceexpense/data/config/
├── ConfigRepository.kt                 # Configuration data access
├── DropdownOptionsDao.kt               # Database storage for options
├── UserPreferencesManager.kt           # SharedPreferences wrapper
└── DefaultOptionsProvider.kt           # Default dropdown values
```

### Configuration Models
```kotlin
data class DropdownConfig(
    val fieldType: FieldType,
    val options: List<String>,
    val defaultOption: String?
)

enum class FieldType {
    EXPENSE_CATEGORY,
    INCOME_CATEGORY,
    TAGS,
    ACCOUNTS,
    TRANSFER_CATEGORY,
    TRANSFER_DESTINATION
}
```

**Note**: Updated structure reflects the text-only input approach, comprehensive form interface, and MediaPipe Tasks integration with Gemma 3 1B model.