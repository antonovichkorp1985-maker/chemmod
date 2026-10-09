# Equipment placement foundation (development, not a player release)

## Implemented

`core.equipment.EquipmentLayout` is a Minecraft-independent immutable geometry
snapshot. It admits multiple independently identified objects with compound
axis-aligned collision boxes, without a one-cell size or coordinate restriction.
Placement/movement rejects duplicate/unknown IDs, positive-volume intersections
with other objects or supplied world obstacles, and missing horizontal support.
Touching faces are allowed. A rejected operation returns the identical original
snapshot; accepted movement retains the ID and does not collide with its old self.

Geometry uses exact Long coordinates in adapter-defined common units, not a
hardcoded voxel size. The eventual Minecraft adapter must choose and validate
its scale, bounds and rounding. No area products, midpoint calculations or
coordinate differences are needed for overlap/support validation.

Contact pads are explicit bottom-face patches, independent from visual models.
Each requested patch must be covered completely by the union of supplied
coplanar rectangles. Overlapping rectangles cannot inflate the supported area;
multiple adjacent pieces may jointly support a vessel. Separated feet do not
require a solid tabletop under the empty space between them. This conservative
policy is not a centre-of-mass, load-bearing, tipping or structural solver.

## Automated checks

`EquipmentPlacementTest` covers shared-cell placement, touching vs overlap,
identity checks, movement/rollback, compound shapes, external furniture
obstacles, exact support height, edge-only contact, holes, duplicate surfaces,
negative/cross-cell/extreme coordinates, malformed geometry and defensive list
copies. An exhaustive 3×3 support-mask test checks all 512 masks with duplicate
surfaces against a discrete coverage oracle.

Run `./gradlew :core:test`; the existing full CI smoke script also runs these
unit tests, the mod build, standalone/Create server smoke and reactor GameTests.
There is no local Java runtime in this sandbox; remote CI is the compilation and
execution gate. Record the actual run outcome separately, not as implied by
this test inventory.

## Not implemented / next integration boundary

This is not yet placeable glassware. No new block, item, model, recipe, inventory,
network payload, saved layout, ray picking, break/drop transaction or heat
simulation is introduced. The existing reactor remains unchanged. Placement
IDs here are not a substitute for authoritative persistent object ownership.

Next: a server-owned world host with persistent per-object identity/content,
selection, placement and removal transactions, followed by visible vessels and
an actual heater. Supply collision and support snapshots from the world without
overwriting furniture. Revalidate when supports change; bound geometry counts
and requests at the adapter boundary. Never accept client-provided dimensions
as authoritative. Cross-chunk ownership and unload handling need explicit tests
before enabling cross-cell installations in-game.

Chisels & Bits is a compatibility target, not a new dependency or an implemented
adapter. Its supported API must be assessed separately; editable voxel geometry
alone does not provide the ownership/storage/network model above. The owner
permits a hard dependency if reuse demonstrably justifies it (ADR-0003).
