# RouterCloud Android

Native Android client for the self-hosted RouterCloud platform.

Built with Kotlin and Jetpack Compose, with a Metro-inspired mobile interface
designed for private LAN and Tailscale access.

## Current status

Active development.

Implemented:

- RouterCloud authentication and session handling
- file and folder browsing
- upload and download
- folder creation
- rename and delete according to backend permissions
- Android share target
- storage usage view
- Metro-inspired mobile navigation
- persistent favorites
- recent files
- one-way folder synchronization from Android to RouterCloud
- unchanged-file skipping during synchronization
- Android SAF folder selection
- biometric unlock support
- RouterCloud branding

## Synchronization v1

The current synchronization model is intentionally conservative:

- direction: Android -> RouterCloud
- new files are uploaded
- changed files are uploaded
- unchanged files are skipped
- remote files are not automatically deleted
- bidirectional synchronization is not implemented yet

See `docs/SYNC-V1.md` and `docs/worklog/` for implementation notes.

## Architecture

Android device -> HTTPS -> RouterCloud -> self-hosted storage

Access is intended through trusted LAN and/or Tailscale paths.

The Android application does not introduce public WAN exposure. The RouterCloud
backend remains authoritative for authentication, authorization, path
containment, overwrite policy and destructive operations.

## Related projects

RouterCloud Android roadmap:
https://github.com/wojko6/Advanced-ASUS-Edge-Gateway-ZTNA-Infrastructure/issues/153

RouterCloud infrastructure:
https://github.com/wojko6/Advanced-ASUS-Edge-Gateway-ZTNA-Infrastructure

RouterCloud Metro UI concept:
https://github.com/wojko6/engineering-ideas/tree/main/ideas/routercloud-metro-ui

RouterCloud / Dufs backend:
https://github.com/wojko6/dufs

## Build

Run `./gradlew :app:assembleDebug`.

The current development baseline builds successfully on Fedora.

## Security

No credentials, private keys, tokens or production secrets should be committed
to this repository.

Public documentation and test data must remain sanitized.
