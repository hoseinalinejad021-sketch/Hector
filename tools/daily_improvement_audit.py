#!/usr/bin/env python3
"""Read-only, evidence-based daily checks for Hector's safety invariants."""
from pathlib import Path
from datetime import datetime, timezone
import sys

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / "artifacts" / "daily-improvement-report.md"
checks = []

def check(name, condition, detail):
    checks.append((name, bool(condition), detail))

def read(rel):
    try:
        return (ROOT / rel).read_text(encoding="utf-8")
    except OSError:
        return ""

manifest = read("app/src/main/AndroidManifest.xml")
registry = read("app/src/main/java/com/hector/android/AgentToolRegistry.kt")
parser = read("app/src/main/java/com/hector/android/ResponsesToolCallParser.kt")
policy = read("app/src/main/java/com/hector/android/AgentTaskPolicy.kt")
verifier = read("app/src/main/java/com/hector/android/AgentTaskVerifier.kt")
workflow = read(".github/workflows/daily-improvement.yml")

required = [
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/hector/android/AgentToolRegistry.kt",
    "app/src/main/java/com/hector/android/ResponsesToolCallParser.kt",
    "app/src/test/java/com/hector/android/AgentToolRegistryTest.kt",
    "app/src/test/java/com/hector/android/ResponsesToolCallParserTest.kt",
    "app/src/main/java/com/hector/android/AgentTaskPolicy.kt",
    "app/src/main/java/com/hector/android/AgentTaskVerifier.kt",
    ".github/workflows/daily-improvement.yml",
]
check("Required safety files exist", all((ROOT / p).is_file() for p in required),
      "Checks source files used by the read-only audit.")
check("Biometric permissions remain absent",
      "android.permission.USE_BIOMETRIC" not in manifest and
      "android.permission.USE_FINGERPRINT" not in manifest,
      "Preserves the project's no-biometric/no-fingerprint requirement.")
check("Agent tools remain read-only allowlisted",
      all(f'ToolDefinition("{name}"' in registry for name in
          ("read_memory", "search_memory", "search_history")) and
      'ToolDefinition("shell"' not in registry and
      'ToolDefinition("execute_code"' not in registry and
      'ToolDefinition("http_request"' not in registry,
      "Expected read-only tools are read_memory, search_memory, and search_history; shell/arbitrary-code/network tools must not be registered.")
check("Memory search is wired through the strict parser",
      '"search_memory"' in parser and
      "search_memory" in registry and
      (ROOT / "app/src/test/java/com/hector/android/AgentToolRegistryTest.kt").is_file() and
      (ROOT / "app/src/test/java/com/hector/android/ResponsesToolCallParserTest.kt").is_file(),
      "The memory-search tool must be explicitly allowlisted and covered by parser/registry unit tests.")
check("Task policy retains bounded execution",
      "MAX_STEPS = 8" in policy and "MAX_RETRIES_PER_STEP = 2" in policy and
      "requiresApproval" in policy and "EXTERNAL_SIDE_EFFECT" in policy,
      "Checks bounded plans/retries and approval gating for external effects.")
check("Success requires observed evidence",
      "executionStarted" in verifier and "requiredChecks" in verifier and
      "VERIFIED_SUCCESS" in verifier and "total == 0" in verifier,
      "Model text alone must not count as verified execution.")
check("Daily workflow uses read-only permissions",
      "permissions:\n  contents: read" in workflow and
      "persist-credentials: false" in workflow,
      "Daily checks must not have repository write access or persist checkout credentials.")

passed = sum(ok for _, ok, _ in checks)
lines = [
    "# Hector Daily Improvement Audit", "",
    f"- Generated: {datetime.now(timezone.utc).isoformat(timespec='seconds')}",
    f"- Result: {'PASS' if passed == len(checks) else 'FAIL'} ({passed}/{len(checks)} checks passed)",
    "",
    "Static source checks only; this report is not proof of runtime behavior or future AI capability.", "",
]
for name, ok, detail in checks:
    lines.extend([f"## [{'PASS' if ok else 'FAIL'}] {name}", detail, ""])
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text("\n".join(lines), encoding="utf-8")
print("\n".join(lines))
sys.exit(0 if passed == len(checks) else 1)
