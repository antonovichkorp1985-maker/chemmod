# ADR-0001: material definitions are scoped; molecular graphs own molecular identity

- Status: Accepted (supersedes the earlier global-authority wording)
- Date: 2026-10-04
- Updated: 2026-10-06

## Context

The target pack contains several mods with overlapping metals, ores, item
forms, world generation and recipes. ChemMod needs coherent definitions for
its *own* material batches and processes, but cannot infer that this makes it
the global authority for every external item or every possible chemical
substance.

A molecule, a mineral, a bulk material, a physical batch and a process are
different concepts. In particular, using a `SpeciesId` or an item identifier as
the identity of a molecule would exclude valid unnamed graphs and would let a
content file assign what must be derived structurally.

## Decision

A valid molecular graph is the authority for molecular identity. Its canonical
key, formula, molar mass, predicted properties and reaction matching are
derived from that graph. Names are a directory convenience only. A molecular
graph neither needs nor receives a material-catalog registration to exist.

`MaterialCatalog` remains a schema-versioned, validated content boundary for
ChemMod-owned bulk-material gameplay:

- `ChemicalSpecies` is a finite reference from a material definition to a
  molecular graph, not a universe-wide substance registry;
- `MineralDefinition`, `DepositDefinition`, `MaterialDefinition`,
  `MaterialBatch` and `ProcessDefinition` model the distinct material domain;
- `ProcessCompiler` validates ChemMod process/form references before runtime;
- exact batch component masses remain authoritative for ChemMod material
  operations.

Optional integrations are one-way adapters. They may consume ChemMod's
well-defined material process data but do not make an external registry part of
core and do not establish a global content-unification policy. Enabling,
disabling or unifying competing pack content is a pack-owner decision.

## Consequences

- `core` stays independent of Minecraft and NeoForge.
- Unnamed molecular graphs can be stored, transformed and discovered without a
  catalog row.
- Material machines retain exact mass accounting without claiming ownership of
  molecular identity or the entire modpack.
- Worldgen and machine adapters use only their scoped ChemMod definitions.
- Tests can validate graph chemistry and material processes independently.
