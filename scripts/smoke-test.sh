#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> Compiling and running unit tests"
./gradlew --no-daemon check installDist :mod:build

chem="core-cli/build/install/chem/bin/chem"

assert_contains() {
    local output=$1
    local expected=$2
    if [[ "$output" != *"$expected"* ]]; then
        echo "Smoke-test failure: expected output to contain: $expected" >&2
        echo "Actual output:" >&2
        echo "$output" >&2
        exit 1
    fi
}

echo "==> Checking ethanol lookup"
lookup_output=$("$chem" lookup CCO)
echo "$lookup_output"
assert_contains "$lookup_output" "Formula:           C2H6O"
assert_contains "$lookup_output" "Molar mass:        46.069 g/mol"
assert_contains "$lookup_output" "Boiling point:     64.0 °C (predicted)"

echo "==> Checking isomer identity"
ethanol_id=$("$chem" lookup CCO | awk -F': *' '/Canonical ID/ {print $2}')
reverse_id=$("$chem" lookup OCC | awk -F': *' '/Canonical ID/ {print $2}')
ether_id=$("$chem" lookup COC | awk -F': *' '/Canonical ID/ {print $2}')
[[ "$ethanol_id" == "$reverse_id" ]] || {
    echo "Smoke-test failure: CCO and OCC must have the same canonical ID" >&2
    exit 1
}
[[ "$ethanol_id" != "$ether_id" ]] || {
    echo "Smoke-test failure: ethanol and dimethyl ether must have different canonical IDs" >&2
    exit 1
}

echo "==> Checking ethanol combustion balance"
balance_output=$("$chem" balance "CCO + O=O -> O=C=O + O")
echo "$balance_output"
assert_contains "$balance_output" "C2H6O + 3 O2 -> 2 CO2 + 3 H2O"
assert_contains "$balance_output" "Atoms and formal charge conserved: true"

echo "==> Checking data-defined reaction rule"
reaction_output=$("$chem" react C=CC chemmod:alkene_hydrogenation 350 '[H][H]' --catalyst chemmod:palladium)
echo "$reaction_output"
assert_contains "$reaction_output" "C3H6 + H2 -> C3H8"
assert_contains "$reaction_output" "Atoms and formal charge conserved: true"

echo "==> Checking distributable NeoForge mod"
mod_jar=$(find mod/build/libs -maxdepth 1 -type f -name 'chemmod-*.jar' ! -name '*sources*' -print -quit)
[[ -n "$mod_jar" ]] || {
    echo "Smoke-test failure: :mod:build did not produce a distributable mod jar" >&2
    find mod/build -type f -name '*.jar' -print >&2 || true
    exit 1
}
jar tf "$mod_jar" | grep -q '^META-INF/neoforge.mods.toml$' || {
    echo "Smoke-test failure: mod metadata is missing from $mod_jar" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^META-INF/jarjar/metadata.json$' || {
    echo "Smoke-test failure: embedded core/runtime metadata is missing from $mod_jar" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^META-INF/LICENSE_chemmod$' || {
    echo "Smoke-test failure: ChemMod license is missing from $mod_jar" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^META-INF/THIRD_PARTY_NOTICES.md$' || {
    echo "Smoke-test failure: third-party notices are missing from $mod_jar" >&2
    exit 1
}
unzip -p "$mod_jar" META-INF/neoforge.mods.toml | grep -q 'license="All Rights Reserved"' || {
    echo "Smoke-test failure: mod metadata does not identify All Rights Reserved" >&2
    exit 1
}
unzip -p "$mod_jar" META-INF/neoforge.mods.toml | grep -q 'modId="tfc"' || {
    echo "Smoke-test failure: optional TerraFirmaCraft adapter metadata is missing" >&2
    exit 1
}
unzip -p "$mod_jar" META-INF/neoforge.mods.toml | grep -q 'modId="create"' || {
    echo "Smoke-test failure: optional Create adapter metadata is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q 'integration/create/KineticOreSeparatorBlock.class$' || {
    echo "Smoke-test failure: Create kinetic separator adapter is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^data/chemmod/recipe/kinetic_ore_separator.json$' || {
    echo "Smoke-test failure: conditional Create kinetic separator recipe is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^assets/chemmod/models/item/kinetic_ore_separator.json$' || {
    echo "Smoke-test failure: Create kinetic separator item model is missing" >&2
    exit 1
}
if jar tf "$mod_jar" | grep -q '^com/simibubi/create/'; then
    echo "Smoke-test failure: Create classes were embedded instead of remaining optional" >&2
    exit 1
