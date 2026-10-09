# M3 chemical reactor — manual acceptance checklist

> This is the developer/source acceptance reference for M3. Player testing of
> the published reactor prerelease uses
> [`v0.9.0-test.4.md`](v0.9.0-test.4.md). It **does not** apply to
> `v0.9.0-test.3`: that artifact has no reactor and remains the M2
> vial/mixing prerelease.

This is a player path. Do not use `/chem`, an item-giving command, an internal
CLI, a save editor, or a synthetic test item to replace it.

## Setup

1. For the published player test, follow
   [`v0.9.0-test.4.md`](v0.9.0-test.4.md) and use its tagged prerelease. To
   validate later source changes, use the matching CI artifact or build the
   branch locally with `./gradlew :mod:jar` on JDK 21.
2. Put the chosen JAR in both the client and dedicated-server `mods`
   directories and verify its SHA-256 value before testing. Do not substitute
   the published `v0.9.0-test.3` JAR: it has no reactor.
3. In a creative test world obtain: one Chemical Reactor, one blast furnace (or
   another supported heat source), fuel, one copper ingot, one 100% ethanol
   vial, and two empty laboratory vials.
4. Place and ignite the blast furnace. Place the Chemical Reactor directly on
   top of it. It must be visibly lit before the reactor can satisfy its heat
   condition.
5. Switch to survival before operating the machine.

The first M3 line is deliberately physical and small:

```text
pure ethanol vial + copper catalyst + heat
  → acetaldehyde vial + hydrogen vial
```

## Successful physical batch

1. Open the reactor by right-clicking it.
2. Insert the ethanol vial into **Target**.
3. Insert one copper ingot into **Cat.**.
4. Insert one empty laboratory vial into each **Products** slot.
5. Do not insert a co-reactant for this particular route.
6. Check the UI: it must identify **Alcohol dehydrogenation**, show its rule
   text through `?`, and change to processing only while the heat source is
   active.
7. Wait for the server-synchronized progress bar to reach the end. It advances
   once per second and is faster with a hotter valid source according to the
   rule's Arrhenius parameters.
8. Verify that the original ethanol vial becomes empty, the copper ingot remains,
   and the two product slots contain separate vials with formulas `C2H4O` and
   `H2`. Their amounts must be exact and must not be merged into one mixture.

## Rejection and atomicity checks

Perform each check with fresh physical vials and confirm that **no input content
is consumed**.

- No heat source below the reactor: the UI names the physical alcohol rule and
  reports that heat is required; progress stays at zero.
- No copper catalyst: the UI names the rule and reports that its catalyst is
  required; progress stays at zero.
- A filled or non-vial product slot: the UI reports that empty product vials are
  required; neither source vial changes.
- A mixture or impure vial in an input: the reactor rejects it rather than
  silently extracting a pure component.
- Replace or remove an input or output vial while a batch is in progress: the
  stored progress must cancel immediately, and the next one-second evaluation
  must re-plan/refuse before it mutates any slot.

## Persistence and multiplayer

1. Start a visible partial batch, leave the input, catalyst, output vials, and
   heat source in place, then restart the server/world. The machine inventory
   and progress must resume only for the same valid physical operation.
2. Join with a second player while the first player has the screen open. Insert
   or remove a vial from one client and confirm that both clients receive the
   synchronized slot/progress state. The final batch must still create each
   product once, never duplicate it, and never consume a stale source.
3. After a successful result, restart once more and verify that product vial
   contents remain intact. Discovery is written server-side only after that
   successful transaction; no creative placement, refusal, or hand-mixing path
   may create a discovery record.

## Automated guard and release gate

JDK-21 CI also runs required headless GameTests for the physical
ethanol + copper + heated-blast-furnace route and its refusal/slot-change
boundaries. They assert that the target is consumed, copper remains, and
`C2H4O`/`H2` are created as two separate vials; missing heat/copper, an
analytically impure ethanol vial, or an explicit ethanol/water mixture leaves
all input/output contents untouched; and a deliberately slow lit-furnace batch
proves that removing an output vial clears progress without consuming ethanol
or producing a partial result. These regression tests contain no player
discovery or command shortcut, but they cannot replace this real client/server
checklist: they do not test the GUI, persistence across a real restart, or
multiplayer observation.

## Post-test.4 source regression: persisted operation identity

These changes are **not in the published test.4 JAR**. Validate them with the
matching commit's CI artifact; test.4 and its tag remain unchanged.

