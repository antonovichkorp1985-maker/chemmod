# ADR-0005: geometry stays derived, configuration becomes part of the graph

- Status: Accepted (detection); Proposed (parity fields)
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

So the question "where does isomerism go" turns out to be three questions, and
they do not have the same answer:

| Question | Derived from the graph? |
| --- | --- |
| Is there a choice at all — do two arrangements exist here? | **yes** |
| Which arrangement is this one? | **no** |
| Does the choice survive, or do the arrangements flow into each other? | **largely yes** |

The first is a graph algorithm: an sp³ atom with four different substituents has
two arrangements whether or not anyone says so, and a double bond with two
different substituents at each end has *cis* and *trans*. Symmetry decides it for
free — the two methyls of isopropanol carry the same fragment label, and so do
the two ring paths of cyclohexanol, so neither carbon is a centre.

Only the second question is genuinely unanswerable from connectivity, because the
graph records who is bonded to whom and has no left or right in it. That is the
one datum that has to be told.

The third is where the earlier framing of this ADR was wrong. It said
configuration "cannot be derived" as though the whole of it were outside the
model, and leaned on "a double bond does not rotate". The real criterion is a
**barrier**, not a bond type:

- Around 1–10 kcal/mol the arrangements interconvert and are conformers of one
  substance. Butane eclipses at 3.5–5 kcal/mol; a cyclohexane ring flips over
  about 10 kcal/mol some 10⁵ times a second.
- Pyramidal inversion is a property of the element. Nitrogen crosses a ~25
  kJ/mol barrier — ammonia does it 4·10¹⁰ times a second — so an amine with three
  different groups is chiral and still cannot be resolved. Phosphorus needs about
  130 kJ/mol, and its enantiomers stand.
- A π bond costs tens of kcal/mol to break, which is why *cis*/*trans* survive.
- **Rotation about a single bond can be blocked too.** IUPAC defines
  atropisomers as the conformers that can be isolated as separate chemical
  species because rotation about a single bond is hindered; Oki's line is a
  half-life above 1000 s, and measured barriers sit near 24 kcal/mol
  (101 kJ/mol). BINAP and 1,1'-bi-2-naphthol are single-bond molecules that are
  two different substances.

So the dividing line between "one substance, many shapes" and "two substances" is
whether the barrier outruns the conditions — and that is mostly computable, from
the bond type, the ring, the element, and the size of the substituents crowding a
bond. Two arrangements that differ matter physically: *cis*- and
*trans*-dichloroethene have different dipole moments and therefore different
boiling points.

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

4. **Detection is derived, so parity is only ever asked for where a choice
   exists.** The model reports its own stereogenic centres and stereogenic double
   bonds from the graph, and separately reports which pyramidal centres invert
   too fast to matter. A centre the model did not detect needs no parity, and a
   centre that inverts away must not become a second substance. Detection lives in
   `stereo/Stereochemistry.kt` and reads the same converged Morgan labels the
   canonical key is built from, so "same substituent" means one thing everywhere.

5. **The digest gains the configuration only where one exists.** A molecule with
   no stereogenic element hashes exactly as it does today, so every persisted
   achiral identity survives unchanged and there is no migration for the
   overwhelming majority of substances. The hashed input is versioned regardless,
   so the day this changes again the old digests can be recognised and upgraded
   on read instead of silently mismatching.

6. **Unspecified is a state, not an absence.** A stereogenic centre with no
   parity recorded means "this substance can exist in two forms and this sample
   is not resolved" — which is what an ordinary synthesis actually produces. It
   must stay distinguishable from "this atom is not stereogenic", or a racemate
   and a pure enantiomer become the same thing.

7. **Notation follows the graph.** `SmilesLikeParser` accepts `@`/`@@` and
   `/`/`\`; `StructureNotationWriter` writes them back. A structure that cannot
   be written down is a structure a player cannot trade or record.

Where the pieces live: detection in `stereo/Stereochemistry.kt` (implemented —
stereogenic centres, stereogenic double bonds, pyramidal chirality centres and
the rapidly inverting subset), the parity fields in `model/MoleculeGraph.kt`, CIP
ranking alongside detection, the digest change in `Canonicalizer.kt`, notation in
the parser and the writer. Steric hindrance at a crowded single bond — the
atropisomer case — needs a substituent size estimate and is not decided yet.

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
