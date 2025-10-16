# Tasks Document

## CLI Module Setup

- [x] 1. Create CLI module structure and build configuration
  - Files: `/cli/build.gradle.kts`, `/cli/src/main/kotlin/com/voiceexpense/eval/`
  - Create new Gradle module for Kotlin JVM (not Android)
  - Configure dependencies on `:app` module's parsing code
  - Set up JAR packaging with fat JAR for all dependencies
  - Configure main class for execution
  - Purpose: Establish CLI module foundation for wrapping parser code
  - _Leverage: `/settings.gradle.kts`, `/app/build.gradle.kts` for patterns_
  - _Requirements: 1_
  - _Prompt: Implement the task for spec ai-parsing-evaluator, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Kotlin/Gradle build engineer | Task: Create new CLI Gradle module at /cli/ that depends on :app module's parsing code, configure as Kotlin JVM application (not Android), set up fat JAR build with main class, following requirement 1 | Restrictions: Do not include Android dependencies, keep module minimal, only depend on necessary :app code | Leverage: Examine /settings.gradle.kts and /app/build.gradle.kts for module patterns | Success: ./gradlew :cli:build succeeds, produces executable JAR, can access :app parsing classes | Instructions: Mark this task as in-progress [-] in tasks.md before starting, mark as complete [x] when done_

## CLI Implementation

- [x] 2. Create JSON data models for CLI I/O
  - File: `/cli/src/main/kotlin/com/voiceexpense/eval/JsonModels.kt`
  - Define input schema: CliInput (utterance, context, model_responses)
  - Define output schemas: CliOutputNeedsAi (prompts_needed), CliOutputComplete (parsed)
  - Use Moshi for JSON serialization with proper annotations
  - Purpose: Establish structured communication between Python and Kotlin CLI
  - _Leverage: Moshi patterns from :app module_
  - _Requirements: 1, 5_
  - _Prompt: Implement the task for spec ai-parsing-evaluator, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Kotlin developer with JSON/Moshi expertise | Task: Create JsonModels.kt with data classes for CLI input (utterance, context, model_responses) and output (needs_ai with prompts, complete with parsed result) using Moshi annotations, following requirements 1 and 5 | Restrictions: Keep models simple and flat, ensure proper JSON field naming with @Json, handle nullability correctly | Leverage: Examine existing Moshi usage in :app for patterns | Success: Models serialize/deserialize correctly, handle all CLI communication scenarios | Instructions: Mark this task as in-progress [-] in tasks.md before starting, mark as complete [x] when done_

- [x] 3. Implement PythonGenAiGateway mock
  - File: `/cli/src/main/kotlin/com/voiceexpense/eval/PythonGenAiGateway.kt`
  - Implement GenAiGateway interface with prompt collection behavior
  - First call: collect prompts for fields needing AI, signal needs AI
  - Second call: return injected responses for those fields
  - Purpose: Mock MediaPipe inference to delegate to Python
  - _Leverage: GenAiGateway interface from :app/ai/parsing/hybrid/GenAiGateway.kt_
  - _Requirements: 6_
  - _Prompt: Implement the task for spec ai-parsing-evaluator, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Kotlin developer familiar with interfaces and mocking | Task: Create PythonGenAiGateway implementing GenAiGateway interface, collect prompts on first pass (signal needs AI), return injected responses on second pass, following requirement 6 | Restrictions: Do not call actual models, must implement GenAiGateway contract exactly, handle field tracking correctly | Leverage: Read GenAiGateway interface from :app/ai/parsing/hybrid/ and MediaPipeGenAiClient for patterns | Success: isAvailable() returns true, structured() collects prompts then returns responses, integrates with parser | Instructions: Mark this task as in-progress [-] in tasks.md before starting, mark as complete [x] when done_

