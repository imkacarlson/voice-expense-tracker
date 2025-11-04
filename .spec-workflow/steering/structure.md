# Project Structure

## Directory Organization

```
voice-expense-tracker/
├── app/                          # Android app module (UI, data, DI, WorkManager)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/voiceexpense/
│   │   │   │   ├── ui/                # Screens, adapters, ViewModels
│   │   │   │   │   ├── confirmation/  # Transaction confirmation workflow
│   │   │   │   │   ├── common/        # MainActivity, shared components
│   │   │   │   │   ├── setup/         # First-run and model setup flows
│   │   │   │   │   └── settings/      # Config management UI
│   │   │   │   ├── data/              # Room models, repositories, remote clients
│   │   │   │   ├── auth/              # Google sign-in & token cache
│   │   │   │   ├── worker/            # WorkManager jobs (sync, diagnostics)
│   │   │   │   ├── di/                # Hilt modules (parsing, repositories, workers)
│   │   │   │   └── util/              # Shared helpers/extensions
│   │   │   ├── res/                   # Android resources (layouts, themes, drawables)
│   │   │   └── AndroidManifest.xml
│   │   ├── test/                      # JVM unit tests
│   │   └── androidTest/               # Instrumented tests
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── parsing/                      # Shared Kotlin parsing library (staged hybrid pipeline)
│   ├── src/main/kotlin/com/voiceexpense/ai/parsing/
│   │   ├── TransactionParser.kt
│   │   ├── ParsedResult.kt
│   │   ├── ParsingContext.kt
│   │   ├── logging/              # ParsingRunLog, Log abstraction
│   │   ├── heuristic/            # HeuristicExtractor, confidence thresholds
│   │   └── hybrid/               # Staged orchestrator, dispatcher, stats
│   └── build.gradle.kts
├── cli/                          # JVM CLI wrapper around shared parser
│   ├── src/main/kotlin/com/voiceexpense/eval/
│   │   ├── CliMain.kt
│   │   ├── PythonGenAiGateway.kt
│   │   ├── JsonModels.kt
│   │   └── ConsoleLogger.kt
│   └── build.gradle.kts
├── evaluator/                    # Python evaluator harness (HuggingFace-based)
│   ├── evaluate.py
│   ├── models.py
│   ├── test_cases.md
│   ├── requirements.txt
│   └── results/
├── backend/                      # Google Apps Script backend
│   └── appscript/expense-tracker-api.gs
├── prompts/                      # Prompt experiments and utterance fixtures
├── scripts/
│   └── build_apk.py
├── docs/                         # Project documentation
│   ├── voice-correction-loop-v1.md
│   └── hybrid-ml-kit-integration.md
├── .spec-workflow/               # Steering workflow assets
│   └── steering/
│       ├── product.md
│       ├── tech.md
│       └── structure.md
├── build.gradle.kts              # Root Gradle configuration
├── settings.gradle.kts           # Module inclusion
├── gradle.properties             # Project-wide properties
├── README.md
└── gradle/                       # Gradle wrapper and scripts
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
@TypeConverters(Converters::class)
data class Transaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val createdAt: Instant = Instant.now(),
    val userLocalDate: LocalDate,
    val amountUsd: BigDecimal?,
    val merchant: String,
    val description: String?,
    val type: TransactionType,
    val expenseCategory: String?,
    val incomeCategory: String?,
    val transferCategory: String? = null,
    val transferDestination: String? = null,
    val tags: List<String> = emptyList(),
    val account: String?,
    val splitOverallChargedUsd: BigDecimal?,
    val confidence: Float,
    val correctionsCount: Int = 0,
    val source: String = "voice",
    val status: TransactionStatus = TransactionStatus.DRAFT,
    val sheetRef: SheetReference? = null
)
```

`transferCategory` and `transferDestination` remain null unless the confirmation form captures values manually; they reserve space for richer transfer workflows.

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
- **Domain Layer**: Use cases, business logic - processes text input via the staged parsing pipeline and enforces transaction rules  
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
- Shared `:parsing` module sits below the domain layer; both Android UI and CLI depend on it via interfaces (`GenAiGateway`, `Log`).

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

### Shared Parsing Library (`:parsing`)
```
parsing/src/main/kotlin/com/voiceexpense/ai/parsing/
├── TransactionParser.kt            # Entry point that coordinates staged parsing
├── ParsedResult.kt                 # Parsed payload + ParsingContext definition
├── ParsingPrompts.kt               # Shared prompt templates + instructions
├── StructuredOutputValidator.kt    # JSON schema validation helpers
├── TagNormalizer.kt                # Tag casing + delimiter normalization
├── heuristic/                      # Stage 1 heuristics and thresholds
│   ├── HeuristicExtractor.kt
│   ├── HeuristicDraft.kt
│   └── HeuristicMappers.kt
├── hybrid/                         # Stage 2 orchestrator + metrics
│   ├── HybridTransactionParser.kt
│   ├── StagedParsingOrchestrator.kt
│   ├── StagedRefinementDispatcher.kt
│   ├── FocusedPromptBuilder.kt
│   ├── FieldSelectionStrategy.kt
│   ├── ConfidenceScorer.kt
│   ├── ProcessingMonitor.kt
│   └── ValidationPipeline.kt
└── logging/                        # Run log + logger abstraction
    ├── ParsingRunLog.kt
    └── Logger.kt
```

