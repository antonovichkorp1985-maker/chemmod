# ADR-0002: batch-aware ore beneficiation follows principles, not GregTech implementation

- Status: Accepted
- Date: 2026-10-05

## Context

GregTech-style gameplay demonstrates useful high-level ore-processing ideas:
multiple physical stages, a meaningful choice between a quick route and a
cleaner route, water as a reagent, and later recovery of useful secondary
materials. ChemMod needs such depth, but it must remain an independent,
server-authoritative materials model and cannot make another mod's registry,
source code, assets, recipe tables, names, or balance authoritative.

## Decision

ChemMod's initial beneficiation branch is independently defined as:

```text
native copper ore -> crushed ore -> dust                    (direct route)
                              -> washed crushed ore -> dust  (water route)
```

The Ore Washer consumes the *persisted analysis* of a collected water vial.
It never samples a biome or external fluid while processing. Better samples
leave less recorded gangue in the material batch; poor or saline samples are
still valid but less efficient. The input and output nominal batch mass remain
identical in this first stage. `MaterialBatch` currently represents composition
as purity and aggregated known impurities, rather than separate physical output
stacks.

A later separator milestone will add explicit, mass-partitioned tailings and
secondary outputs. It must use canonical `ProcessDefinition` multi-output
processes rather than random bonus drops or copied foreign recipe tables.

## Consequences

- Existing direct copper processing remains usable and is a low-tech route.
- World-mined native copper carries measured silicate gangue; creative/admin
  reference stacks remain 100% pure.
- Water quality now has a second gameplay use while still using the canonical,
  optional TFC-compatible vial analysis.
- No GregTech source, assets, recipes, or identifiers are copied or required.