- [x] 4. Create context builder utility
  - File: `/cli/src/main/kotlin/com/voiceexpense/eval/ContextBuilder.kt`
  - Parse ConfigImportSchema JSON into ParsingContext
  - Extract labels from categories/accounts/tags arrays
  - Build ParsingContext with allowedExpenseCategories, allowedAccounts, etc.
  - Purpose: Convert user config file to parser context format
  - _Leverage: ConfigImportSchema from :app/data/config/, ParsingContext from :app/ai/parsing/_
  - _Requirements: 7_
  - _Prompt: Implement the task for spec ai-parsing-evaluator, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Kotlin developer with data transformation experience | Task: Create ContextBuilder utility that reads ConfigImportSchema JSON and builds ParsingContext with extracted labels for categories/accounts/tags, following requirement 7 | Restrictions: Use exact ConfigImportSchema format from app, extract only active labels, handle null/empty gracefully | Leverage: Examine ConfigImportSchema in :app/data/config/ and ParsingContext in :app/ai/parsing/ | Success: Correctly transforms config JSON to ParsingContext, handles all field types | Instructions: Mark this task as in-progress [-] in tasks.md before starting, mark as complete [x] when done_

- [x] 5. Implement CLI main entry point
  - File: `/cli/src/main/kotlin/com/voiceexpense/eval/CliMain.kt`
  - Read JSON from stdin, parse into CliInput
  - Create TransactionParser with PythonGenAiGateway
  - Handle two-phase execution: prompts request vs completion with responses
  - Write JSON output to stdout and exit
  - Purpose: Main CLI executable that wraps parser pipeline
  - _Leverage: TransactionParser, HybridTransactionParser from :app/ai/parsing/_
  - _Requirements: 1, 2, 5, 6_
  - _Prompt: Implement the task for spec ai-parsing-evaluator, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Kotlin developer with CLI and subprocess experience | Task: Implement CliMain.kt entry point that reads JSON stdin, executes TransactionParser with PythonGenAiGateway, returns prompts or final result as JSON stdout, following requirements 1, 2, 5, 6 | Restrictions: Only output JSON to stdout (no logging noise), handle errors with JSON error output and exit code 1, keep logic simple | Leverage: Use existing TransactionParser from :app/ai/parsing/ | Success: CLI reads input, executes parser, returns correct JSON, handles errors gracefully | Instructions: Mark this task as in-progress [-] in tasks.md before starting, mark as complete [x] when done_

## Python Evaluator

- [x] 6. Create Python project structure
  - Files: `/evaluator/evaluate.py`, `/evaluator/models.py`, `/evaluator/requirements.txt`, `/evaluator/README.md`, `/evaluator/.gitignore`
  - Set up basic Python files with minimal structure
  - Create requirements.txt with transformers, torch, bitsandbytes, pandas
  - Add .gitignore for results/ directory
  - Purpose: Establish Python evaluator project foundation
  - _Leverage: Python best practices for simple scripts_
  - _Requirements: 2, 3, 4_
  - _Prompt: Implement the task for spec ai-parsing-evaluator, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Python developer with project setup experience | Task: Create /evaluator/ directory with evaluate.py, models.py, requirements.txt (transformers, torch, bitsandbytes, pandas), README.md, .gitignore (results/), following requirements 2, 3, 4 | Restrictions: Keep structure flat and simple, no complex frameworks, use standard library where possible | Leverage: Standard Python project conventions | Success: Directory structure created, requirements.txt has correct dependencies, .gitignore excludes results/ | Instructions: Mark this task as in-progress [-] in tasks.md before starting, mark as complete [x] when done_

- [x] 7. Implement HuggingFace model inference
  - File: `/evaluator/models.py`
  - Create ModelInference class with model loading (gemma-3-1b-it, gemma-3n-E2B-it)
  - Support 8-bit quantization for memory efficiency
  - Implement generate() method with proper Gemma chat template
  - Return raw text responses for CLI to validate
  - Purpose: Handle AI model loading and inference
  - _Leverage: HuggingFace transformers library, Gemma model documentation_
  - _Requirements: 2_
  - _Prompt: Implement the task for spec ai-parsing-evaluator, first run spec-workflow-guide to get the workflow guide then implement the task: Role: ML engineer with HuggingFace/PyTorch experience | Task: Create ModelInference class in models.py that loads gemma-3-1b-it or gemma-3n-E2B-it with 8-bit quantization, applies proper chat template, generates responses, following requirement 2 | Restrictions: Use temperature=0 for deterministic results, handle model loading errors gracefully, keep interface simple | Leverage: HuggingFace transformers documentation, Gemma model cards | Success: Models load correctly with quantization, generate() returns proper responses, works with both Gemma variants | Instructions: Mark this task as in-progress [-] in tasks.md before starting, mark as complete [x] when done_