The reactor now saves a frozen `processing_operation` witness containing the
rule ID and the full serialized slot inventory (positions, items, counts and
components). On the next server evaluation the witness must equal the current
physical plan before old progress can continue. A mismatch starts a new batch
from zero, without modifying any vial. Heat and catalyst conditions are still
checked from the current world, not trusted from the save.

Only finite progress in `(0, 1]` with an operation witness is resumable. NaN,
infinities and out-of-range values reset progress rather than bypassing the
completion check. Saves made by test.4 or earlier lack that witness: migration
retains all items and vial contents, but discards unfinished progress and its
processing-operator attribution. No completed product is undone or duplicated.

Eight additional headless GameTests exercise:

- fresh block-entity NBT reload of a real partial batch, including a second save
  before its first tick, continuation, exact products, and completed-state reload;
- changed input quantity under the same rule, and mismatching saved rule;
- old-format progress without a witness;
- NaN, both infinities and finite out-of-range progress;
- loss of actual heat after saving;
- a filled product vial and a non-vial product item, preserving the entire inventory.

They use the actual Minecraft item codecs and a newly constructed block entity.
They are **not a process restart, disk flush, chunk-unload test, multiplayer test
or discovery test**. Real server restart and player UI acceptance remain open.
When testing migration, keep a backup of the test.4 world and expect only the
unfinished progress to restart on first load with the new source build.

## Post-test.4 source regression: discovery after physical commit

Three further GameTests exercise discovery without adding gameplay commands:

1. A single physical lifecycle uses two named NeoForge fake players. The real
   sneak-use mixing path, sample insertion, operator selection, no-heat refusal,
   partial progress, and cancellation of the **first** synthesis must all leave
   the world ledger unchanged. A subsequent valid partial batch is saved and
   loaded after another player becomes the most recent viewer. Only completed
   physical product vials may register discoveries, credited to the initiating
   operator. Repeating the synthesis as the second player preserves the original
   author and timestamp.
2. An isolated ledger round-trip verifies Cyrillic names, optional trivial names,
   millisecond timestamps and the SavedData dirty flag. Replaying an existing
   discovery must not overwrite it or mark a freshly loaded ledger dirty.
3. Unsupported schema versions and duplicate canonical keys must be rejected,
   not silently replaced by an empty ledger. This is validation, not a repair
   or migration tool for damaged worlds.

The physical first-synthesis test requires a fresh, disposable GameTest world;
its setup never clears real discovery history. Isolated codec tests do not
write synthetic records to the world ledger. Fake players test the server-side
operator hooks; they do **not** test GUI opening packets, two-client networking,
creative-tab rendering or full process restart. There is still no player-facing
registry viewer, so the discovery parts of manual acceptance remain distinct
from what can currently be observed in the reactor GUI.

## Post-test.4 source: stable result display and readable labels

After a successful commit the UI keeps the completed status, selected rule and
100% bar while the exact resulting inventory stays in place. The internal
reaction progress is zero: this is a display of the committed result, not an
operation waiting to execute again. A persisted `completed_operation` snapshot
allows the same display after an NBT reload, verified on the next server tick.
Taking/replacing any slot clears the result display immediately; changes made
to saved items are detected on the next evaluation. An empty source vial then
shows idle/waiting rather than the incorrect "pure vials only" refusal.
Older saves without this completion snapshot retain their products, but do not
infer a success display from the historical rule ID alone.

The reactor screen now has a stable title and bounded status/rule labels.
Hover either label for its full wrapped text; click `?` to toggle the wrapped
rule explanation. The explanation is a tooltip rather than a paragraph painted
across the inventory. Progress text and the progress bar have separate rows,
slot labels are width-limited, and normal item tooltips are rendered explicitly.
Both English and Russian strings are supplied.

Automated coverage extends the real partial-batch/completed-reload test to
assert the synchronized display, removal reset, and idle empty vial. Another
GameTest checks that changed saved products cannot retain a false success label.
These tests do not render the client. **Manual checks still required:** RU/EN,
small and large GUI scales, hover text, `?` toggle, item tooltips, a completed
batch left for several seconds, and collecting its products after reopening.

## M3 test-release and completion gates

`v0.9.0-test.4` is the player-test artifact for this checklist, not proof that
M3 is already complete. Its publication requires green automated JDK 21 CI and
no regression that introduces gameplay `/chem` shortcuts. The **completion**
gate for M3 is this checklist succeeding on a real client/server build. A failed
case is reported against `test.4` and fixed in a later test prerelease rather
than silently reissuing `test.4`.
