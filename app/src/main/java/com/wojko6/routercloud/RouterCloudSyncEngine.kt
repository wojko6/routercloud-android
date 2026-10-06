package com.wojko6.routercloud

import android.content.Context
import android.net.Uri
import com.wojko6.routercloud.network.RouterCloudClient
import com.wojko6.routercloud.network.RouterCloudDirectory
import com.wojko6.routercloud.network.RouterCloudHttpException
import java.io.IOException

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

            val resolver =
                context.contentResolver

            client.uploadFile(
                path = targetPath,
                inputStreamProvider = {
                    resolver.openInputStream(
                        file.uri,
                    ) ?: throw IOException(
                        "Nie można otworzyć: ${file.relativePath}"
                    )
                },
                contentLength = file.size,
                mediaType = file.mimeType,
            )

            remoteDirectoryCache.remove(
                parentPath(targetPath),
            )

            uploadedFiles++
        }

        manifestStore.save(
            config = config,
            files = files,
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
                throw e
            }
        }
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
