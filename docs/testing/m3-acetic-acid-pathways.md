# M3 source: acetaldehyde-to-acetic-acid pathways

This note records the new M3 reaction-rule work, not owner acceptance. It keeps
acetic acid as a derived molecular graph and uses the existing
`ReactionRuleSet` / `ReactionEngine` / `ReactionBalancer` pipeline; no named
substance recipe or parallel reaction engine is introduced.

## Data-defined steps

Both routes start with the existing graph rule:

1. `chemmod:alcohol_dehydrogenation`: ethanol → acetaldehyde + hydrogen.

The rule set now offers two downstream pathways from acetaldehyde:

| Route | Step | Balanced reaction |
|---|---|---|
| Direct oxidation | `chemmod:aldehyde_oxidation` | 2 C2H4O + O2 → 2 C2H4O2 |
| Hydrate / dehydrogenation | `chemmod:carbonyl_hydration` | C2H4O + H2O → C2H6O2 |
| Hydrate / dehydrogenation | existing `chemmod:alcohol_dehydrogenation` | C2H6O2 → C2H4O2 + H2 |

The product C2H4O2 is made by graph rewriting: the second route forms a geminal
diol from the aldehyde carbonyl and water, then converts one C–O single bond to
a carbonyl while releasing H2. The first route uses the new `atom_transfer`
matcher to transfer one oxygen atom from O2 onto a C=O carbon that has a
hydrogen. That structural constraint matches aldehydes rather than ketones;
the remaining O–O bond is not copied into the product. Exact balancing supplies
the 2:1 aldehyde/oxygen stoichiometry and rejects unbalanced candidates.

A broad formula-level rule such as complete combustion is a fallback when no
structural rule applies. If a graph-specific transformation and a formula rule
both match, the reactor chooses the graph-specific candidate; equally specific
ambiguous candidates remain refused. This allows selective data-defined
synthesis to coexist with general combustion without a substance lookup.

The copper material composition now earns the existing general
`chemmod:oxidation_metal` capability, allowing the new direct oxidation rule to
be exercised with an already obtainable ChemMod catalyst item. This is an
explicit capability abstraction; the graph model does not represent catalyst
surface phase, oxidation state, support, or selectivity.

## Automated evidence

Core tests assert both balanced routes, the exact acetic-acid graph, atom and
charge conservation, missing-catalyst refusal, and rejection of ketones by the
aldehyde matcher. GameTests exercise (a) the direct oxygen/copper path with
physical vial quantities and (b) the hydrate/dehydrogenation path across two
actual reactor batches. These are source-level regression checks; the next full
CI run must pass before treating the implementation gate as met.

## Chemistry and calibration limits requiring review

The direct rule is a net reaction, not an elementary mechanism. It does not
model radical/peroxy intermediates, liquid-water autoxidation, oxygen pressure,
competing oxidation products, or the catalyst surface. Its
53.5 kJ/mol activation energy is informed by a reported apparent value for
gas-phase acetaldehyde oxidation over 150–250 °C, not a validated rate constant
for this particular selective catalyst/channel. The Arrhenius pre-exponential
factor remains a machine-timescale calibration. The hydrate route similarly
uses general carbonyl hydration and alcohol dehydrogenation rules; it does not
claim a catalyst-specific measured rate for gem-diol conversion.

Relevant literature to guide the owner review:

- Gas-phase acetaldehyde oxidation mechanism and kinetics (reported apparent
  activation energies of 53.5 and 57 kJ/mol in two temperature regimes):
  <https://www.ije.ir/article_71116.html>.
- Selective gas-phase acetaldehyde oxidation on an oxygen-precovered Pd–Au
  model surface, which demonstrates a catalyst-specific route and its
  temperature/selectivity limitations: <https://www.osti.gov/pages/biblio/1617152>.
- Ethanol oxidation literature describes sequential ethanol → acetaldehyde →
  acetic acid chemistry and the liquid-phase/water conditions relevant to
  non-catalytic aldehyde autoxidation:
  <https://pubs.rsc.org/en/content/articlepdf/2019/cc/c9cc05813c>.

These references do not validate the current abstract catalyst mapping or
single-product selectivity. That model boundary must be presented honestly in
the owner real-pack review; further calibration or environmental/phase work may
be required.

## Owner real-pack checks for this source build

1. Install the exact same new build on server and clients. In a reactor with
   heat, insert acetaldehyde, the balanced oxygen amount, a ChemMod copper ingot,
   and empty product vial(s). Confirm the selected rule is aldehyde oxidation,
   oxygen is physically consumed at the exact ratio, and the output is the
   acetic-acid structure rather than a peroxide or an unrelated product.
2. Run the alternate route in two batches: acetaldehyde + water → geminal-diol
   intermediate, then that intermediate + copper catalyst → acetic acid + H2.
   Confirm both products, retained remainders and output-vial separation.
3. Save/reload between batches and repeat after a server restart. Check RU/EN
   rule names/descriptions, two-client observation, and that the discovery view
   reports product facts without changing who first synthesized each molecule.
4. Run 100 idle/active reactors at 1 Hz and attach the Spark profile with the
   measured tick cost. A headless GameTest is not evidence for the 5 ms/tick
   owner gate.

No M3 owner acceptance or 100-machine Spark profile is claimed.
