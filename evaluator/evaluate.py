"""Evaluation orchestration utilities."""

from __future__ import annotations

import argparse
import json
import shlex
import subprocess
import sys
from dataclasses import dataclass
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Collection, List, Mapping, MutableMapping, Optional

from models import ModelInference, SUPPORTED_MODELS

CLI_TIMEOUT_SECONDS = 30
PROJECT_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_JAVA_CMD = ("java",)
TEST_CASES_FILE = Path(__file__).resolve().with_name("test_cases.md")
CONFIG_FILE = Path(__file__).resolve().with_name("config.json")
RESULTS_DIR = Path(__file__).resolve().with_name("results")
DECIMAL_TOLERANCE = Decimal("0.01")
FIELD_ORDER = [
    "amountUsd",
    "merchant",
    "description",
    "type",
    "category",
    "tags",
    "userLocalDate",
    "account",
    "splitOverallChargedUsd",
]
FIELD_LABELS = {
    "amountUsd": "Amount",
    "merchant": "Merchant",
    "description": "Description",
    "type": "Type",
    "category": "Category",
    "tags": "Tags",
    "userLocalDate": "Date",
    "account": "Account",
    "splitOverallChargedUsd": "Split Overall",
}


@dataclass
class TestCase:
    """Structured representation of a single evaluation test case."""

    identifier: str
    utterance: str
    expected_amount: Optional[Decimal]
    expected_merchant: Optional[str]
    expected_description: Optional[str]
    expected_type: Optional[str]
    expected_category: Optional[str]
    expected_tags: List[str]
    expected_date: Optional[date]
    expected_account: Optional[str]
    expected_split_overall: Optional[Decimal]


@dataclass
class PromptExchange:
    """Tracks a single prompt/response interaction with the AI model."""

    field: str
    prompt: str
    response: Optional[str] = None


@dataclass
class TestExecutionResult:
    """Outcome of running a single test case through the evaluator."""

    case: TestCase
    status: str
    parsed: Optional[MutableMapping[str, Any]]
    method: Optional[str]
    prompts: List[PromptExchange]
    stats: MutableMapping[str, Any]
    heuristic_results: Optional[MutableMapping[str, Any]]
    heuristic_stats: Optional[MutableMapping[str, Any]]
    errors: List[str]
    ai_calls: int


@dataclass
class FieldComparison:
    """Comparison result for a single parsed field."""

    field: str
    expected: Any
    actual: Any
    match: bool


@dataclass
class TestComparison:
    """Comparison summary for a test case."""

    execution: TestExecutionResult
    field_results: List[FieldComparison]
    overall_match: bool


@dataclass
class EvaluationMetrics:
    """Aggregate accuracy metrics across all test cases."""

    total_tests: int
    passed_tests: int
    per_field_accuracy: MutableMapping[str, Optional[float]]
    overall_accuracy: Optional[float]
    ai_usage_count: int
    total_ai_calls: int
    field_samples: MutableMapping[str, int]
    average_total_ms: Optional[float]
    average_stage0_ms: Optional[float]
    average_stage1_ms: Optional[float]


@dataclass
class CliResponse:
    """Represents the decoded stdout payload from a CLI invocation."""

    data: MutableMapping[str, Any]
    stdout: str
    stderr: str
    returncode: int

    @property
    def status(self) -> str:
        return str(self.data.get("status", "unknown"))


class CliInvocationError(RuntimeError):
    """Raised when the CLI process could not be executed successfully."""

    def __init__(self, message: str, *, stdout: str = "", stderr: str = "") -> None:
        super().__init__(message)
        self.stdout = stdout
        self.stderr = stderr


