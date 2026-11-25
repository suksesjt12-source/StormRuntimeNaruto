#!/usr/bin/env bash
set -euo pipefail

readonly WINE_URL="https://github.com/AndreRH/wine/archive/refs/tags/hangover-11.16.tar.gz"
readonly HANGOVER_URL="https://github.com/AndreRH/hangover/releases/download/hangover-11.16/hangover_11.16_ubuntu2204_jammy_arm64.tar"
readonly WINE_SHA256="21e4dd77a9d1d4897194d5751e03a5fc6cdec4390889ad6a652b87d05aa0bb4d"
readonly HANGOVER_SHA256="ecde5846b2f1b23d394fede04a52cbf31853076b85e98d53a391d9e758345488"

mkdir -p build/hangover-audit
cd build/hangover-audit

curl --fail --location --retry 3 --output wine-hangover-11.16.tar.gz "$WINE_URL"
curl --fail --location --retry 3 --output hangover-11.16-arm64.tar "$HANGOVER_URL"

printf '%s  %s\n' "$WINE_SHA256" wine-hangover-11.16.tar.gz | sha256sum --check --strict
printf '%s  %s\n' "$HANGOVER_SHA256" hangover-11.16-arm64.tar | sha256sum --check --strict

tar -tzf wine-hangover-11.16.tar.gz > wine-source-files.txt
tar -tf hangover-11.16-arm64.tar > hangover-arm64-files.txt

grep -qE '(^|/)configure$' wine-source-files.txt
grep -qE '(^|/)README(\\.md)?$' hangover-arm64-files.txt || true

sha256sum wine-hangover-11.16.tar.gz hangover-11.16-arm64.tar | tee INPUT-SHA256.txt
printf 'Hangover 11.16 source and ARM64 release archive integrity: PASS\n'
printf 'This job does NOT build or execute Wine/Hangover for Android.\n'
printf 'The release archive targets Ubuntu 22.04 ARM64 and is audit input only.\n'
