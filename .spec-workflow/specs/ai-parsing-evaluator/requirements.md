# Requirements Document

## Introduction

The AI Parsing Evaluator is a development tool that enables rapid iteration and optimization of the expense transaction parsing system. Currently, testing AI prompt changes requires rebuilding the Android app, deploying to device, and manually checking logs - a slow and cumbersome process taking 5-10 minutes per iteration. This tool will provide a fast, automated evaluation framework that runs the exact same Kotlin parsing code with HuggingFace models on PC, enabling developers to test prompt changes, heuristics adjustments, and model variations in seconds rather than minutes.

The evaluator will execute the complete 3-stage hybrid parsing pipeline (heuristics extraction → confidence evaluation → focused AI refinement) using the actual production Kotlin code, ensuring perfect fidelity with on-device behavior while dramatically accelerating the development feedback loop.

## Alignment with Product Vision

This feature directly supports the product goals outlined in product.md by:

1. **Improving parsing accuracy**: Faster iteration enables more experimentation with prompts and heuristics to exceed the >90% accuracy target
2. **Optimizing on-device AI performance**: Ability to quickly test different models (gemma-3-1b-it vs gemma-3n-E2B-it) and prompt strategies for the 1B parameter constraint
3. **Maintaining privacy-first architecture**: Evaluator runs locally on developer PC with no cloud dependencies, consistent with on-device AI principles
4. **Accelerating development velocity**: Reduces prompt optimization cycle time from 5-10 minutes to under 30 seconds, enabling rapid improvements

The technical approach aligns with tech.md's hybrid AI strategy and field-by-field parsing fallback mechanisms.

## Requirements

### Requirement 1: Kotlin CLI Wrapper

**User Story:** As a developer, I want to execute the production Kotlin parsing code from Python, so that I have zero code duplication and perfect sync with the Android app.

#### Acceptance Criteria

1. WHEN a new Kotlin CLI module is created THEN it SHALL compile as a standalone JVM application with dependencies on the app's parsing code
2. WHEN the CLI receives JSON input via stdin with `{"utterance": "...", "context": {...}}` THEN it SHALL execute the TransactionParser with the exact same HybridTransactionParser, HeuristicExtractor, and StagedParsingOrchestrator used in production
3. IF the parsing pipeline determines that AI refinement is needed THEN the CLI SHALL return JSON with `{"status": "needs_ai", "heuristic_results": {...}, "prompts_needed": [{field, prompt}]}`
4. WHEN the CLI receives model responses via `{"utterance": "...", "context": {...}, "model_responses": {field: response}}` THEN it SHALL complete Stage 2 refinement and return the final parsed transaction
5. WHEN parsing completes THEN the CLI SHALL output JSON with `{"parsed": {all fields}, "method": "AI"|"HEURISTIC", "confidence": 0.0-1.0, "stats": {timing}}`

### Requirement 2: Python Model Inference

**User Story:** As a developer, I want to run inference using HuggingFace Gemma models, so that I can test the exact models that will be deployed on-device without manual conversion.

#### Acceptance Criteria

1. WHEN the evaluator starts THEN it SHALL load models using `transformers` library with options for `google/gemma-3-1b-it` and `google/gemma-3n-E2B-it`
2. WHEN loading models THEN it SHALL support 8-bit quantization via `bitsandbytes` to reduce memory footprint
3. WHEN the Kotlin CLI returns prompts needing AI THEN the evaluator SHALL run inference through the loaded HuggingFace model
4. WHEN generating responses THEN it SHALL apply proper chat templates and tokenization for Gemma models
5. IF model inference fails or times out THEN the evaluator SHALL record the error and mark the test case as failed

### Requirement 3: Test Case Management

**User Story:** As a developer, I want to define test cases with expected outputs, so that I can automatically evaluate parsing accuracy across diverse transaction types.

#### Acceptance Criteria

1. WHEN test cases are defined THEN they SHALL be stored in JSON format at `/evaluator/test_cases.json`
2. WHEN a test case is defined THEN it SHALL include `{"input": "utterance", "context": {optional}, "expected": {all transaction fields}}`
3. WHEN the evaluator runs THEN it SHALL process all test cases sequentially and collect results
4. WHEN comparing results THEN it SHALL check each field (amountUsd, merchant, description, type, category, tags, date, account, splitOverall) independently
5. IF a field value doesn't match expected THEN it SHALL record the specific field mismatch with actual vs expected values

