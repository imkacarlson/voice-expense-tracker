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
- Google Sign-In: Required for posting to Google Sheets (scope: `https://www.googleapis.com/auth/spreadsheets`). Open Settings and tap Sign in.
- OAuth storage: Access tokens and account info stored in EncryptedSharedPreferences.

Testing
- Unit tests: `./gradlew testDebugUnitTest`
- Instrumented tests: `./gradlew connectedAndroidTest`
- Key test areas: repository mapping, DAO operations, worker posting, parser validation.

Notes
- ASR and parsing use placeholders for now; replace with ML Kit Speech and GenAI Gemini Nano.
- Sync requires a valid Google access token and spreadsheet configuration. If unsigned or token invalid, transactions remain queued and WorkManager retries after sign-in.

Sign-In & Sync Checklist
- Spreadsheet: Enter Spreadsheet ID and Sheet Name in Settings.
- Sign-In: Tap Sign in and choose your Google account. Status updates to the signed-in email.
- Permissions: App requests only the Google Sheets scope for posting.
- Ready State: Settings shows “Sync ready” when both sheet config and sign-in are complete.
