Voice Expense Tracker (Android)

Overview
- Voice-first Android app to log expenses/income/transfers to a Google Sheet.
- On-device only for ASR and parsing; network used only for Sheets sync.

Getting Started
- Requirements: Android Studio (AGP 8.5+), JDK 17, Android SDK (API 34).
- Clone this repo and open `voice-expense-tracker` in Android Studio.
- First build: use Gradle sync to download dependencies.

Run & Build
- Debug build via Android Studio or CLI:
  - `./gradlew :app:assembleDebug`
- Build helper script:
  - `scripts/build_apk.py --install` (checks env, builds, optionally installs)
  - Env vars: `ANDROID_HOME`/`ANDROID_SDK_ROOT`, `JAVA_HOME`, `adb` in PATH

App Structure
- `app/src/main/java/com/voiceexpense/` packages:
  - `ui/` (widget, confirmation, common)
  - `service/` (voice recording)
  - `ai/` (speech, parsing, model management)
  - `data/` (local Room DB, remote Sheets API, repository)
  - `auth/` (encrypted token storage)
  - `worker/` (WorkManager sync)
  - `di/` (Hilt modules)

Configuration
- Settings screen: set Spreadsheet ID, Sheet Name, and Known Accounts.
- OAuth: Uses EncryptedSharedPreferences; add Google Sign-In integration before release.

Testing
- Unit tests: `./gradlew testDebugUnitTest`
- Instrumented tests: `./gradlew connectedAndroidTest`
- Key test areas: repository mapping, DAO operations, worker posting, parser validation.

Notes
- ASR and parsing use placeholders for now; replace with ML Kit Speech and GenAI Gemini Nano.
- Sync requires a valid Google access token and spreadsheet configuration.

