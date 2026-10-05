package com.wojko6.routercloud

import org.junit.Assert.assertEquals
import org.junit.Test


class MetroTileFolderDuplicateMembershipFallbackTest {

    private class FakeStore(
        private val storedValue: String,
    ) : MetroTileFolderPersistenceStore {

        override fun read(
            key: String,
        ): String? =
            if (
                key == METRO_TILE_FOLDERS_PREF_KEY
            ) {
                storedValue
            } else {
                null
            }

        override fun write(
            key: String,
            value: String,
        ) {
            error(
                "Fallback load must not write"
            )
        }
    }


    @Test
    fun duplicateMembershipFallsBackToEmptyFolderState() {
        /*
         * "upload" is illegally assigned to both folders.
         *
         * Decoder must reject the complete persisted folder
         * state and the store loader must expose a safe empty
         * folder list instead of a partially valid hierarchy.
         */
        val encoded =
            """
            {
              "version": 1,
              "folders": [
                {
                  "id": "folder_a",
                  "name": "Folder A",
                  "children": [
                    "upload",
                    "directory"
                  ]
                },
                {
                  "id": "folder_b",
                  "name": "Folder B",
                  "children": [
                    "upload",
                    "storage"
                  ]
                }
              ]
            }
            """.trimIndent()

        val loaded =
            loadPersistedMetroTileFolders(
                store = FakeStore(encoded),
                availableLeafTileIds =
                    setOf(
                        "upload",
                        "directory",
                        "storage",
                    ),
            )

        assertEquals(
            emptyList<MetroTileFolder>(),
            loaded,
        )
    }
}
