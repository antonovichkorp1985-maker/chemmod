# M4 — graph-identified environmental gas reactions (test candidate)

**Status:** M4 implementation and test packaging are in progress. The owner’s integrated real-pack review for M0–M4 is scheduled after implementation is complete; no separate per-milestone review is due during this work. `v1.0.2-test.2` remains the current test prerelease; this change does not authorize another prerelease or a production release.

## Implemented

- New world-field entries store validated molecular graphs under full canonical graph digests. Same-formula isomers remain distinct. Save data includes a structural witness and rejects key/witness mismatches; legacy formula-only entries remain inert for reactions rather than being guessed into structures.
- The sparse gas field transports molecular identities and applies existing `environmental` reaction rules through `GasCellReactionPlanner` / `ReactionEngine`; this is not a second reaction engine. The bundled hydrogen rule conserves atoms: `2 H2 + O2 -> 2 H2O`.
- A world fire, soul fire, or lava in the same loaded 4 m gas cell supplies the 500 K reaction gate. This is an ignition trigger mapped to a rule condition, not a temperature measurement, heat transfer model, or evidence that the mixture is at its LEL.
- Chlorine's graph is retained through transport; the world GameTest checks settling and composition-derived tint. Sparse diffusion now exchanges all six neighbour directions without processing an undirected pair twice.
- World gas field is attached to each level and serializes through its codec. CI covers graph persistence and refuses ignition without a source or from a different cell.
- Added operator-only finite-reference-sample tools for real-pack exercising: `/chem gas release <player> <reference-substance> <moles>` puts a named catalog molecule into that player's current world-field cell; `/chem gas inspect <player>` reports graph witnesses, moles, ideal-gas volume fraction/ppm against a dry-air reference inventory, and the resulting oxygen fraction. This is a concentration diagnostic, not a toxic-dose verdict. These are explicit test/admin injection and inspection commands, not a tank implementation, leak simulator, arbitrary-graph designer, or release of Stage-B vessel work.
- The server now checks each player's dry-air-relative oxygen fraction once per gas-field step. Below the existing 19.5% oxygen-deficient-atmosphere boundary, it applies a brief vanilla Weakness effect as a gameplay warning; normal dry air does not trigger it. A world GameTest covers both sides of the boundary. This is not a chlorine dose model, health-damage model, or protective-equipment simulation.
- Added an optional Create-powered kinetic gas exhaust. A vertical shaft is the power input; the block's low-speed gate and Create network speed determine its generic first-order gas removal. Create remains optional, and the standalone mod has no dependency on its types or registries. The reference rate is `0.02 s^-1` at 16 RPM, scaled linearly with speed and capped at `0.25 s^-1`; this is a provisional gameplay coefficient, not a measured fan curve or certified real-world air-change rate. The M4 GameTest checks that the unpowered block does not remove gas, then connects a Create creative motor and verifies shaft activation and world-field removal.


## Explicit limits — not completed by this candidate

- C1 remains incomplete. The player effect currently covers only oxygen displacement below the established 19.5% boundary. `/chem gas inspect` estimates concentrations against a fixed-volume dry standard-air inventory; humidity, pressure-regulated room displacement, cumulative exposure, and localized weather are not modeled. No chlorine exposure effect, health damage, mask, or filter is active. A chemical-specific dose/exposure rule and its source data must be specified rather than guessed.
- Sky-visible cells exchange with ambient air; the Create kinetic exhaust adds local first-order removal for gases in its 4 m cell. There is no duct network, measured fan curve, pressure-regulated replacement-air flow, or explicit background-air inventory. Exhausted gas leaves the modeled field rather than being transported through a duct to a specified outlet; there is no selective scrubber or replaceable filter. The test command injects reference gases; it does not model a tank leak.
- C2 currently verifies balanced in-cell water production after an in-cell fire/lava trigger. It does **not** enforce a validated H2/air LEL/UEL envelope, model a closed-room blast, apply volume-scaled entity damage, or budget block destruction. The current graph-derived Jones approximation is not validated for hydrogen's measured flammability range, so it must not be passed off as an accurate H2 LEL check. The existing Stage-B tank/`ClosedVessel` remains formula-only and unchanged.
- The 500 K gate is not a measured world temperature. A full ambient-air inventory, local temperature/pressure field, explosion energetics, and source/leak model require separate physical contracts; none are inferred here.
- The solver runs at 1 Hz and checks world ignition blocks in each occupied cell. The 200-active-cell `<2 ms/tick` target still requires an owner Spark profile on the target pack/hardware.
- Lennard–Jones transport lookup still has explicit coverage gaps; unknown data is not guessed.

## CI and owner acceptance

- GitHub CI run `40424889098` passed on `efeb5ea`, including the full build, core tests, world GameTests, checksum generation, and CI artifact upload. Gas GameTests cover oxygen-deficiency warning behavior; `GasAtmosphereTest` has 4 tests. Earlier CI runs caught and corrected the Java/Kotlin default-argument bridge mismatch and an invalid GameTest mock-player call. The Release workflow was skipped; no release was created.
- The `/chem gas release` and `/chem gas inspect` commands below exercise and report graph identity and dry-air-relative concentrations. The integrated real-pack review is scheduled after M0–M4 implementation is complete; the diagnostic does not claim chlorine exposure effects or an explosion model.
- Remaining M4 implementation scope includes validated chemical-specific exposure effects and protective equipment, a ducted ventilation and selective-scrubbing model, a validated LEL/explosion envelope, and target-pack performance profiling. The integrated M0–M4 real-pack review is scheduled after this implementation work is complete; F2 profiling remains an engineering task to include in that pass.

### Minimal world-field test commands

Run as an operator on a disposable copied world. These commands use names from the finite bundled reference directory; `chlorine`, `hydrogen`, and `oxygen` resolve through the existing name aliases.

```text
/chem gas release @s chlorine 4
/chem gas inspect @s
/chem gas release @s hydrogen 2
/chem gas release @s oxygen 1
```

The inspect/release feedback reports the cell's minimum block-coordinate origin. In a disposable area, use `originX+1`, `originY`, `originZ+1` for netherrack and `originX+1`, `originY+1`, `originZ+1` for a supported fire, keeping it off the player while inside the same cell. In this build the expected result is that graph-identified H2 and O2 are consumed according to the ordinary balanced rule and H2O appears; do not expect an explosion or treat these quantities as an LEL demonstration. Use a fresh cell without a fire for the refusal check, and another with fire at least one cell away to test locality. Never run these injection tests in a valued world without a backup.

### Mechanical exhaust real-pack check

In a separate disposable area with Create loaded, inject a small reference-gas sample into a cell that is not open to the sky, place `kinetic_gas_exhaust` in that same 4 m cell, and connect its vertical shaft port to a running Create kinetic network. Use `/chem gas inspect @s` before and after several gas-field steps. Stop or disconnect the shaft and repeat: a stationary exhaust must not remove gas. The built-in GameTest repeats the on/off comparison using Create's creative motor as a deterministic shaft source; the real-pack run is still needed to check placement, shaft engagement, rate feel, and interactions with the player's actual Create configuration.