### Requirement 4: Evaluation Metrics and Reporting

**User Story:** As a developer, I want detailed accuracy metrics and timing data, so that I can measure the impact of prompt and heuristics changes.

#### Acceptance Criteria

1. WHEN evaluation completes THEN it SHALL report overall accuracy (all fields correct) as a percentage
2. WHEN evaluation completes THEN it SHALL report per-field accuracy (amountUsd, merchant, type, category, etc.) as percentages
3. WHEN evaluation completes THEN it SHALL report average parsing time and time breakdown (Stage 0, Stage 1, Stage 2)
4. WHEN evaluation completes THEN it SHALL report AI usage statistics (% of transactions needing AI, which fields most often)
5. WHEN results are generated THEN they SHALL be saved to `/evaluator/results/{timestamp}_report.json` and displayed in console as a formatted table

### Requirement 5: CLI-Python Communication Protocol

**User Story:** As a developer, I want a simple subprocess-based communication protocol, so that integration is straightforward without JVM bridges or complex IPC.

#### Acceptance Criteria

1. WHEN Python calls the Kotlin CLI THEN it SHALL use `subprocess.run()` with JSON via stdin/stdout
2. WHEN the CLI processes input THEN it SHALL return valid JSON on stdout with no logging noise
3. IF the CLI encounters an error THEN it SHALL return JSON with `{"error": "message"}` and exit code 1
4. WHEN Python receives CLI output THEN it SHALL parse JSON and handle malformed responses gracefully
5. IF the CLI process times out (>30 seconds) THEN Python SHALL kill the process and mark the test case as timed out

### Requirement 6: Mock GenAiGateway Implementation

**User Story:** As a developer, I want the Kotlin CLI to mock MediaPipe inference, so that it can delegate model calls to Python while reusing all other parsing logic.

#### Acceptance Criteria

1. WHEN the CLI is initialized THEN it SHALL use a PythonGenAiGateway implementation instead of MediaPipeGenAiClient
2. WHEN PythonGenAiGateway.structured(prompt) is called THEN it SHALL collect the prompt and return a signal that AI is needed
3. WHEN AI responses are provided via CLI input THEN PythonGenAiGateway SHALL return the corresponding response for each field
4. WHEN no AI is needed (heuristics-only path) THEN PythonGenAiGateway SHALL not be invoked
5. WHEN multiple fields need refinement THEN PythonGenAiGateway SHALL track prompts for each field independently

### Requirement 7: Configuration and Context Support

**User Story:** As a developer, I want to provide parsing context (recent merchants, categories, accounts), so that tests reflect realistic parsing conditions.

#### Acceptance Criteria

1. WHEN a test case includes context THEN it SHALL be passed to the Kotlin parser as ParsingContext
2. WHEN context is not provided THEN the parser SHALL use default empty context
3. WHEN confidence thresholds need adjustment THEN they SHALL be configurable via CLI parameters or test case context
4. WHEN testing different configurations THEN each test case SHALL support overriding default settings (thresholds, categories, accounts)
5. IF context references recent merchants/categories THEN the parser SHALL use them for heuristic scoring and AI prompts

#### Configuration File Format

The evaluator SHALL accept the **exact same** configuration JSON file format used by the Android app (`ConfigImportSchema`):

```json
{
  "ExpenseCategory": [
    { "label": "Dining", "position": 0, "active": true },
    { "label": "Groceries", "position": 1, "active": true }
  ],
  "IncomeCategory": [
    { "label": "Salary", "position": 0, "active": true }
  ],
  "TransferCategory": [
    { "label": "Savings", "position": 0, "active": true }
  ],
  "Account": [
    { "label": "Credit Card A", "position": 0, "active": true },
    { "label": "Checking Account", "position": 1, "active": true }
  ],
  "Tag": [
    { "label": "Splitwise", "position": 0, "active": true },
    { "label": "Auto-Paid", "position": 1, "active": true }
  ],
  "defaults": {
    "defaultExpenseCategory": "Dining",
    "defaultIncomeCategory": "Salary",
    "defaultTransferCategory": "Savings",
    "defaultAccount": "Credit Card A"
  }
}
```

