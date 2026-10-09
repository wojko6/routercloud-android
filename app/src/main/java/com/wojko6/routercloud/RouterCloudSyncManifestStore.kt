package com.wojko6.routercloud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal data class RouterCloudSyncFingerprint(
    val size: Long?,
    val modifiedAt: Long?,
)

internal class RouterCloudSyncManifestStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    fun load(
        config: RouterCloudSyncConfig,
    ): Map<String, RouterCloudSyncFingerprint> {
        if (
            preferences.getString(KEY_TREE_URI, null) !=
                config.localTreeUri ||
            preferences.getString(KEY_REMOTE_PATH, null) !=
                config.remotePath
        ) {
            return emptyMap()
        }

        val encoded =
            preferences.getString(
                KEY_FILES,
                null,
            ) ?: return emptyMap()

        return runCatching {
            val array = JSONArray(encoded)

            buildMap {
                for (index in 0 until array.length()) {
                    val item =
                        array.getJSONObject(index)

                    val size =
                        if (item.isNull("size")) {
                            null
                        } else {
                            item.getLong("size")
                        }

                    val modifiedAt =
                        if (item.isNull("modifiedAt")) {
                            null
                        } else {
                            item.getLong("modifiedAt")
                        }

                    put(
                        item.getString("path"),
                        RouterCloudSyncFingerprint(
                            size = size,
                            modifiedAt = modifiedAt,
                        ),
                    )
                }
            }
        }.getOrElse {
            emptyMap()
        }
    }

    fun saveSnapshot(
        config: RouterCloudSyncConfig,
        files: Map<String, RouterCloudSyncFingerprint>,
    ) {
        val array = JSONArray()

        files.toSortedMap().forEach { (path, fingerprint) ->
            array.put(
                JSONObject()
                    .put("path", path)
                    .put(
                        "size",
                        fingerprint.size ?: JSONObject.NULL,
                    )
                    .put(
                        "modifiedAt",
                        fingerprint.modifiedAt ?: JSONObject.NULL,
                    )
            )
        }

        val committed = preferences
            .edit()
            .putString(KEY_TREE_URI, config.localTreeUri)
            .putString(KEY_REMOTE_PATH, config.remotePath)
            .putString(KEY_FILES, array.toString())
            .commit()

        if (!committed) {
            throw IOException(
                "Nie udalo sie trwale zapisac postepu synchronizacji."
            )
        }
    }

    fun save(
        config: RouterCloudSyncConfig,
        files: Collection<RouterCloudLocalEntry>,
    ) {
        saveSnapshot(
            config = config,
            files = files
                .filterNot { it.isDirectory }
                .associate { file ->
                    file.relativePath to RouterCloudSyncFingerprint(
                        size = file.size,
                        modifiedAt = file.modifiedAt,
                    )
                },
        )
    }

    private companion object {
        const val PREFS_NAME =
            "routercloud-sync-manifest-v1"

        const val KEY_TREE_URI =
            "tree_uri"

        const val KEY_REMOTE_PATH =
            "remote_path"

        const val KEY_FILES =
            "files"
    }
}
