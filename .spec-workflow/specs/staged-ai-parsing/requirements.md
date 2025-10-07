# Requirements Document

## Introduction

This feature introduces a **staged AI parsing approach** to improve transaction parsing accuracy and user experience on the 1B parameter Gemma 3 on-device model. Instead of overwhelming the small model with a single complex prompt containing all context, examples, and fields, we break parsing into three progressive stages:

1. **Stage 1: Instant Heuristics (0-50ms)** - Show heuristic results immediately
2. **Stage 2: Focused AI Refinement (500-2000ms)** - Single AI call targeting only low-confidence fields
3. **Stage 3: Background Completion** - AI results update form fields as they arrive

This approach provides instant feedback to users while leveraging AI more effectively by:
- Reducing prompt complexity for the small model
- Focusing AI effort on fields where heuristics failed
- Providing progressive UI updates instead of blocking waits

**Current Problem:** The existing single-call approach builds large prompts (~1700 chars) with full system instructions, 3 few-shot examples, context, and heuristic hints, asking the model to parse ALL fields simultaneously. For a 1B model, this is too much cognitive load, resulting in poor accuracy.

**Proposed Solution:** Use heuristics as the foundation, then make a single focused AI call with a stripped-down prompt that only addresses the 2-4 fields where heuristics had low confidence.

## Alignment with Product Vision

This feature directly addresses the technical constraint in `product.md` regarding on-device AI limitations:
- **"Small model accuracy → hybrid prompting strategies, field-by-field parsing fallbacks"** (product.md, Risks and Mitigations)
- Supports the performance requirement of **"<3s parsing latency"** by showing instant heuristic results
- Aligns with **"Privacy-first and on-device AI only"** principle by keeping all processing local
- Improves the confirmation UI workflow by providing progressive feedback instead of blocking

Addresses the technical decision from `tech.md`:
- **"Field-by-Field Parsing Option: Fallback strategy for smaller model limitations"** (tech.md, Technical Decisions)
- **"Accuracy vs Speed: 1B model trades some accuracy for faster inference"** - this approach optimizes both

## Requirements

### Requirement 1: Instant Heuristic Display

**User Story:** As a user, I want to see the transaction form populated instantly with heuristic results when I tap "create draft", so that I can see immediate feedback and start editing fields while AI refinement happens in the background.

#### Acceptance Criteria

1. WHEN the user taps "create draft" THEN the system SHALL navigate to the confirmation screen within 100ms
2. WHEN the confirmation screen loads THEN the system SHALL display all heuristic-parsed fields immediately (amount, merchant, date, account, type)
3. WHEN a heuristic field has high confidence (>=0.75) THEN the system SHALL display it as a normal editable field
4. WHEN a heuristic field has low confidence (<0.75) THEN the system SHALL display it as a grayed/dimmed field with a subtle loading indicator
5. WHEN heuristics fail to extract a field THEN the system SHALL show an empty field with a loading indicator

### Requirement 2: Confidence-Based Field Identification

**User Story:** As the parsing system, I want to identify which fields need AI refinement based on heuristic confidence scores, so that I can build a focused prompt that only addresses problematic fields.

#### Acceptance Criteria

1. WHEN heuristics complete THEN the system SHALL evaluate confidence scores for each field using FieldConfidenceThresholds
2. WHEN all fields meet their confidence thresholds THEN the system SHALL skip AI refinement entirely
3. WHEN one or more fields fall below their confidence thresholds THEN the system SHALL identify those fields for AI refinement
4. WHEN identifying low-confidence fields THEN the system SHALL include: merchant, description, expenseCategory, incomeCategory, tags, and note (NOT amount, date, or account - these are heuristic-only)
5. IF heuristics provide NO data for a typically-present field (e.g., missing merchant) THEN the system SHALL flag that field for AI refinement

### Requirement 3: Focused AI Prompt Construction

**User Story:** As the parsing system, I want to build a minimal prompt that focuses only on low-confidence fields, so that the small 1B model can succeed at the focused task.

#### Acceptance Criteria

1. WHEN building a focused prompt THEN the system SHALL include only:
   - Simplified system instruction (no full schema, just the 2-4 target fields)
   - User input text
   - Heuristic results (what was already found)
   - Which specific fields need refinement
   - Minimal context (recent merchants/categories only if needed)
2. WHEN building a focused prompt THEN the system SHALL omit:
   - Full JSON schema definition
   - Multiple few-shot examples (max 1 example if needed)
   - Unused context data
3. WHEN building a focused prompt THEN the system SHALL keep total prompt length under 1000 characters (down from 1700)
4. WHEN the focused prompt targets 1-2 fields THEN the system SHALL use a template-based approach (e.g., "Given input '[text]', what is the expense category? Options: [list]")
5. WHEN the focused prompt targets 3+ fields THEN the system SHALL use a minimal JSON request with only those fields in the schema

### Requirement 4: Asynchronous AI Execution

**User Story:** As a user, I want the AI refinement to happen in the background without blocking my ability to edit the form, so that I can start making corrections immediately if needed.

#### Acceptance Criteria

