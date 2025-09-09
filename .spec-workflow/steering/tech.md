# Technology Stack

## Project Type
Android mobile application with flexible input interface (voice + text) and on-device AI processing

## Core Technologies

### Primary Language(s)
- **Language**: Kotlin (Android development)
- **Runtime/Compiler**: Android Runtime (ART), Kotlin compiler
- **Language-specific tools**: Gradle (build system), Android SDK

### Key Dependencies/Libraries
- **MediaPipe Tasks GenAI**: On-device Gemma 3 1B model via `.task` file for natural language understanding and structured parsing
- **ML Kit Speech Recognition**: On-device speech-to-text conversion (optional voice input path)
- **Google Play Services Auth**: OAuth 2.0 authentication for Google account integration
- **Google Apps Script Web App**: For appending structured transaction data to the user's spreadsheet
- **WorkManager**: Background task scheduling for offline queue processing and sync
- **Room Database**: Local SQLite database for transaction persistence and offline queue
- **EncryptedSharedPreferences**: Secure storage for OAuth tokens and sensitive data
- **Android Widget Framework**: Home screen widget implementation
- **OkHttp/Moshi**: HTTP client for Apps Script Web App communication

### Application Architecture
Event-driven architecture with MVVM pattern:
- **Multi-input capture**: Home screen widget for voice OR main app text input
- **Service-based processing**: Foreground service handles ASR and AI processing (voice path)
- **Activity-based confirmation**: Full-screen form UI with comprehensive field editing
- **Background sync**: WorkManager handles offline queue and Apps Script posting

### Data Storage (if applicable)
- **Primary storage**: Room Database (SQLite) for local transaction persistence
- **Caching**: In-memory caching for recent merchants, categories, and accounts
- **Configuration storage**: SharedPreferences for user-configurable dropdown options
- **Data formats**: JSON for API communication, SQLite for local storage
- **Secure storage**: EncryptedSharedPreferences for OAuth tokens, Hardware-backed Keystore for sensitive data

### External Integrations (if applicable)
- **APIs**: Google Apps Script HTTPS endpoint for transaction posting
- **Protocols**: HTTPS/REST
- **Authentication**: OAuth 2.0 with Google Sign-In (userinfo.email access token) validated server-side by Apps Script

### Monitoring & Dashboard Technologies (if applicable)
- **Dashboard Framework**: Native Android UI (Activities/Fragments) with comprehensive form interface
- **Real-time Communication**: Local broadcasts for service-to-UI communication
- **Visualization Libraries**: Native Android views for transaction confirmation and history
- **State Management**: Android ViewModel with LiveData/StateFlow for reactive UI updates

## Development Environment

### Build & Development Tools
- **Build System**: Gradle with Android Gradle Plugin
- **Package Management**: Gradle dependency management, Google Maven repository
- **Development workflow**: Android Studio with instant run, emulator testing

### Code Quality Tools
- **Static Analysis**: Android Lint, detekt for Kotlin
- **Formatting**: ktlint for Kotlin code style enforcement
- **Testing Framework**: JUnit for unit tests, Espresso for UI tests, Robolectric for Android unit tests
- **Documentation**: KDoc for code documentation

### Build Orchestration & APK Packaging
- **Strategy**: Keep a native Android/Kotlin build via Gradle, and replicate the referenced automation pattern using a lightweight Python helper script that validates prerequisites and drives Gradle to produce installable APKs.
- **Helper Script**: `scripts/build_apk.py`
  - Checks `ANDROID_HOME`/`ANDROID_SDK_ROOT`, `JAVA_HOME`, and `adb` availability; prints clear remediation steps.
  - Runs `./gradlew assembleDebug` (or `assembleRelease` with signing) and streams output.
  - Options: `--install` (adb install on connected device/emulator), `--release` (signed build), `--keystore <path>`/`--storepass`/`--keyalias` for release signing, `--module <app>` for multi-module builds.
  - Outputs the absolute path to the built APK: `app/build/outputs/apk/<variant>/app-<variant>.apk`.
