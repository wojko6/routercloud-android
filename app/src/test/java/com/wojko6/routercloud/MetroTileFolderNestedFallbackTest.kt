package com.wojko6.routercloud

import org.junit.Assert.assertEquals
import org.junit.Test


class MetroTileFolderNestedFallbackTest {

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
    fun nestedFolderStateFallsBackToEmptyFolderState() {
        /*
         * folder_b illegally references folder_a as a child.
         *
         * Nested folders are forbidden in Metro Folders v1.
         * The complete persisted folder state must therefore
         * be rejected before it can reach rendering.
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
                    "folder_a",
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
