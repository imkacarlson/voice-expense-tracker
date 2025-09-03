# Requirements Document

## Introduction

Implement Google Identity authentication and a TokenProvider to obtain and manage OAuth 2.0 access tokens for the Google Sheets API on Android. Replace placeholder token handling with a secure, reliable flow that allows the app to post confirmed transactions to the user’s spreadsheet. The feature includes UI sign-in status, sign-in/out actions, token storage, and repository integration with robust 401 retry behavior.

## Alignment with Product Vision

This feature enables privacy-preserving, offline-first capture with reliable sync by providing the minimal online capability needed: obtaining a Sheets access token to append rows. It adheres to the vision by using Google Sign-In with minimal scopes, secure on-device storage, and no cloud AI usage. It unlocks end-to-end posting so that voice-captured transactions can reach Google Sheets.

## Requirements

### Requirement 1: Google Sign-In with Sheets scope

**User Story:** As a user, I want to sign in to my Google account with Sheets access so that the app can post my confirmed transactions to my spreadsheet.

#### Acceptance Criteria

1. WHEN the user taps Sign In in Settings THEN the system SHALL present the Google Sign-In consent with the Sheets scope ("https://www.googleapis.com/auth/spreadsheets").
2. WHEN sign-in succeeds THEN the system SHALL persist the selected account’s email and a valid access token securely.
3. IF the user cancels or sign-in fails THEN the system SHALL show an actionable message and SHALL NOT persist stale or partial credentials.
4. WHEN the user taps Sign Out THEN the system SHALL clear stored tokens and account info.
5. WHEN signed in, Settings SHALL display the signed-in email; when signed out, it SHALL display "Not signed in".

### Requirement 2: Secure token storage

**User Story:** As a privacy-conscious user, I want my tokens stored securely so that my account remains protected.

#### Acceptance Criteria

1. WHEN an access token is obtained THEN the system SHALL store it via EncryptedSharedPreferences (hardware-backed where available).
2. The system SHALL NOT log tokens or include them in crash messages.
3. IF the app is uninstalled or user signs out THEN tokens SHALL no longer be retrievable by the app.

### Requirement 3: TokenProvider abstraction and DI

**User Story:** As a developer, I want a clear TokenProvider interface and Hilt wiring so that token acquisition and invalidation is testable and replaceable.

#### Acceptance Criteria

1. The system SHALL provide a `TokenProvider` implementation that can:
   - getAccessToken(accountEmail, scope) → returns a valid token (fetch or refresh as needed)
   - invalidateToken(accountEmail, scope) → invalidates cached token
2. Hilt SHALL provide the concrete TokenProvider via a @Provides binding.
3. Unit tests SHALL be able to substitute a fake TokenProvider for deterministic scenarios.

### Requirement 4: Repository integration and 401 retry

**User Story:** As a user, I want my transactions to sync reliably so that transient auth issues resolve automatically.

#### Acceptance Criteria

1. WHEN `TransactionRepository.syncPending()` needs a token THEN the system SHALL obtain it via `TokenProvider.getAccessToken()` for the signed-in account and Sheets scope.
2. WHEN an append request returns 401 THEN the system SHALL call `TokenProvider.invalidateToken()` and retry once with a freshly retrieved token.
3. IF the second attempt still fails with 401 THEN the system SHALL stop retrying, keep the transaction queued, and surface a failure count in the sync result.
4. WHEN a post succeeds THEN the system SHALL mark the transaction POSTED and store `SheetReference` with row index if available.
5. The repository SHALL not proceed if no signed-in account exists or spreadsheet configuration is missing; it SHALL return a result indicating failures without crashing.

### Requirement 5: Settings gating and UX

**User Story:** As a user, I want clear guidance in Settings so that I know what’s needed for sync to work.

#### Acceptance Criteria

1. WHEN spreadsheet ID and sheet name are both set AND the user is signed in THEN Settings SHALL display that sync is ready (with the sheet name).
2. IF either spreadsheet configuration is missing OR the user is signed out THEN Settings SHALL display an appropriate gating message and disable any manual "test post" actions (if present in future versions).
3. The Settings screen SHALL update status immediately after sign-in/out.

### Requirement 6: Permissions and scopes

**User Story:** As a privacy-first user, I want minimal permissions so that the app only accesses what it needs.

#### Acceptance Criteria

1. The system SHALL request only the Sheets scope for Google Sign-In; no Drive-wide scopes.
2. The app SHALL not add analytics/telemetry as part of this feature.

### Requirement 7: Error handling and resilience

**User Story:** As a user, I want the app to fail gracefully so that I don’t lose data.

#### Acceptance Criteria

1. IF token acquisition fails due to network or user cancellation THEN the app SHALL keep transactions queued and prompt the user to sign in later.
2. IF a sync run encounters repeated auth errors THEN the app SHALL not crash and SHALL schedule future retries via WorkManager policies.
3. All user-visible errors SHALL be concise and non-technical.

## Non-Functional Requirements

### Code Architecture and Modularity
- Single Responsibility Principle: Keep TokenProvider, AuthRepository, and Settings UI concerns separated.
- Modular Design: Token acquisition logic isolated from repository posting logic.
- Dependency Management: Provide TokenProvider via Hilt; avoid API leakage into unrelated layers.
- Clear Interfaces: `TokenProvider`, `AuthRepository` methods well defined and documented.

### Performance
- Token retrieval SHALL complete within 2 seconds under typical conditions; retries are backgrounded by WorkManager.
- Sync runs SHALL not block the UI thread.

### Security
- Store tokens in EncryptedSharedPreferences; never log tokens.
- Use minimal OAuth scopes.
- Do not transmit tokens outside Google APIs; HTTPS only.

### Reliability
- One retry after 401 with token invalidation.
- Transactions remain queued on failure; no data loss.

### Usability
- Settings clearly communicates sign-in status and sync readiness.
- Error messages guide the user to resolve issues (sign in, set sheet config).
