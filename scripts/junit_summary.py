#!/usr/bin/env python3
"""Summarize Gradle JUnit XML results for humans and CI annotations.

GitHub's raw-log endpoint is not always reachable, so the raw Gradle output can
be unreadable even for a green run. This script answers two questions directly
from the XML reports Gradle already writes: which test classes actually ran, and
how many tests each of them reported.

Usage: scripts/junit_summary.py [REPO_ROOT]

Exit status:
  0  every module produced results and no test failed or errored
  1  no results at all, :core results missing, or any failure/error reported
  2  a report file could not be parsed
"""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass
from pathlib import Path

CORE_PREFIX = "io.github.antonovichkorp.chemmod.core"
DISPLAY_PREFIX = "io.github.antonovichkorp.chemmod."


@dataclass(frozen=True)
class SuiteResult:
    module: str
    name: str
    tests: int
    failures: int
    errors: int
    skipped: int

    @property
    def short_name(self) -> str:
        return self.name[len(DISPLAY_PREFIX):] if self.name.startswith(DISPLAY_PREFIX) else self.name

    @property
    def broken(self) -> bool:
        return self.failures > 0 or self.errors > 0


def _int(element: ElementTree.Element, attribute: str) -> int:
    raw = element.get(attribute)
    try:
        return int(raw) if raw is not None else 0
    except ValueError:
        return 0


def _suites_in_file(path: Path, module: str) -> list[SuiteResult]:
    root = ElementTree.parse(path).getroot()
    # Gradle writes <testsuite> per class; tolerate a <testsuites> wrapper too.
    elements = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
    results = []
    for element in elements:
        name = element.get("name")
        if not name:
            continue
        results.append(
            SuiteResult(
                module=module,
                name=name,
                tests=_int(element, "tests"),
                failures=_int(element, "failures"),
                errors=_int(element, "errors"),
                skipped=_int(element, "skipped"),
            )
        )
    return results


def collect(root: Path) -> list[SuiteResult]:
    """Read every <module>/build/test-results/test/*.xml report under root."""
    results: list[SuiteResult] = []
    for report in sorted(root.glob("*/build/test-results/test/*.xml")):
        module = report.parts[len(root.parts)]
        try:
            results.extend(_suites_in_file(report, module))
        except ElementTree.ParseError as error:
            raise ValueError(f"unreadable JUnit report {report}: {error}") from error
    return results


def render(suites: list[SuiteResult]) -> list[str]:
    lines = [
        f"{suite.module} {suite.short_name}: tests={suite.tests} failures={suite.failures} "
        f"errors={suite.errors} skipped={suite.skipped}"
        for suite in sorted(suites, key=lambda s: (s.module, s.name))
    ]
    lines.append(
        "JUnit totals: classes={classes} tests={tests} failures={failures} "
        "errors={errors} skipped={skipped}".format(
            classes=len(suites),
            tests=sum(s.tests for s in suites),
            failures=sum(s.failures for s in suites),
            errors=sum(s.errors for s in suites),
            skipped=sum(s.skipped for s in suites),
        )
    )
    return lines


def main(argv: list[str]) -> int:
    root = Path(argv[1]) if len(argv) > 1 else Path(__file__).resolve().parent.parent
    try:
        suites = collect(root)
    except ValueError as error:
        print(error, file=sys.stderr)
        return 2
    if not suites:
        print(
            f"Smoke-test failure: no JUnit XML under {root}/<module>/build/test-results/test; "
            "unit tests did not run",
            file=sys.stderr,
        )
        return 1
    if not any(suite.name.startswith(CORE_PREFIX) for suite in suites):
        print("Smoke-test failure: :core:test produced no results", file=sys.stderr)
        return 1
    for line in render(suites):
        print(line)
    broken = [suite for suite in suites if suite.broken]
    if broken:
        names = ", ".join(suite.short_name for suite in broken)
        print(f"Smoke-test failure: JUnit failures in {names}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
