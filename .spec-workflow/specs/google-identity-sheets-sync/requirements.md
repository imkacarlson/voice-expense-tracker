# Requirements Document

## Introduction

Implement end-to-end Google Identity authentication and token management for the Google Sheets API so the app can reliably post confirmed transactions to a configured spreadsheet. This feature introduces real Sign-In with Sheets scope consent, secure token storage/refresh, clear “sync ready” gating in Settings, and robust background sync behavior (including 401 retry, offline queueing, and sign-out flows).

## Alignment with Product Vision

- Privacy-first and on-device AI: Only network calls are to Google Sheets for final posting; no cloud AI.
- Offline-first: Capture, parse, and confirm entirely offline; queue posts until online.
- Minimal friction: Simple sign-in and clear readiness state; reliable background posting via WorkManager.
- Security: Narrow OAuth scope (Sheets only), secure storage, and no sensitive logs.

## Requirements

### Requirement 1 — Google Sign-In with Sheets scope

**User Story:** As a user, I want to sign in with my Google account and grant permission for Sheets, so that the app can post my transactions to my spreadsheet.

#### Acceptance Criteria

1. WHEN I tap “Sign in” in Settings THEN the system SHALL show Google consent for scope `https://www.googleapis.com/auth/spreadsheets` and return the selected account.
2. WHEN sign-in completes THEN the system SHALL persist account email and obtain a valid access token for the Sheets scope.
3. IF token fetch fails THEN the system SHALL show a brief error message and keep sync disabled.
4. WHEN I am signed in THEN the system SHALL display “Signed in as <email>”.

### Requirement 2 — Sync readiness gating in Settings

**User Story:** As a user, I want Settings to clearly indicate when sync is ready, so that I know my transactions will be posted.

#### Acceptance Criteria

1. IF Spreadsheet ID or Sheet Name is missing THEN the system SHALL show a message that configuration is required.
2. IF both sheet configuration is set AND I am signed in THEN the system SHALL show a “Sync ready for sheet: <Sheet Name>” status.
3. IF I sign out THEN the system SHALL revert the status to require sign-in and keep local settings intact.

### Requirement 3 — Background sync with token refresh and retries

**User Story:** As a user, I want queued transactions to post to Google Sheets reliably, so that my data appears in my sheet without manual effort.

#### Acceptance Criteria

1. WHEN a transaction is confirmed THEN the system SHALL enqueue it for sync (status QUEUED) and schedule WorkManager with `NetworkType.CONNECTED`.
2. WHEN sync runs AND a valid token exists THEN the system SHALL append a row using the exact column mapping defined by the product/tech docs.
3. IF the Sheets API responds with 401 THEN the system SHALL invalidate the token once, fetch a fresh token, retry the append once, and proceed if successful.
4. IF the append is successful THEN the system SHALL update the transaction to POSTED and persist `sheetRef` (spreadsheetId, sheet name, last row if available).
5. IF the append fails after retry THEN the system SHALL keep the transaction queued or mark as FAILED (implementation decision), and retry later according to WorkManager backoff.
6. WHEN offline THEN the system SHALL not attempt network calls and SHALL retry later automatically when online.

### Requirement 4 — Sign out and token lifecycle

**User Story:** As a user, I want to sign out and revoke local access, so that no further posts happen until I sign in again.

#### Acceptance Criteria

1. WHEN I tap “Sign out” THEN the system SHALL clear stored access token(s) and local account info.
2. AFTER sign out THEN the system SHALL prevent sync posting until I sign in again.
3. WHEN I sign in again THEN previously queued transactions SHALL post normally.

### Requirement 5 — Security, storage, and scope limits

**User Story:** As a privacy-conscious user, I want my tokens stored securely with minimal scope, so that my data and account remain safe.

#### Acceptance Criteria

1. WHEN tokens are stored THEN the system SHALL use EncryptedSharedPreferences (hardware-backed on device) for persistence.
2. THE system SHALL request only the Sheets scope; no additional Google API scopes are requested.
3. THE system SHALL avoid logging tokens or sensitive account details; logs MAY include coarse status and HTTP codes but not headers or bodies containing secrets.

### Requirement 6 — User feedback and error reporting

**User Story:** As a user, I want clear feedback when sync cannot proceed, so that I can fix the issue (sign-in, sheet config, network).

#### Acceptance Criteria

1. IF token acquisition fails THEN the system SHALL show a toast indicating sign-in/token issue and that sync is disabled.
2. IF sheet configuration is incomplete THEN the system SHALL show a toast prompting me to fill Spreadsheet ID and Sheet Name when I attempt to enable sync or confirm a transaction.
3. IF posting fails for a transaction THEN the system SHALL keep it in the queue for retry and MAY notify via a non-intrusive toast or status (implementation detail), avoiding notification spam.

## Non-Functional Requirements

### Code Architecture and Modularity
- Single Responsibility Principle: Token acquisition/refresh isolated behind `TokenProvider` interface.
- Modular Design: Settings UI concerns separated from repository and worker; Sheets client isolated.
- Dependency Management: Inject `TokenProvider`, `AuthRepository`, and `SheetsClient` via Hilt.
- Clear Interfaces: `TokenProvider` defines get/invalidate contract; repository owns sync flow.

### Performance
- Posting a single queued transaction SHOULD complete within 2 seconds on a good connection (excluding backoff waits).
- Token refresh SHOULD complete within 2 seconds under normal conditions.
- WorkManager SHOULD batch multiple queued transactions in one run to minimize wakeups.

### Security
- Store tokens in EncryptedSharedPreferences; never log tokens; avoid wide scopes.
- Use HTTPS with Retrofit/OkHttp; enable BASIC logging only (no bodies) in production.
- Clear tokens immediately on sign-out; invalidate cached tokens on 401 before retry.

### Reliability
- Use WorkManager with `NetworkType.CONNECTED` and exponential backoff.
- On 401, perform exactly one invalidate+refresh retry per transaction batch.
- Offline queue remains durable across app restarts.

### Usability
- Settings displays signed-in email and clear sync readiness.
- Error toasts are concise and action-oriented; no jargon.
- Sign-in and sign-out flows require minimal taps and provide immediate status updates.
