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
    path = ROOT / rel
    try:
        return path.read_text(encoding="utf-8")
    except OSError as exc:
        return ""

manifest = read("app/src/main/AndroidManifest.xml")
registry = read("app/src/main/java/com/hector/android/AgentToolRegistry.kt")
policy = read("app/src/main/java/com/hector/android/AgentTaskPolicy.kt")
verifier = read("app/src/main/java/com/hector/android/AgentTaskVerifier.kt")
workflow = read(".github/workflows/daily-improvement.yml")

required = [
    "app/src/main/AndroidManifest.xml",
    "app/src/main/java/com/hector/android/AgentToolRegistry.kt",
    "app/src/main/java/com/hector/android/AgentTaskPolicy.kt",
    "app/src/main/java/com/hector/android/AgentTaskVerifier.kt",
    ".github/workflows/daily-improvement.yml",
]
check("Required safety files exist", all((ROOT / p).is_file() for p in required),
      "Checks the files used by the read-only safety audit.")
check("Biometric permissions remain absent",
      "android.permission.USE_BIOMETRIC" not in manifest and
      "android.permission.USE_FINGERPRINT" not in manifest,
      "Preserves the project's explicit no-fingerprint/no-biometric requirement.")
check("Agent tools remain read-only allowlisted",
      'ToolDefinition("read_memory"' in registry and
      'ToolDefinition("search_history"' in registry and
      'ToolDefinition("shell"' not in registry and
      'ToolDefinition("execute_code"' not in registry,
      "Expected tools are read_memory and search_history; shell/arbitrary-code tools must not be registered.")
check("Task policy retains bounded execution",
      "MAX_STEPS = 8" in policy and "MAX_RETRIES_PER_STEP = 2" in policy and
      "requiresApproval" in policy and "EXTERNAL_SIDE_EFFECT" in policy,
      "Checks bounded plans/retries and approval gating for external effects.")
check("Success requires observed evidence",
      "executionStarted" in verifier and "requiredChecks" in verifier and
      "VERIFIED_SUCCESS" in verifier and "total == 0" in verifier,
      "Model text alone must not count as verified execution.")
check("Daily workflow uses read-only repository permissions",
      "permissions:\n  contents: read" in workflow and
      "persist-credentials: false" in workflow,
      "Daily checks must not receive repository write access or persist checkout credentials.")

passed = sum(1 for _, ok, _ in checks if ok)
lines = [
    "# Hector Daily Improvement Audit",
    "",
    f"- Generated: {datetime.now(timezone.utc).isoformat(timespec='seconds')}",
    f"- Result: {'PASS' if passed == len(checks) else 'FAIL'} ({passed}/{len(checks)} checks passed)",
    "",
    "This report is a static source check, not proof of runtime behavior or future AI capability.",
    "",
]
for name, ok, detail in checks:
    lines.extend([f"## [{'PASS' if ok else 'FAIL'}] {name}", detail, ""])
REPORT.parent.mkdir(parents=True, exist_ok=True)
REPORT.write_text("\n".join(lines), encoding="utf-8")
print("\n".join(lines))
sys.exit(0 if passed == len(checks) else 1)
