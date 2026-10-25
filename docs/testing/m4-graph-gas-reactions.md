# M4 — graph-identified environmental gas reactions (slice A)

**Status:** implementation in progress; not an M4 completion claim.
**Authorization:** on 2026-10-25 the owner instructed us to proceed with M4 and defer the real-pack acceptance of M0–M3 until the end of M4. This changes development sequencing only: no milestone is accepted by that instruction.

## Implemented in this slice

- New gas-field entries can carry a validated `Molecule` witness. Their field key is `mol:` plus the full canonical graph digest; isomers with the same formula remain separate identities.
- World persistence writes the full structural witness and canonical key, and rejects a mismatched pair on load. Existing formula-labelled `moles` entries remain loadable and transportable, but they are not guessed into molecular structures and therefore cannot enter a reaction.
- `GasField` applies the existing `GasCellReactionPlanner` per occupied cell. Matching, reaction conditions, exact balancing and Arrhenius kinetics remain in `ReactionEngine` and its data-defined `ReactionRuleSet`. Plans are calculated before a cell is changed; only one deterministic applicable reaction is committed per cell per update.
- Added a data-defined hydrogen combustion rule for the M4 C2 case: `2 H2 + O2 -> 2 H2O`. Core and world GameTests have been added; their CI results are pending for this source change.
- The world adapter offers a graph-preserving release API. An actual fire/lava block at a cell's representative block supplies the 500 K reaction-condition gate for that update. This is an ignition trigger mapped to the existing rule threshold, **not** a measured local gas temperature or a world thermal model.

## Current limits / remaining M4 work

- Legacy formula-only entries remain structurally unresolved; preserving them is intentional, because a formula alone does not uniquely define a molecular graph. The tank's older formula-only path has not yet been migrated to graph witnesses.
- Gas transport obtains molecular mass from the graph, while the current Lennard–Jones transport coverage is still queried using the formula string; unknown coverage remains unknown. This does not make formula the reaction identity.
- The environmental planner commits at most one rule per cell per one-second world update. The cost and ordering need to be profiled before broadening this behavior.
- C1 chlorine leak and C2 hydrogen ignition still need the full real-pack scenario. GameTests do not establish player-visible rendering, saved-world restart behavior, pack compatibility, or performance.

## Acceptance evidence to collect at M4 end

- CI: core tests plus NeoForge GameTests for graph identity, balanced in-cell reaction, persistence and refusal boundaries.
- Owner run on the actual pack: chlorine release/settling/visibility; hydrogen-plus-oxygen ignition at an actual fire source; inspect save/restart and confirm no duplicate/lost moles. Record any in-pack temperature or fluid interactions as observations, not as proof of a general thermal model.
- Preserve the separate M0–M3 owner checklists and F2 Spark profile; defer their manual run as the owner requested, but do not mark them accepted until results are supplied.
