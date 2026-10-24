# M2 source acceptance — phase estimates and steel-flask transfer

This checklist applies to a matching source/CI build, not to the historical `v0.9.0-test.3` artifact. It supplements that release's M2 checklist; it does not close M2 until its world-side actions are accepted by the owner.

## A1 — graph-derived phase estimate

1. Use the matching ChemMod build in the Overworld with a filled substance vial and inspect it while the player is at a known height.
2. Confirm the tooltip displays the primary molecule's graph-derived gas/condensed estimate and the temperature/pressure used.
3. At sea-level height the current baseline is Earth ISA (`288.15 K`, `101325 Pa`). Ethanol should be estimated condensed; methane should be estimated gas. The temperature and pressure should change with Overworld altitude according to the same atmosphere model.
4. Switch RU/EN and confirm all phase/status lines are localized.
5. A tooltip for an explicit mixture must say that mixture phase is not estimated; it must not classify every part as an independent pure liquid/gas.
6. Confirm the honest model boundary: ChemMod currently distinguishes vapour from condensed only, not liquid from solid. The current M2 estimate uses Earth ISA by altitude; it is not a sensor for local room temperature, biome/season, fire, or a future thermal field. Do not describe it as a measured indoor temperature.

## A3 — transfer a half-full vial to a steel flask

1. Obtain one empty steel flask and one single filled ethanol vial containing exactly `0.500000 mol`.
2. Hold the empty steel flask in the main hand and the filled vial in the offhand; use the flask.
3. Confirm the vial remains as an empty vial and the steel flask contains the exact ethanol graph and `0.500000 mol`; no material is created, lost, or rounded. The flask tooltip must show formula, amount, purity, molar mass and advanced graph identity.
4. Repeat with an explicit two-component mixture and verify both canonical graph identities and exact amounts survive unchanged.
5. Refuse an already-filled flask, an empty source vial, an invalid component combination, or a stack of source vials without changing either input.
6. Save/reload the world; both the flask and empty source vial must retain their component state. Check RU/EN item names and tooltips.

This M2 slice moves an exact whole portion; the “half-full” sample is represented by its exact quantity. It does not define a vessel-capacity/volume model, chemical compatibility, corrosion, leakage, pressure ratings or any other A4 behaviour. Keep that A4 work in Stage B. The automated guards are `AtmosphericPhaseEstimateTest` and `VialTransferGameTests`; they supplement but do not replace this owner real-pack run.
