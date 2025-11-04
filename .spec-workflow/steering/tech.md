# Technology Stack

## Project Type
Android mobile application with text input interface and on-device AI processing

## Core Technologies

### Primary Language(s)
- **Language**: Kotlin (Android development)
- **Runtime/Compiler**: Android Runtime (ART), Kotlin compiler
- **Language-specific tools**: Gradle (build system), Android SDK

### Key Dependencies/Libraries
- **MediaPipe Tasks GenAI**: On-device Gemma 3 1B model via `.task` file for natural language understanding and structured parsing
- **Google Play Services Auth**: OAuth 2.0 authentication for Google account integration
- **Google Apps Script Web App**: For appending structured transaction data to the user's spreadsheet
- **WorkManager**: Background task scheduling for offline queue processing and sync
- **Room Database**: Local SQLite database for transaction persistence and offline queue
- **EncryptedSharedPreferences**: Secure storage for OAuth tokens and sensitive data
- **OkHttp/Moshi**: HTTP client for Apps Script Web App communication

### Application Architecture
Event-driven architecture with MVVM pattern:
- **Text input capture**: Main app text input interface
- **Activity-based confirmation**: Full-screen form UI with comprehensive field editing
- **Background sync**: WorkManager handles offline queue and Apps Script posting

### Data Storage (if applicable)
- **Primary storage**: Room Database (SQLite) for local transaction persistence
- **Caching**: In-memory caching for recent merchants, categories, and accounts
- **Configuration storage**: SharedPreferences for user-configurable dropdown options
- **Data formats**: JSON for API communication, SQLite for local storage
- **Secure storage**: EncryptedSharedPreferences for OAuth tokens, Hardware-backed Keystore for sensitive data

### External Integrations (if applicable)
- **APIs**: Google Apps Script HTTPS endpoint for transaction posting, secured with script properties (spreadsheet id, allowed email, OAuth client id).
- **Protocols**: HTTPS/REST
- **Authentication**: OAuth 2.0 with Google Sign-In (userinfo.email access token) validated server-side by Apps Script (`tokeninfo` audience check, email whitelist).
- **Server Controls**: Apps Script enforces 30-requests/10-minute rate limiting, appends hashed email logs to a capped audit sheet, and formats timestamps server-side.

### Monitoring & Dashboard Technologies (if applicable)
- **Dashboard Framework**: Native Android UI (Activities/Fragments) with comprehensive form interface
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
- **AI Model Setup**: Gemma 3 1B 4-bit quantized bundle (`.task` or `.litertlm`) must live under `app-private-files/llm/`
  - **Setup Method A**: In-app import via Settings → "Import model (.task/.litertlm)"
  - **Setup Method B**: ADB push method documented in README.md
  - Settings provides "Test AI setup" plus GPU/CPU backend toggles to validate availability and work around Pixel GPU driver issues.
- **CI/CD**: The same Gradle commands run headless in CI. Example: `./gradlew clean assembleDebug` then archive `app/build/outputs/apk/debug/app-debug.apk`. For release, inject signing configs via environment variables or Gradle properties.

### Version Control & Collaboration
- **VCS**: Git
- **Branching Strategy**: Feature branch workflow
- **Code Review Process**: Pull request-based reviews

### Dashboard Development (if applicable)
- **Live Reload**: Android Studio instant run
- **Port Management**: ADB for device communication
- **Multi-Instance Support**: Multiple emulator instances for testing

## Module Layout & Reuse
- `:app`: Android UI, Hilt wiring, data layer, and WorkManager. Injects the shared `TransactionParser` and listens to `StagedRefinementDispatcher` updates to stream Stage 2 results into the confirmation UI.
- `:parsing`: Kotlin/JVM library containing heuristics, staged orchestrator (`StagedParsingOrchestrator`), confidence scoring, run-log capture, and the shared dispatcher. Published as an internal dependency for both the app and CLI.
- `:cli`: Kotlin CLI (`com.voiceexpense.eval.CliMainKt`) that wraps the parsing module for off-device evaluation. Uses `PythonGenAiGateway` to capture stage prompts and replay model responses supplied by Python.
- `evaluator/`: Python orchestrator (`evaluate.py`, `models.py`, `test_cases.md`) that drives the CLI, runs HuggingFace Gemma models (CPU or GPU), batches prompts, and emits Markdown summary/detail reports under `evaluator/results/`.
- `backend/appscript`: Google Apps Script Web App with OAuth token validation, rate limiting, hashed logging, and column mapping that mirrors the Android client payload.
- `prompts/`: Markdown workspace for prompt experiments and curated utterance fixtures used during evaluator runs.

## Deployment & Distribution (if applicable)
- **Target Platform(s)**: Android 14+ (API level 34+), specifically Google Pixel 7a
- **Distribution Method**: APK sideloading only (personal use; no Play Store)
- **Installation Requirements**: Google Pixel device with MediaPipe Tasks support, Google account, Gemma 3 1B `.task` or `.litertlm` bundle staged under `files/llm/`
- **Update Mechanism**: Manual APK updates via `adb install -r` or file transfer

## On-Device AI Processing Strategy

### Model Configuration
- **Primary Model**: Gemma 3 1B 4-bit quantized via MediaPipe Tasks (`.task` or `.litertlm` bundle).
- **Backend Selection**: User-facing toggle chooses GPU (default) or CPU backends; persisted preference mitigates Pixel GPU driver crashes.
- **Prewarm Strategy**: `MediaPipeGenAiClient` prewarms the interpreter on app launch to avoid cold-start latency spikes.
- **Fallback Compatibility**: If the bundle fails to load, the heuristics-only pipeline remains active so drafts still materialize.

