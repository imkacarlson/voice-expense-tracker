Voice Expense Tracker (Android)

Overview
- Text-first Android app to log expenses/income/transfers to a Google Sheet.
- On-device only for parsing; network used only to sync via a Google Apps Script Web App.
 - On-device LLM via MediaPipe Tasks (LlmInference) for structured parsing.

Getting Started
- Requirements: Android Studio (AGP 8.5+), JDK 17, Android SDK (API 34).
- Clone this repo and open `voice-expense-tracker` in Android Studio.
- First build: use Gradle sync to download dependencies.

On-device LLM (MediaPipe) Setup
- Dependency: `com.google.mediapipe:tasks-genai:0.10.27` (already configured).
- Model file: a `.task` model (e.g., Gemma3 1B 4-bit) must be placed at app-private path: `filesDir/llm/model.task`.
- Option A (no adb): In-app import
  - Open Settings → On-device LLM Setup (MediaPipe) → “Import model (.task)” and choose the `.task` file. The app copies it into its sandbox.
  - Tap “Test AI setup” to verify readiness.
- Option B (adb): Push/copy into sandbox
  1. Create target dir inside app sandbox: `adb shell run-as com.voiceexpense mkdir -p files/llm`
  2. Push to temp: `adb push gemma3-1b-it-q4.task /data/local/tmp/model.task`
  3. Copy into app files: `adb shell run-as com.voiceexpense cp /data/local/tmp/model.task files/llm/model.task`
  4. Verify: `adb shell run-as com.voiceexpense ls -la files/llm/`
- Initialize: Open Settings → On-device LLM Setup → “Test AI setup” to validate the model presence.

Run & Build
- Debug build via Android Studio or CLI:
  - `./gradlew :app:assembleDebug`
- Build helper script:
  - `scripts/build_apk.py --install` (checks env, builds, optionally installs)
  - Env vars: `ANDROID_HOME`/`ANDROID_SDK_ROOT`, `JAVA_HOME`, `adb` in PATH

App Structure
- `app/src/main/java/com/voiceexpense/` packages:
  - `ui/` (confirmation, common, settings, setup)
  - `ai/` (parsing, model management)
  - `data/` (local Room DB, remote Apps Script API, repository)
  - `auth/` (encrypted token storage)
  - `worker/` (WorkManager sync)
  - `di/` (Hilt modules)

Configuration
- Settings screen: enter the Apps Script Web App URL and Known Accounts.
- Google Sign-In: Required for posting (uses `userinfo.email` access token). Open Settings and tap Sign in.
- OAuth storage: Access tokens and account info stored in EncryptedSharedPreferences.

Testing
- Unit tests: `./gradlew testDebugUnitTest`
- Instrumented tests: `./gradlew connectedAndroidTest`
- Key test areas: repository mapping, DAO operations, worker posting, parser validation.
- Tests:
  - `TransactionParserTest` (MediaPipe path and fallback),
  - `TransactionPromptsTest`, setup guide UI test, and a baseline performance test.

Notes
- Users can use the Android keyboard mic button for voice-to-text; the app itself is text-only. LLM parsing uses MediaPipe `LlmInference` with a local `.task` model.
- Sync posts to your Google Apps Script Web App. If unsigned or token invalid, transactions remain queued and WorkManager retries after sign-in.

Sign-In & Sync Checklist
- Web App URL: Paste your Apps Script deployment URL into Settings.
- Sign-In: Tap Sign in and choose your Google account. Status updates to the signed-in email.
- Permissions: App uses only the `userinfo.email` scope for token; Apps Script validates the email server-side.
- Ready State: Settings shows “Sync ready via Apps Script” when URL is set and you’re signed in.