- **AI Model Setup**: Gemma 3 1B 4-bit quantized model (`.task` file) must be placed at `app-private-files/llm/model.task`
  - **Setup Method A**: In-app import via Settings → "Import model (.task)"
  - **Setup Method B**: ADB push method documented in README.md
- **CI/CD**: The same Gradle commands run headless in CI. Example: `./gradlew clean assembleDebug` then archive `app/build/outputs/apk/debug/app-debug.apk`. For release, inject signing configs via environment variables or Gradle properties.

### Version Control & Collaboration
- **VCS**: Git
- **Branching Strategy**: Feature branch workflow
- **Code Review Process**: Pull request-based reviews

### Dashboard Development (if applicable)
- **Live Reload**: Android Studio instant run
- **Port Management**: ADB for device communication
- **Multi-Instance Support**: Multiple emulator instances for testing

## Deployment & Distribution (if applicable)
- **Target Platform(s)**: Android 14+ (API level 34+), specifically Google Pixel 7a
- **Distribution Method**: APK sideloading only (personal use; no Play Store)
- **Installation Requirements**: Google Pixel device with MediaPipe Tasks support, Google account, Gemma 3 1B model file
- **Update Mechanism**: Manual APK updates via `adb install -r` or file transfer

## On-Device AI Processing Strategy

### Model Configuration
- **Primary Model**: Gemma 3 1B 4-bit quantized via MediaPipe Tasks
- **Model File**: `.task` format, approximately 500MB
- **Inference Engine**: MediaPipe LlmInference API
- **Fallback Strategy**: Heuristic parsing when model unavailable

### Prompting Strategy for Smaller Models
Given the 1B parameter constraint, optimized prompting approach:

#### **Single-Shot Parsing (Primary)**
- **System Instruction**: Concise, strict JSON schema specification
- **Few-Shot Examples**: Limit to 6-8 targeted examples based on input type detection
- **Context Integration**: Recent merchants/accounts/categories from user history
- **Prompt Composition**: System + Context + Examples + Input (total <2000 tokens)

#### **Field-by-Field Parsing (Fallback)**
For complex transactions, optional multi-prompt approach:
1. **Amount Extraction**: "Extract USD amount from: [input]"
2. **Merchant Identification**: "Extract merchant/vendor from: [input]"
3. **Category Classification**: "Classify expense category from: [input] Options: [categories]"
4. **Account Detection**: "Identify payment account from: [input] Known: [accounts]"
5. **Tag Extraction**: "Extract relevant tags from: [input]"

#### **Template-Based Parsing**
For highly structured inputs:
- **Expense Template**: "I spent $X at Y for Z"
- **Income Template**: "Income: $X from Y, tag Z"
- **Transfer Template**: "Transfer $X from Y to Z"

### Hybrid Processing Architecture
- **AI-First Path**: MediaPipe structured output with strict validation
- **Heuristic Fallback**: Regex-based parsing for reliability
- **Confidence Scoring**: Evaluate parsing quality and UI highlighting
- **Performance Monitoring**: Track parsing success rates and latency

## Form Interface Technology

### Input Components
- **EditText Fields**: All transaction fields directly editable
- **Spinner/Dropdown**: Configurable option selection
- **DatePickerDialog**: Date selection for backdated transactions
- **Multi-Select Components**: Tag selection with custom UI
- **Number Input**: Decimal amount entry with validation

### Configuration Management
- **SharedPreferences**: Store user-configured dropdown options
- **Settings Activity**: UI for managing dropdown contents
- **Data Binding**: Two-way binding between form fields and transaction model
- **Validation**: Real-time field validation with visual feedback

### UI/UX Patterns
- **Scrollable Form**: All fields visible with ScrollView
- **Field Highlighting**: Missing/low-confidence fields marked visually
- **Progressive Disclosure**: Show relevant fields based on transaction type
- **Accessibility**: Screen reader compatibility, large touch targets

