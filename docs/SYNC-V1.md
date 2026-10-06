# RouterCloud Android — Sync v1

## Status

Implemented and manually validated.

Sync v1 provides a conservative one-way synchronization workflow:
Android device → RouterCloud

The feature intentionally does not implement bidirectional synchronization
or deletion propagation.

## Local source

The user selects a directory using Android Storage Access Framework
(OpenDocumentTree).

RouterCloud stores a persisted URI permission for the selected tree.

This avoids requesting broad filesystem access.

## Remote destination

Default destination:
- /MobileSync

## Components

### RouterCloudSyncStore

Stores:
- local SAF tree URI,
- remote destination path,
- last successful synchronization timestamp,
- whether background synchronization is enabled.

### RouterCloudLocalTreeScanner

Recursively enumerates the selected SAF document tree.

For each entry it obtains:
- relative path,
- document URI,
- MIME type,
- size when available,
- modification timestamp when available,
- directory/file type.

### RouterCloudSyncManifestStore

Stores the local state observed during the last successful synchronization.

Sync v1 fingerprints files using:
- file size,
- modification timestamp.

### Background execution

Background synchronization is explicit opt-in.

`RouterCloudBackgroundSyncController` schedules:
- unique periodic WorkManager execution approximately every 15 minutes;
- a unique one-time "run now" worker when requested by the user.

Both use a connected-network constraint.

`RouterCloudSyncWorker`:
- refuses to synchronize when background sync has been disabled;
- restores the separately stored RouterCloud background session;
- runs the same synchronization engine used by foreground sync;
- marks the last successful synchronization only after a completed pass;
- retries transient I/O failures and HTTP 408, 429 and 5xx responses;
- fails closed for permanent authorization, SAF and configuration failures.

WorkManager periodic timing is intentionally treated as approximate. Android may
delay execution according to system scheduling and power-management policy.

### Background session

The unattended worker does not store the RouterCloud username or password.

It stores only the session cookies required to resume the authenticated
RouterCloud session. The serialized session is:
- encrypted with AES/GCM;
- protected by a key generated in Android Keystore;
- stored in the application's `noBackupFilesDir`;
- separate from the biometric unlock session.

The background key deliberately does not require biometric authentication,
because an unattended WorkManager execution could not otherwise decrypt it.

This is an explicit security trade-off controlled by the background-sync opt-in.

Disabling background synchronization or logging out clears the background
session and cancels scheduled work. Locking the foreground UI does not disable
background synchronization.

### Notifications

The background worker remains silent when nothing changed.

A successful run generates a notification only when at least one file was
uploaded. Permanent failures requiring user action may also notify the user.

On Android 13 and newer, notifications are shown only when
`POST_NOTIFICATIONS` permission has been granted. Denying notification
permission does not disable synchronization.

### RouterCloudSyncEngine

The engine:
1. serializes synchronization passes inside the application process;
2. scans the local tree;
3. creates the remote root if required;
4. creates missing remote directories;
5. compares files against the previous local manifest;
6. verifies that an unchanged remote file still exists and has the expected size;
7. uploads new files;
8. safely replaces changed existing files;
9. skips unchanged files;
10. saves the manifest only after the synchronization pass completes.

For an existing changed file, direct overwrite is intentionally avoided because
the hardened RouterCloud backend does not permit ordinary PUT overwrite under
the current delete policy.

The replacement workflow:
1. uploads the new contents to a unique temporary file in the same directory;
2. moves the current target to a unique temporary backup name;
3. moves the uploaded candidate to the original target name;
4. attempts to restore the backup if promotion fails;
5. removes temporary artifacts when possible.

The replacement path requires the backend to report upload, safe-move and
RouterCloud delete permission.

## Deliberate safety properties

Sync v1 does not:
- delete local files,
- propagate local deletions to RouterCloud,
- download remote files as part of synchronization,
- resolve concurrent modifications,
- perform automatic conflict resolution,
- provide bidirectional synchronization.

Internal temporary files used by safe replacement may be removed through the
backend's RouterCloud-specific delete authorization. This is implementation
cleanup, not deletion propagation.

## Why sync is not bidirectional yet

The current RouterCloud directory model provides useful metadata such as
file size and modification time, but the Android implementation does not
yet have a sufficiently strong remote version identity such as:
- checksum,
- ETag,
- immutable version ID.

Using only timestamps for destructive bidirectional synchronization would
create unnecessary conflict and data-loss risk.

## Manual validation

Foreground sync validation:
- new file -> uploaded 1, skipped 0
- no local change -> uploaded 0, skipped 1
- modified file -> uploaded 1, skipped 0

Background validation:
- periodic WorkManager job registered with a network constraint;
- manual one-time background worker started successfully;
- encrypted background session restored successfully;
- changed existing remote file completed the safe-replacement path;
- background pass observed `uploaded=1 skipped=1 scanned=2`;
- following unchanged pass observed `uploaded=0 skipped=2 scanned=2`;
- the upload pass produced one RouterCloud success notification;
- the unchanged pass did not produce another success notification.

The last-success timestamp and synchronization manifest were updated after a
successful background pass.

The SAF scanner also correctly reported an empty directory as zero files.

## Next steps

Recommended order:
1. richer synchronization status/history in the UI;
2. automated tests for manifest/change and safe-replacement decisions;
3. configurable remote destination;
4. long-running/foreground execution strategy for very large uploads;
5. stronger content/version identity such as checksum or ETag;
6. explicit conflict model;
7. optional bidirectional synchronization.