fi
jar tf "$mod_jar" | grep -q 'TerraFirmaCraftWaterAdapter.class$' || {
    echo "Smoke-test failure: optional TerraFirmaCraft water adapter is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q 'MaterialFormItem.class$' || {
    echo "Smoke-test failure: canonical material form item is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^assets/chemmod/models/item/copper_ingot.json$' || {
    echo "Smoke-test failure: canonical copper ingot model is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^data/c/tags/item/ingots/copper.json$' || {
    echo "Smoke-test failure: common copper ingot tag is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q 'ChemBlocks.class$' || {
    echo "Smoke-test failure: canonical world blocks are missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q 'CopperStorageBlockEntity.class$' || {
    echo "Smoke-test failure: persistent copper storage block entity is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^assets/chemmod/blockstates/copper_block.json$' || {
    echo "Smoke-test failure: copper storage block state is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^data/c/tags/item/storage_blocks/copper.json$' || {
    echo "Smoke-test failure: common copper storage tag is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^data/chemmod/loot_table/blocks/copper_block.json$' || {
    echo "Smoke-test failure: state-preserving copper storage loot table is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^data/chemmod/worldgen/placed_feature/native_copper_ore.json$' || {
    echo "Smoke-test failure: native copper worldgen is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^data/chemmod/loot_table/blocks/native_copper_ore.json$' || {
    echo "Smoke-test failure: native copper ore loot is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q 'StoneMortarBlock.class$' || {
    echo "Smoke-test failure: manual material processing block is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q 'OreWasherBlock.class$' || {
    echo "Smoke-test failure: water-gated ore washer is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q 'OreSeparatorBlock.class$' || {
    echo "Smoke-test failure: physical ore separator is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^assets/chemmod/models/item/copper_purified_crushed_ore.json$' || {
    echo "Smoke-test failure: purified copper ore form model is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^data/chemmod/recipe/ore_washer.json$' || {
    echo "Smoke-test failure: ore washer recipe is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^data/chemmod/recipe/ore_separator.json$' || {
    echo "Smoke-test failure: physical ore separator recipe is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^assets/chemmod/models/item/ore_separator.json$' || {
    echo "Smoke-test failure: physical ore separator model is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^data/c/tags/item/purified_crushed_ores/copper.json$' || {
    echo "Smoke-test failure: common purified copper ore tag is missing" >&2
    exit 1
}
unzip -p "$mod_jar" data/chemmod/loot_table/blocks/native_copper_ore.json | grep -q 'primary_mass_micrograms' || {
    echo "Smoke-test failure: mined copper ore does not carry exact primary mass" >&2
    exit 1
}
unzip -p "$mod_jar" data/chemmod/loot_table/blocks/native_copper_ore.json | grep -q 'silicate_gangue' || {
    echo "Smoke-test failure: mined copper ore does not carry measured gangue" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^data/chemmod/recipe/stone_mortar.json$' || {
    echo "Smoke-test failure: stone mortar recipe is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q 'RefractoryFurnaceBlock.class$' || {
    echo "Smoke-test failure: canonical melting adapter is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q 'IngotMoldBlock.class$' || {
    echo "Smoke-test failure: canonical casting adapter is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^assets/chemmod/models/item/molten_copper_crucible.json$' || {
    echo "Smoke-test failure: molten copper container model is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q 'MetalworkingBenchBlock.class$' || {
    echo "Smoke-test failure: canonical metal forming adapter is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^assets/chemmod/models/item/copper_gear.json$' || {
    echo "Smoke-test failure: finished copper gear model is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^data/c/tags/item/gears/copper.json$' || {
    echo "Smoke-test failure: common copper gear tag is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^data/chemmod/recipe/metalworking_chisel.json$' || {
    echo "Smoke-test failure: count-aware cutting tool recipe is missing" >&2
    exit 1
}
jar tf "$mod_jar" | grep -q '^data/chemmod/recipe/drawplate.json$' || {
    echo "Smoke-test failure: drawplate recipe is missing" >&2
    exit 1
}
echo "NeoForge test jar: $mod_jar"

# First prove the optional adapter does not hard-break a standalone ChemMod server,
# then start the same headless server with Create on ModDevGradle's runtime classpath.
./scripts/server-smoke-test.sh standalone
./scripts/server-smoke-test.sh create

# Required in-world M3 regression: this runs the dedicated GameTest server and
# exits non-zero if the physical ethanol/copper/heat batch does not complete.
# It is intentionally supplementary to the player acceptance checklist; it
# does not use an in-game command path to award discovery or simulate survival.
echo "==> Running Chemical Reactor GameTests"
./gradlew --no-daemon :mod:runGameTestServer

echo "==> Smoke test passed"