- [x] 8. Implement CLI subprocess communication
  - File: `/evaluator/evaluate.py` (partial)
  - Create function to call Kotlin CLI via subprocess with JSON I/O
  - Handle two-phase calls: prompt request, then completion with responses
  - Parse JSON output from CLI, handle errors and timeouts
  - Purpose: Bridge Python and Kotlin CLI for test execution
  - _Leverage: Python subprocess module, json module_
  - _Requirements: 1, 5_
  - _Prompt: Implement the task for spec ai-parsing-evaluator, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Python developer with subprocess and IPC experience | Task: Create CLI communication functions in evaluate.py that call Kotlin JAR via subprocess, send JSON stdin, parse JSON stdout, handle two-phase execution (prompts → responses), following requirements 1 and 5 | Restrictions: Implement 30-second timeout, handle CLI crashes gracefully, log stderr on errors, parse JSON carefully | Leverage: Python subprocess.run() with stdin/stdout pipes, json module | Success: Successfully calls CLI, handles two-phase flow, robust error handling for crashes/timeouts | Instructions: Mark this task as in-progress [-] in tasks.md before starting, mark as complete [x] when done_

- [x] 9. Implement markdown table parsing for test cases
  - File: `/evaluator/evaluate.py` (partial)
  - Parse test_cases.md markdown table into test case objects
  - Extract columns: ID, Input, Amount, Merchant, Type, Category, Tags, Date, Account, Split Overall
  - Handle empty cells and type conversions (string → decimal, date parsing)
  - Purpose: Load test cases from user-friendly markdown format
  - _Leverage: Python re module for markdown parsing, or pandas if simpler_
  - _Requirements: 3, 8_
  - _Prompt: Implement the task for spec ai-parsing-evaluator, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Python developer with text parsing experience | Task: Implement markdown table parser in evaluate.py that reads test_cases.md, extracts test case data (ID, Input, expected fields), handles type conversions, following requirements 3 and 8 | Restrictions: Handle malformed tables gracefully, skip invalid rows with warning, convert types correctly (decimal, date, comma-separated tags) | Leverage: Python re module for table parsing or pandas read_csv with custom sep | Success: Parses test_cases.md correctly, returns list of test case objects, handles edge cases | Instructions: Mark this task as in-progress [-] in tasks.md before starting, mark as complete [x] when done_

- [x] 10. Implement config loading and context building
  - File: `/evaluator/evaluate.py` (partial)
  - Load config.json (ConfigImportSchema format)
  - Extract labels from ExpenseCategory, IncomeCategory, Account, Tag arrays
  - Build context dict with allowedExpenseCategories, allowedAccounts, etc.
  - Purpose: Load user configuration for parser context
  - _Leverage: Python json module, config.json uses ConfigImportSchema format_
  - _Requirements: 7_
  - _Prompt: Implement the task for spec ai-parsing-evaluator, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Python developer with JSON and data processing | Task: Implement config loader in evaluate.py that reads config.json (ConfigImportSchema format), extracts labels from category arrays, builds context dict for CLI, following requirement 7 | Restrictions: Use exact ConfigImportSchema format, extract only active=true labels, handle missing config gracefully with empty context | Leverage: Reference ConfigImportSchema format from design document | Success: Loads config.json correctly, builds proper context dict, handles missing/invalid config | Instructions: Mark this task as in-progress [-] in tasks.md before starting, mark as complete [x] when done_