def find_cli_jar() -> Path:
    """Return the most recent CLI jar built by Gradle."""
    libs_dir = PROJECT_ROOT / "cli" / "build" / "libs"
    if not libs_dir.exists():  # pragma: no cover - user must build jar first
        raise CliInvocationError(
            f"CLI jar directory not found: {libs_dir}. Run './gradlew :cli:build' first."
        )
    jars = sorted(libs_dir.glob("*.jar"), key=lambda p: p.stat().st_mtime, reverse=True)
    if not jars:  # pragma: no cover - jar missing
        raise CliInvocationError(
            f"No CLI jar found in {libs_dir}. Run './gradlew :cli:build' first."
        )
    return jars[0]


def load_config_context(path: Optional[Path] = None) -> MutableMapping[str, Any]:
    """Load ConfigImportSchema JSON and build the CLI context dict."""

    source = path or CONFIG_FILE
    if not source.exists():
        return {}

    try:
        raw = json.loads(source.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        print(f"Failed to parse config.json: {exc}", file=sys.stderr)
        return {}

    context: MutableMapping[str, Any] = {
        "allowedExpenseCategories": extract_active_labels(raw.get("ExpenseCategory")),
        "allowedIncomeCategories": extract_active_labels(raw.get("IncomeCategory")),
        "allowedAccounts": extract_active_labels(raw.get("Account")),
        "allowedTags": extract_active_labels(raw.get("Tag")),
    }
    if context["allowedExpenseCategories"] and not raw.get("recentCategories"):
        context["recentCategories"] = context["allowedExpenseCategories"][:5]
    if context["allowedAccounts"]:
        context["knownAccounts"] = context["allowedAccounts"]
    return context


def extract_active_labels(value: Any) -> List[str]:
    if not isinstance(value, list):
        return []
    labels: List[str] = []
    for entry in value:
        if not isinstance(entry, Mapping):
            continue
        if entry.get("active", True) is False:
            continue
        label = entry.get("label")
        if isinstance(label, str) and label.strip():
            labels.append(label.strip())
    return labels


def load_test_cases(path: Optional[Path] = None) -> List[TestCase]:
    """Parse the markdown test case table into structured objects."""

    source = path or TEST_CASES_FILE
    if not source.exists():
        raise FileNotFoundError(f"test cases file not found: {source}")

    lines = source.read_text(encoding="utf-8").splitlines()
    header: Optional[List[str]] = None
    cases: List[TestCase] = []
    for idx, raw_line in enumerate(lines):
        line = raw_line.strip()
        if not line.startswith("|"):
            continue
        cells = [cell.strip() for cell in line.strip("|").split("|")]
        if header is None:
            header = [normalize_header(cell) for cell in cells]
            continue
        if is_divider_row(cells):
            continue
        if len(cells) < len(header):
            print(
                f"Skipping malformed row {idx + 1}: expected {len(header)} cells, got {len(cells)}",
                file=sys.stderr,
            )
            continue
        row = {header[i]: cells[i] for i in range(len(header))}
        test_case = build_test_case(row, line_number=idx + 1)
        if test_case:
            cases.append(test_case)
    return cases


def build_test_case(row: Mapping[str, str], *, line_number: int) -> Optional[TestCase]:
    identifier = row.get("id", "").strip()
    utterance = row.get("input", "").strip()
    if not identifier or not utterance:
        print(
            f"Skipping row {line_number}: missing required 'ID' or 'Input' column",
            file=sys.stderr,
        )
        return None

    return TestCase(
        identifier=identifier,
        utterance=utterance,
        expected_amount=parse_decimal(row.get("amount")),
        expected_merchant=normalize_string(row.get("merchant")),
        expected_description=normalize_string(row.get("description")),
        expected_type=normalize_string(row.get("type")),
        expected_category=normalize_string(row.get("category")),
        expected_tags=parse_tags(row.get("tags")),
        expected_date=parse_date(row.get("date")),
        expected_account=normalize_string(row.get("account")),
        expected_split_overall=parse_decimal(row.get("split_overall")),
    )


def parse_decimal(value: Optional[str]) -> Optional[Decimal]:
    if not value:
        return None
    text = value.strip()
    if not text:
        return None
    try:
        return Decimal(text)
    except Exception:
        print(f"Invalid decimal value '{value}'", file=sys.stderr)
        return None


def parse_date(value: Optional[str]) -> Optional[date]:
    if not value:
        return None
    text = value.strip()
    if not text:
        return None
    try:
        return date.fromisoformat(text)
    except Exception:
        print(f"Invalid date value '{value}'", file=sys.stderr)
        return None


def parse_tags(value: Optional[str]) -> List[str]:
    if not value:
        return []
    tags = [tag.strip() for tag in value.split(",") if tag.strip()]
    return tags


def normalize_string(value: Optional[Any]) -> Optional[str]:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def normalize_header(cell: str) -> str:
    return cell.strip().lower().replace(" ", "_")


def is_divider_row(cells: List[str]) -> bool:
    return all(set(cell) <= {"-", ":"} for cell in cells)


def build_cli_payload(
    utterance: str,
    context: Optional[Mapping[str, Any]] = None,
    model_responses: Optional[Mapping[str, str]] = None,
) -> MutableMapping[str, Any]:
    payload: MutableMapping[str, Any] = {
        "utterance": utterance,
        "context": dict(context or {}),
    }
    if model_responses:
        payload["model_responses"] = dict(model_responses)
    return payload


def run_cli(
    payload: Mapping[str, Any],
    *,
    jar_path: Optional[Path] = None,
    java_cmd: tuple[str, ...] = DEFAULT_JAVA_CMD,
    timeout_seconds: int = CLI_TIMEOUT_SECONDS,
) -> CliResponse:
    """Invoke the Kotlin CLI with the given JSON payload."""

    jar = jar_path or find_cli_jar()
    args = (*java_cmd, "-jar", str(jar))
    input_text = json.dumps(payload, ensure_ascii=False)

    try:
        completed = subprocess.run(
            args,
            input=input_text,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=timeout_seconds,
            check=False,
            text=True,
        )
    except subprocess.TimeoutExpired as exc:
        raise CliInvocationError(
            f"CLI timed out after {timeout_seconds} seconds",
            stdout=exc.stdout or "",
            stderr=exc.stderr or "",
        ) from exc
    except FileNotFoundError as exc:  # pragma: no cover - environment issue
        raise CliInvocationError(
            f"Failed to launch CLI process: {args[0]!r} not found"
        ) from exc

    stdout = completed.stdout.strip()
    stderr = completed.stderr.strip()

    if completed.returncode != 0:
        raise CliInvocationError(
            f"CLI exited with code {completed.returncode}",
            stdout=stdout,
            stderr=stderr,
        )

    if not stdout:
        raise CliInvocationError("CLI produced no output", stdout=stdout, stderr=stderr)

    try:
        data = json.loads(stdout)
        if not isinstance(data, MutableMapping):  # pragma: no cover - defensive
            raise TypeError("CLI output must be a JSON object")
    except Exception as exc:  # pragma: no cover - parsing failure
        raise CliInvocationError(
            "Failed to parse CLI JSON output",
            stdout=stdout,
            stderr=stderr,
        ) from exc

    return CliResponse(data=data, stdout=stdout, stderr=stderr, returncode=completed.returncode)


def execute_test_case(
    case: TestCase,
    *,
    model: ModelInference,
    base_context: Mapping[str, Any],
    jar_path: Optional[Path] = None,
    java_cmd: tuple[str, ...] = DEFAULT_JAVA_CMD,
) -> TestExecutionResult:
    context = dict(base_context)
    if case.expected_date:
        context["defaultDate"] = case.expected_date.isoformat()

    prompts: List[PromptExchange] = []
    errors: List[str] = []
    heuristic_stats: Optional[MutableMapping[str, Any]] = None
    heuristic_results: Optional[MutableMapping[str, Any]] = None

    try:
        first = run_cli(
            build_cli_payload(case.utterance, context),
            jar_path=jar_path,
            java_cmd=java_cmd,
        )
    except CliInvocationError as exc:
        errors.append(f"CLI error (stage1): {exc}")
        return TestExecutionResult(
            case=case,
            status="cli_error",
            parsed=None,
            method=None,
            prompts=prompts,
            stats={},
            heuristic_results=None,
            heuristic_stats=None,
            errors=errors,
            ai_calls=0,
        )

    heuristic_stats = first.data.get("stats") if isinstance(first.data.get("stats"), MutableMapping) else None
    heuristics_raw = first.data.get("heuristic_results")
    if isinstance(heuristics_raw, MutableMapping):
        heuristic_results = heuristics_raw

    if first.status == "error":
        errors.append(str(first.data.get("message", "CLI reported error")))
        return TestExecutionResult(
            case=case,
            status="cli_error",
            parsed=None,
            method=None,
            prompts=prompts,
            stats={},
            heuristic_results=heuristic_results,
            heuristic_stats=heuristic_stats,
            errors=errors,
            ai_calls=0,
        )

    final_response = first

    if first.status == "needs_ai":
        prompt_entries = first.data.get("prompts_needed") or []
        ai_responses: MutableMapping[str, str] = {}
        for entry in prompt_entries:
            if not isinstance(entry, Mapping):
                continue
            field = normalize_string(str(entry.get("field", "")))
            prompt_text = normalize_string(entry.get("prompt"))
            if not field or not prompt_text:
                continue
            exchange = PromptExchange(field=field, prompt=prompt_text)
            try:
                exchange.response = model.generate(prompt_text)
            except Exception as exc:  # pragma: no cover - model runtime issue
                errors.append(f"Model inference failed for field '{field}': {exc}")
                prompts.append(exchange)
                return TestExecutionResult(
                    case=case,
                    status="model_error",
                    parsed=None,
                    method=None,
                    prompts=prompts,
                    stats={},
                    heuristic_results=heuristic_results,
                    heuristic_stats=heuristic_stats,
                    errors=errors,
                    ai_calls=len([p for p in prompts if p.response]),
                )
            ai_responses[field] = exchange.response
            prompts.append(exchange)

        try:
            final_response = run_cli(
                build_cli_payload(
                    case.utterance,
                    context,
                    model_responses=ai_responses,
                ),
                jar_path=jar_path,
                java_cmd=java_cmd,
            )
        except CliInvocationError as exc:
            errors.append(f"CLI error (stage2): {exc}")
            return TestExecutionResult(
                case=case,
                status="cli_error",
                parsed=None,
                method=None,
                prompts=prompts,
                stats={},
                heuristic_results=heuristic_results,
                heuristic_stats=heuristic_stats,
                errors=errors,
                ai_calls=len([p for p in prompts if p.response]),
            )

    status = final_response.status
    parsed = final_response.data.get("parsed") if isinstance(final_response.data.get("parsed"), MutableMapping) else None
    stats = final_response.data.get("stats") if isinstance(final_response.data.get("stats"), MutableMapping) else {}
    method = normalize_string(final_response.data.get("method"))

    return TestExecutionResult(
        case=case,
        status=status,
        parsed=parsed,
        method=method,
        prompts=prompts,
        stats=stats,
        heuristic_results=heuristic_results,
        heuristic_stats=heuristic_stats,
        errors=errors,
        ai_calls=len([p for p in prompts if p.response]),
    )


def run_evaluation(
    *,
    model_name: str,
    test_cases_path: Optional[Path] = None,
    config_path: Optional[Path] = None,
    jar_path: Optional[Path] = None,
    only_test_ids: Optional[Collection[str]] = None,
    java_cmd: Optional[tuple[str, ...]] = None,
) -> List[TestExecutionResult]:
    """Execute the full evaluation workflow for all test cases."""

    test_cases = load_test_cases(test_cases_path)
    if only_test_ids:
        normalized_ids = {
            str(identifier).strip()
            for identifier in only_test_ids
            if str(identifier).strip()
        }
        if normalized_ids:
            filtered_cases: List[TestCase] = [
                case for case in test_cases if case.identifier in normalized_ids
            ]
            missing = sorted(normalized_ids - {case.identifier for case in filtered_cases})
            for missing_id in missing:
                print(f"Warning: test ID '{missing_id}' not found in test_cases.md", file=sys.stderr)
            if not filtered_cases:
                return []
            test_cases = filtered_cases
    base_context = load_config_context(config_path)
    model = ModelInference(model_name)
    java_cmd = java_cmd or DEFAULT_JAVA_CMD

    results: List[TestExecutionResult] = []
    for case in test_cases:
        result = execute_test_case(
            case,
            model=model,
            base_context=base_context,
            jar_path=jar_path,
            java_cmd=java_cmd,
        )
        results.append(result)
    return results


def compare_results(executions: List[TestExecutionResult]) -> List[TestComparison]:
    """Compare parsed results against expectations for each test."""

    comparisons: List[TestComparison] = []
    for execution in executions:
        parsed = execution.parsed or {}
        field_results: List[FieldComparison] = []

        field_results.append(
            compare_field(
                "amountUsd",
                execution.case.expected_amount,
                parsed.get("amountUsd"),
                kind="decimal",
            )
        )
        field_results.append(
            compare_field(
                "merchant",
                execution.case.expected_merchant,
                parsed.get("merchant"),
                kind="string",
            )
        )
        field_results.append(
            compare_field(
                "description",
                execution.case.expected_description,
                parsed.get("description"),
                kind="string",
            )
        )
        field_results.append(
            compare_field(
                "type",
                execution.case.expected_type,
                parsed.get("type"),
                kind="string",
            )
        )
        actual_category = parsed.get("expenseCategory") or parsed.get("incomeCategory")
        field_results.append(
            compare_field(
                "category",
                execution.case.expected_category,
                actual_category,
                kind="string",
            )
        )
        field_results.append(
            compare_field(
                "tags",
                execution.case.expected_tags,
                parsed.get("tags"),
                kind="tags",
            )
        )
        field_results.append(
            compare_field(
                "userLocalDate",
                execution.case.expected_date,
                parsed.get("userLocalDate"),
                kind="date",
            )
        )
        field_results.append(
            compare_field(
                "account",
                execution.case.expected_account,
                parsed.get("account"),
                kind="string",
            )
        )
        field_results.append(
            compare_field(
                "splitOverallChargedUsd",
                execution.case.expected_split_overall,
                parsed.get("splitOverallChargedUsd"),
                kind="decimal",
            )
        )

        overall_match = execution.status == "complete" and all(
            fr.match for fr in field_results if fr.expected is not None
        )
        comparisons.append(
            TestComparison(
                execution=execution,
                field_results=field_results,
                overall_match=overall_match,
            )
        )
    return comparisons


def compare_field(field: str, expected: Any, actual: Any, *, kind: str) -> FieldComparison:
    expected_canonical = canonical_value(expected, kind)
    actual_canonical = canonical_value(actual, kind)
    match = values_match(expected_canonical, actual_canonical, kind)
    return FieldComparison(
        field=field,
        expected=format_display(expected, kind),
        actual=format_display(actual, kind),
        match=match,
    )


def canonical_value(value: Any, kind: str) -> Any:
    if kind == "decimal":
        return coerce_decimal(value)
    if kind == "string":
        text = normalize_string(value)
        return text.casefold() if text else None
    if kind == "tags":
        tags = coerce_tags(value)
        return tuple(sorted(tag.casefold() for tag in tags)) if tags is not None else None
    if kind == "date":
        coerced = coerce_date(value)
        return coerced
    return value


def values_match(expected: Any, actual: Any, kind: str) -> bool:
    if expected is None:
        if kind == "tags":
            return actual in (None, (), [])
        return actual is None
    if actual is None:
        return False
    if kind == "decimal":
        if expected is None or actual is None:
            return expected is None and actual is None
        return (actual - expected).copy_abs() <= DECIMAL_TOLERANCE
    return expected == actual


def format_display(value: Any, kind: str) -> Any:
    if value is None:
        return None
    if kind == "decimal":
        decimal_value = coerce_decimal(value)
        return str(decimal_value) if decimal_value is not None else value
    if kind == "date":
        coerced = coerce_date(value)
        return coerced.isoformat() if coerced else value
    if kind == "tags":
        tags = coerce_tags(value) or []
        return tags
    return value


def coerce_decimal(value: Any) -> Optional[Decimal]:
    if value is None:
        return None
    if isinstance(value, Decimal):
        return value
    if isinstance(value, (int, float)):
        return Decimal(str(value))
    if isinstance(value, str):
        text = value.strip()
        if not text:
            return None
        try:
            return Decimal(text)
        except InvalidOperation:
            return None
    return None


def coerce_date(value: Any) -> Optional[date]:
    if value is None:
        return None
    if isinstance(value, date):
        return value
    if isinstance(value, str):
        text = value.strip()
        if not text:
            return None
        try:
            return date.fromisoformat(text)
        except ValueError:
            return None
    return None


def coerce_tags(value: Any) -> Optional[List[str]]:
    if value is None:
        return None
    if isinstance(value, (list, tuple, set)):
        tags: List[str] = []
        for item in value:
            text = normalize_string(item)
            if text:
                tags.append(text)
        return tags
    text = normalize_string(value)
    if text:
        return [text]
    return []


def compute_metrics(comparisons: List[TestComparison]) -> EvaluationMetrics:
    total = len(comparisons)
    passed = sum(1 for comp in comparisons if comp.overall_match)
    per_field_counts: MutableMapping[str, list[int]] = {}
    field_samples: MutableMapping[str, int] = {}
    total_ms_values: List[float] = []
    stage0_ms_values: List[float] = []
    stage1_ms_values: List[float] = []

    for comp in comparisons:
        stats = comp.execution.stats
        if isinstance(stats, Mapping):
            total_ms = stats.get("total_ms")
            if isinstance(total_ms, (int, float)):
                total_ms_values.append(float(total_ms))
            stage0 = stats.get("stage0_ms")
            if isinstance(stage0, (int, float)):
                stage0_ms_values.append(float(stage0))
            stage1 = stats.get("stage1_ms")
            if isinstance(stage1, (int, float)):
                stage1_ms_values.append(float(stage1))

        for field in comp.field_results:
            bucket = per_field_counts.setdefault(field.field, [0, 0])
            if field.expected is None:
                continue
            bucket[1] += 1
            if field.match:
                bucket[0] += 1

    per_field_accuracy: MutableMapping[str, Optional[float]] = {}
    for field, (matches, total_samples) in per_field_counts.items():
        per_field_accuracy[field] = (matches / total_samples) if total_samples else None
        field_samples[field] = total_samples

    ai_usage = sum(1 for comp in comparisons if comp.execution.ai_calls > 0)
    total_ai = sum(comp.execution.ai_calls for comp in comparisons)
    overall_accuracy = (passed / total) if total else None

    return EvaluationMetrics(
        total_tests=total,
        passed_tests=passed,
        per_field_accuracy=per_field_accuracy,
        overall_accuracy=overall_accuracy,
        ai_usage_count=ai_usage,
        total_ai_calls=total_ai,
        field_samples=field_samples,
        average_total_ms=_mean(total_ms_values),
        average_stage0_ms=_mean(stage0_ms_values),
        average_stage1_ms=_mean(stage1_ms_values),
    )


def write_markdown_reports(
    comparisons: List[TestComparison],
    metrics: EvaluationMetrics,
    *,
    output_dir: Optional[Path] = None,
) -> tuple[Path, Path]:
    """Generate detailed and summary markdown reports."""

    target_dir = output_dir or RESULTS_DIR
    target_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    results_path = target_dir / f"{timestamp}_results.md"
    summary_path = target_dir / f"{timestamp}_summary.md"

    results_markdown = build_results_markdown(comparisons)
    summary_markdown = build_summary_markdown(metrics)

    results_path.write_text(results_markdown, encoding="utf-8")
    summary_path.write_text(summary_markdown, encoding="utf-8")
    return results_path, summary_path


def build_results_markdown(comparisons: List[TestComparison]) -> str:
    lines: List[str] = ["# Evaluation Results", ""]
    headers = [
        "Test",
        "Input",
        "Overall",
        "Method",
        "AI Calls",
    ] + [FIELD_LABELS[field] for field in FIELD_ORDER] + ["Errors"]
    lines.append("| " + " | ".join(headers) + " |")
    lines.append("| " + " | ".join(["---"] * len(headers)) + " |")

    for comp in comparisons:
        execution = comp.execution
        field_map = {fr.field: fr for fr in comp.field_results}
        row = [
            escape_markdown(execution.case.identifier),
            escape_markdown(execution.case.utterance),
            format_overall_cell(comp),
            escape_markdown(execution.method or "—"),
            str(execution.ai_calls),
        ]
        for field in FIELD_ORDER:
            field_comp = field_map.get(field)
            row.append(format_field_cell(field_comp))
        row.append(format_errors_cell(execution))
        lines.append("| " + " | ".join(row) + " |")

    lines.append("")
    return "\n".join(lines)


def build_summary_markdown(metrics: EvaluationMetrics) -> str:
    lines: List[str] = ["# Evaluation Summary", ""]
    lines.append("| Metric | Value |")
    lines.append("| --- | --- |")
    failed = metrics.total_tests - metrics.passed_tests
    lines.append(f"| Total tests | {metrics.total_tests} |")
    lines.append(f"| Passed | {metrics.passed_tests} |")
    lines.append(f"| Failed | {failed} |")
    overall = f"{metrics.overall_accuracy * 100:.1f}%" if metrics.overall_accuracy is not None else "n/a"
    lines.append(f"| Overall accuracy | {overall} |")
    if metrics.total_tests:
        ai_usage_pct = metrics.ai_usage_count / metrics.total_tests * 100
        tests_using_ai_value = f"{metrics.ai_usage_count} ({ai_usage_pct:.1f}%)"
    else:
        tests_using_ai_value = "0"
    lines.append(f"| Tests using AI | {tests_using_ai_value} |")
    lines.append(f"| Total AI calls | {metrics.total_ai_calls} |")
    lines.append(f"| Avg total time | {format_ms(metrics.average_total_ms)} |")
    lines.append(f"| Avg stage0 time | {format_ms(metrics.average_stage0_ms)} |")
    lines.append(f"| Avg stage1 time | {format_ms(metrics.average_stage1_ms)} |")

    lines.append("")
    lines.append("## Per-field Accuracy")
    lines.append("")
    lines.append("| Field | Accuracy | Samples |")
    lines.append("| --- | --- | --- |")
    for field in FIELD_ORDER:
        accuracy = metrics.per_field_accuracy.get(field)
        samples = metrics.field_samples.get(field, 0)
        accuracy_text = f"{accuracy * 100:.1f}%" if accuracy is not None else "n/a"
        lines.append(f"| {FIELD_LABELS[field]} | {accuracy_text} | {samples} |")

    lines.append("")
    return "\n".join(lines)


def format_field_cell(field_comp: Optional[FieldComparison]) -> str:
    if field_comp is None:
        return "—"
    symbol = "✅" if field_comp.match else "❌"
    actual_text = value_to_text(field_comp.actual)
    expected_text = value_to_text(field_comp.expected)
    if field_comp.expected is None or expected_text == "—":
        return escape_markdown(f"{symbol} {actual_text}")
    return escape_markdown(f"{symbol} {actual_text} / {expected_text}")


def value_to_text(value: Any) -> str:
    if value is None:
        return "—"
    if isinstance(value, (list, tuple, set)):
        return ", ".join(sorted(str(item) for item in value)) if value else "—"
    return str(value)


def format_overall_cell(comparison: TestComparison) -> str:
    symbol = "✅" if comparison.overall_match else "❌"
    status = comparison.execution.status
    if status != "complete":
        return escape_markdown(f"{symbol} ({status})")
    return symbol


def format_errors_cell(execution: TestExecutionResult) -> str:
    errors = list(execution.errors)
    if execution.status != "complete" and not errors:
        errors.append(execution.status)
    if not errors:
        return "—"
    return escape_markdown("; ".join(errors))


def format_ms(value: Optional[float]) -> str:
    if value is None:
        return "n/a"
    return f"{value:.1f} ms"


def escape_markdown(text: Optional[str]) -> str:
    if text is None:
        return "—"
    escaped = text.replace("|", "\\|").replace("\n", "<br>")
    return escaped if escaped else "—"

def _mean(values: List[float]) -> Optional[float]:
    return (sum(values) / len(values)) if values else None


def parse_cli_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run the AI parsing evaluator against configured test cases.",
    )
    parser.add_argument(
        "--model",
        required=True,
        choices=SUPPORTED_MODELS,
        help="HuggingFace model identifier to use for AI refinement.",
    )
    parser.add_argument(
        "--jar",
        type=Path,
        help="Path to the Kotlin CLI jar. Defaults to the latest build in cli/build/libs.",
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=CONFIG_FILE,
        help="Path to config.json exported from the app (defaults to evaluator/config.json).",
    )
    parser.add_argument(
        "--test-cases",
        type=Path,
        default=TEST_CASES_FILE,
        help="Path to the markdown table of test cases (defaults to evaluator/test_cases.md).",
    )
    parser.add_argument(
        "--test",
        dest="tests",
        action="append",
        help="Limit evaluation to specific test case IDs. Supply multiple times to include several IDs.",
    )
    parser.add_argument(
        "--results-dir",
        type=Path,
        default=RESULTS_DIR,
        help="Directory to write markdown reports (defaults to evaluator/results/).",
    )
    parser.add_argument(
        "--java",
        metavar="CMD",
        help="Override the java command used to launch the CLI (e.g. 'java -Xmx4g').",
    )
    return parser.parse_args(argv)


