package com.wojko6.routercloud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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

    fun save(
        config: RouterCloudSyncConfig,
        files: Collection<RouterCloudLocalEntry>,
    ) {
        val array = JSONArray()

        files
            .filterNot { it.isDirectory }
            .sortedBy { it.relativePath.lowercase() }
            .forEach { file ->
                array.put(
                    JSONObject()
                        .put(
                            "path",
                            file.relativePath,
                        )
                        .put(
                            "size",
                            file.size
                                ?: JSONObject.NULL,
                        )
                        .put(
                            "modifiedAt",
                            file.modifiedAt
                                ?: JSONObject.NULL,
                        ),
                )
            }

        preferences
            .edit()
            .putString(
                KEY_TREE_URI,
                config.localTreeUri,
            )
            .putString(
                KEY_REMOTE_PATH,
                config.remotePath,
            )
            .putString(
                KEY_FILES,
                array.toString(),
            )
            .apply()
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
