# ADR-0001: ChemMod owns canonical materials and processes

- Status: Accepted
- Date: 2026-10-04

## Context

The target modpack may contain several technical mods with overlapping metals,
ores, item forms, world generation, and recipes. Allowing each integration to
become a peer source of truth would create duplicate items, inconsistent
composition, and recipes that cannot preserve material state.

Chemical species, minerals, deposits, processable materials, physical batches,
and processes are different domain concepts. Treating all of them as an item ID
would also couple the chemistry core to Minecraft and NeoForge.

## Decision

ChemMod is the canonical authority for substances, minerals, deposits, ores,
materials, alloys, forms, metallurgy, world-generation policy, and process
definitions in the target pack.

The independent `core` module defines separate, schema-versioned models:

- `ChemicalSpecies`
- `MineralDefinition`
- `DepositDefinition`
- `MaterialDefinition`
- `MaterialBatch`
- `ProcessDefinition`

Stable lowercase `namespace:path` identifiers connect definitions. A
`ProcessCompiler` resolves those identifiers and validates that every requested
form belongs to its canonical material before runtime registration.

Integrations are one-way optional adapters from ChemMod definitions to external
mod APIs. An adapter must not make an external material registry authoritative.
Duplicate external content may be disabled only after ChemMod provides a tested
canonical replacement.

## Consequences

- `core` remains independent of Minecraft and NeoForge.
- World generation and machine adapters consume canonical definitions instead
  of inventing parallel materials.
- Material batches can preserve purity and impurities through future machines.
- Processes can be validated in unit tests before game registration.
- Existing gameplay remains available while M1.5 is introduced incrementally.
