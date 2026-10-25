# M4 — graph-identified environmental gas reactions (test candidate)

**Status:** implementation and test packaging in progress; neither M4 nor M0–M3 is owner-accepted. The owner has now explicitly requested an immutable **player-test prerelease** after this implementation pass. This is not authorization to call milestones accepted or to publish a production release.

## Implemented

- New world-field entries store validated molecular graphs under full canonical graph digests. Same-formula isomers remain distinct. Save data includes a structural witness and rejects key/witness mismatches; legacy formula-only entries remain inert for reactions rather than being guessed into structures.
- The sparse gas field transports molecular identities and applies existing `environmental` reaction rules through `GasCellReactionPlanner` / `ReactionEngine`; this is not a second reaction engine. The bundled hydrogen rule conserves atoms: `2 H2 + O2 -> 2 H2O`.
- A world fire, soul fire, or lava in the same loaded 4 m gas cell supplies the 500 K reaction gate. This is an ignition trigger mapped to a rule condition, not a temperature measurement, heat transfer model, or evidence that the mixture is at its LEL.
- Chlorine's graph is retained through transport; the world GameTest checks settling and composition-derived tint. Sparse diffusion now exchanges all six neighbour directions without processing an undirected pair twice.
- World gas field is attached to each level and serializes through its codec. CI covers graph persistence and refuses ignition without a source or from a different cell.
- Added operator-only finite-reference-sample tools for real-pack exercising: `/chem gas release <player> <reference-substance> <moles>` puts a named catalog molecule into that player's current world-field cell; `/chem gas inspect <player>` reports graph witnesses and amounts in that cell. These are explicit test/admin injection and inspection commands, not a tank implementation, leak simulator, arbitrary-graph designer, or release of Stage-B vessel work.

## Explicit limits — not completed by this candidate

- C1 is only a release/transport/settling/visibility test path. `GasToxicity` currently gives mechanism assessments in core but is not connected to player exposure damage/debuffs; no chlorine-mask or filter model is active. A correct dose/exposure rule and its source data must be specified rather than guessed.
- Sky-visible cells exchange with ambient air. There is no block-based room fan/ventilation machine, and the gas field does not store background air as explicit molecular moles. The test command injects reference gases; it does not model a tank leak.
- C2 currently verifies balanced in-cell water production after an in-cell fire/lava trigger. It does **not** enforce a validated H2/air LEL/UEL envelope, model a closed-room blast, apply volume-scaled entity damage, or budget block destruction. The current graph-derived Jones approximation is not validated for hydrogen's measured flammability range, so it must not be passed off as an accurate H2 LEL check. The existing Stage-B tank/`ClosedVessel` remains formula-only and unchanged.
- The 500 K gate is not a measured world temperature. A full ambient-air inventory, local temperature/pressure field, explosion energetics, and source/leak model require separate physical contracts; none are inferred here.
- The solver runs at 1 Hz and checks world ignition blocks in each occupied cell. The 200-active-cell `<2 ms/tick` target still requires an owner Spark profile on the target pack/hardware.
- Lennard–Jones transport lookup still has explicit coverage gaps; unknown data is not guessed.

## CI and owner acceptance

- Latest completed code CI before this command addition: run `40208054225` on `b5b55cc` passed, including core tests and NeoForge GameTests. The command addition and current release candidate must pass a fresh CI run before publication.
- Owner's real-pack test should use the new commands below and record launch, RU/EN commands, chlorine transport/visibility, hydrogen rule output, save/restart, and any logs. An absent chlorine debuff or absent explosion is expected in this slice and must be recorded as a known gap, not as pass for full C1/C2.
- Full M4 acceptance still requires the separately scoped exposure, ventilation, LEL/explosion, and performance work above plus owner review. M0–M3 real-pack checklists and F2 Spark remain separate open gates; the owner requested to perform those reviews after this M4 implementation pass.

### Minimal world-field test commands

Run as an operator on a disposable copied world. These commands use names from the finite bundled reference directory; `chlorine`, `hydrogen`, and `oxygen` resolve through the existing name aliases.

```text
/chem gas release @s chlorine 4
/chem gas inspect @s
/chem gas release @s hydrogen 2
/chem gas release @s oxygen 1
```

The inspect/release feedback reports the cell's minimum block-coordinate origin. In a disposable area, use `originX+1`, `originY`, `originZ+1` for netherrack and `originX+1`, `originY+1`, `originZ+1` for a supported fire, keeping it off the player while inside the same cell. In this build the expected result is that graph-identified H2 and O2 are consumed according to the ordinary balanced rule and H2O appears; do not expect an explosion or treat these quantities as an LEL demonstration. Use a fresh cell without a fire for the refusal check, and another with fire at least one cell away to test locality. Never run these injection tests in a valued world without a backup.
