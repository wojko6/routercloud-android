package com.wojko6.routercloud

import android.content.Context
import android.net.Uri
import android.util.Log
import com.wojko6.routercloud.network.RouterCloudClient
import com.wojko6.routercloud.network.RouterCloudDirectory
import com.wojko6.routercloud.network.RouterCloudEntry
import com.wojko6.routercloud.network.RouterCloudHttpException
import java.io.IOException
import java.util.UUID

internal data class RouterCloudSyncResult(
    val scannedFiles: Int,
    val uploadedFiles: Int,
    val skippedFiles: Int,
    val createdDirectories: Int,
)

internal class RouterCloudSyncEngine(
    private val context: Context,
    private val client: RouterCloudClient,
) {
    private val scanner =
        RouterCloudLocalTreeScanner(context)

    private val manifestStore =
        RouterCloudSyncManifestStore(context)

    private val remoteDirectoryCache =
        mutableMapOf<String, RouterCloudDirectory>()

    fun sync(
        config: RouterCloudSyncConfig,
    ): RouterCloudSyncResult =
        synchronized(SYNC_LOCK) {
            syncInternal(config)
        }

    private fun syncInternal(
        config: RouterCloudSyncConfig,
    ): RouterCloudSyncResult {
        val treeUri =
            Uri.parse(config.localTreeUri)

        val localEntries =
            scanner.scan(treeUri)

        val directories =
            localEntries
                .filter { it.isDirectory }
                .sortedBy {
                    it.relativePath.count { c ->
                        c == '/'
                    }
                }

        val files =
            localEntries
                .filterNot { it.isDirectory }

        val previousManifest =
            manifestStore.load(config)

        val manifestProgress =
            RouterCloudSyncManifestProgress(
                initial = previousManifest,
                persist = { snapshot ->
                    manifestStore.saveSnapshot(
                        config = config,
                        files = snapshot,
                    )
                },
            )

        var createdDirectories = 0
        var uploadedFiles = 0
        var skippedFiles = 0

        if (
            ensureRemoteDirectory(
                config.remotePath,
            )
        ) {
            createdDirectories++
        }

        directories.forEach { directory ->
            val remotePath =
                joinRemotePath(
                    config.remotePath,
                    directory.relativePath,
                )

            if (
                ensureRemoteDirectory(
                    remotePath,
                )
            ) {
                createdDirectories++
            }

        }

        files.forEach { file ->
            val targetPath =
                joinRemotePath(
                    config.remotePath,
                    file.relativePath,
                )

            val previous =
                previousManifest[
                    file.relativePath
                ]

            val localUnchanged =
                previous != null &&
                    previous.size == file.size &&
                    previous.modifiedAt ==
                        file.modifiedAt &&
                    (
                        file.size != null ||
                            file.modifiedAt != null
                    )

            val remoteStillPresent =
                if (localUnchanged) {
                    remoteFileMatches(
                        path = targetPath,
                        expectedSize = file.size,
                    )
                } else {
                    false
                }

            if (
                localUnchanged &&
                remoteStillPresent
            ) {
                skippedFiles++
                return@forEach
            }

            val existingRemote =
                findRemoteEntry(
                    targetPath,
                )

            if (existingRemote?.isDirectory == true) {
                throw IOException(
                    "Docelowa ścieżka RouterCloud jest katalogiem."
                )
            }

            when (
                decideRouterCloudSyncTargetAction(
                    previous = previous,
                    remoteEntryExists = existingRemote != null,
                )
            ) {
                RouterCloudSyncTargetAction.UPLOAD -> {
                    uploadNewRemoteFileSafely(
                        file = file,
                        targetPath = targetPath,
                    )
                }

                RouterCloudSyncTargetAction.CONFLICT -> {
                    throw IOException(
                        "Konflikt synchronizacji: plik istnieje " +
                            "na RouterCloud, ale nie ma go " +
                            "w historii synchronizacji. " +
                            "Podmiana została zablokowana."
                    )
                }

                RouterCloudSyncTargetAction.REPLACE -> {
                    replaceRemoteFileSafely(
                        file = file,
                        targetPath = targetPath,
                    )
                }
            }

            remoteDirectoryCache.remove(
                parentPath(targetPath),
            )

            manifestProgress.checkpoint(
                path = file.relativePath,
                fingerprint = RouterCloudSyncFingerprint(
                    size = file.size,
                    modifiedAt = file.modifiedAt,
                ),
            )

            uploadedFiles++
        }

        manifestProgress.complete(
            files.associate { file ->
                file.relativePath to RouterCloudSyncFingerprint(
                    size = file.size,
                    modifiedAt = file.modifiedAt,
                )
            },
        )

        return RouterCloudSyncResult(
            scannedFiles = files.size,
            uploadedFiles = uploadedFiles,
            skippedFiles = skippedFiles,
            createdDirectories =
                createdDirectories,
        )
    }

    private fun ensureRemoteDirectory(
        path: String,
    ): Boolean {
        if (path.isBlank()) {
            return false
        }

        return try {
            client.createDirectory(path)

            remoteDirectoryCache.remove(
                parentPath(path),
            )

            true
        } catch (
            e: RouterCloudHttpException,
        ) {
            if (e.statusCode == 405) {
                false
            } else {
                Log.w(
                    TAG,
                    "MKCOL_HTTP status=${e.statusCode}",
                )
                throw e
            }
        }
    }

    private fun findRemoteEntry(
        path: String,
    ): RouterCloudEntry? {
        val parent =
            parentPath(path)

        val name =
            path
                .trim('/')
                .substringAfterLast('/')

        val directory =
            remoteDirectoryCache
                .getOrPut(parent) {
                    client.listDirectory(parent)
                }

        return directory.entries
            .firstOrNull {
                it.name == name
            }
    }

    private fun uploadLocalFile(
        file: RouterCloudLocalEntry,
        targetPath: String,
    ) {
        val resolver =
            context.contentResolver

        client.uploadFile(
            path = targetPath,
            inputStreamProvider = {
                resolver.openInputStream(
                    file.uri,
                ) ?: throw IOException(
                    "Nie można otworzyć pliku lokalnego."
                )
            },
            contentLength = file.size,
            mediaType = file.mimeType,
        )
    }

    private fun uploadNewRemoteFileSafely(
        file: RouterCloudLocalEntry,
        targetPath: String,
    ) {
        val parent = parentPath(targetPath)
        val directory = client.listDirectory(parent)

        check(directory.allowUpload) {
            "RouterCloud nie zezwala na wysyłanie."
        }

        check(directory.allowMove) {
            "RouterCloud nie zezwala na bezpieczny MOVE."
        }

        val token = UUID.randomUUID()
            .toString()
            .replace("-", "")

        val stagedPath = joinRemotePath(
            parent,
            "routercloud-sync-new-$token.tmp",
        )

        try {
            performRouterCloudNewUpload(
                upload = {
                    uploadLocalFile(
                        file = file,
                        targetPath = stagedPath,
                    )
                },
                promote = {
                    client.rename(
                        sourcePath = stagedPath,
                        destinationPath = targetPath,
                    )
                },
                cleanup = {
                    client.delete(stagedPath)
                },
                onCleanupFailure = { cleanup ->
                    Log.w(
                        TAG,
                        "UPLOAD_STAGING_CLEANUP_FAILED",
                        cleanup,
                    )
                },
            )
        } finally {
            remoteDirectoryCache.remove(parent)
        }
    }

    private fun replaceRemoteFileSafely(
        file: RouterCloudLocalEntry,
        targetPath: String,
    ) {
        val parent =
            parentPath(targetPath)

        val directory =
            remoteDirectoryCache
                .getOrPut(parent) {
                    client.listDirectory(parent)
                }

        check(directory.allowUpload) {
            "RouterCloud nie zezwala na wysyłanie plików."
        }

        check(directory.allowMove) {
            "RouterCloud nie zezwala na bezpieczną zmianę nazwy."
        }

        check(directory.allowDelete) {
            "RouterCloud nie zezwala na bezpieczną aktualizację istniejącego pliku."
        }

        val token =
            UUID.randomUUID()
                .toString()
                .replace("-", "")
                .take(12)

        val stagedPath =
            joinRemotePath(
                parent,
                "routercloud-sync-new-$token.tmp",
            )

        val backupPath =
            joinRemotePath(
                parent,
                "routercloud-sync-old-$token.tmp",
            )

        uploadLocalFile(
            file = file,
            targetPath = stagedPath,
        )

        remoteDirectoryCache.remove(parent)

        var originalMoved = false

        try {
            client.rename(
                sourcePath = targetPath,
                destinationPath = backupPath,
            )

            originalMoved = true

            client.rename(
                sourcePath = stagedPath,
                destinationPath = targetPath,
            )
        } catch (e: Exception) {
            if (originalMoved) {
                runCatching {
                    client.rename(
                        sourcePath = backupPath,
                        destinationPath = targetPath,
                    )
                }.onFailure {
                    Log.e(
                        TAG,
                        "REPLACE_ROLLBACK_FAILED",
                    )
                }
            }

            runCatching {
                client.delete(stagedPath)
            }

            remoteDirectoryCache.remove(parent)

            throw e
        }

        runCatching {
            client.delete(backupPath)
        }.onFailure {
            Log.w(
                TAG,
                "REPLACE_BACKUP_CLEANUP_FAILED",
            )
        }

        remoteDirectoryCache.remove(parent)
    }

    private fun remoteFileMatches(
        path: String,
        expectedSize: Long?,
    ): Boolean {
        if (expectedSize == null) {
            return false
        }

        val parent =
            parentPath(path)

        val name =
            path
                .trim('/')
                .substringAfterLast('/')

        val directory =
            remoteDirectoryCache
                .getOrPut(parent) {
                    client.listDirectory(parent)
                }

        val remoteEntry =
            directory.entries
                .firstOrNull {
                    it.name == name
                }
                ?: return false

        return !remoteEntry.isDirectory &&
            remoteEntry.size == expectedSize
    }

    private fun joinRemotePath(
        first: String,
        second: String,
    ): String =
        listOf(
            first.trim('/'),
            second.trim('/'),
        )
            .filter {
                it.isNotEmpty()
            }
            .joinToString("/")

    private companion object {
        const val TAG =
            "RouterCloudSyncEngine"

        val SYNC_LOCK =
            Any()
    }

    private fun parentPath(
        path: String,
    ): String =
        path
            .trim('/')
            .substringBeforeLast(
                '/',
                "",
            )
}