## Technical Requirements & Constraints

### Performance Requirements
- **Parsing latency**: <3 seconds median for speech-to-structured-data
- **End-to-end flow**: <30 seconds from input to confirmation
- **Memory usage**: Minimal footprint, efficient model loading
- **Accuracy**: >90% parsing accuracy for common transaction types
- **Model Loading**: <5 seconds for first-time model initialization

### Compatibility Requirements  
- **Platform Support**: Android 14+ (MediaPipe Tasks requirement)
- **Device Support**: Google Pixel 7a and newer Pixel devices with sufficient RAM
- **Dependency Versions**: MediaPipe Tasks GenAI 0.10.27+, Google Play Services
- **Model Requirements**: Gemma 3 1B 4-bit quantized (.task format)
- **Standards Compliance**: Android security best practices, OAuth 2.0 standard

### Security & Compliance
- **Security Requirements**: 
  - On-device AI processing only (no cloud AI calls)
  - Secure OAuth token storage with hardware-backed encryption
  - Minimal permissions (microphone for voice path, internet for sync only)
  - No sensitive data in logs or crash reports
  - Model file stored in app-private directory
- **Privacy Model**: User owns all data, no telemetry or analytics collection
- **Threat Model**: Protect OAuth tokens, prevent data leakage, secure local storage

### Scalability & Reliability
- **Expected Load**: Single-user application with daily transaction volume <100 entries
- **Availability Requirements**: Offline-first operation, reliable sync when online
- **Growth Projections**: Support for multiple Google accounts, family sharing in future versions

## Technical Decisions & Rationale

### Decision Log
1. **MediaPipe Tasks over ML Kit GenAI**: More flexible model management, supports Gemma 3 1B specifically
2. **Gemma 3 1B over larger models**: Balances on-device performance with accuracy for 500MB footprint
3. **Hybrid AI Strategy**: Ensures app functionality even when model fails
4. **Form-First Confirmation**: Better UX than pure voice, allows precise editing
5. **Configurable Dropdowns**: User-specific categories and accounts improve parsing accuracy
6. **Field-by-Field Parsing Option**: Fallback strategy for smaller model limitations
7. **Room Database**: Type-safe SQLite wrapper, excellent WorkManager integration for offline queue
8. **Widget + App Input**: Accommodates different user preferences and contexts

### AI Processing Trade-offs
- **Accuracy vs Speed**: 1B model trades some accuracy for faster inference
- **Single vs Multi-Prompt**: Single-shot preferred for speed, multi-prompt for complex cases
- **Examples vs Context**: Balance few-shot examples with user-specific context
- **Validation Strictness**: Strict JSON validation prevents malformed outputs

## Known Limitations

- **Device Compatibility**: Limited to devices supporting MediaPipe Tasks GenAI
- **Model Size**: 500MB model file requires sufficient device storage
- **Currency Support**: USD-only for initial version, no multi-currency handling
- **Language Support**: English-only speech recognition and parsing
- **Network Dependency**: Requires internet connection for Apps Script sync (offline queue mitigates UX impact)
- **Processing Power**: May be slower on older/lower-spec devices
- **Model Accuracy**: 1B parameter model may have lower accuracy than larger cloud models

## Future Technical Improvements

### Model Optimization
- **Quantization**: Explore 8-bit or INT8 quantization for smaller footprint
- **Model Updates**: Support for downloading updated Gemma models
- **Multi-Language**: Support for additional languages beyond English

### Processing Enhancements
- **Context Learning**: Improve parsing accuracy using user's historical data
- **Receipt OCR**: Add photo-based transaction capture
- **Smart Suggestions**: Predictive text for merchant and category fields

### Performance Optimization
- **Model Caching**: Keep model warm in memory for frequent users
- **Batch Processing**: Process multiple queued transactions efficiently
- **Background Processing**: Defer heavy AI work to background threads