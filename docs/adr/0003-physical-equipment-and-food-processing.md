# ADR-0003: Physical equipment and independent food-process chemistry

- Status: Owner requirements accepted; implementation design proposed, not implemented.
- Date: 2026-10-09
- Scope: Future ChemMod equipment/placement and food chemistry. Does not close M3.

## Owner corrections

Equipment is not inherently a one-block item or a rectangular recipe multiblock.
Large electric furnaces and distillation installations are physical structures
assembled from functional equipment, including crane-assisted installation.
Small laboratory equipment must be placeable below block scale: multiple racks,
vials and other objects may share one world cell if their occupied volumes fit.
The Minecraft storage grid must not define the permitted dimensions of objects.
Chisels & Bits is relevant as an example of sub-block geometry, not an assumed
mandatory dependency or a proven arbitrary-equipment container API.

ChemMod must remain independently useful. Optional external food integrations
must depend on ChemMod's model/API rather than making external food mods the
source of its chemistry. Food processing must transform composition and state;
adding ingredient nutrition values is not sufficient.

## 1. Separate world indexing, physical occupancy, rendering and interaction

Proposed common placed-object representation:
- stable object identity and serialization schema;
- local position/orientation and occupied volume(s);
- render model separate from collision and picking geometry;
- material, mass, support/contact requirements and relevant equipment state;
- contents, interfaces and allowed interactions belonging to the object, not
  inferred from which decorative block texture surrounds it.

Small objects can be hosted together in a cell/work-surface container. One
ordinary BlockPos cannot hold several independent ordinary block entities;
sharing requires an aggregate host or an explicitly compatible multipart API.
The host owns serialized subobjects, collision union and ray picking of the
individual object. Placement is validated by the server against occupied
volumes, supporting surfaces and placement constraints, not just an empty cell.
A fine placement grid can assist positioning without imposing one-object-per-block.

Picking, breaking, moving, stacking and dropping must preserve each object's
contents and identity exactly once. Tests must cover overlapping placement,
rejected support, reload, host destruction, chunk boundaries and multiplayer
edits. Many small objects should use batched rendering and scheduled processing,
not an independently ticking full entity for every vial.

VoxelShape can approximate physical occupancy; it does not automatically supply
rigid-body dynamics or support/load calculations. Model meshes do not implicitly
become collision meshes. Chisels & Bits geometry sharing, partial supporting
surfaces and tool interaction require checking the actual installed version and
available API before claiming compatibility. A custom equipment host cannot
silently replace and discard someone else's block/bit contents.

### Required laboratory equipment and heating (owner follow-up)

The owner explicitly requires physical laboratory equipment, not an inventory
screen permanently standing in for an apparatus:
- individually placeable flasks and test tubes, with suitable stands/racks;
- graduated cylinders and other measuring vessels, with meaningful capacity
  and graduations rather than identical decorative bottle icons;
- identifiable heat-compatible vessels, with material/geometry/rating-based
  operating limits; measuring/calibrated glassware is not interchangeable with
  vessels intended for heating, and reference measurement temperature matters;
- alcohol/spirit lamps with actual fuel consumption;
- gas burners fed through physical hose connections, with gas supply and flow
  conditions rather than a decorative hose painted on the model;
- heating plates/tables with defined energy supply and thermal contact;
- vacuum pumps (owner follow-up), connected to sealed apparatus through suitable
  tubing/ports. Pumping must account for vessel gas volume, gas removal/exhaust,
  leaks, finite pumping rate and attainable pressure, rather than a Boolean
  vacuum flag. Vessel pressure ratings and protective traps/isolation must be
  considered. Vacuum filtration and reduced-pressure distillation are intended
  applications, not currently implemented features.

Owner clarification: laboratory furnishing includes full workbenches with
functional drawers, shelves and cabinets, in metal, stone and wood—not only
floating worktops or individual decorative tables. Custom-built geometry in the
style of Chisel/Chisels & Bits is also intended; Macaw's Furniture is one possible
source of furniture, not a mandated foundation. Storage must be functional and
preserve item contents; carved geometry alone does not supply an inventory,
chemical resistance or heat resistance. Material-dependent suitability and
support must be distinct from visual appearance. Whether storage is provided by
an existing furniture block or a future ChemMod module requires implementation
and compatibility checks, without discarding the original block's state.

