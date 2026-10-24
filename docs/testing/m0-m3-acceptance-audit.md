# M0–M3 acceptance audit before continuing M4

**Audit date:** 2026-10-24
**Branch/commit audited:** `arena/bb7eef66-chemmod` / `20a022bd14ff9f8433fff1fffbf2d958e4c76225`
**Normative plan:** Google Drive `ПЛАН-ДРУГА.md` v1.3, §11–13 (exported and checked during this audit).
**Boundary references:** `docs/architecture.md`, ADR-0003, ADR-0004, and the M2/M3 player checklists.

This is a source/test evidence audit, **not** a milestone completion declaration. A source implementation, passing CI, prerelease publication, or a partial owner report is not owner acceptance. Unknown acceptance evidence is marked unknown rather than inferred.

## Summary

| Milestone | Source implementation | Automated evidence | Owner acceptance | Audit disposition |
|---|---|---|---|---|
| M0 — CLI core: parse, canonicalize, properties; F1 ethanol ≈64 °C | Core, parser, CLI and property predictor exist. | Core tests and CLI smoke checks passed in CI; however, the current ethanol fixture is 83.7 °C, not the literal plan criterion of ≈64 °C. | No explicit M0 sign-off found in repository/available conversation. | **Open:** resolve the plan/model discrepancy without breaking first-principles accuracy. |
| M1 — five automatically balanced reactions and validator | Implemented; the bundled rule set has 16 rules and the existing graph engine/balancer is used. | Reaction-engine, balancing and data-coherence tests passed in the recorded CI runs. | No explicit M1 sign-off found. | **Source/test gate met; owner sign-off not evidenced.** |
| M2 — ten in-game vials, data component, two languages; A1–A3, G1–G2 | Persistent substance/mixture components, localized content, a ten-position laboratory holder and vial interaction exist. | M2 prerelease checklist and GameTests exist; CI run `40128179574` passed the complete automated suite at `4a8ccdb`. | No complete M2 owner checklist/result found. | **Open:** A1's current-temperature phase display and A3's partial transfer into a steel flask are not demonstrated by the current vial-to-vial mixing path; verify whether these criteria were explicitly revised. |
| M3 — first machine, 1 Hz, profiling; B1 + F2 | Physical chemical reactor, 1 Hz evaluation, heat/catalyst path, transactional products and extensive headless GameTests exist. | `40128179574` passed; `40129926901` failed one Create GameTest (`TestProcessing.precisionMechanismCrafting`); the subsequent full run `40130776192` on `534daa1` passed, including all 49 classes / 362 JUnit tests and GameTests. No 100-machine Spark profile is present. | Partial owner feedback: reaction function was confirmed on a client; the reactor visibility issue was reported and hotfixed. The full checklist still explicitly leaves GUI, restart and multiplayer acceptance open. | **Open:** full real-pack acceptance and F2 profile remain outstanding. Also reconcile the milestone label “acetic acid” with implemented/user-story B1 ethanol → acetaldehyde + hydrogen before calling the exact milestone accepted. |

No M0–M3 milestone is claimed owner-accepted by this audit. This does not negate the documented partial reactor feedback.

## Evidence and gaps

### M0 — CLI core

- `Molecule`/`MoleculeGraph`, `SmilesLikeParser`, `MoleculeCanonicalizer`, `Validation`, `PropertyPredictor` and `core-cli` provide the named source features.
- `MoleculeTest` checks graph validity, formula/molar mass and distinguishing ethanol from dimethyl ether. Its current ethanol boiling-point assertion is **83.7 °C**; methanol is asserted at **64.3 °C**. CI's CLI smoke suite also exercises ethanol lookup, isomer identity and combustion balance (`docs/testing/ci-test-verification.md`).
- The plan's M0 row states “F1 (ethanol ≈64 °C)”. This conflicts both with the current model/test (83.7 °C) and the real substance represented by the string `CCO`; 64 °C is close to methanol, not ethanol. Do not force a per-substance calibration or alter the first-principles model just to match a likely plan typo. Owner confirmation or an authoritative plan correction is required to close the literal acceptance criterion.
- No explicit owner acceptance artifact for M0 was located.

### M1 — reactions and validation

- `ReactionRuleSet`, `ReactionEngine`, `ReactionBalancer`, and structural/condition rules implement data-defined transformations and exact atom/charge balancing. `default.json` has 16 rules, exceeding the plan's minimum of five without adding one row per substance.
- Core reaction and balancing suites passed in runs `40128179574` and `40129926901`; the latter's overall workflow is nevertheless **failed**, not green.
- No explicit owner acceptance artifact for M1 was located. As this is a core-only milestone, the current evidence is automated rather than a real-pack test.

