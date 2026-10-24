# M0–M3 acceptance audit before continuing M4

**Audit date:** 2026-10-24
**Branch/code commit audited:** `arena/bb7eef66-chemmod` / `671f9dc` (M2 source slice; M4 planner baseline is `20a022b`)
**Normative plan:** Google Drive `ПЛАН-ДРУГА.md` v1.3, §11–13 (exported and checked during this audit).
**Boundary references:** `docs/architecture.md`, ADR-0003, ADR-0004, and the M2/M3 player checklists.

This is a source/test evidence audit, **not** a milestone completion declaration. A source implementation, passing CI, prerelease publication, or a partial owner report is not owner acceptance. Unknown acceptance evidence is marked unknown rather than inferred.

## Summary

| Milestone | Source implementation | Automated evidence | Owner acceptance | Audit disposition |
|---|---|---|---|---|
| M0 — CLI core: parse, canonicalize, properties; F1 ethanol ≈64 °C | Core, parser, CLI and property predictor exist. | Core tests and CLI smoke checks passed in CI; the current graph model predicts ethanol at 83.7 °C. | Owner clarified on 2026-10-24 that properties must follow realistic general models/rules, not arbitrary per-substance calibration. This is design direction, not milestone sign-off. | **Source/test gate met under realistic-model direction.** Correct the literal 64 °C plan example: it cannot be required for ethanol; compare the derived prediction against real ethanol data and the model's stated error range. |
| M1 — five automatically balanced reactions and validator | Implemented; the bundled rule set has 16 rules and the existing graph engine/balancer is used. | Reaction-engine, balancing and data-coherence tests passed in the recorded CI runs. | No explicit M1 sign-off found. | **Source/test gate met; owner sign-off not evidenced.** |
| M2 — ten in-game vials, data component, two languages; A1–A3, G1–G2 | Persistent substance/mixture components, localized content, ten-position holder and vial interaction exist. The M2 source now includes graph-derived phase estimates and exact vial-to-steel-flask transfer. | Full CI/GameTest run `40132418812` passed on `671f9dc`: 50 JUnit classes / 364 tests with zero failures/errors; resource tests and GameTest harness also passed. | Owner confirmed on 2026-10-24 that both A1 phase display and A3 steel-flask transfer remain active. This confirms scope, not completion/acceptance. | **Open:** obtain owner real-pack acceptance. A1 currently uses an altitude-based Earth ISA estimate; actual room/biome temperatures are not modeled. |
| M3 — first machine, 1 Hz, profiling; B1 + F2 | Physical chemical reactor, 1 Hz evaluation, heat/catalyst path, transactional products and extensive headless GameTests exist. This change adds two data-defined downstream acetic-acid pathways and selective atom transfer. | Full CI run `40134273860` passed on `c1ca94a`: 50 JUnit classes / 367 tests, zero failures/errors, plus the GameTest harness and both new reactor-pathway tests. Initial run `40133876566` exposed two route-test timing/fixture issues, corrected before the green run. A matching manual-acceptance JAR is available as a CI artifact. No 100-machine Spark profile is present. | Partial owner feedback: reaction function was confirmed on a client; the reactor visibility issue was reported and hotfixed. The full checklist still explicitly leaves GUI, restart and multiplayer acceptance open. No owner chemistry sign-off for the simplified catalyst/selectivity abstraction is recorded. | **Open:** obtain owner chemistry/real-pack review, complete the full real-pack acceptance, and capture the F2 profile. The pathways and limitations are documented in `m3-acetic-acid-pathways.md`; CI is not owner acceptance. |

No M0–M3 milestone is claimed owner-accepted by this audit. This does not negate the documented partial reactor feedback.

## Evidence and gaps

### M0 — CLI core

- `Molecule`/`MoleculeGraph`, `SmilesLikeParser`, `MoleculeCanonicalizer`, `Validation`, `PropertyPredictor` and `core-cli` provide the named source features.
- `MoleculeTest` checks graph validity, formula/molar mass and distinguishing ethanol from dimethyl ether. Its current ethanol boiling-point assertion is **83.7 °C**; methanol is asserted at **64.3 °C**. CI's CLI smoke suite also exercises ethanol lookup, isomer identity and combustion balance (`docs/testing/ci-test-verification.md`).
- The plan's M0 row states “F1 (ethanol ≈64 °C)”. The owner clarified on 2026-10-24 that behavior and properties must be realistic and derived from general models/rules. Thus 64 °C is not a hard output target for ethanol; 64 °C is close to methanol, while ethanol's measured normal boiling point is about 78 °C. Preserve the graph-derived model, report its error against real data, and correct the plan example rather than adding a substance-specific calibration.
- This resolves the design direction, but no explicit owner acceptance of the M0 implementation was given.

### M1 — reactions and validation

- `ReactionRuleSet`, `ReactionEngine`, `ReactionBalancer`, and structural/condition rules implement data-defined transformations and exact atom/charge balancing. `default.json` has 16 rules, exceeding the plan's minimum of five without adding one row per substance.
- Core reaction and balancing suites passed in runs `40128179574` and `40129926901`; the latter's overall workflow is nevertheless **failed**, not green.
- No explicit owner acceptance artifact for M1 was located. As this is a core-only milestone, the current evidence is automated rather than a real-pack test.

