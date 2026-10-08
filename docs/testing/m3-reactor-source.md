# M3 chemical reactor — manual acceptance checklist

> This guide applies to the source branch after the `Chemical Reactor` commits.
> It **does not** apply to the published `v0.9.0-test.3` JAR: that artifact has
> no reactor and must remain the M2 vial/mixing prerelease.

This is a player path. Do not use `/chem`, an item-giving command, an internal
CLI, a save editor, or a synthetic test item to replace it.

## Setup

1. Use the `chemmod-m3-manual-acceptance-<commit>` artifact from the newest
   successful **CI** run of this branch. It contains the exact JDK-21-tested
   mod JAR and its SHA-256 file; artifacts expire after 14 days and are not a
   GitHub prerelease. If it has expired, build the same branch locally with
   `./scripts/smoke-test.sh` on JDK 21.
2. Put that JAR in both the client and dedicated-server `mods` directories and
   verify the SHA-256 value before testing. Do not substitute the published
   `v0.9.0-test.3` JAR: it has no reactor.
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
ethanol + copper + heated-blast-furnace route and its slot-change boundary.
They assert that the target is consumed, copper remains, and `C2H4O`/`H2` are
created as two separate vials; a deliberately slow lit-furnace batch then
proves that removing an output vial clears progress without consuming ethanol
or producing a partial result. These regression tests contain no player
discovery or command shortcut, but they cannot replace this real client/server
checklist: they do not test the GUI, persistence across a real restart, or
multiplayer observation.

The next prerelease is not created merely because CI compiles. It is eligible
only when this checklist succeeds on a real client/server build, the automated
JDK 21 CI is green, and no regression reintroduces gameplay `/chem` shortcuts.
The first release carrying this M3 reactor will be versioned separately from
`v0.9.0-test.3` (planned next label: `v0.9.0-test.4`).
