# Project Structure

## Directory Organization

```
voice-expense-tracker/
├── app/                          # Android application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/voiceexpense/
│   │   │   │   ├── ui/           # User interface components
│   │   │   │   │   ├── widget/   # Home screen widget implementation
│   │   │   │   │   ├── confirmation/ # Transaction confirmation activity
│   │   │   │   │   └── common/   # Shared UI components
│   │   │   │   ├── service/      # Foreground services
│   │   │   │   │   ├── voice/    # Voice recording and processing
│   │   │   │   │   └── sync/     # Background sync services
│   │   │   │   ├── data/         # Data layer
│   │   │   │   │   ├── local/    # Room database, DAOs
│   │   │   │   │   ├── remote/   # Google Apps Script Web App integration
│   │   │   │   │   ├── repository/ # Repository pattern implementations
│   │   │   │   │   └── model/    # Data models and entities
│   │   │   │   ├── ai/           # On-device AI processing
│   │   │   │   │   ├── speech/   # ML Kit Speech Recognition
│   │   │   │   │   ├── parsing/  # Gemini Nano structured parsing
│   │   │   │   │   └── model/    # AI model management
│   │   │   │   ├── auth/         # Google OAuth implementation
│   │   │   │   ├── worker/       # WorkManager background tasks
│   │   │   │   └── util/         # Utility classes and extensions
│   │   │   ├── res/              # Android resources
│   │   │   │   ├── layout/       # XML layouts
│   │   │   │   ├── values/       # Strings, colors, dimensions
│   │   │   │   ├── drawable/     # Icons and graphics
│   │   │   │   └── xml/          # App widget configurations
│   │   │   └── AndroidManifest.xml
│   │   ├── test/                 # Unit tests
│   │   │   └── java/com/voiceexpense/
│   │   │       ├── ai/           # AI parsing tests with fixtures
│   │   │       ├── data/         # Repository and model tests
│   │   │       └── util/         # Utility testing
│   │   └── androidTest/          # Instrumentation tests
│   │       └── java/com/voiceexpense/
│   │           ├── ui/           # UI and widget integration tests
│   │           ├── service/      # Service lifecycle tests
│   │           └── data/         # Database integration tests
│   ├── build.gradle.kts          # App module build configuration
│   └── proguard-rules.pro        # Code obfuscation rules
├── gradle/                       # Gradle wrapper and configurations
├── scripts/                      # Build and utility scripts
│   └── build_apk.py             # APK build automation script
├── docs/                        # Project documentation
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
- **Services**: `PascalCase` with suffix (e.g., `VoiceRecordingService`, `SyncService`)
- **ViewModels**: `PascalCase` with suffix (e.g., `TransactionViewModel`, `ConfirmationViewModel`)
- **Repositories**: `PascalCase` with suffix (e.g., `TransactionRepository`, `AuthRepository`)
- **Data Models**: `PascalCase` (e.g., `Transaction`, `ParsedExpense`, `SyncStatus`)
- **Workers**: `PascalCase` with suffix (e.g., `SyncWorker`, `RetryWorker`)
- **Utilities**: `PascalCase` with suffix (e.g., `DateUtils`, `CurrencyFormatter`, `AudioHelper`)
- **Tests**: `PascalCase` with suffix (e.g., `TransactionRepositoryTest`, `ParsingServiceTest`)

### Code
- **Classes/Data Classes**: `PascalCase` (e.g., `Transaction`, `VoiceProcessor`, `AppsScriptClient`)
- **Functions/Methods**: `camelCase` (e.g., `parseTransaction`, `saveToDatabase`, `authenticateUser`)
- **Constants**: `UPPER_SNAKE_CASE` (e.g., `MAX_RETRIES`, `DEFAULT_TIMEOUT_MS`, `SHEETS_SCOPE`)
- **Variables/Properties**: `camelCase` (e.g., `transactionAmount`, `userAccount`, `isOffline`)
- **Package Names**: `lowercase` with dots (e.g., `com.voiceexpense.data.local`, `com.voiceexpense.ai.parsing`)

## Import Patterns

### Import Order
1. Android framework imports
2. External library imports (ML Kit, Google APIs, Room, WorkManager)
3. Internal module imports (same package)
4. Internal cross-package imports
5. Static imports

### Module Organization
```kotlin
// Example import structure
import android.content.Context
import android.speech.SpeechRecognizer
import androidx.work.WorkManager
import androidx.room.Database
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.mlkit.nl.languageid.LanguageIdentification
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
    private fun handleVoiceInput() { }
    
    // 5. Helper methods
    private fun updateUI(transaction: Transaction) { }
    private fun showError(message: String) { }
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
    val source: String = "voice",
    val status: TransactionStatus = TransactionStatus.DRAFT,
    val sheetRef: SheetReference? = null
)
```

## Code Organization Principles

1. **Single Responsibility**: Each file handles one specific concern (voice processing, database operations, UI state)
2. **Layer Separation**: Clear boundaries between UI, business logic, and data layers
3. **Dependency Injection**: Use Hilt for dependency management across the application
4. **Reactive Patterns**: Flow/LiveData for data streams, StateFlow for UI state
5. **Error Handling**: Consistent Result/Either pattern for operation outcomes
6. **Testing**: Each component is testable in isolation with clear mocking points

## Module Boundaries

### Core Application Layers
- **UI Layer**: Activities, Fragments, ViewModels, Widgets - handles user interactions
- **Domain Layer**: Use cases, business logic - processes voice input and transaction rules  
- **Data Layer**: Repositories, data sources, models - manages local and remote data

### Feature Boundaries
- **Voice Capture**: Widget → Service → AI Processing pipeline (isolated from UI)
- **Transaction Management**: Repository pattern with Room + Apps Script integration
- **Authentication**: OAuth flow isolated with secure token storage
- **Background Sync**: WorkManager tasks independent from main app lifecycle

### Dependencies Direction
```
UI Layer → Domain Layer → Data Layer
Widget → Service → Repository → DataSource
```
- No circular dependencies
- Inner layers don't know about outer layers
- Dependency inversion through interfaces

## Code Size Guidelines

### File Size Limits
- **Activity/Fragment**: Maximum 300 lines (prefer ViewModels for logic)
- **ViewModel/Repository**: Maximum 400 lines (split by responsibility)
- **Service/Worker**: Maximum 200 lines (focused single-purpose classes)
- **Data Classes**: Maximum 100 lines (use composition for complex models)
- **Utility Classes**: Maximum 150 lines (group related functions)

### Function/Method Size
- **Public methods**: Maximum 30 lines (prefer composition)
- **Private helpers**: Maximum 15 lines (single responsibility)
- **Complex operations**: Break into smaller functions with descriptive names

### Class Complexity
- **Maximum cyclomatic complexity**: 10 per method
- **Maximum nesting depth**: 3 levels (early returns, guard clauses)
- **Constructor parameters**: Maximum 5 (use data classes or builders)

## AI Processing Structure

### Voice Processing Pipeline
```
app/src/main/java/com/voiceexpense/ai/
├── speech/
│   ├── SpeechRecognitionService.kt    # ML Kit ASR integration
│   ├── AudioRecordingManager.kt       # Audio capture and processing
│   └── TranscriptionListener.kt       # ASR result handling
├── parsing/
│   ├── TransactionParser.kt           # Gemini Nano integration
│   ├── StructuredOutputValidator.kt   # JSON schema validation
│   ├── ParsedResult.kt                # Parsing result models
│   └── ParsingPrompts.kt              # Prompt templates and examples
└── model/
    ├── ModelManager.kt                # AI model lifecycle management
    ├── OnDeviceConfig.kt              # Model configuration
    └── ModelMetrics.kt                # Performance tracking
```

### Separation of Concerns
- Speech recognition isolated from parsing logic
- Structured output validation separate from AI model calls
- Model management handles loading/unloading efficiently
- Clear error boundaries between ASR failures and parsing failures

## Documentation Standards

- All public APIs documented with KDoc
- Complex business logic includes inline comments explaining "why" not "what"
- README files for major feature modules (ai/, data/, ui/)
- Architecture Decision Records (ADRs) for significant technical choices
- API contract documentation for structured parsing input/output
- Testing documentation with fixture examples for AI parsing scenarios

## Testing Structure

### Test Organization
```
app/src/test/java/com/voiceexpense/
├── ai/
│   ├── parsing/
│   │   ├── TransactionParserTest.kt           # Unit tests with fixtures
│   │   └── fixtures/
│   │       ├── ValidUtterances.kt             # Sample inputs/outputs
│   │       └── EdgeCaseInputs.kt              # Ambiguous/invalid cases
│   └── speech/
│       └── SpeechRecognitionServiceTest.kt    # Mock ASR integration
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
- Mock external dependencies (Apps Script client, ML Kit)
- Integration tests for critical paths (widget → service → confirmation)
- Performance tests for parsing latency requirements (<3s)
- Error scenario testing for offline/auth failures
