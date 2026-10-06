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
- last successful synchronization timestamp.

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

### RouterCloudSyncEngine

The engine:
1. scans the local tree;
2. creates the remote root if required;
3. creates missing remote directories;
4. compares files against the previous local manifest;
5. verifies that an unchanged remote file still exists and has the expected size;
6. uploads new or changed files;
7. skips unchanged files;
8. saves the manifest only after the synchronization pass completes.

## Deliberate safety properties

Sync v1 does not:
- delete local files,
- delete remote files,
- download remote files,
- resolve concurrent modifications,
- perform automatic conflict resolution,
- run periodically in the background.

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

Observed sequence:
- new file -> uploaded 1, skipped 0
- no local change -> uploaded 0, skipped 1
- modified file -> uploaded 1, skipped 0

The SAF scanner also correctly reported an empty directory as zero files.

## Next steps

Recommended order:
1. dedicated synchronization screen;
2. display last successful sync;
3. richer run summary and error reporting;
4. configurable remote destination;
5. automated tests for manifest/change decisions;
6. WorkManager-based background execution;
7. stronger content/version identity;
8. conflict model;
9. optional bidirectional synchronization.