- [x] 11. Implement test execution orchestration
  - File: `/evaluator/evaluate.py` (partial)
  - Main loop: for each test case, call CLI → run model inference → call CLI again
  - Collect results: actual parsed values, timing, method used
  - Handle errors: CLI crashes, timeouts, model failures
  - Purpose: Orchestrate full evaluation flow for all test cases
  - _Leverage: Functions from previous tasks (CLI comm, model inference, parsing)_
  - _Requirements: 1, 2, 3, 4_
  - _Prompt: Implement the task for spec ai-parsing-evaluator, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Python developer with workflow orchestration experience | Task: Implement main evaluation loop in evaluate.py that processes each test case (CLI call → model inference → CLI completion), collects results, handles errors, following requirements 1, 2, 3, 4 | Restrictions: Continue on errors (mark test as failed), log progress to console, track timing for each test | Leverage: Use CLI communication, model inference, and parsing functions created in previous tasks | Success: Processes all test cases sequentially, collects complete results, robust error handling | Instructions: Mark this task as in-progress [-] in tasks.md before starting, mark as complete [x] when done_

- [x] 12. Implement field comparison and metrics calculation
  - File: `/evaluator/evaluate.py` (partial)
  - Compare actual vs expected for each field (amount, merchant, type, category, etc.)
  - Calculate per-field accuracy percentages
  - Calculate overall accuracy (all fields correct)
  - Track AI usage statistics
  - Purpose: Evaluate parsing accuracy and generate metrics
  - _Leverage: Python comparison operators, basic statistics_
  - _Requirements: 4_
  - _Prompt: Implement the task for spec ai-parsing-evaluator, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Python developer with data analysis experience | Task: Implement result comparison functions in evaluate.py that compare actual vs expected for each field, calculate per-field and overall accuracy, track AI usage stats, following requirement 4 | Restrictions: Handle null/empty values correctly, use fuzzy matching for merchant names if appropriate, calculate percentages accurately | Leverage: Python's standard comparison, basic math for percentages | Success: Correctly identifies field mismatches, calculates accurate metrics, handles edge cases | Instructions: Mark this task as in-progress [-] in tasks.md before starting, mark as complete [x] when done_

- [x] 13. Implement markdown report generation
  - File: `/evaluator/evaluate.py` (partial)
  - Generate results markdown table: test ID, input, overall pass/fail, per-field actual/expected with ✓/✗
  - Generate summary markdown table: total tests, passed/failed, per-field accuracy, timing stats
  - Write to `/evaluator/results/{timestamp}_results.md` and `_summary.md`
  - Purpose: Create human-readable evaluation reports
  - _Leverage: Python string formatting, markdown table syntax_
  - _Requirements: 4, 8_
  - _Prompt: Implement the task for spec ai-parsing-evaluator, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Python developer with report generation experience | Task: Implement markdown report generators in evaluate.py that create results table (per-test details) and summary table (aggregate metrics), write to timestamped files, following requirements 4 and 8 | Restrictions: Use proper markdown table syntax, format numbers cleanly, include ✓/✗ indicators, timestamp filenames | Leverage: Python string formatting, datetime for timestamps | Success: Generates well-formatted markdown tables, files written correctly, reports are readable | Instructions: Mark this task as in-progress [-] in tasks.md before starting, mark as complete [x] when done_

## Documentation

- [x] 14. Write evaluator README documentation
  - File: `/evaluator/README.md`
  - Document setup instructions: build CLI, install Python deps, copy config
  - Document usage: how to create test cases, run evaluation, review results
  - Include examples and troubleshooting tips
  - Note: Instructions for running/testing will be validated later with user
  - Purpose: Enable developers to use the evaluator effectively
  - _Leverage: Standard README format, design document for reference_
  - _Requirements: All_
  - _Prompt: Implement the task for spec ai-parsing-evaluator, first run spec-workflow-guide to get the workflow guide then implement the task: Role: Technical writer with developer documentation experience | Task: Write comprehensive README.md for evaluator covering setup (CLI build, Python deps, config), usage (test cases, running, results), examples, troubleshooting, covering all requirements | Restrictions: Keep instructions clear and concise, include code examples, document commands without running them | Leverage: Reference design document for technical details | Success: README covers all necessary steps, clear for developers to follow, includes practical examples | Instructions: Mark this task as in-progress [-] in tasks.md before starting, mark as complete [x] when done_
