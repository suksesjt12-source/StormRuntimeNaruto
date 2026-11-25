#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_HOME:?ANDROID_HOME is required}"
NDK="$ANDROID_HOME/ndk/27.2.12479018"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
CC="$TOOLCHAIN/aarch64-linux-android26-clang"
READELF="$TOOLCHAIN/llvm-readelf"
OUT="build/bionic-probe"
mkdir -p "$OUT"

"$CC" -std=c11 -O2 -fPIE -pie \
  -Wl,--dynamic-linker,/system/bin/linker64 \
  -Wl,-z,relro,-z,now \
  runtime/bionic/storm_bionic_probe.c \
  -o "$OUT/storm-bionic-probe"

"$READELF" -h "$OUT/storm-bionic-probe" > "$OUT/ELF-AUDIT.txt"
"$READELF" -l "$OUT/storm-bionic-probe" >> "$OUT/ELF-AUDIT.txt"
"$READELF" -d "$OUT/storm-bionic-probe" >> "$OUT/ELF-AUDIT.txt"

grep -q 'Class:.*ELF64' "$OUT/ELF-AUDIT.txt"
grep -q 'Machine:.*AArch64' "$OUT/ELF-AUDIT.txt"
grep -q '/system/bin/linker64' "$OUT/ELF-AUDIT.txt"
grep -q 'libc.so' "$OUT/ELF-AUDIT.txt"

sha256sum "$OUT/storm-bionic-probe" | tee "$OUT/SHA256SUMS.txt"
{
  echo "Bionic ARM64 ELF cross-build: PASS"
  echo "ELF class: ELF64"
  echo "Machine: AArch64"
  echo "Interpreter: /system/bin/linker64"
  echo "libc dependency: libc.so"
  echo "Execution on physical Android: NOT TESTED by this workflow"
} >> "$OUT/ELF-AUDIT.txt"
cat "$OUT/ELF-AUDIT.txt"
