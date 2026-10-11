# Working agreement with the owner

These are standing instructions from the owner (antonovichkorp1985), not this agent's
preferences. They outrank anything inferred from the code.

## Conversation

- **Duplicate messages.** The owner's client sometimes sends the same message several times.
  When a message repeats one already answered and acted on, reply with the single word
  **«сделано»** and nothing else. Do not re-explain, do not repeat the summary, do not ask
  the question again.

## Design

- **No tabulation.** Behaviour must follow from properties of atoms and molecules, not from a
  row per substance. The test is: does adding a new substance require a new row or a new
  rule? If it does, the design is wrong. Properties live in data per *element*; outcomes are
  computed.
- Add property models and rule *types*, never substance tables.
- No hardcoding where an abstraction is possible.

## Process

- ChemMod scope only. The Aeronautics Plus instruction is the boundary.
- Substantive chunks, not many small commits. GitHub prerelease before acceptance.
- **CI green is not acceptance.** World-side changes need the gametest harness plus an
  owner run on the real pack.
- No parallel agents.
- Prefer the provided edit tools over shell heredocs; after creating a file, confirm it is
  staged before committing.
