package com.wojko6.routercloud

import android.content.SharedPreferences


internal const val METRO_TILE_FOLDERS_PREF_KEY =
    "tile_folders_v1"


internal interface MetroTileFolderPersistenceStore {

    fun read(
        key: String,
    ): String?

    fun write(
        key: String,
        value: String,
    )
}


internal class SharedPreferencesMetroTileFolderPersistenceStore(
    private val preferences: SharedPreferences,
) : MetroTileFolderPersistenceStore {

    override fun read(
        key: String,
    ): String? =
        preferences.getString(
            key,
            null,
        )

    override fun write(
        key: String,
        value: String,
    ) {
        preferences
            .edit()
            .putString(
                key,
                value,
            )
            .apply()
    }
}


internal fun loadPersistedMetroTileFolders(
    store: MetroTileFolderPersistenceStore,
    availableLeafTileIds: Set<String>,
): List<MetroTileFolder> {
    val encoded =
        store.read(
            METRO_TILE_FOLDERS_PREF_KEY,
        )
            ?: return emptyList()

    return decodeMetroTileFolders(
        encoded = encoded,
        availableLeafTileIds =
            availableLeafTileIds,
    )
        ?: emptyList()
}


internal fun savePersistedMetroTileFolders(
    store: MetroTileFolderPersistenceStore,
    folders: Collection<MetroTileFolder>,
    availableLeafTileIds: Set<String>,
): Boolean {
    if (
        !validateMetroTileFolderState(
            folders = folders,
            availableLeafTileIds =
                availableLeafTileIds,
        )
    ) {
        return false
    }

    val encoded =
        runCatching {
            encodeMetroTileFolders(
                folders,
            )
        }.getOrNull()
            ?: return false

    store.write(
        key = METRO_TILE_FOLDERS_PREF_KEY,
        value = encoded,
    )

    return true
}
