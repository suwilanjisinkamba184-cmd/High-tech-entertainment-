#!/usr/bin/env python3
"""Create an honest per-emulator Android test report from Gradle JUnit XML."""
from __future__ import annotations

import argparse
import datetime as dt
import glob
import json
import os
import re
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--api-level", required=True)
args = parser.parse_args()

root = Path.cwd()
out_dir = root / "build" / "diagnostics"
out_dir.mkdir(parents=True, exist_ok=True)

patterns = [
    "app/build/test-results/testDebugUnitTest/*.xml",
    "app/build/outputs/androidTest-results/connected/**/*.xml",
    "app/build/reports/androidTests/connected/**/*.xml",
]
files: list[Path] = []
for pattern in patterns:
    files.extend(Path(p) for p in glob.glob(str(root / pattern), recursive=True) if Path(p).is_file())
files = sorted(set(files))

cases: list[dict[str, str]] = []
suite_totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
for file in files:
    try:
        tree = ET.parse(file)
    except (ET.ParseError, OSError):
        continue
    for node in tree.iter():
        if node.tag not in ("testsuite", "testsuites"):
            continue
        for key in ("tests", "failures", "errors", "skipped"):
            try:
                suite_totals[key] += int(node.attrib.get(key, "0"))
            except ValueError:
                pass
    for case in tree.iter("testcase"):
        outcome = "PASS"
        detail = ""
        failure = case.find("failure")
        error = case.find("error")
        skipped = case.find("skipped")
        if failure is not None:
            outcome, detail = "FAIL", (failure.attrib.get("message") or (failure.text or "")).strip()[:500]
        elif error is not None:
            outcome, detail = "ERROR", (error.attrib.get("message") or (error.text or "")).strip()[:500]
        elif skipped is not None:
            outcome, detail = "SKIPPED", (skipped.attrib.get("message") or "").strip()[:300]
        cases.append({
            "class": case.attrib.get("classname", ""),
            "name": case.attrib.get("name", ""),
            "outcome": outcome,
            "detail": detail,
            "source": str(file.relative_to(root)),
        })

gradle_file = root / "app" / "build.gradle.kts"
gradle_text = gradle_file.read_text(errors="replace") if gradle_file.exists() else ""
version_name = re.search(r'versionName\\s*=\\s*"([^"]+)"', gradle_text)
version_code = re.search(r"versionCode\\s*=\\s*(\\d+)", gradle_text)
commit = os.environ.get("GITHUB_SHA", "unknown")
timestamp = dt.datetime.now(dt.timezone.utc).isoformat()

def matched(*needles: str) -> bool:
    return any(any(needle.lower() in c["name"].lower() or needle.lower() in c["class"].lower() for needle in needles) and c["outcome"] == "PASS" for c in cases)

feature_rows = [
    ("Splash branding", "PASS" if matched("startupDisplaysApprovedBrandingWithoutRemovedLogo") else "NOT VERIFIED", "Checks approved splash text and confirms the removed logo is absent."),
    ("AI assistant", "PARTIAL" if matched("homeVoiceEntryOpensCompactAssistant") else "NOT VERIFIED", "UI entry and voice preset logic only; no live microphone/WebSocket/provider conversation test."),
    ("Account and Settings navigation", "PARTIAL" if matched("accountTabAndSettingsHubRemainReachable") else "NOT VERIFIED", "Navigation reachability only; no live sign-in/profile/avatar integration test."),
    ("Video poster URL/fallback logic", "PASS (UNIT LOGIC)" if matched("VideoThumbnailUrlTest") else "NOT VERIFIED", "Validates URL selection and fallback logic; does not prove remote image loading or playback."),
    ("Music playback", "NOT TESTED", "No end-to-end play/pause/seek/background playback test in this suite."),
    ("Video playback/fullscreen", "NOT TESTED", "No end-to-end embedded player, fullscreen, rotation or provider-error test."),
    ("Audio/video downloads", "NOT TESTED", "No end-to-end download, file-integrity or offline-open test."),
    ("Google authentication/session restore", "NOT TESTED", "No end-to-end OAuth or persisted-session test."),
    ("Founder Studio upload/publishing", "NOT TESTED", "No end-to-end Founder authorization, upload or publishing test."),
    ("Live Supabase data/network behavior", "NOT TESTED", "Build configuration is validated; live catalogue and failure/retry journeys are not exercised here."),
]

passed = sum(1 for c in cases if c["outcome"] == "PASS")
failed = sum(1 for c in cases if c["outcome"] in ("FAIL", "ERROR"))
skipped = sum(1 for c in cases if c["outcome"] == "SKIPPED")
report = [
    "# HTE Universe Android emulator test report",
    "",
    f"- Generated (UTC): {timestamp}",
    f"- Commit: {commit}",
    f"- Android emulator API: {args.api_level}",
    f"- App version: {(version_name.group(1) if version_name else 'not found')} ({(version_code.group(1) if version_code else 'version code not found')})",
    f"- JUnit XML files discovered: {len(files)}",
    f"- Parsed test cases: {len(cases)} (PASS {passed}, FAIL/ERROR {failed}, SKIPPED {skipped})",
    f"- Gradle suite totals: {json.dumps(suite_totals, sort_keys=True)}",
    "",
    "## Feature-level status",
    "",
    "| Feature | Status | What this result proves |",
    "|---|---|---|",
]
for name, status, note in feature_rows:
    report.append(f"| {name} | **{status}** | {note} |")
report += ["", "## Individual test cases", ""]
if cases:
    report += ["| Test | Result | Source |", "|---|---|---|"]
    for case in cases:
        test_name = f"{case['class']}.{case['name']}".strip(".")
        report.append(f"| {test_name} | {case['outcome']}{(': ' + case['detail']) if case['detail'] else ''} | {case['source']} |")
else:
    report.append("**NO JUNIT TEST CASES WERE FOUND.** Inspect the workflow logs; this report must not be interpreted as a pass.")
report += [
    "",
    "## Release interpretation",
    "",
    "A green emulator job means only the tests listed above passed on this emulator API. It does not mean every HTE Universe feature works.",
    "Features marked PARTIAL or NOT TESTED remain release blockers until dedicated automated tests and physical-device acceptance cover them.",
    "",
]
md_path = out_dir / f"android-feature-test-report-api-{args.api_level}.md"
json_path = out_dir / f"android-feature-test-report-api-{args.api_level}.json"
md_path.write_text("\\n".join(report) + "\\n")
json_path.write_text(json.dumps({
    "generated_utc": timestamp, "commit": commit, "api_level": args.api_level,
    "app_version_name": version_name.group(1) if version_name else None,
    "app_version_code": int(version_code.group(1)) if version_code else None,
    "suite_totals": suite_totals, "cases": cases,
    "feature_status": [{"feature": n, "status": s, "evidence": note} for n, s, note in feature_rows],
}, indent=2) + "\\n")
print(f"Android feature report written: {md_path}")
print(f"JUnit cases parsed: {len(cases)}; pass={passed}; fail/error={failed}; skipped={skipped}")
