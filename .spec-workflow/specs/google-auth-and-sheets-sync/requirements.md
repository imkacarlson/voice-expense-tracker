# Requirements Document

## Introduction

Implement Google Sign-In based OAuth 2.0 authorization and reliable Google Sheets sync so the app can post confirmed transactions to a private spreadsheet without embedding secrets. The feature adds a minimal sign-in experience, secure token storage/refresh, and robust sync behavior (including 401 refresh/retry) that integrates with the existing Room queue, WorkManager, and `SheetsClient`.

## Alignment with Product Vision

This feature enables the core product promise: capture on-device and sync to the user’s own Google Sheet using network only for posting. It preserves privacy (no cloud AI), avoids insecure API keys or bundled service-account secrets, and keeps the UX simple (one-time consent, silent refresh) in line with the voice-first minimal-friction flow.

## Requirements

### Requirement 1: Google Sign-In (Sheets scope)

**User Story:** As a user, I want to sign in with my Google account and grant access to my spreadsheet so that the app can post transactions to my private Google Sheet.

#### Acceptance Criteria

1. WHEN the user taps “Sign in”, THEN the system SHALL launch Google Sign-In with the minimal Sheets write scope.
2. IF the user completes consent, THEN the system SHALL obtain an access token (and refresh token when available) and return to the app.
3. IF the user cancels or denies consent, THEN the system SHALL show an actionable error and keep posting disabled.
4. WHEN the user is already signed in with valid tokens, THEN the system SHALL not prompt again and proceeds silently.

### Requirement 2: Secure token storage and refresh

**User Story:** As a user, I want my credentials handled securely so that I don’t need to repeatedly sign in and my tokens are protected on device.

#### Acceptance Criteria

1. WHEN tokens are received, THEN the system SHALL store them using EncryptedSharedPreferences and SHALL NOT log or expose them.
2. WHEN the access token is expired or near expiry, THEN the system SHALL refresh it using the refresh token without user interaction.
3. IF token refresh fails (e.g., revoked, invalid), THEN the system SHALL mark auth as invalid and prompt the user to sign in again.

### Requirement 3: Posting with 401 refresh/retry

**User Story:** As a user, I want reliable posting so that transient auth failures recover automatically.

#### Acceptance Criteria

1. WHEN `SheetsClient.appendRow` receives HTTP 401, THEN the system SHALL attempt a single token refresh and retry the append once.
2. IF the retry succeeds, THEN the system SHALL treat the transaction as posted and continue syncing remaining items.
3. IF the retry fails or refresh is not possible, THEN the system SHALL leave the transaction in QUEUED (or FAILED if appropriate), log a meaningful reason, and surface a non-blocking status to the user.

### Requirement 4: Update transaction status and sheet reference

**User Story:** As a user, I want posted transactions to be clearly marked so that I can trust the sync state.

#### Acceptance Criteria

1. WHEN an append succeeds, THEN the system SHALL set the transaction status to POSTED.
2. WHEN the Sheets API returns an updated range (or equivalent reference), THEN the system SHOULD store a `sheetRef` (spreadsheetId, sheetId or name, and row index if derivable).
3. IF the reference is not available, THEN the system SHALL still mark POSTED but leave `sheetRef` null.

### Requirement 5: Network/offline queue behavior

**User Story:** As a user, I want the app to work offline and sync later so that I can capture anywhere.

#### Acceptance Criteria

1. IF the device is offline, THEN confirmed transactions SHALL be enqueued (QUEUED) and not attempted until network is available.
2. WHEN the device regains connectivity, THEN WorkManager SHALL run a constrained job to drain the queue.
3. WHEN a posting attempt fails with non-auth transient errors (5xx/timeout), THEN the system SHALL back off and retry according to WorkManager policy without user intervention.

### Requirement 6: Settings gating and actionable states

**User Story:** As a user, I want clear setup requirements so that I know what’s missing to enable sync.

#### Acceptance Criteria

1. IF spreadsheet ID or sheet name is missing, THEN syncing SHALL be disabled and the UI SHALL prompt to complete Settings.
2. IF the user is signed out or tokens are invalid, THEN syncing SHALL be disabled and the UI SHALL prompt to Sign in.
3. WHEN gating conditions are resolved, THEN syncing SHALL resume automatically on the next cycle.

### Requirement 7: Sign-out

**User Story:** As a user, I want to sign out so that the app no longer has access to my data.

#### Acceptance Criteria

1. WHEN the user taps “Sign out”, THEN the system SHALL revoke/clear tokens and remove them from secure storage.
2. AFTER sign-out, THEN the system SHALL block posting and prompt for Sign in on the next attempt.

### Requirement 8: Minimal user-facing errors and logs

**User Story:** As a user, I want concise, helpful messages so that I can fix issues quickly without noise.

#### Acceptance Criteria

1. WHEN a sync error occurs, THEN the system SHALL present a short, actionable message (e.g., “Sign in required”, “Check spreadsheet ID”).
2. Internal logs SHALL include error category (auth/network/validation) without sensitive data.

## Non-Functional Requirements

### Code Architecture and Modularity
- Single Responsibility Principle: Keep auth, sync, and networking concerns isolated.
- Modular Design: Implement sign-in UI and token handling in `auth/`; keep `SheetsClient` focused on API calls; orchestrate retries in repository/worker.
- Dependency Management: Use Hilt for injecting `AuthRepository`, `SheetsClient`, and DAO including Worker injection (no service locator).
- Clear Interfaces: `AuthRepository` exposes get/refresh/clear; `SheetsClient` exposes append with explicit Result mapping.

### Performance
- Token refresh and one retry SHALL add no more than one extra request per 401 event.
- Sync of 10 queued items SHOULD complete under 5 seconds on a typical connection.
- Background work SHALL use WorkManager with network constraints to minimize battery impact.

### Security
- Store tokens only in EncryptedSharedPreferences; never in plaintext logs or crash reports.
- Request minimal scopes necessary to append values to the configured spreadsheet.
- Use HTTPS only; validate base URL; no embedded API keys or service-account secrets.

### Reliability
- WorkManager backoff for transient failures; single-retry policy on 401 after refresh.
- Transactions SHALL not be marked POSTED unless the API call succeeds.
- Idempotency: Avoid duplicate posts by status transitions; never retry after success.

### Usability
- One-tap Sign in; silent refresh; clear call-to-action when setup is incomplete.
- Error messages are concise and actionable; no technical jargon exposed to users.