**Usage:** Developer copies their existing config file from their phone to `/evaluator/config.json` - no conversion or modification needed. The Python evaluator extracts the labels and builds `ParsingContext` with `allowedExpenseCategories`, `allowedAccounts`, `allowedTags`, etc., which gets passed to the Kotlin CLI exactly as the Android app would.

### Requirement 8: Human-Readable Data Formats and Workflow

**User Story:** As a developer, I want simple, human-readable markdown formats for test cases and results, so that I can easily create tests, review results, and track improvements over time in a visually clear way.

#### Acceptance Criteria

1. WHEN defining test cases THEN they SHALL be stored in markdown table format at `/evaluator/test_cases.md` with all expected transaction fields
2. WHEN evaluation completes THEN results SHALL be exported to markdown table at `/evaluator/results/{timestamp}_results.md` with actual vs expected comparisons for all fields
3. WHEN viewing results THEN mismatched fields SHALL be easily identifiable with ✓ (match) and ✗ (mismatch) indicators
4. WHEN a summary is needed THEN the tool SHALL generate `/evaluator/results/{timestamp}_summary.md` with aggregate metrics in markdown table format
5. IF test cases need editing THEN the developer SHALL be able to edit the markdown table in any text editor with clear column alignment

#### Example Test Cases Format (`test_cases.md`)

| ID | Input | Amount | Merchant | Type | Category | Tags | Date | Account | Split Overall | Notes |
|---|---|---|---|---|---|---|---|---|---|---|
| tc001 | Coffee shop five dollars latte | 5.00 | Coffee Shop | Expense | Dining | | 2025-10-16 | | | Simple single amount |
| tc002 | Dinner at restaurant thirty charged my share twenty | 20.00 | Restaurant A | Expense | Dining | Splitwise | 2025-10-16 | | 30.00 | Split expense with overall |
| tc003 | Supermarket one eighty seven groceries credit card | 1.87 | Supermarket | Expense | Groceries | | 2025-10-16 | Credit Card A | | Amount with account |
| tc004 | Streaming service seven auto-paid subscription | 7.00 | Streaming Service | Expense | Personal | Auto-Paid, Subscription | 2025-10-16 | | | Multiple tags |
| tc005 | Income paycheck two thousand | 2000.00 | Employer | Income | Salary | | 2025-10-16 | | | Income transaction |

#### Example Results Format (`{timestamp}_results.md`)

| Test ID | Input | Overall | Amount |  | Merchant |  | Type |  | Category |  | Time (ms) | Method |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| | | Pass/Fail | Actual / Expected | Match | Actual / Expected | Match | Actual / Expected | Match | Actual / Expected | Match | | |
| tc001 | Coffee shop five dollars latte | ✓ PASS | 5.00 / 5.00 | ✓ | Coffee Shop / Coffee Shop | ✓ | Expense / Expense | ✓ | Dining / Dining | ✓ | 2341 | AI |
| tc002 | Dinner at restaurant thirty... | ✓ PASS | 20.00 / 20.00 | ✓ | Restaurant A / Restaurant A | ✓ | Expense / Expense | ✓ | Dining / Dining | ✓ | 3102 | AI |
| tc003 | Supermarket one eighty seven... | ✗ FAIL | 1.87 / 1.87 | ✓ | Supermarket / Supermarket | ✓ | Expense / Expense | ✓ | Food / Groceries | ✗ | 2876 | AI |
| tc004 | Streaming service seven... | ✓ PASS | 7.00 / 7.00 | ✓ | Streaming Service / Streaming Service | ✓ | Expense / Expense | ✓ | Personal / Personal | ✓ | 1205 | HEURISTIC |
| tc005 | Income paycheck two thousand | ✓ PASS | 2000.00 / 2000.00 | ✓ | Employer / Employer | ✓ | Income / Income | ✓ | Salary / Salary | ✓ | 2654 | AI |

#### Example Summary Format (`{timestamp}_summary.md`)