The same historical inventory lists Chisel 1.4.1, Chisels & Bits 21.1.32,
Chipped 4.0.2 and Rechiseled 1.2.6. These are distinct mods: decorative block
variants are not equivalent to editable sub-block geometry or functional
furniture. Their presence in that snapshot does not establish current runtime
compatibility with ChemMod equipment.

Existing furniture should be eligible as a supporting work surface where its
actual support geometry permits it; a special ChemMod table must not be required
merely to place a flask. The historical pack inventory from 2026-10-05 lists
`mcw-furniture-3.4.1-mc1.21.1neoforge.jar` (Macaw's Furniture). This is not proof
of the current PC installation or of tested equipment-placement compatibility.
Check individual tabletop heights, collision/support shapes and interaction
behaviour before advertising support. Do not replace another mod's furniture
block to host equipment. A decorative table does not automatically supply heat,
chemical resistance or other laboratory capabilities; the independent ChemMod
baseline and optional integration boundary remain unchanged.

The heat-source block below the current M3 reactor is a temporary test adapter,
not the final laboratory workflow. Future heating must model transfer into the
vessel/contents over time, accounting for supported heat capacities, losses,
power and material limits. Source temperature must not simply be equated to
instantaneous sample temperature. Relevant boiling, evaporation and damage
behaviour require explicit models and validation; they are not implemented by
adding a heat-resistance icon alone.

A first future vertical slice should combine a working surface, independently
placeable/selectable vessels and holders, and one actual controllable heater.
Additional vessel types, gas supply/hoses and more elaborate installations can
extend that foundation. This is a design direction, not a claim that the
current cube or a visibility hotfix already provides this system.

### Pack furnishing choice — 2026-10-09 clarification

The owner now confirms that the Drive pack matches the PC installation except
for newer Aeronautics Plus and ChemMod updates. This supersedes earlier warnings
that the pack inventory is only historical for other mods. A fresh Drive check
found Chisel 1.4.1, Chisels & Bits 21.1.32 and Chipped 4.0.2 JARs in the current
mods folder (`1ycFsvYr_O8WXedIz6G-GOU2sgT4q6HaR`). Macaw's Furniture is represented
by `mods/.index/macaws-furniture.pw.toml`, specifying
`mcw-furniture-3.4.1-mc1.21.1neoforge.jar` (CurseForge file 7255584), rather than a
loose JAR in that listing. The index confirms the intended dependency/version, not that the JAR was
uploaded to Drive or loaded on the PC. The missing loose JAR may reflect an
incomplete upload; the cause has not been established.

Chosen direction for this pack: Macaw's Furniture for ready-made furnishing
where its actual blocks and storage behaviour fit; Chisels & Bits for custom
bench/shelf geometry; Chisel/Chipped for decorative material variants. No new
furniture mod is required for the initial development target. This is not a
claim that Macaw's supplies every requested metal/stone lab cabinet. Functional
storage absent from existing furniture must be supplied by a separately designed
module, not inferred from carved drawer shapes. ChemMod provides the independent
apparatus/placement foundation; support for bit-built surfaces is an optional
integration requiring tests. First validate placement on ordinary surfaces,
then furniture and partial surfaces without overwriting their block contents.

## 2. Large installations and installation mechanics

A large installation consists of functional modules and connections, not a
single block with an oversized model or a decorative shell that merely unlocks
a recipe. Its spatial assembly and process representation are related but
separate: e.g. a column's feed, separation section, heating, condensation and
output connections must have defined roles in the simulated process.

Assembly must account for component mass, dimensions, orientation, supports,
connection points and lifting/installation constraints. Cranes/lifting equipment
are intended to perform installation operations rather than being a cosmetic
animation played after an entire machine appears. Exact construction methods
and lifting/structural models remain to be designed and validated.

Cross-cell installations need ownership/lookup rules, safe partial unload and
breakup, and server-authoritative interactions without duplicating inventories.
Visual detail need not imply a separate solver node for every bolt. Model
resolution and engineering approximations must be explicit and revisable; this
is not a declaration of a permanent realism ceiling or a promise of full CFD/FEA.

Baseline ChemMod placement and equipment operation must not require another mod.
Optional crane/kinetic/geometry adapters can extend the baseline through APIs.
External model assets require suitable redistribution/modification rights,
provenance, attribution where required, and adaptation for Minecraft rendering.

## 3. Independent core and outward integrations

Owner clarification: **ChemMod supplies the independent foundation, essential
functions and API; a separate food addon extends that foundation and integrates
it with farming/cooking mods.** Food-specific integration is not to be bundled
into the main mod as an alternative interpretation of this requirement.
The addon may add domain-specific process models, profiles and supported
transformations through the base API, not just map item IDs. The base provides
shared composition/state/quantity/process contracts; it is not required to
ship every food-specific model before that addon can exist.
The food addon and spatial equipment work remain future scope, not a new
priority that displaces the current M3 work.


Proposed dependency boundary:

```text
ChemMod domain model + its own baseline content/processes
                      ↑
                  ChemMod API
                      ↑
          optional compatibility addon → external mod API
```

ChemMod's molecular/process core must not import external farming/cooking APIs.
Existing third-party mods do not automatically acquire a dependency on ChemMod;
the compatibility addons depend on both, or a cooperating mod explicitly adopts
ChemMod's API. Adding pack-wide balance overrides remains a separate owner choice.

External item IDs/tags can locate an adapter's initial material profile, but
cannot be the authority for chemical identity, all food state, or transformation
results. ChemMod must exercise its generic input/process contracts using its own base
content without an external food mod; this does not require it to ship a complete
farming/cooking content set or every food-specific transformation. Unknown external foods/processes remain
unknown or explicitly estimated, not assigned invented exact compositions.

## 4. Food composition evolves through actual processing

Represent at least these separate concepts:
- physical portions and quantities of known components;
- structural/material state and food matrix relevant to the chosen model;
- process conditions and history needed for supported transformations;
- biologically accessible nutrition derived from composition/state using an
  explicit model, not treated as synonymous with all chemical energy or mass.

Processing depends on temperature over time, duration, oxygen exposure, water,
pH and other conditions where relevant to the supported rule. The same starting
food can produce different outputs when boiled, baked, fried, dried or fermented.
Different endpoints may be food, broth, vapor, gas, waste or residues, each with
its own accounted composition. Evaporation raises concentrations per unit mass
without magically creating nutrients. Leaching moves components into another
phase rather than deleting them from the whole system.

Protein denaturation is not equivalent to loss of all protein nutrition and can
increase digestibility. Severe heating can additionally alter amino-acid
availability or cause other transformations. Vitamin losses depend on the
specific substance and conditions; there must not be a universal temperature
that erases all vitamins/protein. Relevant thermal/oxidative reactions and
empirical kinetic parameters must be represented explicitly at a supported
resolution, with limitations stated rather than invented from a molecular graph.

A compatibility addon supplies actual ingredient portions, dynamic dish data
and available process conditions, then consumes ChemMod's result. It must not
replace this with a fixed nutrition bonus keyed only by finished recipe ID.
If an external process omits temperature/duration, an explicitly documented
process profile or override is needed; do not claim measured conditions.

Optional TFC/other player-diet adapters translate results into the selected
player physiology system. They must avoid double-applying hunger/diet effects
and do not make that external system a dependency of the chemical model.

## 5. Current implementation boundary

The current reactor, inventory vials and water sample nutrient field do not
implement this spatial equipment or food system. In particular, water's
`nutrientsPpm` is not a general food nutrition model. Existing model/recipe
limitations must not be presented as the final architecture.

Future-track implementation order (not the immediate development queue): specify placed-object and process contracts;
prove several independently selectable/savable small objects in one cell;
validate material portions and condition-dependent transformations in core;
then extend equipment assembly, lifting and narrowly scoped compatibility
adapters. This records the target requirements, not permission to claim these
systems are already complete or to skip the existing M3 acceptance gate.


### Conditional dependency permission — owner clarification, 2026-10-09

The owner permits a required external dependency if it is genuinely needed for
the many small physical objects. This relaxes the earlier absolute no-required-
mod constraint for equipment placement; it does not select Chisels & Bits as a
required dependency now or change the separate food-addon architecture.

Evaluate the actual supported API, NeoForge 1.21.1 compatibility, independent
object identity/inventory, picking, collision, persistence and multiplayer
before deciding whether reuse warrants a hard dependency. Editable voxel
geometry alone is not evidence that a mod supplies these equipment semantics.
Keep the chemistry/domain model decoupled where practical. Record the concrete
benefit and maintenance/version costs before introducing a required dependency;
small object dimensions alone are not sufficient justification.

The owner also flags a possibly incomplete Drive upload: a .pw.toml entry is
not proof that the corresponding JAR finished uploading. Do not describe
Macaw's Furniture as confirmed running based on that entry alone.
