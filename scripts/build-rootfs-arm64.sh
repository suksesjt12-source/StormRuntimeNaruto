#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$ROOT/build/rootfs-arm64"
STAGE="$WORK/stage"
ROOTFS="$STAGE/rootfs"
IMAGE="storm-runtime-rootfs:bookworm-arm64"
ARCHIVE="$WORK/storm-rootfs-bookworm-arm64.tar.xz"
CONTAINER_ID=""

for tool in docker readelf tar sha256sum; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "Required tool not found: $tool" >&2
    exit 2
  }
done

mkdir -p "$WORK"
rm -rf "$STAGE" "$ARCHIVE"
mkdir -p "$ROOTFS"

cleanup() {
  if [[ -n "$CONTAINER_ID" ]]; then
    docker rm -f "$CONTAINER_ID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

echo "[1/5] Building Debian Bookworm ARM64 userspace image"
docker buildx build \
  --platform linux/arm64 \
  --pull \
  --load \
  --tag "$IMAGE" \
  --file "$ROOT/runtime/rootfs/Dockerfile" \
  "$ROOT"

echo "[2/5] Exporting container filesystem"
CONTAINER_ID="$(docker create --platform linux/arm64 "$IMAGE")"
docker export "$CONTAINER_ID" | tar -xf - -C "$ROOTFS"
docker rm "$CONTAINER_ID" >/dev/null
CONTAINER_ID=""

echo "[3/5] Validating rootfs ABI and essential files"
test -s "$ROOTFS/usr/bin/bash"
test -s "$ROOTFS/lib/aarch64-linux-gnu/libc.so.6"
test -e "$ROOTFS/lib/ld-linux-aarch64.so.1"
readelf -h "$ROOTFS/usr/bin/bash" | grep -q 'Class:.*ELF64'
readelf -h "$ROOTFS/usr/bin/bash" | grep -q 'Machine:.*AArch64'
readelf -h "$ROOTFS/lib/aarch64-linux-gnu/libc.so.6" | grep -q 'Machine:.*AArch64'
INTERPRETER="$(readelf -l "$ROOTFS/usr/bin/bash" | sed -n 's/.*Requesting program interpreter: \(.*\)]/\1/p')"
test "$INTERPRETER" = "/lib/ld-linux-aarch64.so.1"
test -x "$ROOTFS/bin/bash"
test -d "$ROOTFS/usr"
test -d "$ROOTFS/etc"
test -d "$ROOTFS/tmp"

echo "[4/5] Recording installed package versions"
docker run --rm --platform linux/arm64 "$IMAGE" \
  dpkg-query -W -f='${Package}=${Version}\\n' > "$STAGE/packages.lock"
test -s "$STAGE/packages.lock"

echo "[5/6] Writing rootfs provenance manifest"
cat > "$STAGE/manifest.properties" <<MANIFEST
format=storm-rootfs-v1
distribution=Debian
release=bookworm
base.image=debian:bookworm-slim
architecture=arm64
abi=arm64-v8a
libc=glibc
libc.package=libc6
loader=/lib/ld-linux-aarch64.so.1
shell=/bin/bash
rootfs.scope=minimal Linux userspace; not a complete Windows runtime
rootfs.includes=Debian base utilities; selected runtime shared-library dependencies
rootfs.excludes=PRoot; Wine; Hangover; Box64; DXVK; Android Vulkan driver; game files
runtime.status=not yet validated on Android
MANIFEST

echo "[6/6] Packaging normalized rootfs archive"
tar --sort=name --mtime='UTC 2020-01-01' --owner=0 --group=0 --numeric-owner \
  -C "$STAGE" -cJf "$ARCHIVE" rootfs manifest.properties packages.lock
(
  cd "$WORK"
  sha256sum "$(basename "$ARCHIVE")" > SHA256SUMS
)

echo "Rootfs archive: $ARCHIVE"
cat "$WORK/SHA256SUMS"
echo "This artifact is an ARM64 Glibc rootfs only; it does not contain a working PRoot or Windows runtime."