### Android Integration Layer
```
app/src/main/java/com/voiceexpense/ai/
├── mediapipe/MediaPipeGenAiClient.kt   # MediaPipe Tasks client, backend toggle, prewarm
├── model/ModelManager.kt               # Model import, validation, storage helpers
├── performance/AiPerformanceOptimizer.kt # Timing + warm-up instrumentation
└── error/AiErrorHandler.kt             # Parser + model error categorisation
```

- `TransactionParser` is injected into UI layers via Hilt; it depends on `HybridTransactionParser` and listens for staged refinements through `StagedRefinementDispatcher`.
- `MediaPipeGenAiClient` selects GPU/CPU backends, prewarms the interpreter, and streams prompts to the parsing module via `GenAiGateway`.
- Parsing diagnostics accumulate in `ParsingRunLogStore` so the UI and evaluator share identical Markdown exports.
- Evaluator CLI reuses the same module; only the `GenAiGateway` implementation differs (Python-driven instead of MediaPipe).

### Form Interface Structure
```
app/src/main/java/com/voiceexpense/ui/
├── confirmation/
│   ├── TransactionConfirmationActivity.kt  # Form UI, staged refinement indicators, diagnostics export
│   ├── ConfirmationViewModel.kt            # Draft state + dispatcher subscription
│   └── ValidationEngine.kt                 # Field validation + manual edit reconciliation
├── common/
│   ├── MainActivity.kt                     # Home screen with staged draft creation
│   ├── MainViewModel.kt                    # Recent transactions stream (top 10)
│   ├── RecentTransactionsAdapter.kt        # Transaction list binder
│   └── TransactionDetailsActivity.kt       # Read-only detail and diagnostics view
└── settings/
    ├── SettingsActivity.kt                 # Configuration management
    ├── DropdownEditorActivity.kt           # Manage dropdown options
    └── ModelSetupActivity.kt               # AI model import/test + backend toggle
```

### Separation of Concerns
- Shared `:parsing` module owns heuristics, staged refinement, and logging; Android only provides a `GenAiGateway` implementation.
- UI layer observes `StagedRefinementDispatcher` events and never touches MediaPipe directly.
- Settings/configuration screens manage dropdown options and AI model lifecycle independently of transaction storage.
- MediaPipe client (`MediaPipeGenAiClient`) encapsulates backend selection, model validation, and error conversion into user-facing messages.
- Diagnostics export leverages `ParsingRunLogStore` so the same instrumentation powers the evaluator and confirmation screen.
- Validation (`ValidationEngine`) stays isolated from data access and simply emits sanitized drafts back to the ViewModel.

## Evaluation Toolkit
- `cli/src/main/kotlin/com/voiceexpense/eval/` wraps the shared parser in `CliMain.kt`, serialising stdin/stdout JSON for Stage 1 (needs_ai) and Stage 2 (complete) flows.
- `PythonGenAiGateway` records prompts during the first pass and replays HuggingFace model responses injected by Python, keeping CLI output deterministic.
- `evaluator/evaluate.py` orchestrates CLI invocations, batches prompts through Gemma models, and writes Markdown summaries/detailed reports under `evaluator/results/`.
- `evaluator/test_cases.md` stores utterances + expected fields; blank cells mean "not asserted" so suites stay flexible as prompts evolve.
- Use the toolkit before shipping prompt or heuristic edits: `./gradlew :cli:build` then `python evaluator/evaluate.py --model google/gemma-3-1b-it --test smoke`.

## Apps Script Backend
- Located at `backend/appscript/expense-tracker-api.gs`; deployed as a Google Apps Script Web App behind `doPost`.
- Validates OAuth tokens via `tokeninfo`, enforces a 30-requests/10-minute cache-based rate limit, and hashes emails before logging.
- Appends expenses using the user's column layout, formatting timestamps with the spreadsheet timezone.
- Maintains a rolling audit log sheet (capped at 1,000 entries) and gracefully handles configuration gaps or auth failures with structured JSON responses.

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
│   ├── error/AiErrorHandlerTest.kt
│   ├── model/ModelManagerTest.kt
│   └── parsing/TransactionParserTest.kt
├── auth/
│   ├── AuthRepositoryTest.kt
│   └── TokenProviderTest.kt
├── data/
│   ├── config/
│   │   ├── ConfigImporterTest.kt
│   │   └── ConfigRepositoryTest.kt
│   ├── local/TransactionDaoTest.kt
│   ├── remote/AppsScriptClientTest.kt
│   └── repository/
│       ├── TransactionRepositoryTest.kt
│       └── TransactionRepositoryAuthTest.kt
├── testutil/
│   ├── MainDispatcherRule.kt
│   └── TestDispatchers.kt
├── ui/
│   ├── common/SettingsActivityAuthTest.kt
│   └── confirmation/
│       ├── ConfirmationViewModelTest.kt
│       ├── ValidationEngineTest.kt
│       └── ConfirmationActivityNavigationTest.kt
└── worker/
    ├── SyncWorkerTest.kt
    └── SyncWorkerAuthTest.kt
```

### Testing Principles
- Use fixture data for consistent AI parsing tests
- Mock external dependencies (Apps Script client, MediaPipe Tasks)
- Integration tests for critical paths (widget → service → confirmation, text input → parsing → form)
- Form validation tests with edge cases
- Performance tests for parsing latency requirements (<3s)
- Error scenario testing for offline/auth failures
- Configuration management tests for dropdown persistence
- Offline evaluator harness runs (`./gradlew :cli:build` + `python evaluator/evaluate.py`) guard prompt changes with field-level accuracy metrics.

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
