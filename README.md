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
- safe replacement of changed remote files
- optional WorkManager-based background synchronization
- manual "run now" background synchronization
- Android SAF folder selection
- encrypted background session storage with Android Keystore
- synchronization notifications for uploads and action-required failures
- biometric unlock support
- RouterCloud branding

## Synchronization v1

The current synchronization model is intentionally conservative:

- direction: Android -> RouterCloud
- new files are uploaded
- changed files are safely replaced
- unchanged files are skipped
- local deletions are not propagated to RouterCloud
- bidirectional synchronization is not implemented
- background synchronization is explicit opt-in
- periodic background work uses WorkManager with a network constraint
- the current periodic interval is approximately 15 minutes and is not exact
- a manual background "run now" action is also available

Background synchronization stores only the RouterCloud session required by the
worker. The session is encrypted with AES-GCM using a key held by Android
Keystore and is kept in the application's no-backup storage. RouterCloud login
credentials are not stored for the worker.

The normal biometric session remains separate. Locking the application does not
disable an already enabled background synchronization workflow. Explicit logout
or disabling background synchronization clears the background session and
cancels scheduled work.

Changed existing files use a fail-safe replacement workflow: upload a temporary
candidate, move the old file to a temporary backup, promote the candidate, and
attempt rollback if promotion fails. This operation is used only when the
backend grants upload, safe-move and RouterCloud delete permissions.

Successful background runs notify only when at least one file was actually
uploaded. Runs where all files are unchanged remain silent. Permanent failures
that need user action may generate a notification when Android notification
permission is granted.

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
