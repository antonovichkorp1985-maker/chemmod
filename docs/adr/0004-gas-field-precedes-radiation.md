# ADR-0004: the gas field comes first and owns transported radiation sources

- Status: Accepted
- Date: 2026-10-11
- Updated: 2026-10-11

## Context

`ПЛАН-ДРУГА.md` v1.3 specifies two world-scale fields. The gas field (§5, owner
edit of 27.09) is a sparse field with a configurable cell — bench 4³/8³, local
refinement down to a block, 16³ only as a far LOD — under a budget of <2 ms per
tick for 200 cells; it diffuses by concentration gradient every N ticks with a
vertical buoyancy bias, and its parameters are data: molar mass, diffusion
coefficient, toxicity, LEL/UEL, reactivity with the environment. The radiation
field (§6) computes instantaneous dose on demand from sources by distance and
shielding, stores only accumulated dose in a sparse grid with a configurable
cell, and follows per-kind range rules (α centimetres, β blocks,
γ `I = I₀·exp(−μx)`, n moderation and capture).

The two are coupled by the plan itself: **Rn-220 is a radioactive gas and is
carried by the gas system** (radon in uranium mine workings is the cited real
hazard). §8.5 then requires sensor blocks to read the core fields directly.

Recorded state at the time of this decision, verified against the repository:

- `RadiationField` exists in core and matches the §6 design (on-demand dose,
  sparse accumulation grid, LOD by aggregation), but a `FieldSource` is pinned to
  one position.
- `IsotopeComposition` gives the natural isotopic composition of seven elements
  and the specific activity that follows from it; `DecayNetwork` already provides
  `secularEquilibriumActivityBecquerel`.
- There is no phase model: the only `GAS` in the code is a material form in
  `Definitions.kt` and the gas constant. Nothing decides gas/liquid/solid.
- There is no fluid or vessel capacity model in `mod/`: `FluidStack`,
  `millibucket` and `IFluidHandler` return zero matches across `mod/src/main`.
  The Aeronautics Plus instruction makes this the gate for any AP interface —
  "when a ChemMod tank in the world can be filled, AP reads its contents; until
  then ChemMod writes no API for AP".
- `isotopes.json` has no gamma branch type, and no table of μ exists for any
  shielding material, so §6's gamma rule has an implementation but no emission.
- Radiation gameplay was recorded as **frozen by the owner's decision** in
  `docs/handover/2026-10-10-plan-v1.3-new-items.md`. The owner unfroze it on
  2026-10-11 with the order: chemistry base → gas → radioactivity.

The plan lists LEL/UEL and toxicity among the gas parameters held in data. Taken
literally that is a per-substance table, which ADR-0001 forbids: identity and
properties come from the molecular graph, not from a catalogue row.

## Decision

1. **Order.** Finish the chemistry base, then build the gas field, then the
   radiation zone. No world-side radiation work starts before the gas field
   exists, because the plan's own radon case makes radiation a consumer of gas
   transport.

2. **The gas field owns transported sources.** When gas carrying a radioactive
   species enters a cell, the gas system registers a source in that cell's
   `RadiationField`; when it leaves, it removes it. A source is created and
   destroyed, never moved. `RadiationField` stays a pure consumer and learns
   nothing about gas.

3. **Flammability limits are derived, not tabulated.** The stoichiometric
   concentration `C_st` follows from balancing combustion, then
   `LFL = 0.55·C_st` and `UFL = 3.5·C_st` (Jones, US Bureau of Mines, 1938; a
   2023 *Fuel* fit over the same idea gives c₀ = 0.476 with small nitrogen and
   halogen corrections, which stay class rules). Mixtures use Le Chatelier,
   `1/LFL_mix = Σ yᵢ/LFLᵢ`. Where combustion cannot be balanced — today
   `estimateCombustionEnthalpy` accepts only neutral C/H/O species — the model
   refuses instead of guessing.

4. **Toxicity is modelled by mechanism, not by a number.** Acid gas plus moisture
   yields acid; oxidisers, simple asphyxiants by oxygen displacement, and
   CO-type binding are mechanisms the molecule's classes and reactions already
   express. Where no mechanism applies the model reports that it has no toxicity
   model rather than inventing a dose.

5. **Secular equilibrium is the source term for ore.** A radioactive rock emits
   as its whole chain in equilibrium, per the plan's "activities of the members
   equal the activity of the parent", not as its parent nuclide alone.

6. **Gamma needs data before it needs code.** A gamma branch type in the decay
   scheme and a table of linear attenuation coefficients per material are
   prerequisites; until they exist, γ is a shielding rule with nothing to
   attenuate.

## Consequences

The work queue this implies, in order:

- **A — chemistry base.** A1 substance → element masses (needed by both fields).
  A2 the remaining property holes: nitriles, esters, carboxylic acids,
  polyhalides, and removing the `exactFormulaCelsius = {"H2O": 100.0}` anchor
  with a ratchet test in the same commit. A3 a phase rule over the boiling point.
  A4 §8.1 container-material × substance matrix and a real vessel capacity in the
  world — the AP gate. Handover discrepancies 1 and 2 are already closed: the
  element table covers H through Og, silicon included, and
  `ContentCoherenceTest` fails if a mineral names an element the bundled table
  does not define. The silicon chain of epic E0 is therefore no longer blocked by
  content, but it is not on this path.
- **B — gas field.** Molar mass and buoyancy are already derivable from a
  formula. The diffusion coefficient is intended to come from a Chapman–Enskog
  style rule over atomic diffusion volumes — to be sourced when implemented, not
  assumed here. Then the sparse field itself within the recorded budget, then
  flammability by Jones, then environmental reactivity as ordinary reaction
  rules.
- **C — radiation zone.** Gamma branches and μ tables, secular equilibrium as the
  source term, the world adapter (entity dose, dosimeter, accumulated dose), and
  finally radon as a gas-carried source.

Acceptance boundary: core changes are accepted by CI. World-side changes are
accepted only through the `gametest` harness plus a run on the real pack by the
owner, because the M5 handover requires a slice to start from the actual
mod-list, Create/TFC configs and an actual game test rather than assumptions
about a "standard" Create.
