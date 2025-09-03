# Requirements Document

## Introduction

Implement the “capture → confirmation” navigation glue so that when a draft transaction is created from voice capture, the app immediately opens the confirmation screen with that draft loaded. This reduces friction and makes the end-to-end flow demoable with today’s stubbed ASR and parser.

## Alignment with Product Vision

- Speed-first capture: One tap on the widget should lead directly to a concise confirmation loop without manual navigation.
- Offline-first: Draft creation and navigation must work without network; sync remains handled later by WorkManager.
- Privacy-first: No cloud AI calls are introduced by this feature; only local navigation and repository reads.

## Requirements

### Requirement 1: Launch confirmation on draft save

**User Story:** As a user, I want the confirmation screen to open as soon as my spoken entry is parsed into a draft so I can immediately review and correct it.

#### Acceptance Criteria

1. WHEN `VoiceRecordingService` saves a draft THEN the system SHALL start `TransactionConfirmationActivity` in a new task and include `EXTRA_TRANSACTION_ID` with the draft’s id.
2. IF the service already launched the confirmation in the last 2 seconds for the same id THEN the system SHALL NOT launch another instance (debounce to avoid duplicates).
3. WHEN the confirmation is launched THEN the service SHALL stop foreground work promptly to avoid lingering notifications.

### Requirement 2: Load draft by ID in confirmation

**User Story:** As a user, I want the confirmation screen to show the draft created from my utterance so that corrections apply to the right transaction.

#### Acceptance Criteria

1. WHEN `TransactionConfirmationActivity` receives `EXTRA_TRANSACTION_ID` THEN the system SHALL fetch the draft by id from `TransactionRepository` and call `viewModel.setDraft(draft)` to initialize the voice correction loop.
2. IF no draft is found for the provided id THEN the system SHALL show a brief error (e.g., toast) and finish the activity gracefully.
3. WHEN the draft is loaded THEN the loop SHALL begin and allow voice corrections and confirmation per existing behavior.

### Requirement 3: Backward-compatible broadcast (optional listeners)

**User Story:** As a developer, I want to retain the existing broadcast so that other components may listen in future without blocking the primary navigation.

#### Acceptance Criteria

1. WHEN a draft is saved THEN the system SHALL continue sending the `ACTION_DRAFT_READY` broadcast with `EXTRA_TRANSACTION_ID`.
2. The broadcast SHALL be non-blocking and SHALL NOT be required for confirmation Activity launch.

## Non-Functional Requirements

### Code Architecture and Modularity
- Single Responsibility: Navigation trigger remains in service; draft loading logic remains in Activity/ViewModel using the repository.
- Clear Interfaces: Use `TransactionRepository.getById(id)` to retrieve the draft; keep navigation extras stable (`EXTRA_TRANSACTION_ID`).

### Performance
- Launch latency from draft save to Activity display SHOULD be under 300 ms on a Pixel 7a.
- No more than one Activity launch per draft id.

### Security
- No new permissions; extras contain only a UUID id. Do not log sensitive values.

### Reliability
- Handle missing/invalid id gracefully without crashes.
- Ensure the service stops foreground mode after launching the Activity to avoid notification churn.

### Usability
- Seamless transition from capture to confirmation with no intermediate taps.
- Brief user feedback when a draft cannot be loaded (and return to prior context).
