> Archived: This document describes a feature removed by the text-first-input-refactor. The app no longer includes voice correction; manual form editing is the only correction method. Kept here for historical reference.

# Voice Correction Loop V1

Components
- CorrectionIntentParser: Utterance → intent mapping (amount, type, merchant, tags, date, confirm/cancel).
- PromptRenderer: Concise prompts for summary, missing fields, and clarifications.
- TtsEngine: Interruptible TTS wrapper (stubbed; replace with Android TTS).
- VoiceCorrectionController: Orchestrates speak → listen → parse → apply → validate; emits prompts and events.
- ConfirmationViewModel: Bridges controller to UI, persists confirm → queued via TransactionRepository.

Key Flows
- Start: ViewModel.setDraft() starts the controller and wires updates.
- Correction: onTranscript() → parse intent → update transaction → summary prompt.
- Ambiguity: validator or heuristics → clarify prompt.
- Confirm/Cancel: controller emits events → VM persists or clears.
- Timeout: 8s silence → reprompt once → 8s → end session without saving.

Testing
- Unit tests: CorrectionIntentParserTest, PromptRendererTest.
- Integration: VoiceCorrectionControllerTest for correction → confirm path.

Developer Notes
- Enable local debug logs via SettingsKeys.DEBUG_LOGS (SharedPreferences). Activity passes the flag to controller.
- Replace TtsEngine with Android TextToSpeech; ensure stop() interrupts.
- For real ASR, feed live transcripts into ViewModel.applyCorrection().
