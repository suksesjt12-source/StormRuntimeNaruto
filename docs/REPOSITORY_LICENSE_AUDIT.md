# Repository License Audit

Updated: 2026-11-26

## Findings from the current repository tree

- No root-level `LICENSE`, `COPYING`, or `NOTICE` file was present in the audited branch tree.
- The Android app declares AndroidX DocumentFile 1.0.1. Its upstream license and required notices must be included in a release notice bundle.
- Android SDK, NDK, Gradle, CMake, and GitHub Actions dependencies are toolchain/build dependencies; their individual license and terms remain governed by their upstream distributions.
- No Wine, Hangover, DXVK, Box64, or FEX source/binary is currently vendored in the app runtime tree.

## Required before runtime redistribution

For each added third-party component, record:

1. Canonical upstream repository and exact immutable commit/tag.
2. SPDX license identifier and license text.
3. Copyright and attribution notices.
4. Source redistribution, modification, and binary redistribution obligations.
5. Whether linking or packaging changes the obligations for this application.
6. Build provenance and hashes for every shipped binary.

## Phase 2 onward

Wine, Hangover, and any selected WoW64 translator must be audited at the exact selected revision before integration. Do not infer a license from a project name or from a package recipe. Do not package Ubuntu/Termux binaries or proprietary game data as part of this project.

## Current status

Repository-level audit identified the absence of a root license/notice file. This document is an inventory of the current findings, not legal advice and not a completed third-party redistribution clearance. The project owner must choose and add an appropriate license for original project code and provide required third-party notices before distributing release APKs.
