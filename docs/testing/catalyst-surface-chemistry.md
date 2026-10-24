# M3 — research and implementation of catalyst surface chemistry

**Status:** core modeling contract and unit tests added; no validated surface profile is active in the reactor yet. M3 catalyst acceptance remains open.

## Why the bulk-composition rule is insufficient

A catalyst is not determined by its element set alone. Its exposed phase, facet, oxidation state, defects, accessible area, adsorbed coverage and reaction environment determine which sites exist and what they do. BET area is an input to capacity, not a universal activity multiplier: equal area does not imply equal active-site density or selectivity.

### Iron oxide scale is not one unique catalyst surface

High-temperature oxidation of iron/steel above roughly 570 °C commonly produces a layered scale, with hematite (Fe₂O₃) at the gas-facing outer surface, magnetite (Fe₃O₄) beneath it, and wüstite (Fe₁₋ₓO) next to the metal. Wüstite is unstable below about 570 °C and transforms during cooling, so a cooled scale is not simply the same three-phase surface. Temperature, oxygen potential, steel composition and preparation change the resulting layers and defects.

Evidence:

- Corrosion Science study on high-temperature inner oxide scale: <https://www.sciencedirect.com/science/article/abs/pii/S0010938X21007484>.
- Review of high-temperature iron/steel oxidation: <https://doi.org/10.1023/A:1023685905159>.

Catalytic response is phase- and defect-specific. Work on well-defined oxide films reports catalytic activity for defect-containing α-Fe₂O₃(0001), while the studied Fe₃O₄(111) and defect-free hematite surfaces were inactive under those reaction conditions: <https://www.sciencedirect.com/science/article/abs/pii/S0079681601000569>.

Surface-area figures cannot be transplanted between samples. One study of iron oxides produced from hydrous ferric oxide reports BET areas from 272.8 down to 18.3 m²/g as thermal treatment changes the samples; exposed Fe(III) activity per area is more relevant than bulk magnetism for that study's ortho–para H₂ conversion: <https://onlinelibrary.wiley.com/doi/10.1002/ece2.70004>. Another iron-oxide H₂O₂ decomposition study found the largest-area sample was not the most efficient; crystallinity changed the result: <https://pubs.acs.org/doi/10.1021/ja072918x>. These numbers are not a surface-area assignment for game iron scale and do not calibrate M3 acetic-acid synthesis.

### Roadmap dependency: why there is no iron scale yet

This is a content dependency, not a missing M3 item. The current material catalog contains a ChemMod copper material and native-copper beneficiation; it has **no iron material/form pipeline**. Hematite and magnetite occur as mineral definitions only, not as obtainable ChemMod iron/oxide material batches. `docs/adr/0002-ore-beneficiation-direction.md` deliberately implements the copper branch only. The owner roadmap's M3 B1 line is the first physical reactor; environmental metal oxidation is described in the gas/environment layer, not as a prerequisite output of that reactor.

So the correct sequence is: implement and accept the planned iron-bearing ore/material processing stage, then model controlled iron oxidation/scale formation and its phase/state, then consider that characterized surface for a data-defined reaction. Do not add a free-standing iron-scale item or assume Fe2O3/Fe3O4/FeO surface behavior before those dependencies exist. The absence of iron scale does not block the current M3 prerelease checklist; M3's separate surface-chemistry gap remains that the existing copper catalyst is still represented only by bulk composition, without a validated item-form surface profile.

## Current M3 copper route — research, not yet a calibrated profile

The existing M3 first step is ethanol → acetaldehyde + H2 on a copper catalyst, so copper is the relevant currently available surface material. Research on clean, dry Cu(111)/(110) single-crystal surfaces reports that ethanol dehydrogenation proceeds at steps/kinks, and inducing more surface defects increases ethoxy intermediates and acetaldehyde/H2 production. The experiments used controlled ultrahigh-vacuum surfaces; they do **not** establish a defect density, active area, oxidation state, or rate for ChemMod's ordinary 1 kg copper ingot. Source: <https://www.osti.gov/pages/servlets/purl/1388884>.

Supported Cu/SiO2 catalysts likewise vary in particle size and measured active copper area (reported values in the study range from 0.9 to 2.4 m2 per gram of catalyst), and their support/preparation are specified. Those data cannot be assigned to a pure copper ingot or dust by formula alone: <https://pmc.ncbi.nlm.nih.gov/articles/PMC10394689/>. Other supported-Cu studies report that Cu oxidation state, support, coverage and deactivation alter activity/selectivity, reinforcing that a generic “contains Cu” capability is not a surface model: <https://www.colab.ws/articles/10.1016/j.apcata.2020.117710>.

