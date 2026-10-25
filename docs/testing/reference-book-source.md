# M2/A2 source acceptance — chemical reference book

This checklist applies to a build containing the reference-book implementation. It does not change the historical `v1.0.2-test.1` candidate and does not by itself close M2/A2 or any owner acceptance.

## Interaction

1. Put the Chemical Reference Book in one hand and a filled single-substance reference vial in the other.
2. Sneak-use the book. Confirm a report is shown and neither item nor sample amount changes.
3. Repeat with the book in the off hand. Without sneaking, confirm normal use is not intercepted.
4. Try an empty vial, a mixture vial, and (if available) a valid molecular graph outside the named reference directory. Confirm each gives a clear message and no item/component is changed.
5. Switch between English and Russian and verify the name, labels, and model-limit statements are localized.

## Model and claim boundaries

- Only structures resolving to the finite `CommonSubstances` reference directory have entries. This is not an arbitrary-graph designer and does not enlarge the catalog.
- Formula, molar mass, boiling-point estimate/basis, combustion estimate, and structural flags are sourced from the existing graph/property models. Missing model values are reported as unavailable, not filled with guesses.
- Solubility is shown only when the existing rule model produces `SOLUBLE` or `INSOLUBLE`. `UNKNOWN` is omitted from both the book and the vial tooltip; the salt rule is coarse, not a Ksp calculation.
- Structural flags are explicitly identified as model features, not toxicity ratings or a safety assessment. The book is not chemical handling advice.
- Mixtures are not summarized as if they were single substances.

## Automated coverage

`ReferenceBookReportTest` verifies finite-directory lookup, graph-derived report data, and omission of unknown solubility. `ChemicalReferenceBookGameTests` exercises the sneaking interaction and non-sneaking pass-through. CI/GameTests supplement, but do not replace, the owner run on the real pack.

## Owner result

Pending. Record the exact build, RU/EN observations, pass/fail, and any save/reload or client/server issue here after manual testing.
