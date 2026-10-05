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
echo "NeoForge test jar: $mod_jar"

echo "==> Smoke test passed"
