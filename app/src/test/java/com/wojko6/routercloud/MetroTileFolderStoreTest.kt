package com.wojko6.routercloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetroTileFolderStoreTest {

    private val availableLeafTileIds =
        setOf(
            "upload",
            "directory",
            "storage",
        )

    private class FakeStore :
        MetroTileFolderPersistenceStore {

        val values =
            mutableMapOf<String, String>()

        override fun read(
            key: String,
        ): String? =
            values[key]

        override fun write(
            key: String,
            value: String,
        ) {
            values[key] = value
        }
    }

    @Test
    fun missingStoredState_loadsEmptyFolderList() {
        val store =
            FakeStore()

        val loaded =
            loadPersistedMetroTileFolders(
                store = store,
                availableLeafTileIds =
                    availableLeafTileIds,
            )

        assertEquals(
            emptyList<MetroTileFolder>(),
            loaded,
        )
    }

    @Test
    fun validFolderState_survivesSaveAndReload() {
        val store =
            FakeStore()

        val folders =
            listOf(
                MetroTileFolder(
                    id = "folder_a",
                    name = "Folder A",
                    childTileIds =
                        listOf(
                            "upload",
                            "directory",
                        ),
                ),
            )

        assertTrue(
            savePersistedMetroTileFolders(
                store = store,
                folders = folders,
                availableLeafTileIds =
                    availableLeafTileIds,
            ),
        )

        val loaded =
            loadPersistedMetroTileFolders(
                store = store,
                availableLeafTileIds =
                    availableLeafTileIds,
            )

        assertEquals(
            folders,
            loaded,
        )

        assertTrue(
            METRO_TILE_FOLDERS_PREF_KEY in
                store.values,
        )
    }

    @Test
    fun malformedStoredState_fallsBackToEmptyFolderList() {
        val store =
            FakeStore()

        store.values[
            METRO_TILE_FOLDERS_PREF_KEY
        ] = """{"version":1,"folders":["""

        val loaded =
            loadPersistedMetroTileFolders(
                store = store,
                availableLeafTileIds =
                    availableLeafTileIds,
            )

        assertEquals(
            emptyList<MetroTileFolder>(),
            loaded,
        )
    }

    @Test
    fun invalidState_isRejectedWithoutOverwritingPreviousValue() {
        val store =
            FakeStore()

        val validFolders =
            listOf(
                MetroTileFolder(
                    id = "folder_a",
                    name = "Folder A",
                    childTileIds =
                        listOf(
                            "upload",
                            "directory",
                        ),
                ),
            )

        assertTrue(
            savePersistedMetroTileFolders(
                store = store,
                folders = validFolders,
                availableLeafTileIds =
                    availableLeafTileIds,
            ),
        )

        val previousValue =
            store.values[
                METRO_TILE_FOLDERS_PREF_KEY
            ]

        val invalidFolders =
            listOf(
                MetroTileFolder(
                    id = "folder_bad",
                    name = "Bad",
                    childTileIds =
                        listOf(
                            "storage",
                        ),
                ),
            )

        assertFalse(
            savePersistedMetroTileFolders(
                store = store,
                folders = invalidFolders,
                availableLeafTileIds =
                    availableLeafTileIds,
            ),
        )

        assertEquals(
            previousValue,
            store.values[
                METRO_TILE_FOLDERS_PREF_KEY
            ],
        )
    }
}