### M2 — vial/data-component milestone

- `SubstanceContents` is a persistent graph-witness component (current schema v4); explicit mixtures use `MixtureContents` (schema v1). `SubstanceVialItem` derives formula and molar mass and displays amount/purity; creative examples and the physical holder exist. RU/EN language files are present. `v0.9.0-test.3` is the published M2-oriented checklist.
- Automated codec, mixing and world-side coverage exists, but the documented M2 checklist asks for manual item/tooltips, save/reload, and dedicated-server/client observation. The available record does not contain a completed owner checklist. A source-level key-set check on this audit found 238 keys in each RU/EN file and no missing keys; that does not substitute for an in-game language/UI check.
- The current vial tooltip shows formula and molar mass but does not show phase at the world/current temperature. No world thermal-field temperature source exists yet, so inventing a temperature or using an unrelated field would not meet the stated criterion.
- The current survival operation combines two filled vials into a vial and an empty vial. It does **not** demonstrate A3's stated partial-quantity transfer from a half-full vial into a steel flask. Before implementing that route, verify whether the plan's flask requirement was superseded by the later M2 vial/mixing scope. Keep A4 vessel/tank work in Stage B as instructed; do not silently pull that broader work into M2.
- `G1`'s versioned persistence is implemented and codec/GameTest-covered. The forward-looking “M2 save opens in M5 without loss” portion cannot be proven until the M5 migration exists and a real saved-world migration is exercised. `G2` has RU/EN resources, but presence of two files alone is not proof that all runtime text is translated; the owner run should include language switching.

### M3 — reactor

- The current implementation is aligned with user story B1: heated copper-catalyzed ethanol dehydrogenation to acetaldehyde and hydrogen, with server-side progress, exact products, physical slots and transactional commit. The automated regression checklist is in `docs/testing/m3-reactor-source.md`.
- The M3 milestone row in §13 instead says “first machine (acetic acid)”. That conflicts with §11 B1 and the documented implementation/checklist. This audit does not choose which plan text to discard. Resolve the inconsistency before asserting the exact milestone is closed.
- The plan's F2 requirement is 100 machines at 1 Hz under 5 ms/tick with a Spark profile attached. No profile or equivalent performance artifact was found. Unit/GameTests establish correctness, not this performance claim.
- `docs/testing/m3-reactor-source.md` explicitly leaves actual GUI rendering/localization, process restart, and two-client multiplayer open. The only recorded player feedback is partial: the owner confirmed the reaction worked and supplied a screenshot of the visibility defect; that is not full M3 acceptance.
- CI at `20a022b` passed 49 classes / 362 JUnit tests with zero failures/errors, but that full run failed the Create GameTest `TestProcessing.precisionMechanismCrafting`. The subsequent full CI run `40130776192` for audit commit `534daa1` passed, including the JUnit suite and GameTest harness. Use that subsequent run as current automated evidence; the earlier failure remains part of the history, not the current CI status. Local Gradle execution was unavailable because Java is not installed in this sandbox.

## M4 sequencing guard

A graph-based environmental reaction planner was added in `20a022b`, but it remains unconnected to `GasField` and world persistence. Per the active owner instruction, do not add further M4 integration or call M4 closed until the M0–M3 acceptance gaps above are resolved in order. M4 remains the next planned code milestone after that gate; the existing partial planner work does not substitute for M0–M3 acceptance.

## Next closure steps

1. Owner resolves the M0 ethanol ≈64 °C discrepancy and the M3 acetic-acid-vs-B1 route wording; preserve the physical model and documented requirements rather than guessing.
2. Confirm whether M2 A1 phase display and A3 steel-flask partial transfer remain active acceptance criteria or were superseded. If active, implement them without expanding A4 vessel/tank work beyond Stage B.
3. Keep the full CI/GameTest suite green on subsequent code changes. The latest full run `40130776192` is green; that is automated evidence, not owner acceptance.
4. Supply the matching build for owner real-pack acceptance of M2/M3; collect the completed checklists. Capture the required 100-machine Spark profile for F2.
5. Only after M0→M3 are accepted in order, continue M4 structural-identity migration, cell-update integration, and C1/C2 world acceptance. Do not create a release until all M0–M4 gates are closed.
