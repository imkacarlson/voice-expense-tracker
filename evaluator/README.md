# AI Parsing Evaluator

This tool wraps the production Kotlin parsing pipeline in a local CLI so you can measure prompt changes against a suite of utterances without rebuilding the Android app. The Python orchestrator drives the CLI, runs fallback AI models, compares outputs against expectations, and generates markdown reports.

## Prerequisites

- JDK 21+ (needed by Gradle to build the CLI jar)
- Python 3.10+ with `pip`
- NVIDIA GPU recommended for 8-bit Gemma models (falls back to CPU if necessary)
- Access to a `config.json` exported from the mobile app (ConfigImportSchema format)

## Setup

1. **Build the Kotlin CLI jar**
   ```bash
   ./gradlew :cli:build
   ```
   The artifact is written to `cli/build/libs/`.

2. **Install Python dependencies**
   ```bash
   cd evaluator
   python -m venv venv
   venv/Scripts/activate  # Windows
   # OR: source venv/bin/activate  # Linux/Mac
   pip install -r requirements.txt
   ```

3. **Authenticate with HuggingFace (for gated models like Gemma)**
   - Visit https://huggingface.co/google/gemma-3-1b-it and click "Agree and access repository" to accept the license
   - Get your access token from https://huggingface.co/settings/tokens (create a new token with "Read" permissions if needed)
   - Log in via the CLI:
     ```bash
     huggingface-cli login
     ```
     Paste your token when prompted
   - The model will be downloaded automatically on first run (~2GB) and cached locally for future runs

4. **Provide configuration**
   - Copy your exported app configuration to `evaluator/config.json`.
   - The file must follow the ConfigImportSchema used by the Android app (categories, accounts, tags, defaults).

5. **Create or edit test cases**
   - Open `evaluator/test_cases.md`.
   - Each row in the markdown table should include the utterance plus expected values (amount, merchant, type, category, tags, date, account, split overall).
   - Leave cells blank for fields that are not relevant to a scenario.

## Running an evaluation

```bash
python evaluate.py \
  --model google/gemma-3-1b-it \
  --test test-001 \
  --java "C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
```

**Note:** On Windows, if your Java path contains spaces, use the `--java` flag to specify the full path to Java 21+ (required by the CLI jar).

The workflow performs two phases per test case:
1. Call the Kotlin CLI to determine whether AI assistance (prompt generation) is required.
2. If prompts are returned, run the Gemma model to satisfy them, then call the CLI again to complete refinement.

### Outputs

- Detailed per-test report: `evaluator/results/<timestamp>_results.md`
- Summary metrics: `evaluator/results/<timestamp>_summary.md`

The summary outlines overall accuracy, per-field accuracy, AI usage statistics, and average runtime. The detailed report includes each test case, the end-to-end status, AI method used, and field-by-field comparisons with ✓/✗ markers.

## Troubleshooting

- **`java` or JDK not found** – ensure JDK 21 is installed and `JAVA_HOME` is exported before building the CLI.
- **`cli/build/libs` does not contain a jar** – run `./gradlew :cli:build` and verify there were no compilation errors.
- **`bitsandbytes` import errors** – the package requires a compatible GPU/driver. Install the CPU-only alternative or run with a smaller model.
- **CLI times out** – check that the jar path is correct and that the Kotlin CLI can locate `:app` dependencies. Review stderr captured in the markdown report for details.
- **Invalid config/test cases** – the evaluator logs parsing issues to stderr while building contexts or reading the markdown table; fix the malformed row or JSON and rerun.

## Next steps

- Add or update test cases whenever new parsing behaviours are introduced.
- Iterate on prompts in the Kotlin code, rebuild the CLI, and rerun evaluations to compare summary metrics.