1. WHEN the confirmation screen displays THEN the system SHALL launch AI refinement on a background coroutine
2. WHEN AI refinement is in progress THEN the system SHALL allow the user to edit any field at any time
3. WHEN the user manually edits a field that is being refined THEN the system SHALL mark that field as "user-modified" and skip applying AI results to it
4. WHEN AI refinement completes THEN the system SHALL update only non-user-modified fields
5. WHEN AI refinement takes longer than 3 seconds THEN the system SHALL continue displaying loading indicators but not block user interaction

### Requirement 5: Progressive Field Updates

**User Story:** As a user, I want to see low-confidence fields update with AI-refined values as they become available, so that I can see the AI's suggestions and decide whether to accept them.

#### Acceptance Criteria

1. WHEN AI refinement produces results THEN the system SHALL update the corresponding fields in the confirmation form
2. WHEN a field receives an AI-refined value THEN the system SHALL remove the loading indicator and un-dim the field
3. WHEN a field receives an AI-refined value THEN the system SHALL briefly highlight the field (e.g., subtle green flash) to indicate it was updated
4. WHEN all AI refinement completes THEN the system SHALL remove all loading indicators
5. IF AI refinement fails or returns no results THEN the system SHALL remove loading indicators and keep heuristic values (or empty fields)

### Requirement 6: Selective Field Parsing

**User Story:** As the parsing system, I want to only ask the AI to refine specific field types, so that I don't waste inference cycles on fields that heuristics handle well.

#### Acceptance Criteria

1. WHEN determining AI refinement targets THEN the system SHALL NEVER include amount, date, or account (heuristics-only fields)
2. WHEN determining AI refinement targets THEN the system SHALL consider: merchant (if not found or low confidence), description (always), expenseCategory/incomeCategory (always), tags (if complex), note (if needed)
3. WHEN amount was extracted by heuristics with high confidence THEN the system SHALL NOT ask AI to refine it
4. WHEN date was extracted by heuristics THEN the system SHALL NOT ask AI to refine it
5. WHEN account was extracted by heuristics THEN the system SHALL NOT ask AI to refine it

### Requirement 7: Backward Compatibility

**User Story:** As a developer, I want the staged parsing approach to integrate seamlessly with the existing HybridTransactionParser, so that the change is transparent to other parts of the system.

#### Acceptance Criteria

1. WHEN staged parsing is implemented THEN the public API of HybridTransactionParser SHALL remain unchanged
2. WHEN staged parsing produces a final result THEN the system SHALL return a HybridParsingResult with the same structure as before
3. WHEN merging heuristic and AI results THEN the system SHALL use the existing mergeResults() logic
4. WHEN validation is needed THEN the system SHALL use the existing ValidationPipeline
5. WHEN confidence scoring is needed THEN the system SHALL use the existing ConfidenceScorer

### Requirement 8: Error Handling and Fallback

**User Story:** As a user, I want the app to gracefully handle AI failures during focused refinement, so that I can still complete my transaction even if AI fails.

#### Acceptance Criteria

1. WHEN AI refinement fails due to model unavailability THEN the system SHALL keep heuristic results and remove loading indicators
2. WHEN AI refinement fails due to validation errors THEN the system SHALL log the error and keep heuristic results
3. WHEN AI refinement times out (>5s) THEN the system SHALL cancel the operation and keep heuristic results
4. WHEN AI refinement produces invalid JSON THEN the system SHALL fall back to heuristic results
5. WHEN AI refinement produces results that fail validation THEN the system SHALL keep heuristic results and log the validation error

## Non-Functional Requirements

### Code Architecture and Modularity

- **Single Responsibility Principle**: Create a new `StagedParsingOrchestrator` class that handles the three-stage flow, separate from the existing single-call logic
- **Modular Design**: Extract focused prompt building into a `FocusedPromptBuilder` separate from the existing `PromptBuilder`
- **Dependency Management**: Reuse existing components (HeuristicExtractor, GenAiGateway, ValidationPipeline) without modification
- **Clear Interfaces**: Maintain the existing `HybridTransactionParser` interface contract
- **Testability**: Design components to be testable in isolation with mock AI responses

### Performance

- **Stage 1 Latency**: Heuristic extraction and UI display must complete in <100ms
- **Stage 2 Latency**: Focused AI refinement must complete in <2000ms for typical cases (2-3 fields)
- **Total Latency**: End-to-end parsing (heuristics + AI) should improve to <2s median (down from current 2-3s)
- **Prompt Size**: Focused prompts must be <1000 characters (target: 600-800 chars for best performance)
- **Memory**: No significant memory overhead beyond existing parsing pipeline

### Security

- **On-Device Processing**: All AI refinement remains on-device; no cloud calls
- **Data Privacy**: Input text and parsed results never leave the device
- **Secure Context**: Parsing context (recent merchants/accounts) pulled from local encrypted storage only

### Reliability

- **Graceful Degradation**: If AI refinement fails, users can still complete transactions with heuristic results
- **No Blocking**: UI must remain responsive even if AI takes longer than expected
- **User Override**: Users can edit any field at any time, overriding both heuristic and AI results
- **Consistent State**: Form state must remain consistent even if AI updates arrive after user edits

### Usability

- **Instant Feedback**: Users see results immediately instead of waiting 2-3 seconds for a blank screen
- **Progressive Disclosure**: Loading indicators clearly show which fields are being refined
- **Visual Feedback**: Subtle highlighting shows when fields are updated by AI
- **No Disruption**: AI updates don't disrupt user editing or form interaction
