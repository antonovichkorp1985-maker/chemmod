# ADR-0005: geometry stays derived, configuration becomes part of the graph

- Status: Proposed
- Date: 2026-10-11
- Updated: 2026-10-11

## Context

Identity is the graph digest (ADR-0001). `MoleculeCanonicalizer.canonicalKey`
hashes, per atom, the tuple (atomic number, formal charge, explicit hydrogens,
implicit hydrogens, bond count, bond-order sum), iterates Morgan-style over
neighbour labels, and folds in the sorted bond list. Verified against the
current code, that tuple contains **no spatial information of any kind**, and
the parser has none either: `SmilesLikeParser`'s `BRACKET_PATTERN` is
`([A-Z][a-z]?)(H(\d*)?)?([+-](\d*)?)?` — no `@`, no `/`, no `\`.

The consequence is concrete. *cis*- and *trans*-2-butene are the same substance
in this model, and so are *(R)*- and *(S)*-2-butanol. Structural isomers are
already distinct, because they differ in connectivity; configurational isomers
are not, because they differ only in arrangement.

Meanwhile `geometry/MolecularGeometry.kt` already computes VSEPR shape and ideal
bond angle, and its own header states what it is not: "This is not a coordinate
model. It answers 'is water bent and at what angle', not 'where are its atoms in
space'." Water is bent there because oxygen has two bonds and two lone pairs —
derived, never told.

So the question "where does isomerism go" splits into three things that are not
the same kind of thing:

| | Can it be derived from connectivity? | Belongs in identity? |
| --- | --- | --- |
| Conformation (rotamers, chair/boat) | no, but it is not a different substance | no |
| Local shape (VSEPR angles) | yes — already implemented | no |
| Configuration (E/Z, R/S) | **no** | **yes** |

Conformation and shape are computed. Configuration cannot be computed, which is
precisely why it has to be recorded: two graphs with identical connectivity and
different configuration are different substances with different properties —
*cis*- and *trans*-dichloroethene have different dipole moments and therefore
different boiling points.

## Decision

1. **Coordinates are never identity.** No coordinate field is added to `Atom`.
   If coordinates are ever produced — for rendering, for a force field, for a
   docking-style check — they are a derived layer keyed by the graph digest, so
   a rotation of the molecule cannot change what the substance is.

2. **Configuration becomes two explicit fields on the graph**, both defaulting to
   unspecified: a tetrahedral parity on `Atom`, and a double-bond geometry on
   `Bond`. Nothing else is stored — no angles, no coordinates, no priority
   numbers.

3. **CIP priorities are computed, never stored.** *R*/*S* and *E*/*Z* are defined
   by ranking substituents, and ranking is a function of the graph. The stored
   datum is only the parity relative to that ranking. Storing the ranking itself
   would be a per-substance table wearing a laboratory coat.

4. **The digest gains the configuration only where one exists.** A molecule with
   no stereogenic element hashes exactly as it does today, so every persisted
   achiral identity survives unchanged and there is no migration for the
   overwhelming majority of substances. The hashed input is versioned regardless,
   so the day this changes again the old digests can be recognised and upgraded
   on read instead of silently mismatching.

5. **Unspecified is a state, not an absence.** A stereogenic centre with no
   parity recorded means "this substance can exist in two forms and this sample
   is not resolved" — which is what an ordinary synthesis actually produces. It
   must stay distinguishable from "this atom is not stereogenic", or a racemate
   and a pure enantiomer become the same thing.

6. **Notation follows the graph.** `SmilesLikeParser` accepts `@`/`@@` and
   `/`/`\`; `StructureNotationWriter` writes them back. A structure that cannot
   be written down is a structure a player cannot trade or record.

Where the pieces live: the fields in `model/MoleculeGraph.kt`, a new
`stereo/Stereochemistry.kt` holding CIP ranking and the detection of
stereogenic centres and double bonds, the digest change in `Canonicalizer.kt`,
notation in the parser and the writer.

## Consequences

- Enantiomers become separate substances with identical predicted properties,
  which is correct: a property model built on connectivity and dispersion has no
  reason to tell them apart, and the day one does (a chiral catalyst, a
  biological receptor) it reads the parity that is now on the graph.
- *cis*/*trans* pairs become separate substances with **different** predicted
  properties only if a property branch reads the geometry flag. Until one does,
  they differ in identity and agree in every number — an honest gap, and a
  visible one, not a fake resolution.
- Reaction rules gain the ability to be stereospecific later, because the
  matcher can now see configuration. No rule is required to.
- This is a change to the identity of chiral substances and therefore touches
  persisted worlds; it lands with the digest version of decision 4, not before.
