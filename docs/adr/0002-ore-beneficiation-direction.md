# ADR-0002: physical, batch-aware ore beneficiation follows principles, not GregTech implementation

- Status: Accepted
- Date: 2026-10-05
- Updated: 2026-10-05

## Context

GregTech-style gameplay demonstrates useful high-level ore-processing ideas:
multiple physical stages, a meaningful choice between a quick route and a
cleaner route, water as a process input, and later recovery of secondary
materials. ChemMod needs such depth, but must remain an independent,
server-authoritative material model. Another mod's registry, source code, assets,
recipe tables, identifiers and balance are not authoritative here.

M3 represented a batch with ppm purity and retained its nominal mass during
washing. That established water analysis gameplay, but it could not be a final
physical model: raising the purity of a fixed-mass batch implicitly increased the
mass of primary copper.

## Decision

Material batches now use schema 2. Every batch stores exact integer microgram
masses for its primary material and each named impurity; displayed purity is a
derived rounded value. A batch must assign every microgram to one component.
Schema-1 ppm batches are upgraded on read so existing worlds remain usable.

The independent copper branch is:

```text
native copper ore -> crushed ore -> impure dust                  (fast route)
                              -> water wash -> concentrate + tailings
                              -> dry separation -> copper dust + residual gangue
                              -> accumulated melt -> one-kilogram ingots
```

The Ore Washer consumes the *persisted analysis* of a collected water vial.
It never samples a biome or external fluid while processing. Better samples
leave less gangue in the concentrate; poor or saline samples leave more. The
primary copper mass is invariant. All removed gangue is emitted as physical,
server-authoritative tailings.

The Ore Separator materializes the remaining measured impurities as physical
dust outputs. Its catalog declaration is a canonical, mass-conserving multi-output
reference process; runtime scales its component outputs from the persisted batch
rather than using random bonus drops. The first separator is deliberately a
low-throughput, power-API-free adapter. A future optional kinetic adapter may
operate the same canonical process when a supported mechanical mod is present.

A refractory furnace may merge compatible molten batches into one capped crucible.
The ingot mold splits exactly one standard ingot mass and retains the remaining
molten batch. This makes sub-ingot recovery playable without inventing or losing
mass.

## Consequences

- Existing direct copper processing remains a low-tech route and preserves its
  impurities.
- World-mined native copper begins as exactly 850 g Cu and 150 g silica gangue
  per 1 kg batch; creative/admin references remain exactly 100% pure.
- Clean and saline water preserve the established target quality ordering while
  their outputs now conserve mass physically.
- Water quality remains a gameplay use of the canonical, optional
  TerraFirmaCraft-compatible vial analysis.
- The core now has a reusable foundation for exact multi-output processes,
  component-aware containers and later mechanical adapters.
- No GregTech source, assets, recipes or identifiers are copied or required.
