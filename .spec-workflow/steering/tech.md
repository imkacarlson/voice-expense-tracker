# Technology Stack

## Project Type
Android mobile application with voice-first interface and on-device AI processing

## Core Technologies

### Primary Language(s)
- **Language**: Kotlin (Android development)
- **Runtime/Compiler**: Android Runtime (ART), Kotlin compiler
- **Language-specific tools**: Gradle (build system), Android SDK

### Key Dependencies/Libraries
- **ML Kit GenAI**: On-device Gemini Nano for natural language understanding and structured parsing
- **ML Kit Speech Recognition**: On-device speech-to-text conversion
- **Google Play Services Auth**: OAuth 2.0 authentication for Google account integration
- **Google Sheets API**: For posting structured transaction data to user's spreadsheet
- **WorkManager**: Background task scheduling for offline queue processing and sync
- **Room Database**: Local SQLite database for transaction persistence and offline queue
- **EncryptedSharedPreferences**: Secure storage for OAuth tokens and sensitive data
- **Android Widget Framework**: Home screen widget implementation
- **Retrofit/OkHttp**: HTTP client for Google Sheets API communication

### Application Architecture
Event-driven architecture with MVVM pattern:
- **Widget-triggered capture**: Home screen widget initiates voice recording
- **Service-based processing**: Foreground service handles ASR and AI processing
- **Activity-based confirmation**: Single-screen confirmation UI with voice correction loop
- **Background sync**: WorkManager handles offline queue and Google Sheets posting

### Data Storage (if applicable)
- **Primary storage**: Room Database (SQLite) for local transaction persistence
- **Caching**: In-memory caching for recent merchants, categories, and accounts
- **Data formats**: JSON for API communication, SQLite for local storage
- **Secure storage**: EncryptedSharedPreferences for OAuth tokens, Hardware-backed Keystore for sensitive data

### External Integrations (if applicable)
- **APIs**: Google Sheets API v4 for transaction posting
- **Protocols**: HTTPS/REST for Google API communication
- **Authentication**: OAuth 2.0 with Google Sign-In, minimal scopes (Google Sheets access only)

### Monitoring & Dashboard Technologies (if applicable)
- **Dashboard Framework**: Native Android UI (Activities/Fragments)
- **Real-time Communication**: Local broadcasts for service-to-UI communication
- **Visualization Libraries**: Native Android views for transaction confirmation
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
- **Why not Capacitor here**: The prior project’s script used Capacitor to wrap web assets. This app is native-first (Gemini Nano, Widgets, WorkManager, Room). Replicating the automation pattern (prereq checks + deterministic packaging) preserves the same DX benefits without introducing a web wrapper or custom plugins.
- **CI/CD**: The same Gradle commands run headless in CI. Example: `./gradlew clean assembleDebug` then archive `app/build/outputs/apk/debug/app-debug.apk`. For release, inject signing configs via environment variables or Gradle properties.
- **Next Step (implementation)**: Add `scripts/build_apk.py` with the above behavior and a `README` snippet documenting usage.

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
- **Installation Requirements**: Google Pixel device with Gemini Nano support, Google account
- **Update Mechanism**: Manual APK updates via `adb install -r` or file transfer

## Technical Requirements & Constraints

### Performance Requirements
- **Parsing latency**: <3 seconds median for speech-to-structured-data
- **End-to-end flow**: <30 seconds from widget tap to confirmation
- **Memory usage**: Minimal footprint, efficient model loading
- **Accuracy**: >90% parsing accuracy for common transaction types

### Compatibility Requirements  
- **Platform Support**: Android 14+ (Gemini Nano requirement)
- **Device Support**: Google Pixel 7a and newer Pixel devices
- **Dependency Versions**: Latest stable ML Kit GenAI, Google Play Services
- **Standards Compliance**: Android security best practices, OAuth 2.0 standard

### Security & Compliance
- **Security Requirements**: 
  - On-device AI processing only (no cloud AI calls)
  - Secure OAuth token storage with hardware-backed encryption
  - Minimal permissions (microphone, internet for Sheets sync only)
  - No sensitive data in logs or crash reports
- **Privacy Model**: User owns all data, no telemetry or analytics collection
- **Threat Model**: Protect OAuth tokens, prevent data leakage, secure local storage

### Scalability & Reliability
- **Expected Load**: Single-user application with daily transaction volume <100 entries
- **Availability Requirements**: Offline-first operation, reliable sync when online
- **Growth Projections**: Support for multiple Google accounts, family sharing in future versions

## Technical Decisions & Rationale

### Decision Log
1. **On-device AI Processing**: Chosen for privacy and offline capability; trade-off of device compatibility for user data ownership
2. **Kotlin over Java**: Modern Android development standard, null safety, conciseness
3. **Room Database**: Type-safe SQLite wrapper, excellent WorkManager integration for offline queue
4. **Widget-first UX**: Minimizes friction for voice capture, aligns with "speed-first" principle
5. **WorkManager for Sync**: Handles network constraints, battery optimization, and retry logic automatically
6. **Structured JSON Output**: Enforces data consistency, enables robust parsing validation

## Known Limitations

- **Device Compatibility**: Limited to Google Pixel devices with Gemini Nano support
- **Currency Support**: USD-only for initial version, no multi-currency handling
- **Language Support**: English-only speech recognition and parsing
- **Network Dependency**: Requires internet connection for Google Sheets sync (offline queue mitigates UX impact)
- **Account Limitations**: Single Google account per app instance initially
