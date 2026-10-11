# Third-party notices

ChemMod's All Rights Reserved terms apply only to original ChemMod materials.
They do not replace or restrict the licenses of the components below.

## Kotlin standard library

ChemMod release JARs embed Kotlin Standard Library 2.0.21.

Copyright © 2010–2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
Licensed under the Apache License, Version 2.0:
<https://www.apache.org/licenses/LICENSE-2.0>

The embedded library JAR retains its own license and notice metadata.

## kotlinx.serialization

ChemMod release JARs embed kotlinx.serialization Core and JSON 1.7.3.

Copyright © 2017–2024 JetBrains s.r.o. and kotlinx.serialization contributors.
Licensed under the Apache License, Version 2.0:
<https://www.apache.org/licenses/LICENSE-2.0>

The embedded library JARs retain their own license and notice metadata.

## Gradle Wrapper

The repository contains Gradle Wrapper files from the Gradle project. They are
used as build tooling and are not original ChemMod code.

Copyright © 2015–2024 the original authors.
Licensed under the Apache License, Version 2.0:
<https://www.apache.org/licenses/LICENSE-2.0>

## Create compatibility API

ChemMod 0.8.0-test.1 compiles its optional kinetic-separator adapter against the
public API of Create 6.0.10 for Minecraft 1.21.1. Create is **not embedded,
redistributed, or copied** into ChemMod; it remains a separately installed optional
runtime mod. No Create assets or source code are included in this repository.

Create code is made available by The Create Team / The Creators of Create under
its MIT code license; Create assets retain their separate All Rights Reserved
status. See <https://github.com/Creators-of-Create/Create/blob/mc1.21.1-6.0.10/LICENSE.md>.

## Platform dependencies

Minecraft, NeoForge, and their APIs are external platform dependencies and are
not relicensed as part of ChemMod. Minecraft is a trademark of Microsoft
Corporation. ChemMod is not affiliated with or endorsed by Microsoft or Mojang.


## Original laboratory assets

The rack, tray, vial-label and reactor models/textures introduced in test.7 are
original ChemMod procedural assets produced by `scripts/generate_lab_assets.py`.
They do not copy external machine models or textures and are covered by the
repository's ChemMod license. The generator uses only the Python standard
library; it introduces no bundled asset-library dependency.
