#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$ROOT/build/glibc-arm64"
SRC="$WORK/src"
BUILD="$WORK/build"
SYSROOT="$WORK/sysroot"
TAG="glibc-2.39"
REPO="https://github.com/bminor/glibc.git"
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
export CC=aarch64-linux-gnu-gcc
export CXX=aarch64-linux-gnu-g++
export AR=aarch64-linux-gnu-ar
export RANLIB=aarch64-linux-gnu-ranlib
"$SRC/configure" --build="$BUILD_TRIPLET" --host=aarch64-linux-gnu --prefix=/usr --with-headers=/usr/aarch64-linux-gnu/include --enable-kernel=4.14 --disable-werror
make -j"$(nproc)"
make install DESTDIR="$SYSROOT"
cat > "$WORK/manifest.txt" <<MANIFEST
Storm Runtime experimental Glibc sysroot
Upstream: $REPO
Tag: $TAG
Target: aarch64-linux-gnu (ARM64)
Minimum Linux kernel ABI: 4.14
Build host: $BUILD_TRIPLET
Compiler: $(aarch64-linux-gnu-gcc --version | head -n1)
Scope: Glibc sysroot only; not a complete Linux rootfs.
Not included: PRoot, Wine, Hangover, Box64, DXVK, Vulkan driver, Android display driver.
Not verified: execution on Android, execution on Helio G100, Windows PE32 execution.
MANIFEST
printf 'Glibc ARM64 sysroot built at %s\n' "$SYSROOT"
