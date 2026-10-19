#!/usr/bin/env bash
# Cross-compiles playitd (playit.gg tunnel daemon) for Android arm64 with the NDK.
# The official static Linux build uses musl, which can't resolve DNS on Android (no /etc/resolv.conf),
# so we build against bionic instead. Output: <assets/runtime>/playitd
set -euo pipefail
OUT="${1:?usage: build_playit.sh <assets/runtime dir>}"
OUT="$(cd "$OUT" && pwd)"
WORK="${PLAYIT_WORK:-$PWD/build/playit}"

export ANDROID_NDK_HOME="${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_HOME:-}}"
[ -d "$ANDROID_NDK_HOME" ] || { echo "Android NDK tidak ditemukan"; exit 1; }

if [ -z "${PLAYIT_TAG:-}" ]; then
  AUTH=()
  [ -n "${GITHUB_TOKEN:-}" ] && AUTH=(-H "Authorization: Bearer $GITHUB_TOKEN")
  PLAYIT_TAG=$(curl -sSfL "${AUTH[@]}" https://api.github.com/repos/playit-cloud/playit-agent/releases/latest \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["tag_name"])')
fi
echo "playit-agent tag: $PLAYIT_TAG"

if [ ! -d "$WORK/.git" ] || [ "$(git -C "$WORK" describe --tags --exact-match 2>/dev/null)" != "$PLAYIT_TAG" ]; then
  rm -rf "$WORK"
  git clone --depth 1 --branch "$PLAYIT_TAG" https://github.com/playit-cloud/playit-agent "$WORK"
fi

rustup toolchain install stable --profile minimal >/dev/null
rustup target add aarch64-linux-android --toolchain stable
command -v cargo-ndk >/dev/null || cargo install cargo-ndk --locked

cd "$WORK"
cargo +stable ndk -t arm64-v8a -P 26 build --release -p playitd --bin playitd

BIN="$WORK/target/aarch64-linux-android/release/playitd"
"$ANDROID_NDK_HOME"/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip "$BIN" || true
cp "$BIN" "$OUT/playitd"
file "$OUT/playitd" || true
ls -la "$OUT/playitd"