### Hybrid Pipeline (Stage 1 → Stage 2)
1. **Stage 1 Heuristics** — `HeuristicExtractor` parses amounts, merchants, categories, and accounts without touching the LLM; results seed the draft and run log.
2. **Stage 2 Staged Refinement** — `StagedParsingOrchestrator` creates prioritized prompts for low-confidence fields. MediaPipe (app) or `PythonGenAiGateway` (CLI) fulfils requests, and responses are validated before merging.
3. **Live Updates** — `StagedRefinementDispatcher` broadcasts per-field updates so UI components can animate loading indicators, highlight new values, and enable diagnostics export after completion.
4. **Confidence & Metrics** — `ConfidenceScorer` plus `ProcessingMonitor` compute confidence scores, track durations, and log refined fields for analytics and evaluator summaries.
5. **Heuristic Failover** — Timeouts or invalid AI outputs fall back to the Stage 1 draft with lower confidence, keeping the flow unblocked while signaling manual review.

### Focused Prompt Strategy
- Prompts are field-specific and include allowed-value lists from `ParsingContext` (categories, tags, accounts) to keep outputs bounded.
- Refinement priority: merchant → description → account → tags → category; we abort downstream prompts once upstream fields reach threshold confidence.
- Prompt size is capped (<1k tokens) to fit the 5k-character budget enforced in the parser, and repeats are avoided through staged tracking.
- Responses flow through `StructuredOutputValidator` and `TagNormalizer` to enforce casing, delimiters, and schema compliance before hitting the UI.

## Evaluation Toolchain & Automation
- **CLI Bridge (`:cli`)**: `com.voiceexpense.eval.CliMainKt` wraps the shared parser. It emits `needs_ai` payloads with heuristics and pending prompts, then consumes injected responses during the second pass.
- **Python Orchestrator**: `evaluator/evaluate.py` batches prompts via HuggingFace (`models.py`), supports `google/gemma-3-1b-it` and `google/gemma-3n-E2B-it`, and writes Markdown summaries/detailed reports to `evaluator/results/`.
- **Test Case Source**: `evaluator/test_cases.md` drives expectations per utterance (amount, merchant, type, etc.); blank cells indicate "don't care" fields.
- **Metrics**: Aggregates overall accuracy, per-field accuracy, AI usage counts, and stage timings (Stage 0/1/total) mirroring `ProcessingMonitor`.
- **Workflow**: Build CLI (`./gradlew :cli:build`), activate Python venv, run `python evaluate.py --model google/gemma-3-1b-it --test smoke`, review `*_summary.md` before shipping prompt changes.
- **Continuous Usage**: Use evaluator when tweaking prompts, heuristics, or MediaPipe settings to quantify regressions without reinstalling the Android app.

## Logging & Diagnostics
- **ParsingRunLog**: Stage 1 and Stage 2 events append to `ParsingRunLogBuilder`; entries cover heuristics, prompts, responses, validation, and summary results.
- **Run Log Store**: `ParsingRunLogStore` keeps in-memory builders keyed by transactionId so the confirmation screen can export Markdown via the "Export diagnostics" button.
- **CLI ConsoleLogger**: CLI routes parsing logs through `ConsoleLogger`, aligning evaluator output with app diagnostics for consistent troubleshooting.
- **UI Surfacing**: `TransactionConfirmationActivity` enables export once at least one staged refinement completes, ensuring all prompts are captured.
- **Apps Script Logging**: Backend writes hashed, rate-limited logs to a separate sheet for auditability without leaking personal data.

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
- **Staged Refinement Indicators**: Inline progress spinners and dimmed inputs for fields awaiting Stage 2 responses; highlight animations fire on completion.
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
- **Model Requirements**: Gemma 3 1B 4-bit quantized bundle (.task or .litertlm)
- **Standards Compliance**: Android security best practices, OAuth 2.0 standard

### Security & Compliance
- **Security Requirements**: 
  - On-device AI processing only (no cloud AI calls)
  - Secure OAuth token storage with hardware-backed encryption
  - Minimal permissions (internet for sync only, no microphone required)
  - No sensitive data in logs or crash reports
  - Model file stored in app-private directory
  - Apps Script backend validates OAuth tokens via `tokeninfo`, enforces 30-requests/10-minute rate limiting, hashes emails in logs, and trims audit sheets to 1,000 entries.
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
7. **Sequential Field Refinement**: Stage 2 runs single-field prompts in priority order to keep prompts short, preserve modifiers, and eliminate cross-field drift.
8. **Shared Parsing Module & CLI**: Extract `TransactionParser`/hybrid logic into `:parsing` and expose it via `:cli` so evaluator runs identical code paths while keeping the Android bundle lean.
9. **Evaluator Orchestration**: Python harness with HuggingFace models provides measurable accuracy/latency regressions before prompt or heuristic changes ship.
10. **Room Database**: Type-safe SQLite wrapper, excellent WorkManager integration for offline queue

### AI Processing Trade-offs
- **Accuracy vs Speed**: 1B model trades some accuracy for faster inference
- **Sequential Single-Field Prompts**: Staged orchestrator + dispatcher keep prompts laser-focused per field. Latency rises slightly but accuracy and explainability improve.
- **Examples vs Context**: Balance few-shot examples with user-specific context
- **Validation Strictness**: Strict JSON validation prevents malformed outputs
- **Evaluator Feedback Loop**: Offline evaluator catches regressions quickly but depends on well-maintained markdown fixtures.
- **Text vs Voice Complexity**: Simplified text-only processing reduces complexity

## Known Limitations

- **Device Compatibility**: Limited to devices supporting MediaPipe Tasks GenAI
- **Model Size**: 500MB model file requires sufficient device storage
- **Currency Support**: USD-only for initial version, no multi-currency handling
- **Language Support**: English-only text parsing
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