**Evaluation Summary**

| Metric | Value |
|---|---|
| Total Tests | 5 |
| Passed | 4 |
| Failed | 1 |
| Overall Accuracy | 80.0% |
| **Per-Field Accuracy** | |
| Amount Accuracy | 100.0% |
| Merchant Accuracy | 100.0% |
| Type Accuracy | 100.0% |
| Category Accuracy | 80.0% |
| Tags Accuracy | 100.0% |
| Date Accuracy | 100.0% |
| Account Accuracy | 100.0% |
| **Performance** | |
| Avg Parsing Time | 2435.6 ms |
| AI Usage | 80.0% |
| Heuristic Only | 20.0% |
| **Configuration** | |
| Model Used | gemma-3-1b-it |
| Timestamp | 2025-10-16T14:30:45Z |

#### Developer Workflow Example

**Creating test cases:**
1. Open `/evaluator/test_cases.md` in any text editor or IDE
2. Add new row to the markdown table with the utterance and expected field values
3. Save file (markdown tables auto-format in most editors)
4. Run: `python evaluate.py`

**Reviewing results:**
1. Open `/evaluator/results/2025-10-16_143045_results.md` in any markdown viewer
2. Scan the "Overall" column for ✗ FAIL markers
3. Look at the "Match" columns to see which specific fields failed (✗ indicators)
4. Compare "Actual / Expected" values side-by-side for failed fields
5. Make prompt adjustments in Kotlin code
6. Re-run: `python evaluate.py`

**Tracking improvements:**
1. Open `/evaluator/results/2025-10-16_143045_summary.md` (baseline)
2. Make changes to prompts/heuristics
3. Run evaluation again to generate `/evaluator/results/2025-10-16_150230_summary.md`
4. Compare summary tables: did overall_accuracy improve? Which per-field accuracies changed?
5. Keep summary files in git to track improvements over time

## Non-Functional Requirements

### Code Architecture and Modularity

- **Single Responsibility Principle**: CLI module handles I/O and wiring; Python handles orchestration and model inference; Kotlin parsing code remains unchanged
- **Modular Design**: CLI is an independent Gradle module with minimal dependencies; Python evaluator is standalone in `/evaluator/`
- **Dependency Management**: CLI depends on `:app` module's parsing code; Python has isolated dependencies (transformers, torch)
- **Clear Interfaces**: GenAiGateway provides clean abstraction for swapping MediaPipe with Python-backed inference

### Performance

- **CLI startup time**: <2 seconds for JVM initialization and Kotlin compilation
- **Model loading time**: <10 seconds for loading gemma-3-1b-it with 8-bit quantization
- **Single test case execution**: <5 seconds end-to-end (including subprocess overhead and model inference)
- **Full evaluation suite**: <2 minutes for 20 test cases

### Reliability

- **Error isolation**: CLI crashes or hangs SHALL NOT crash the Python evaluator; subprocess management with timeouts
- **Graceful degradation**: Malformed JSON from CLI SHALL be logged and marked as test failure, not crash evaluator
- **Deterministic results**: Same test cases SHALL produce identical results across runs (given same model and configuration)
- **Resource cleanup**: Python evaluator SHALL properly close subprocess handles; Kotlin CLI SHALL exit cleanly

### Usability

- **Simple setup**: Developer can install dependencies (`pip install -r requirements.txt`) and build CLI (`./gradlew :cli:build`) in <5 minutes
- **Clear output**: Evaluation results displayed as formatted table with color coding (green = passing, red = failing)
- **Incremental testing**: Developer can run single test case for quick debugging: `python evaluate.py --test "specific utterance"`
- **Model switching**: Easy command-line flag to switch between gemma-3-1b-it and gemma-3n-E2B-it: `python evaluate.py --model gemma-3-1b-it`

### Maintainability

- **Zero code duplication**: CLI wrapper is <150 lines; all parsing logic reused from `:app` module
- **Documentation**: README.md in `/evaluator/` with setup instructions, usage examples, and test case format
- **Version control**: Test cases and results tracked in git; results/ directory gitignored to avoid clutter
- **Extensibility**: Easy to add new test cases; easy to add new metrics (e.g., confidence score accuracy)
