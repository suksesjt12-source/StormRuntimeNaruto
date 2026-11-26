#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$ROOT/build/glibc-arm64"
SRC="$WORK/src"
BUILD="$WORK/build"
SYSROOT="$WORK/sysroot"
TAG="glibc-2.39"
REPO="https://github.com/bminor/glibc.git"
JOBS="${BUILD_JOBS:-2}"

if ! [[ "$JOBS" =~ ^[1-9][0-9]*$ ]]; then
  echo "BUILD_JOBS must be a positive integer" >&2
  exit 2
fi

for tool in git make gcc aarch64-linux-gnu-gcc aarch64-linux-gnu-g++ readelf; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "Required build tool not found: $tool" >&2
    exit 2
  }
done

mkdir -p "$WORK"
if [[ ! -d "$SRC/.git" ]]; then
  git clone --filter=blob:none --no-checkout "$REPO" "$SRC"
fi

git -C "$SRC" fetch --depth=1 origin "refs/tags/$TAG:refs/tags/$TAG"
git -C "$SRC" checkout --detach "$TAG"

rm -rf "$BUILD" "$SYSROOT"
mkdir -p "$BUILD" "$SYSROOT"
cd "$BUILD"

BUILD_TRIPLET="$(gcc -dumpmachine)"
SOURCE_COMMIT="$(git -C "$SRC" rev-parse HEAD)"
export CC=aarch64-linux-gnu-gcc
export CXX=aarch64-linux-gnu-g++
export AR=aarch64-linux-gnu-ar
export RANLIB=aarch64-linux-gnu-ranlib

"$SRC/configure" \
  --build="$BUILD_TRIPLET" \
  --host=aarch64-linux-gnu \
  --prefix=/usr \
  --with-headers=/usr/aarch64-linux-gnu/include \
  --enable-kernel=4.14 \
  --disable-werror \
  --disable-nscd

make -j"$JOBS"
make install DESTDIR="$SYSROOT"

LOADER="$SYSROOT/usr/lib/ld-linux-aarch64.so.1"
if [[ ! -s "$LOADER" ]]; then
  LOADER="$SYSROOT/lib/ld-linux-aarch64.so.1"
fi
test -s "$LOADER"
readelf -h "$LOADER" | grep -q 'Class:.*ELF64'
readelf -h "$LOADER" | grep -q 'Machine:.*AArch64'

cat > "$WORK/manifest.txt" <<MANIFEST
Storm Runtime experimental Glibc sysroot
Upstream: $REPO
Tag: $TAG
Source commit: $SOURCE_COMMIT
Target: aarch64-linux-gnu (ARM64)
Minimum Linux kernel ABI: 4.14
Build host: $BUILD_TRIPLET
Compiler: $(aarch64-linux-gnu-gcc --version | head -n1)
Build jobs: $JOBS
Scope: Glibc sysroot only; not a complete Linux rootfs.
Not included: PRoot, Wine, Hangover, Box64, DXVK, Vulkan driver, Android display driver.
Not verified: execution on Android, execution on Helio G100, Windows PE32 execution.
MANIFEST

printf 'Glibc ARM64 sysroot built at %s\n' "$SYSROOT"
printf 'Glibc source commit: %s\n' "$SOURCE_COMMIT"
