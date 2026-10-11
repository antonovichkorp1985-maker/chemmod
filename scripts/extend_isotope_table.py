#!/usr/bin/env python3
"""Add the nuclides that natural isotopic composition needs, and check the whole table.

`chemmod/isotopes.json` is the nuclide registry: `IsotopeData.fromJson` refuses a decay branch
whose daughter is not in it, and `DecayNetwork.of` refuses the same again. So before an element
can be given a natural composition, every nuclide that composition names has to be registered.

Four were missing and are added here:

    Rb-85  stable
    Rb-87  beta- to Sr-87, half-life 4.97e10 a = 1.568412e18 s
           (Brookhaven National Laboratory BNL-221671-2021-JAAM, table of primordial radionuclides:
            "37 rubidium Rb 87* 86.909 18 4.97e10 a")
           The beta energy is NOT sourced in this slice, so it carries the 0.0 sentinel and must
           not be read as a Q value; the half-life is what the specific activity needs.
    Sr-87  stable (Rb-87 daughter)
    Pb-204 stable (the only non-radiogenic lead isotope)

Everything else in the file is left exactly as it is. The script is idempotent: a record that is
already present is reported and skipped.

Run from the repository root:

    python3 scripts/extend_isotope_table.py

It rewrites `core/src/main/resources/chemmod/isotopes.json` keeping one record per line, then
validates the *whole* table and prints the report.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ISOTOPES_JSON = Path("core/src/main/resources/chemmod/isotopes.json")
ELEMENTS_JSON = Path("core/src/main/resources/chemmod/elements.json")
SECONDS_PER_YEAR = 31_557_600.0  # Julian year, the unit the rest of the table is expressed in

# (symbol, massNumber) -> record. Written out verbatim so the file stays readable.
ADDITIONS: list[str] = [
    '{"symbol":"Rb","massNumber":85}',
    '{"symbol":"Rb","massNumber":87,"halfLifeSeconds":1.568412e+18,'
    '"branches":[{"mode":"beta-","daughterSymbol":"Sr","daughterMassNumber":87,'
    '"fraction":1.0,"energyMeV":0.0}]}',
    '{"symbol":"Sr","massNumber":87}',
    '{"symbol":"Pb","massNumber":204}',
]

# Decay modes and what they do to (Z, A); must agree with DecayMode in the core.
MODES = {
    "alpha": (-2, -4),
    "beta-": (+1, 0),
    "beta+": (-1, 0),
    "electron-capture": (-1, 0),
}


def main() -> int:
    text = ISOTOPES_JSON.read_text(encoding="utf-8")
    elements = {
        e["symbol"]: e["atomicNumber"]
        for e in json.loads(ELEMENTS_JSON.read_text(encoding="utf-8"))["elements"]
    }
    document = json.loads(text)
    existing = {(r["symbol"], r["massNumber"]) for r in document["isotopes"]}

    # --- merge, rebuilding the record block so the commas stay legal ------
    lines = text.splitlines()
    record_re = re.compile(r'^\s*\{"symbol".*\},?\s*$')
    indices = [i for i, line in enumerate(lines) if record_re.match(line)]
    head, tail = lines[: indices[0]], lines[indices[-1] + 1 :]
    records = [lines[i].strip().rstrip(",") for i in indices]
    added: list[str] = []
    skipped: list[str] = []
    for record in ADDITIONS:
        parsed = json.loads(record)
        key = (parsed["symbol"], parsed["massNumber"])
        if key in existing:
            skipped.append(f"{key[0]}-{key[1]}")
            continue
        records.append(record)
        added.append(f"{key[0]}-{key[1]}")
    block = [f"    {record}," for record in records]
    block[-1] = block[-1].rstrip(",")
    new_text = "\n".join(head + block + tail) + "\n"
    new_document = json.loads(new_text)  # fails loudly before anything is written
    if len(new_document["isotopes"]) != len(document["isotopes"]) + len(added):
        raise SystemExit("record count changed by more than the additions")

    print(f"added:   {added or 'nothing'}")
    print(f"already present: {skipped or 'nothing'}")

    # --- validate the whole table -----------------------------------------
    table = json.loads(new_text)["isotopes"]
    by_key = {}
    problems: list[str] = []
    for record in table:
        key = (record["symbol"], record["massNumber"])
        if key in by_key:
            problems.append(f"duplicate nuclide {key[0]}-{key[1]}")
        by_key[key] = record
        z = elements.get(record["symbol"])
        if z is None:
            problems.append(f"{key[0]}-{key[1]}: symbol is not an element")
            continue
        if record["massNumber"] < z:
            problems.append(f"{key[0]}-{key[1]}: mass number below atomic number {z}")
        half_life = record.get("halfLifeSeconds")
        branches = record.get("branches", [])
        if half_life is not None and not (half_life > 0.0):
            problems.append(f"{key[0]}-{key[1]}: half-life must be positive")
        if half_life is None and branches:
            problems.append(f"{key[0]}-{key[1]}: branches without a half-life")
        if half_life is not None and not branches:
            problems.append(f"{key[0]}-{key[1]}: half-life without a decay branch")
        total = sum(b["fraction"] for b in branches)
        if branches and abs(total - 1.0) > 1e-6:
            problems.append(f"{key[0]}-{key[1]}: branch fractions sum to {total}")
        for branch in branches:
            if branch["mode"] not in MODES:
                problems.append(f"{key[0]}-{key[1]}: unknown mode '{branch['mode']}'")
                continue
            if branch["energyMeV"] < 0.0:
                problems.append(f"{key[0]}-{key[1]}: negative decay energy")
            dz, da = MODES[branch["mode"]]
            expected_z = z + dz
            expected_a = record["massNumber"] + da
            expected_symbol = next(
                (s for s, zz in elements.items() if zz == expected_z), None
            )
            if expected_symbol != branch["daughterSymbol"]:
                problems.append(
                    f"{key[0]}-{key[1]} {branch['mode']} must give {expected_symbol}, "
                    f"got {branch['daughterSymbol']}"
                )
            if branch["daughterMassNumber"] != expected_a:
                problems.append(
                    f"{key[0]}-{key[1]} {branch['mode']} must conserve A={expected_a}, "
                    f"got {branch['daughterMassNumber']}"
                )
            daughter = (branch["daughterSymbol"], branch["daughterMassNumber"])
            if daughter not in by_key and daughter not in {
                (r["symbol"], r["massNumber"]) for r in table
            }:
                problems.append(f"{key[0]}-{key[1]}: daughter {daughter[0]}-{daughter[1]} unregistered")

    # --- cycles: a back edge on the current path. Chains legitimately converge
    # (several branches of one series end in the same lead isotope), and convergence is
    # not a cycle, so the walk carries its own path instead of a visited set.
    cycles: set[str] = set()
    for record in table:
        start = (record["symbol"], record["massNumber"])
        stack = [(start, (start,))]
        while stack:
            current, path = stack.pop()
            for branch in by_key.get(current, {}).get("branches", []):
                nxt = (branch["daughterSymbol"], branch["daughterMassNumber"])
                if nxt in path:
                    cycles.add(" -> ".join(f"{s}-{a}" for s, a in path + (nxt,)))
                else:
                    stack.append((nxt, path + (nxt,)))
    problems.extend(f"decay cycle {cycle}" for cycle in sorted(cycles))

    # --- report ------------------------------------------------------------
    symbols = sorted({r["symbol"] for r in table})
    stable = [r for r in table if not r.get("branches")]
    sentinels = sorted(
        f'{r["symbol"]}-{r["massNumber"]}'
        for r in table
        for b in r.get("branches", [])
        if b["energyMeV"] == 0.0
    )
    print(f"nuclides: {len(table)} over {len(symbols)} elements; stable: {len(stable)}")
    print(f"zero-energy sentinels ({len(sentinels)}): {', '.join(sentinels)}")
    print(f"Rb-87 half-life check: {by_key[('Rb', 87)]['halfLifeSeconds']:.6g} s "
          f"= {by_key[('Rb', 87)]['halfLifeSeconds'] / SECONDS_PER_YEAR:.4g} a")

    if added and not problems:
        ISOTOPES_JSON.write_text(new_text, encoding="utf-8")
    if problems:
        print("CHECKS FAILED:")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print("all checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
