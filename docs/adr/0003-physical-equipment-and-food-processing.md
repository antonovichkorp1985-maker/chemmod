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
