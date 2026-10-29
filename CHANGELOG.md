# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/lang/en/lang.txt).

## Unreleased

### Added
- CM-1: Full physicochemical core for ionic exchange (metathesis) reactions and solubility table
  - `Phase` enum (SOLID, LIQUID, AQUEOUS, MOLTEN, GAS) with `hasFreeMobileIons`
  - `MetathesisDrivingForce` extended with `Solubility.Cation`, NH4⁺ support, three driving forces:
    precipitate, gas, weak electrolyte
  - Phase gate: only AQUEOUS/MOLTEN have free mobile ions; SOLID/GAS → `NO_FREE_IONS`
  - `Solubility` extensions: `formsGas()` and `formsWeakElectrolyte()` for gas-forming and neutralization detection
  - `MixtureReactionPlanner` fix: iterate all candidates instead of just `first().reactants.drop(1)`
  - Expanded solubility table: typical precipitates (AgCl, BaSO₄), gas-forming reactions (H⁺ + CO₃²⁻),
    neutralization (H⁺ + OH⁻)
- Solubility.Cation sealed interface with `Elemental` and `Ammonium` data classes
- New `formsGas(cation, anion)` and `formsWeakElectrolyte(cation, anion)` functions
- `Phase` enum with `hasFreeMobileIons` property

### Changed
- `MetathesisDrivingForce.Outcome` added `NO_FREE_IONS` variant
- `default.json`: version bump to 1.0.2-test.4
- `build.gradle.kts` (root): version `1.0.2-test.3` → `1.0.2-test.4`
- `Element`: `commonIonCharges` validation updated
- `Solubility.isSoluble` and `Solubility.precipitates` now use `Cation` interface consistently

### Fixed
- `MixtureReactionPlanner`: co-reactant pair iteration now checks all candidates, not just the first
- Test coverage: new unit tests for gas, weak electrolyte, phase gate, NO_FREE_IONS outcomes
- `MetathesisDrivingForceTest`: comprehensive test suite for all outcomes and phases

## v1.0.2-test.3

### Added
- Initial release with core MixtureReactionPlanner and batch planning
- ReactionEngine with bundled rule set (complete_combustion, hydrogen_combustion, alkene_hydrogenation, etc.)
- ReactionBalancer with linear algebra for rational coefficient balancing
- ReactionBatchPlanner with micromole extent planning
- IonicSolubilityClassifier with anion-group classification
- CommonSubstances facade and ReferenceMoleculeCatalog
- Element table and Element data class with electronegativity, ionic radius, common ion charges
- Material system (MineralDefinition, DepositDefinition, MaterialDefinition, MaterialForm, MaterialBatch)
- First set of reaction rules (combustion, hydrogenation, hydrogenation variants, oxidation, halogenation, hydration)
- Basic M0 parser (elements B C N O F P S Cl Br I, bonds -, =, #, branches (), closures, bracket atoms [NH4+], [O-])
- Minecraft integration: Chemistry Table, Chemical Reactor, and empty vial mechanics
- Itemstack canonical key, structural witness, purity/ppm system
- Water/acid/gas sample mechanics with structural witness verification

## v1.0.2-test.2

### Added
- Initial project scaffold with Gradle wrapper
- Core model: Element, Molecule, Atom, Bond, BondOrder, formal charge support
- SMILES-like parser with canonical key generation
- Basic build and test infrastructure