Accordingly, the current catalyst surface contract is ready for characterized profiles, but no Cu ingot profile or invented defect fraction is being enabled. The next M3 decision is whether a physically prepared/high-area copper form is in scope for this milestone; if not, retain the route for owner testing and keep its surface limitation explicit until the planned material/preparation work supplies a defensible catalyst form.

## Relevance to the current acetic-acid pathways

The M3 direct rule models the net gas-phase transformation of acetaldehyde and oxygen but is not a validated universal oxidation surface. A Pd–Au(111) study specifically used an oxygen-precovered surface; selectivity changed with Pd coverage and temperature: <https://www.osti.gov/pages/biblio/1617152>. Separately, ethanol oxidation on hematite photoanodes favors acetaldehyde, with a larger activation barrier for its further oxidation; those photoelectrochemical conditions cannot be copied to the current heated vial reactor: <https://chemistry-europe.onlinelibrary.wiley.com/doi/10.1002/cssc.202102313>.

A study of ethanol oxidation to acetic acid also reports that, in its tested liquid-phase conditions with oxygen and water, acetaldehyde-to-acetic-acid conversion was largely non-catalytic and radical-initiated; in gas-phase operation acetic acid was much less prominent: <https://pubs.rsc.org/cc/article/55/79/11833/635397/The-catalytic-and-radical-mechanism-for-ethanol>. This means the model must first distinguish phase and pathway before attaching an oxide-scale catalyst to the reaction.

Therefore this change does **not** label iron scale as a catalyst for either current route, select a phase by name alone, assign arbitrary BET/site-density/adsorption values, or claim that the reaction is selective. These are deliberately unfilled data until a specific prepared surface and reaction condition are supported.

## Implemented model contract

- `CatalystSurfaceProfile` identifies a characterized surface sample/phase and carries measured specific area, per-site-type site density and evidence reference.
- `CatalystSurfaceModel.inventory` computes exposed area from actual catalyst mass, then converts it to site moles; optional coverage blocks only the corresponding site type.
- `ReactionEnvironment.catalystSurfaceSiteMoles` carries accessible site inventory.
- `ReactionConditions.minimumCatalystSurfaceSiteMoles` lets an ordinary `ReactionRuleSet` rule require both a site type and a minimum inventory; `ReactionEngine` rejects missing or insufficient sites.
- `ReactionKinetics` can optionally scale its Arrhenius rate by accessible-site amount divided by a data-declared reference amount; without the site model, that rate is zero.
- The reactor reads material ID, form, and primary mass from the existing material batch component and looks up an exact material-form profile. Unknown material/forms return no sites; no new inventory component is assumed.
- Missing profile means no modeled sites. There is no fallback from Fe/Cu/Pd bulk composition to a surface state.
- The bundled surface profile file is empty pending data. The new tests use explicitly labelled arithmetic fixtures only; those fixture values are not chemistry data.

The surface inventory currently does not provide an adsorption isotherm, partial-pressure-dependent coverage, redox cycling, facet reconstruction, diffusion, or intrinsic turnover-frequency kinetics. Coverage fractions must currently be supplied explicitly. The new pathway gate/rate fields are not yet used by existing M3 reaction rules, so the M3 surface requirement remains unclosed.

## Next data and gameplay steps

1. Define the intended iron-scale preparation (oxidation temperature/atmosphere and cooling history), resulting phase stack, support/substrate, exposed outer phase/facet, and sample morphology. Do not equate a generic iron ingot or hematite dust with that specimen.
2. Select a reaction that literature actually demonstrates on that characterized surface, including liquid/gas phase, temperature, oxygen/water partial pressures, and competing products. If using scale for an M3 pathway, establish reaction-specific activity and selectivity first.
3. Add item-carried surface state and any preparation/activation/regeneration transformations through existing material/reaction systems; do not create a second reaction engine.
4. Add source-traceable measured/DFT-derived BET area, site density, adsorption/coverage and kinetics; use uncertainty bounds where the literature is sample-dependent.
5. Make M3 rules consume this surface inventory in the reactor, verify coverage and surface-state persistence through save/restart, then run GameTests, CI, owner real-pack, and the separate 100-machine Spark F2 check.

## Regression checks added

- Surface inventory scales by characterized area and sample mass.
- Coverage blocks the correct site type, not every surface site indiscriminately.
- Unknown profiles and impossible/unknown coverage inputs are refused.
- A data-defined reaction with a surface requirement refuses zero or insufficient sites and proceeds only above the declared threshold.
- The empty bundled profile set proves no surface activity is silently inferred from composition.
