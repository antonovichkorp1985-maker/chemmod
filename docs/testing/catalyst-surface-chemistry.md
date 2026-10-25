# M3 — research and implementation of catalyst surface chemistry

**Status:** a narrowly scoped, data-defined Cu(110) surface profile is now wired to the existing ethanol-dehydrogenation rule and reactor inventory. It is a geometrically derived model of a prepared coupon, not an experimentally calibrated reactor catalyst; CI/GameTests and owner real-pack acceptance remain outstanding.

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

So the correct sequence is: implement and accept the planned iron-bearing ore/material processing stage, then model controlled iron oxidation/scale formation and its phase/state, then consider that characterized surface for a data-defined reaction. Do not add a free-standing iron-scale item or assume Fe2O3/Fe3O4/FeO surface behavior before those dependencies exist. The absence of iron scale does not block the current M3 work. For the available copper route, a separate prepared Cu(110) coupon now has a constrained profile; it does not imply any iron-scale properties or an acquisition route.

## Current M3 copper route — constrained prepared Cu(110) coupon profile

The M3 first step is ethanol → acetaldehyde + H2. Wang et al.'s research on clean, dry Cu(111)/(110) single-crystal surfaces reports dehydrogenation at steps/kinks and increased ethoxy intermediates/acetaldehyde-H2 production as defects are induced. The experiments were conducted on prepared surfaces in ultrahigh vacuum; they do **not** establish activity for an ordinary copper ingot or a rate for an ambient-pressure reactor. The profile uses only the supported Cu(110) facet and an approximately 7% effective step-site fraction reported for the studied ethanol/alcohol probe conditions. Source: <https://www.osti.gov/pages/servlets/purl/1388884>.

The inventory calculation is explicit and limited. The profile uses an FCC Cu lattice parameter of `3.6147e-10 m`; the ideal Cu(110) surface atom density is `sqrt(2)/a^2`, and the active-site estimate is that density times 0.07 divided by Avogadro's constant, or about `1.2581071e-6 mol/m^2`. The coupon's `4.47589058277345e-4 m^2/g` area is **not a measured BET area**: it is the two broad faces of an idealized 0.5 mm thick, 1 g FCC-copper coupon, using the lattice-derived bulk density and no roughness allowance. This gives about `5.6311498e-10 mol` of modeled step sites per nominal coupon. The rule threshold is rounded slightly down to `5.6e-10 mol` to avoid a floating-point boundary while still requiring essentially the full 1 g coupon.

