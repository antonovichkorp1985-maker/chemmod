"""Tests for scripts/junit_summary.py, exercised against synthetic Gradle reports."""

import contextlib
import io
import tempfile
import unittest
from pathlib import Path

from junit_summary import collect, main, render

SUITE = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="{name}" tests="{tests}" skipped="{skipped}" failures="{failures}"
           errors="{errors}" hostname="runner" time="{time}">
  <testcase name="example" classname="{name}" time="0.01"/>
</testsuite>
"""


def write_report(root: Path, module: str, name: str, **counts) -> Path:
    counts = {"tests": 1, "skipped": 0, "failures": 0, "errors": 0, "time": "0.1", **counts}
    path = root / module / "build" / "test-results" / "test" / f"TEST-{name}.xml"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(SUITE.format(name=name, **counts))
    return path


class JunitSummaryTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name).resolve()
        self.addCleanup(self._tmp.cleanup)

    def run_main(self):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            status = main(["junit_summary.py", str(self.root)])
        return status, out.getvalue().splitlines(), err.getvalue()

    def test_collects_every_module_and_totals(self):
        write_report(self.root, "core", "io.github.antonovichkorp.chemmod.core.thermal.ThermalTransferTest", tests=16)
        write_report(self.root, "core", "io.github.antonovichkorp.chemmod.core.equipment.EquipmentPlacementTest",
                     tests=14, skipped=1)
        write_report(self.root, "mod", "io.github.antonovichkorp.chemmod.ReactorInputPolicyTest", tests=5)
        suites = collect(self.root)
        self.assertEqual(3, len(suites))
        self.assertEqual(
            [("core", "core.equipment.EquipmentPlacementTest"),
             ("core", "core.thermal.ThermalTransferTest"),
             ("mod", "ReactorInputPolicyTest")],
            [(suite.module, suite.short_name) for suite in suites],
        )
        self.assertEqual(35, sum(suite.tests for suite in suites))
        status, lines, err = self.run_main()
        self.assertEqual(0, status, err)
        self.assertIn("core core.thermal.ThermalTransferTest: tests=16 failures=0 errors=0 skipped=0", lines)
        self.assertIn("mod ReactorInputPolicyTest: tests=5 failures=0 errors=0 skipped=0", lines)
        self.assertEqual("JUnit totals: classes=3 tests=35 failures=0 errors=0 skipped=1", lines[-1])

    def test_failure_or_error_fails_the_summary_after_listing_classes(self):
        write_report(self.root, "core", "io.github.antonovichkorp.chemmod.core.thermal.ThermalTransferTest",
                     tests=16, failures=1)
        write_report(self.root, "mod", "io.github.antonovichkorp.chemmod.ReactorInputPolicyTest", tests=5, errors=2)
        status, lines, err = self.run_main()
        self.assertEqual(1, status)
        self.assertEqual(3, len(lines))  # two classes plus the totals line
        self.assertIn("thermal.ThermalTransferTest", err)
        self.assertIn("ReactorInputPolicyTest", err)

    def test_missing_results_are_a_failure_not_a_silent_pass(self):
        status, lines, err = self.run_main()
        self.assertEqual(1, status)
        self.assertEqual([], lines)
        self.assertIn("no JUnit XML", err)

    def test_results_without_the_core_module_are_a_failure(self):
        write_report(self.root, "mod", "io.github.antonovichkorp.chemmod.ReactorInputPolicyTest", tests=5)
        status, lines, err = self.run_main()
        self.assertEqual(1, status)
        self.assertEqual([], lines)
        self.assertIn(":core:test produced no results", err)

    def test_unparsable_report_is_reported_as_a_parse_error(self):
        path = write_report(self.root, "core", "io.github.antonovichkorp.chemmod.core.ParseTest", tests=1)
        path.write_text("<testsuite name='broken'")
        status, lines, err = self.run_main()
        self.assertEqual(2, status)
        self.assertEqual([], lines)
        self.assertIn("unreadable JUnit report", err)

    def test_wrapper_element_and_unnamed_suites_are_handled(self):
        path = self.root / "core" / "build" / "test-results" / "test" / "TEST-wrapper.xml"
        path.parent.mkdir(parents=True)
        path.write_text(
            "<?xml version='1.0' encoding='UTF-8'?><testsuites>"
            "<testsuite name='io.github.antonovichkorp.chemmod.core.A' tests='2' failures='0' errors='0'/>"
            "<testsuite tests='9'/>"
            "</testsuites>"
        )
        suites = collect(self.root)
        self.assertEqual(["io.github.antonovichkorp.chemmod.core.A"], [suite.name for suite in suites])
        self.assertEqual((2, 0, 0, 0), (suites[0].tests, suites[0].failures, suites[0].errors, suites[0].skipped))

    def test_missing_count_attributes_default_to_zero(self):
        path = self.root / "core" / "build" / "test-results" / "test" / "TEST-bare.xml"
        path.parent.mkdir(parents=True)
        path.write_text("<?xml version='1.0'?><testsuite name='io.github.antonovichkorp.chemmod.core.Bare'/>")
        suites = collect(self.root)
        self.assertEqual((0, 0, 0, 0), (suites[0].tests, suites[0].failures, suites[0].errors, suites[0].skipped))
        self.assertEqual(
            ["core core.Bare: tests=0 failures=0 errors=0 skipped=0",
             "JUnit totals: classes=1 tests=0 failures=0 errors=0 skipped=0"],
            render(suites),
        )

    def test_non_standard_class_names_are_not_truncated(self):
        write_report(self.root, "core", "io.github.antonovichkorp.chemmod.core.CTest")
        write_report(self.root, "core", "org.other.Suite")
        self.assertEqual(["core.CTest", "org.other.Suite"], sorted(s.short_name for s in collect(self.root)))


if __name__ == "__main__":
    unittest.main()
