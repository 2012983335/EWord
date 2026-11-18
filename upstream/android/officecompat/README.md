# EWord Office compatibility module

Source: CollaboraOnline/online.mirror, commit 31fe13e27b4e317249fe6229a172fdbf2c2106d9, android/lib.
The Java and resource files retain upstream MPL-2.0 headers. COPYING is upstream's licence notice.

Changes: EWord Gradle packaging, isolated process/task/preferences, single-file read-only launch,
no all-files permission request, JNI keep rules and pinned runtime preparation.
Full corresponding source: https://github.com/CollaboraOnline/online.mirror/tree/31fe13e27b4e317249fe6229a172fdbf2c2106d9

From repository root, run `pwsh ./verification/prepare-office-runtime.ps1` before Gradle.
It checks the official APK SHA-256 and extracts assets/native libraries into ignored runtime/.
It excludes upstream app-specific dexopt profiles. Runtime assets are bundled at build time;
users never download them at first launch. See verification/verify-office-apk.ps1 for package checks.
For a full native source rebuild, follow upstream android/README.md at the pinned commit.

Upstream licences are bundled as assets/license.html and notice.txt, with an EWord source notice
in assets/licences/NOTICE-officecompat.txt. No Collabora endorsement is implied.