These values define an idealized prepared Cu(110) reference sample, not a measured commercial product. No 0.9–2.4 m2/g supported-Cu/SiO2 values are imported: support and preparation differ (<https://pmc.ncbi.nlm.nih.gov/articles/PMC10394689/>). Other supported-Cu studies likewise show that oxidation state, support, coverage and deactivation alter activity/selectivity (<https://www.colab.ws/articles/10.1016/j.apcata.2020.117710>). The 1 g `copper_catalyst_coupon` is supplied as a prepared reference item only; its crafting/acquisition/preparation route is deliberately deferred. The ordinary `copper_ingot` has no profile and cannot satisfy the ethanol rule's surface-site requirement.

The rule's Arrhenius coefficients remain the existing gameplay-scale approximation (`A = 900 s^-1`, `Ea = 45 kJ/mol`), not measured coefficients from Wang et al. The rate is scaled by available site moles relative to the nominal coupon inventory. UHV/TPD site evidence does not calibrate ambient pressure, macroscopic reactor kinetics, a turnover frequency, or selectivity; those remain caveats for owner review. This implements surface-site modeling in the existing data-defined rule path without a parallel reaction engine.

## Relevance to the current acetic-acid pathways

The M3 direct rule models the net gas-phase transformation of acetaldehyde and oxygen but is not a validated universal oxidation surface. A Pd–Au(111) study specifically used an oxygen-precovered surface; selectivity changed with Pd coverage and temperature: <https://www.osti.gov/pages/biblio/1617152>. Separately, ethanol oxidation on hematite photoanodes favors acetaldehyde, with a larger activation barrier for its further oxidation; those photoelectrochemical conditions cannot be copied to the current heated vial reactor: <https://chemistry-europe.onlinelibrary.wiley.com/doi/10.1002/cssc.202102313>.

A study of ethanol oxidation to acetic acid also reports that, in its tested liquid-phase conditions with oxygen and water, acetaldehyde-to-acetic-acid conversion was largely non-catalytic and radical-initiated; in gas-phase operation acetic acid was much less prominent: <https://pubs.rsc.org/cc/article/55/79/11833/635397/The-catalytic-and-radical-mechanism-for-ethanol>. This means the model must first distinguish phase and pathway before attaching an oxide-scale catalyst to the reaction.

Therefore the Cu(110) profile is scoped only to the ethanol-dehydrogenation rule. It does **not** label iron scale as a catalyst, transfer Cu(110) activity to either acetic-acid pathway, or claim selectivity. Surface, adsorption and reaction-specific kinetics data for those other routes remain deliberately unassigned until a condition-matched catalyst is supported.

## Implemented model contract

- `CatalystSurfaceProfile` identifies the exact material/form/phase/facet and stores the modeled area, per-site-type active fraction, lattice parameter, and evidence caveat. For the Cu coupon, area is geometry-derived rather than a BET measurement.
- `CatalystSurfaceModel.inventory` computes exposed area from actual catalyst mass, then converts it to site moles; optional coverage blocks only the corresponding site type. Mass is stored in micrograms and converted to grams before applying m²/g.
- `ReactionEnvironment.catalystSurfaceSiteMoles` carries accessible site inventory. The existing ethanol dehydrogenation rule requires `chemmod:cu110_step_site` and scales its Arrhenius rate by site amount relative to one nominal coupon.
- The reactor reads material ID, form, and primary mass from the existing material-batch component and looks up an exact material-form profile. Unknown material/forms return no sites; no new inventory component or reaction engine is assumed.
- The prepared `copper_catalyst_coupon` is the only bundled surface profile. Ordinary copper ingot remains uncharacterized and is rejected by the ethanol surface-site gate; the acquisition/preparation path is deferred.
- The profile lookup/site inventory, rule gate/rate scaling, coupon export and tooltip have targeted core/automated tests. Until they run in CI and the owner executes the matching real client/server build, this is an implementation in progress, not M3 or owner acceptance.

The model still does not provide an adsorption isotherm, partial-pressure-dependent coverage, redox cycling, facet reconstruction, diffusion, or measured intrinsic turnover-frequency kinetics. Coverage fractions must currently be supplied explicitly. UHV/TPD-derived surface-site evidence is not a macrokinetic calibration for an ambient reactor. The acetic-acid oxidation rule remains a separate abstraction and is not made experimentally surface-validated by the Cu(110) coupon.

## Remaining data and gameplay steps

1. Keep the coupon's synthesis/preparation/acquisition chain deferred until its owner scope is reviewed; do not create an unsupported recipe or infer that ordinary ingots have the coupon's site profile.
2. Validate the Cu(110) profile evidence and its transfer boundary with the owner. In particular, review the 7% active-site interpretation, idealized geometry, UHV-to-reactor caveat, and uncalibrated Arrhenius constants.
3. Run core tests, JDK-21 CI/GameTests, and the matching real client/server owner checks. Confirm tooltip presentation, coupon persistence, one-coupon reaction success, and ordinary-ingot refusal. These checks are still pending at this revision.
4. Keep the acetic-acid oxidation surface model separate until a specific catalyst surface and phase/condition-compatible evidence justify changing that rule. Do not project the Cu(110) ethanol data onto it.
5. If iron scale is considered later, first accept the iron-bearing material/ore stage, then define controlled oxidation/cooling history, resulting phase stack, substrate, exposed facet and sample morphology. Do not add an iron-scale item before that dependency.
6. Future surface-model expansion may add adsorption isotherms, redox cycling, facet reconstruction, diffusion, and measured kinetics through the existing data-defined reaction and material systems, not a parallel engine. The separate 100-machine Spark F2 check remains an owner performance gate, not evidence for surface chemistry.

## Regression checks

- Surface inventory scales by characterized area and sample mass, including the microgram-to-gram conversion.
- Coverage blocks the correct site type, not every surface site indiscriminately.
- Unknown profiles and impossible/unknown coverage inputs are refused.
- A data-defined reaction with a surface requirement refuses zero or insufficient sites and proceeds only above the declared threshold.
- The bundled Cu coupon resolves to the Cu(110) profile; ordinary copper ingot and iron material resolve to no surface sites.
- Material catalog/export tests cover the coupon form; reactor GameTests use the prepared coupon for ethanol dehydrogenation and check that an ordinary ingot cannot pass the site gate. These tests are written but not yet run at this revision.
