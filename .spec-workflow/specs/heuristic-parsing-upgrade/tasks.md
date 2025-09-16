# Tasks Document

- [x] 1. Implement heuristic extractor and draft models
  - File: app/src/main/java/com/voiceexpense/ai/parsing/heuristic/HeuristicExtractor.kt
  - Extract date, amount, merchant, Splitwise totals, account, tags with confidence scoring; emit `HeuristicDraft`.
  - Purpose: Provide deterministic parsing so hybrid pipeline can skip LLM when confident.
  - _Leverage: app/src/main/java/com/voiceexpense/ai/parsing/ParsingContext.kt, existing regex helpers in HybridTransactionParser._
  - _Requirements: 1.1, 1.2, 1.3_
  - _Prompt: Role: Kotlin engineer focused on NLP heuristics | Task: Implement deterministic extraction of key transaction fields, producing a `HeuristicDraft` with confidence metrics per requirements 1.1–1.3, reusing current parsing context data. | Restrictions: Do not invoke MediaPipe or LLM code; keep heuristics pure Kotlin utilities. | _Leverage: ParsingContext, StructuredOutputValidator.sanitizeAmounts._ | _Requirements: 1.1, 1.2, 1.3_ | Success: Heuristic extractor returns populated fields on sample utterances with confidence ≥ thresholds, unit tests cover Splitwise and non-split cases.

- [x] 2. Update hybrid parser orchestration and merger
  - File: app/src/main/java/com/voiceexpense/ai/parsing/hybrid/HybridTransactionParser.kt
  - Integrate heuristics first, decide on LLM invocation, merge heuristic and AI results, update confidence and monitoring.
  - Purpose: Ensure pipeline invokes LLM only when necessary and preserves heuristic values.
  - _Leverage: HeuristicExtractor, ProcessingMonitor, ConfidenceScorer._
  - _Requirements: 1.3, 2.1, 2.3_
  - _Prompt: Role: Android AI pipeline engineer | Task: Refactor HybridTransactionParser to use heuristic drafts before prompt construction, call LLM conditionally, merge outputs, and record provenance, satisfying requirements 1.3, 2.1, 2.3. | Restrictions: Maintain existing logging and error handling semantics; avoid blocking the UI thread. | _Leverage: new heuristic package, existing validation/monitoring utilities._ | Success: Parser skips LLM when heuristics complete required fields, hybrids handle validation failures gracefully, integration tests verify both paths.

- [x] 3. Revise prompt builder and examples
  - File: app/src/main/java/com/voiceexpense/ai/parsing/hybrid/PromptBuilder.kt
  - Embed known fields JSON, condense context, swap few-shot examples with sheet-aligned Input→Output pairs.
  - Purpose: Reduce tokens and align LLM output with actual Google Sheet mapping.
  - _Leverage: prompts/sample-utterances.md, SchemaTemplates._
  - _Requirements: 2.1, 2.2_
  - _Prompt: Role: Prompt engineer for on-device LLMs | Task: Update PromptBuilder to accept heuristic known fields, build compact prompt with real sample mappings, and keep total tokens < 800 per requirements 2.1, 2.2. | Restrictions: Keep output format JSON-only; ensure Splitwise rule included without redundancy. | _Leverage: SchemaTemplates, TransactionPrompts._ | Success: Unit tests confirm prompt includes known-field JSON and revised examples; manual token count stays under target.

- [x] 4. Extend validation/normalization for heuristic merge
  - File: app/src/main/java/com/voiceexpense/ai/parsing/hybrid/ValidationPipeline.kt
  - Allow merging heuristic defaults with AI JSON, ensure amount≤overall rule enforced, normalize options.
  - Purpose: Guarantee combined outputs remain consistent and safe for UI/Sheets.
  - _Leverage: StructuredOutputValidator, HeuristicDraftMerger._
  - _Requirements: 2.2, 2.3_
  - _Prompt: Role: Kotlin data validation engineer | Task: Update validation pipeline to reconcile heuristic values with AI output, enforce business constraints, and surface errors per requirements 2.2, 2.3. | Restrictions: Maintain existing logging channel names; no heavy dependencies. | _Leverage: mas existing validator utilities._ | Success: Unit tests demonstrate validation passes when heuristics fill fields and rejects inconsistent AI data with clear errors.

- [x] 5. Add UI confidence indicators and integration tests
  - File: app/src/main/java/com/voiceexpense/ui/confirmation/ConfirmationViewModel.kt
  - Surface low-confidence flags based on heuristic coverage; add tests for heuristic-only, hybrid, AI failure flows.
  - Purpose: Inform users when manual review is needed without slowing flow.
  - _Leverage: existing confidence scoring, ConfirmationViewModelTest._
  - _Requirements: 1.3, 2.3_
  - _Prompt: Role: Android UI engineer | Task: Update confirmation view model to expose confidence indicators from new heuristic pipeline and extend tests to cover heuristic-only and fallback flows. | Restrictions: Preserve existing state exposure patterns; avoid UI thread blocking. | _Leverage: HybridTransactionParser integration tests, existing test fixtures._ | Success: UI reflects confidence states; tests verify state outputs for all parsing outcomes.

- [x] 6. Testing suite updates
  - File: app/src/test/java/com/voiceexpense/ai/parsing/heuristic/HeuristicExtractorTest.kt (new), app/src/test/java/com/voiceexpense/ai/parsing/hybrid/HybridTransactionParserTest.kt, app/src/androidTest/java/com/voiceexpense/ai/parsing/hybrid/HybridPerformanceTest.kt
  - Add coverage for heuristic extractor, hybrid orchestrator, prompt builder token counts, and ensure latency improvement.
  - Purpose: Validate new heuristics and ensure performance targets on Pixel 7a.
  - _Leverage: existing fixtures in HybridTransactionParserTest, prompts/sample-utterances.md._
  - _Requirements: 1.1, 1.2, 2.1_
  - _Prompt: Role: Kotlin test engineer | Task: Create and update tests for heuristic extraction, hybrid orchestration, and prompt performance per requirements 1.1, 1.2, 2.1, using existing fixtures. | Restrictions: Tests must run offline and deterministically; no dependency on actual LLM. | _Leverage: Mock GenAiGateway, ValidationPipeline, prompts fixtures._ | Success: New tests pass, ensuring heuristics accuracy and latency assertions show ≥20% improvement vs baseline.
