# M2/A2 source acceptance — reaction-derived substance display and reference book

This checklist applies to a build containing the reaction-derived sample display and reference-book implementation. It does not change the historical `v1.0.2-test.1` candidate and does not by itself close M2/A2 or any owner acceptance.

## Interaction

1. Find the Chemical Reference Book and a filled single-substance sample in the creative tab.
2. Sneak-use the book while holding the sample in the other hand. Confirm a report is shown and neither item nor sample amount changes.
3. Repeat with the book in the off hand. Without sneaking, confirm normal use is not intercepted.
4. Try an empty vial, a mixture vial, and (if available) a valid molecular graph outside the rule-derived species set. Confirm each gives a clear message and no item/component is changed.
5. Switch between English and Russian and verify the name, labels, and model-limit statements are localized.

## Source of displayed content

- Molecular samples are drawn from participants of the authored reaction network: explicit rule co-reactants/products and the reactants/products of balanced outcomes that the bundled graph rules actually produce from the finite named target-graph pool.
- `CommonSubstances` remains a name/alias and candidate-graph directory; its entries are not all automatically exposed as creative samples. Validation-only entries with no authored reaction connection stay out of the displayed molecular list.
- Catalysts and other physical material forms remain sourced from the material/catalysis definitions and are displayed as material items, not mislabeled as molecular-vial samples.
- General structural matchers can accept unbounded molecular graphs. This list is finite and does not claim to enumerate every possible graph or theoretical product.

## Model and claim boundaries

- The book reads only a molecular species in the finite reaction-derived set. It is not an arbitrary-graph designer and does not add manually assigned substance properties.
- Formula, molar mass, boiling-point estimate/basis, combustion estimate, and structural flags come from existing graph/property models. Missing model values are reported as unavailable, not filled with guesses.
- Solubility is shown only when the existing rule model produces `SOLUBLE` or `INSOLUBLE`. `UNKNOWN` is omitted from both the book and vial tooltip; the salt rule is coarse, not a Ksp calculation.
- Structural flags are explicitly identified as model features, not toxicity ratings or a safety assessment. The book is not chemical handling advice.
- Mixtures are not summarized as if they were single substances.

## Automated coverage

`ReactionSpeciesCatalog` and `ReferenceBookReportTest` cover rule-derived membership, graph-derived report data, exclusion of unrelated named validation species, and omission of unknown solubility. `ChemicalReferenceBookGameTests` exercises the sneaking interaction and non-sneaking pass-through. CI/GameTests supplement, but do not replace, the owner run on the real pack.

## Owner result

Pending. Record the exact build, RU/EN observations, pass/fail, and any save/reload or client/server issue here after manual testing.
