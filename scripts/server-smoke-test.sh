#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

mode="${1:-standalone}"
case "$mode" in
    standalone)
        gradle_args=(-Pchemmod.createRuntime=false)
        ;;
    create)
        gradle_args=()
        ;;
    *)
        echo "Usage: $0 [standalone|create]" >&2
        exit 2
        ;;
esac

run_dir="mod/run"
log_file="$run_dir/chemmod-server-smoke-${mode}.log"
rm -rf "$run_dir/world"
mkdir -p "$run_dir"
printf 'eula=true\n' > "$run_dir/eula.txt"
: > "$log_file"

echo "==> Starting a headless NeoForge server (${mode})"
setsid ./gradlew --no-daemon "${gradle_args[@]}" :mod:runServer > "$log_file" 2>&1 &
server_pid=$!

cleanup() {
    kill -TERM -- "-$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
}
trap cleanup EXIT

deadline=$((SECONDS + 240))
while (( SECONDS < deadline )); do
    if grep -Eq 'Done \([0-9.,]+s\)! For help' "$log_file"; then
        if grep -Eqi "Failed to load datapacks|Couldn't load tag|Errors in currently selected datapacks|Exception loading level" "$log_file"; then
            echo "Server smoke-test failure: runtime data-pack errors were reported" >&2
            tail -n 200 "$log_file" >&2
            exit 1
        fi
        if [[ "$mode" == "create" ]] && ! grep -Fq "ChemMod Create compatibility adapter enabled" "$log_file"; then
            echo "Server smoke-test failure: Create was present but ChemMod did not activate its adapter" >&2
            tail -n 200 "$log_file" >&2
            exit 1
        fi
        echo "Headless NeoForge server reached a playable state (${mode})"
        exit 0
    fi
    if ! kill -0 "$server_pid" 2>/dev/null; then
        echo "Server smoke-test failure: dedicated server exited before startup completed" >&2
        tail -n 200 "$log_file" >&2
        exit 1
    fi
    sleep 2
done

echo "Server smoke-test failure: dedicated server did not finish startup within 240 seconds" >&2
tail -n 200 "$log_file" >&2
exit 1
