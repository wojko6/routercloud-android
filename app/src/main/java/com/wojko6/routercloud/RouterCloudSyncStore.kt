package com.wojko6.routercloud

import android.content.Context
import android.net.Uri

internal data class RouterCloudSyncConfig(
    val localTreeUri: String,
    val remotePath: String,
    val lastSuccessfulSyncAt: Long,
)

internal class RouterCloudSyncStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    fun load(): RouterCloudSyncConfig? {
        val localTreeUri =
            preferences.getString(
                KEY_LOCAL_TREE_URI,
                null,
            ) ?: return null

        return RouterCloudSyncConfig(
            localTreeUri = localTreeUri,
            remotePath =
                preferences.getString(
                    KEY_REMOTE_PATH,
                    DEFAULT_REMOTE_PATH,
                ) ?: DEFAULT_REMOTE_PATH,
            lastSuccessfulSyncAt =
                preferences.getLong(
                    KEY_LAST_SUCCESS,
                    0L,
                ),
        )
    }

    fun save(
        localTreeUri: Uri,
        remotePath: String = DEFAULT_REMOTE_PATH,
    ) {
        preferences
            .edit()
            .putString(
                KEY_LOCAL_TREE_URI,
                localTreeUri.toString(),
            )
            .putString(
                KEY_REMOTE_PATH,
                remotePath
                    .trim()
                    .trim('/'),
            )
            .apply()
    }

    fun isBackgroundEnabled(): Boolean =
        preferences.getBoolean(
            KEY_BACKGROUND_ENABLED,
            false,
        )

    fun setBackgroundEnabled(
        enabled: Boolean,
    ) {
        preferences
            .edit()
            .putBoolean(
                KEY_BACKGROUND_ENABLED,
                enabled,
            )
            .apply()
    }

    fun markSuccessfulSync(
        timestamp: Long = System.currentTimeMillis(),
    ) {
        preferences
            .edit()
            .putLong(
                KEY_LAST_SUCCESS,
                timestamp,
            )
            .apply()
    }

    fun clear() {
        preferences
            .edit()
            .clear()
            .apply()
    }

    internal companion object {
        const val DEFAULT_REMOTE_PATH =
            "MobileSync"

        private const val PREFS_NAME =
            "routercloud-sync-v1"

        private const val KEY_LOCAL_TREE_URI =
            "local_tree_uri"

        private const val KEY_REMOTE_PATH =
            "remote_path"

        private const val KEY_LAST_SUCCESS =
            "last_success"

        private const val KEY_BACKGROUND_ENABLED =
            "background_enabled"
    }
}