### M2 — vial/data-component milestone

- `SubstanceContents` is a persistent graph-witness component (current schema v4); explicit mixtures use `MixtureContents` (schema v1). `SubstanceVialItem` derives formula and molar mass and displays amount/purity; creative examples and the physical holder exist. RU/EN language files are present. `v0.9.0-test.3` is the published M2-oriented checklist.
- Automated codec, mixing and world-side coverage exists, but the documented M2 checklist asks for manual item/tooltips, save/reload, and dedicated-server/client observation. The available record does not contain a completed owner checklist. Current RU/EN files each contain 252 keys; CI enforces matching key sets. That does not substitute for an in-game language/UI check.
- The A1 source implementation now derives gas/condensed state from the molecule's predicted boiling point and the Earth ISA T/P at the inspecting player's Overworld altitude. The tooltip labels this as an estimate; the model still cannot distinguish liquid from solid, calculate mixture equilibrium, or represent actual room/biome heating. A true local thermal field remains future work.
- The A3 source implementation now adds a steel-flask item and a snapshot-checked, exact whole-portion transfer from one filled vial into one empty flask. The source vial remains empty and the flask preserves the original component payload. This covers the plan's half-full sample (the amount is data, not a substance row); it does not claim a capacity model. Keep A4 vessel compatibility/corrosion work in Stage B as instructed.
- `G1`'s versioned persistence is implemented and codec/GameTest-covered. The forward-looking “M2 save opens in M5 without loss” portion cannot be proven until the M5 migration exists and a real saved-world migration is exercised. `G2` has RU/EN resources and CI checks exact key parity; the owner run should still include in-game language switching and visible text.

### M3 — reactor

- The existing B1 machine stage is heated copper-catalyzed ethanol dehydrogenation to acetaldehyde and hydrogen, with server-side progress, exact products, physical slots and transactional commit. The same rule engine now has aldehyde oxygen-atom transfer and two acetic-acid pathways: direct acetaldehyde oxidation, and aldehyde hydration followed by dehydrogenation. Detailed balances and limitations are in `docs/testing/m3-acetic-acid-pathways.md`; the automated regression checklist remains `docs/testing/m3-reactor-source.md`.
- On 2026-10-24 the owner clarified that real synthesis may have several routes and stages, and ChemMod should model those routes as stages too. Therefore acetic acid in §13 is not a competing alternative to B1: it is the intended downstream result to be built from realistic data-defined steps/pathways. CI run `40134273860` passed both new reactor pathway GameTests and the full suite (50 JUnit classes / 367 tests), after an earlier failed test attempt was corrected. The abstract catalyst/selectivity treatment still needs owner review. Keep each stage in existing reaction rules/engine and do not tabulate outputs per substance.
- The plan's F2 requirement is 100 machines at 1 Hz under 5 ms/tick with a Spark profile attached. No profile or equivalent performance artifact was found. Unit/GameTests establish correctness, not this performance claim.
- `docs/testing/m3-reactor-source.md` explicitly leaves actual GUI rendering/localization, process restart, and two-client multiplayer open. The only recorded player feedback is partial: the owner confirmed the reaction worked and supplied a screenshot of the visibility defect; that is not full M3 acceptance.
- CI at `20a022b` passed 49 classes / 362 JUnit tests with zero failures/errors, but that full run failed the Create GameTest `TestProcessing.precisionMechanismCrafting`. The subsequent runs `40130776192` (`534daa1`), `40131033228` (`953b568`), and latest `40132418812` (`671f9dc`) passed, including the JUnit suite and GameTest harness; the latest totals are 50 classes / 364 JUnit tests. Use the latest run as automated evidence; the earlier failure remains in history, not current CI status. Local Gradle execution was unavailable because Java is not installed in this sandbox.

## M4 sequencing guard

A graph-based environmental reaction planner was added in `20a022b`, but it remains unconnected to `GasField` and world persistence. Per the active owner instruction, do not add further M4 integration or call M4 closed until the M0–M3 acceptance gaps above are resolved in order. M4 remains the next planned code milestone after that gate; the existing partial planner work does not substitute for M0–M3 acceptance.

## Next closure steps

1. Carry the owner's realism direction into the acceptance record: judge ethanol's graph-derived prediction against real data and documented model error, not the erroneous ≈64 °C literal; build M3's acetic-acid result through real data-defined reaction stages and multiple pathways.
2. The two M2 gaps are implemented in source; deliver matching build `671f9dc` for the owner real-pack checklist in `docs/testing/m2-vial-steel-flask-source.md`. Record any issue with the stated ISA/phase-model limitations rather than calling it measured room temperature.
3. Keep full CI/GameTests green on subsequent code changes. Latest full run `40132418812` is green; this is automated evidence, not owner acceptance.
4. Collect completed M2/M3 real-pack checklists and capture the required 100-machine Spark profile for F2.
5. Only after M0→M3 are accepted in order, continue M4 structural-identity migration, cell-update integration, and C1/C2 world acceptance. Do not create a release until all M0–M4 gates are closed.
