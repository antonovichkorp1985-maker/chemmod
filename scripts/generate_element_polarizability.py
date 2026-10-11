#!/usr/bin/env python3
"""Fill ``polarizibilityCubicAngstroms`` in ``elements.json`` from one source.

The single source is the recommended column of Figure 1 ("Recommended values from
Table 1 for the atomic polarizabilities (atomic units; estimated uncertainties in
parentheses) of elements Z = 1-120") of

    P. Schwerdtfeger, J. K. Nagle, "2018 Table of static dipole polarizabilities of
    the neutral elements in the periodic table", Mol. Phys. 117 (2019) 1200-1225,
    https://doi.org/10.1080/00268976.2018.1535143
    updated table: https://ctcp.massey.ac.nz/2018Tablepol.pdf

The same document gives the conversion it uses: 1 a.u. = 0.14818471 A^3.

Every figure cell was cross-read against the Table 1 "recommended" row for that
element where the PDF text reached it (Z = 1..85 plus the superheavy rows the
publisher's index exposed): Se 28.9 -> 29(1), Br 21 -> 21(1), Kr 16.78 -> 16.8(0),
Y 162 -> 162(12), Zr 112, Nb 98, Mo 87, Tc 79, Ru 72, Rh 66, Pd 26.14 -> 26.1(1),
Ag 55, Yb 139.3 -> 139(6), Lu 137, Hf 103, Ta 74, W 68, Re 62, Os 57, Ir 54,
Pt 48, Au 36 -> 36±3, Hg 33.91 -> 33.9(4), Tl 50, Pb 47, Bi 48, Po 44, At 42.

Livermorium (Z = 116) is deliberately left without a value: the paper states it
covers Z = 1-120 "except for livermorium where we could not find a reliable value".
A gap stays a gap rather than becoming an invented number; ``Element`` keeps the
field nullable and ``FormulaCalculator.polarizibilityCubicAngstroms`` returns null
for a molecule that contains it.

Run it from the repository root:

    python3 scripts/generate_element_polarizability.py

It rewrites ``core/src/main/resources/chemmod/elements.json`` in place, keeping the
one-element-per-line layout, and prints the checks it performed.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ELEMENTS_JSON = Path("core/src/main/resources/chemmod/elements.json")
AU_TO_CUBIC_ANGSTROM = 0.14818471  # stated by the source document itself

# Z -> (alpha_D in atomic units, quoted uncertainty in atomic units).
# Transcribed from Figure 1, the paper's own "recommended" column.
RECOMMENDED_ATOMIC_UNITS: dict[int, tuple[float, float]] = {
    1: (4.50, 0.2), 2: (1.38, 0.01),
    3: (164.0, 0.5), 4: (37.7, 0.1), 5: (20.5, 0.1), 6: (11.3, 0.2), 7: (7.4, 0.2),
    8: (5.3, 0.2), 9: (3.74, 0.08), 10: (2.66, 0.01),
    11: (163.0, 1.0), 12: (71.2, 0.4), 13: (57.8, 0.1), 14: (37.3, 0.7), 15: (25.0, 1.0),
    16: (19.4, 0.1), 17: (14.6, 0.2), 18: (11.1, 0.02),
    19: (290.0, 1.0), 20: (161.0, 4.0), 21: (97.0, 10.0), 22: (100.0, 10.0),
    23: (87.0, 10.0), 24: (83.0, 12.0), 25: (68.0, 9.0), 26: (62.0, 4.0), 27: (55.0, 4.0),
    28: (49.0, 3.0), 29: (47.0, 1.0), 30: (38.7, 0.3), 31: (50.0, 3.0), 32: (40.0, 1.0),
    33: (30.0, 1.0), 34: (29.0, 1.0), 35: (21.0, 1.0), 36: (16.8, 0.02),
    37: (320.0, 1.0), 38: (197.0, 1.0), 39: (162.0, 12.0), 40: (112.0, 13.0),
    41: (98.0, 8.0), 42: (87.0, 6.0), 43: (79.0, 10.0), 44: (72.0, 10.0), 45: (66.0, 10.0),
    46: (26.1, 0.1), 47: (55.0, 8.0), 48: (46.0, 2.0), 49: (65.0, 4.0), 50: (53.0, 6.0),
    51: (43.0, 2.0), 52: (38.0, 4.0), 53: (32.9, 0.1), 54: (27.3, 0.2),
    55: (401.0, 1.0), 56: (272.0, 10.0),
    57: (215.0, 20.0), 58: (205.0, 20.0), 59: (216.0, 20.0), 60: (208.0, 20.0),
    61: (200.0, 20.0), 62: (192.0, 20.0), 63: (184.0, 20.0), 64: (158.0, 20.0),
    65: (170.0, 20.0), 66: (165.0, 15.0), 67: (156.0, 10.0), 68: (150.0, 15.0),
    69: (144.0, 15.0), 70: (139.0, 6.0),
    71: (137.0, 7.0), 72: (103.0, 6.0), 73: (74.0, 20.0), 74: (68.0, 15.0),
    75: (62.0, 3.0), 76: (57.0, 3.0), 77: (54.0, 7.0), 78: (48.0, 4.0), 79: (36.0, 3.0),
    80: (33.9, 0.4), 81: (50.0, 2.0), 82: (47.0, 3.0), 83: (48.0, 4.0), 84: (44.0, 4.0),
    85: (42.0, 4.0), 86: (35.0, 2.0),
    87: (318.0, 2.0), 88: (246.0, 4.0),
    89: (203.0, 12.0), 90: (217.0, 54.0), 91: (154.0, 20.0), 92: (129.0, 17.0),
    93: (151.0, 20.0), 94: (132.0, 20.0), 95: (131.0, 25.0), 96: (144.0, 25.0),
    97: (125.0, 25.0), 98: (122.0, 20.0), 99: (118.0, 20.0), 100: (113.0, 20.0),
    101: (109.0, 20.0), 102: (110.0, 6.0),
    103: (320.0, 20.0), 104: (112.0, 10.0), 105: (42.0, 4.0), 106: (40.0, 4.0),
    107: (38.0, 4.0), 108: (36.0, 4.0), 109: (34.0, 3.0), 110: (32.0, 3.0),
    111: (32.0, 6.0), 112: (28.0, 2.0), 113: (29.0, 2.0), 114: (31.0, 4.0),
    115: (71.0, 20.0), 117: (76.0, 15.0), 118: (58.0, 6.0),
}

# Z = 116 (Lv) is absent on purpose: no reliable value in the source.
DOCUMENTED_GAPS: dict[int, str] = {
    116: "the 2018 table reports no reliable value for livermorium",
}

ELEMENT_LINE = re.compile(
    r'^(\s*\{"symbol":"(?P<symbol>[A-Za-z]{1,3})","name":"[^"]*",'
    r'"atomicNumber":(?P<z>\d+),"atomicMass":[^,]*,"valences":\[[^\]]*\],'
    r'"valenceElectrons":\d+)(?P<rest>,?"polarizibilityCubicAngstroms":[0-9.]+)?(?P<tail>\},?)$'
)


def cubic_angstroms(atomic_number: int) -> float | None:
    if atomic_number not in RECOMMENDED_ATOMIC_UNITS:
        return None
    value_au, _uncertainty = RECOMMENDED_ATOMIC_UNITS[atomic_number]
    return round(value_au * AU_TO_CUBIC_ANGSTROM, 3)


def main() -> int:
    original_text = ELEMENTS_JSON.read_text(encoding="utf-8")
    document = json.loads(original_text)
    elements = document["elements"]

    # --- checks that must hold before anything is written -------------------
    failures: list[str] = []
    if [e["atomicNumber"] for e in elements] != list(range(1, 119)):
        failures.append("elements.json is not Z = 1..118 in order")
    unknown_z = sorted(
        (set(RECOMMENDED_ATOMIC_UNITS) | set(DOCUMENTED_GAPS)) - set(range(1, 119))
    )
    if unknown_z:
        failures.append(f"source table mentions elements outside the table: {unknown_z}")
    covered = set(RECOMMENDED_ATOMIC_UNITS) | set(DOCUMENTED_GAPS)
    if covered != set(range(1, 119)):
        failures.append(f"source table and documented gaps do not cover Z = 1..118: "
                        f"missing {sorted(set(range(1, 119)) - covered)}")
    if failures:
        print("REFUSING TO WRITE:")
        for failure in failures:
            print(f"  - {failure}")
        return 1

    # --- rewrite, one element per line, key order untouched -----------------
    rewritten: list[str] = []
    filled = 0
    gaps: list[str] = []
    for line in original_text.splitlines():
        match = ELEMENT_LINE.match(line)
        if match is None:
            rewritten.append(line)
            continue
        z = int(match.group("z"))
        value = cubic_angstroms(z)
        if value is None:
            gaps.append(f"{match.group('symbol')} ({DOCUMENTED_GAPS[z]})")
            rewritten.append(f'{match.group(1)}{match.group("tail")}')
            continue
        filled += 1
        rewritten.append(
            f'{match.group(1)},"polarizibilityCubicAngstroms":{value}'
            f'{match.group("tail")}'
        )
    new_text = "\n".join(rewritten) + "\n"

    # --- checks on the result ------------------------------------------------
    new_document = json.loads(new_text)
    new_elements = {e["atomicNumber"]: e for e in new_document["elements"]}
    old_elements = {e["atomicNumber"]: e for e in elements}
    if set(new_document) != set(document):
        failures.append("top-level keys changed")
    if new_document["schemaVersion"] != document["schemaVersion"]:
        failures.append("schemaVersion changed")
    if len(new_elements) != 118:
        failures.append(f"element count is {len(new_elements)}, expected 118")
    for z, element in new_elements.items():
        before = dict(old_elements[z])
        after = dict(element)
        before.pop("polarizibilityCubicAngstroms", None)
        after.pop("polarizibilityCubicAngstroms", None)
        if before != after:
            failures.append(f"Z={z}: a non-polarizability field changed")
        value = element.get("polarizibilityCubicAngstroms")
        if value is not None and value <= 0.0:
            failures.append(f"Z={z}: polarizability must be positive where it is known")
        if z in RECOMMENDED_ATOMIC_UNITS and value is None:
            failures.append(f"Z={z}: source has a value but the JSON does not")
        if z in DOCUMENTED_GAPS and value is not None:
            failures.append(f"Z={z}: documented gap got a value")
    if len(rewritten) != len(original_text.splitlines()):
        failures.append("line count changed; the layout must stay one element per line")

    print(f"filled {filled} of {len(new_elements)} elements")
    print(f"documented gaps ({len(gaps)}):")
    for gap in gaps:
        print(f"  - {gap}")

    # --- chemistry spot checks a reviewer can verify by hand ----------------
    spot = {"H": 0.667, "C": 1.674, "Cl": 2.163, "Fe": 9.187, "Au": 5.335, "U": 19.116}
    by_symbol = {e["symbol"]: e for e in new_elements.values()}
    for symbol, expected in spot.items():
        actual = by_symbol[symbol]["polarizibilityCubicAngstroms"]
        status = "ok" if abs(actual - expected) < 5e-4 else "MISMATCH"
        print(f"  {symbol:>2} = {actual:.3f} A^3 (expected {expected:.3f}) {status}")
        if status == "MISMATCH":
            failures.append(f"spot check failed for {symbol}")

    def sum_alpha(formula: dict[str, int]) -> float:
        return round(sum(by_symbol[s]["polarizibilityCubicAngstroms"] * n
                         for s, n in formula.items()), 3)

    # Additive free-atom sums are NOT measured molecular polarizabilities. For a
    # halide the scheme is close (CCl4 +3 %), for a hydride it overshoots because a
    # hydrogen bound to carbon is far less polarizable than a free hydrogen atom
    # (CH4 +63 %). Both numbers are printed so the gap stays visible; any model that
    # consumes the sum has to treat it as a fitted descriptor and carry its own
    # intercept, which is exactly what the boiling-point branches do.
    measured_molecule = {("C", "Cl", 4): 10.002, ("C", "H", 4): 2.66}
    for (heavy, light, count), reference in measured_molecule.items():
        formula = {heavy: 1, light: count}
        name = "".join(f"{s}{n if n > 1 else ''}" for s, n in formula.items())
        total = sum_alpha(formula)
        deviation = 100.0 * (total - reference) / reference
        print(f"  {name:>4} additive {total:6.3f} A^3 vs molecule {reference:6.3f} A^3 "
              f"({deviation:+.0f} %)")
    ccl4 = sum_alpha({"C": 1, "Cl": 4})
    if not 9.5 < ccl4 < 11.0:
        failures.append(f"CCl4 sum {ccl4} is far from the measured 10.002")

    # --- informational: the source's own anomalies, printed not asserted ----
    for label, chain in {
        "group 18 He-Xe": [2, 10, 18, 36, 54],
        "group 17 F-I": [9, 17, 35, 53],
        "period 3 Na-Ar": [11, 12, 13, 14, 15, 16, 17, 18],
    }.items():
        values = [new_elements[z]["polarizibilityCubicAngstroms"] for z in chain]
        print(f"  {label}: {values}")

    if failures:
        print("CHECKS FAILED:")
        for failure in failures:
            print(f"  - {failure}")
        return 1

    ELEMENTS_JSON.write_text(new_text, encoding="utf-8")
    print(f"wrote {ELEMENTS_JSON}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