def main(argv: Optional[List[str]] = None) -> None:  # pragma: no cover - CLI entrypoint
    args = parse_cli_args(argv)
    java_cmd: Optional[tuple[str, ...]] = None
    if args.java:
        tokens = shlex.split(args.java)
        if not tokens:
            print("Error: --java command is empty after parsing.", file=sys.stderr)
            raise SystemExit(2)
        java_cmd = tuple(tokens)

    try:
        executions = run_evaluation(
            model_name=args.model,
            test_cases_path=args.test_cases,
            config_path=args.config,
            jar_path=args.jar,
            only_test_ids=args.tests,
            java_cmd=java_cmd,
        )
    except FileNotFoundError as exc:
        print(f"Error: {exc}", file=sys.stderr)
        raise SystemExit(2) from exc
    except (RuntimeError, ValueError) as exc:
        print(f"Error: {exc}", file=sys.stderr)
        raise SystemExit(3) from exc

    if not executions:
        print("No matching test cases to execute.", file=sys.stderr)
        raise SystemExit(4)

    comparisons = compare_results(executions)
    metrics = compute_metrics(comparisons)
    results_path, summary_path = write_markdown_reports(
        comparisons,
        metrics,
        output_dir=args.results_dir,
    )

    print(f"Model: {args.model}")
    print(f"Tests processed: {metrics.total_tests}")
    print(f"Passed: {metrics.passed_tests} | Failed: {metrics.total_tests - metrics.passed_tests}")
    print(f"Results written to: {results_path}")
    print(f"Summary written to: {summary_path}")

    failing = [
        comp.execution.case.identifier
        for comp in comparisons
        if not comp.overall_match
    ]
    if failing:
        print("Failing test IDs: " + ", ".join(failing), file=sys.stderr)
        raise SystemExit(5)

    raise SystemExit(0)


if __name__ == "__main__":
    main()
