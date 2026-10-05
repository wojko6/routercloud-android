package com.wojko6.routercloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetroTileFolderPersistenceTest {

    private val availableLeafTileIds =
        setOf(
            "upload",
            "directory",
            "storage",
        )

    @Test
    fun folderState_roundTripsThroughJson() {
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

        val encoded =
            encodeMetroTileFolders(
                folders,
            )

        val decoded =
            decodeMetroTileFolders(
                encoded = encoded,
                availableLeafTileIds =
                    availableLeafTileIds,
            )

        assertEquals(
            folders,
            decoded,
        )
    }

    @Test
    fun duplicateLeafAcrossFolders_isRejectedDuringDecode() {
        val encoded =
            """
            {
              "version": 1,
              "folders": [
                {
                  "id": "folder_a",
                  "name": "Folder A",
                  "children": ["upload", "directory"]
                },
                {
                  "id": "folder_b",
                  "name": "Folder B",
                  "children": ["upload", "storage"]
                }
              ]
            }
            """.trimIndent()

        val decoded =
            decodeMetroTileFolders(
                encoded = encoded,
                availableLeafTileIds =
                    availableLeafTileIds,
            )

        assertNull(decoded)
    }

    @Test
    fun nestedFolderReference_isRejectedDuringDecode() {
        val encoded =
            """
            {
              "version": 1,
              "folders": [
                {
                  "id": "folder_a",
                  "name": "Folder A",
                  "children": ["upload", "directory"]
                },
                {
                  "id": "folder_b",
                  "name": "Folder B",
                  "children": ["folder_a", "storage"]
                }
              ]
            }
            """.trimIndent()

        val decoded =
            decodeMetroTileFolders(
                encoded = encoded,
                availableLeafTileIds =
                    availableLeafTileIds,
            )

        assertNull(decoded)
    }

    @Test
    fun malformedJson_isRejectedDuringDecode() {
        val decoded =
            decodeMetroTileFolders(
                encoded = """{"version":1,"folders":[""",
                availableLeafTileIds =
                    availableLeafTileIds,
            )

        assertNull(decoded)
    }

    @Test
    fun unsupportedVersion_isRejectedDuringDecode() {
        val encoded =
            """
            {
              "version": 99,
              "folders": []
            }
            """.trimIndent()

        val decoded =
            decodeMetroTileFolders(
                encoded = encoded,
                availableLeafTileIds =
                    availableLeafTileIds,
            )

        assertNull(decoded)
    }

    @Test
    fun emptyFolderState_roundTrips() {
        val encoded =
            encodeMetroTileFolders(
                emptyList(),
            )

        val decoded =
            decodeMetroTileFolders(
                encoded = encoded,
                availableLeafTileIds =
                    availableLeafTileIds,
            )

        assertEquals(
            emptyList<MetroTileFolder>(),
            decoded,
        )
    }

    @Test
    fun missingRequiredField_isRejectedDuringDecode() {
        val encoded =
            """
            {
              "version": 1,
              "folders": [
                {
                  "id": "folder_a",
                  "children": ["upload", "directory"]
                }
              ]
            }
            """.trimIndent()

        val decoded =
            decodeMetroTileFolders(
                encoded = encoded,
                availableLeafTileIds =
                    availableLeafTileIds,
            )

        assertNull(decoded)
    }

    @Test
    fun duplicateChildInsideFolder_isRejectedDuringDecode() {
        val encoded =
            """
            {
              "version": 1,
              "folders": [
                {
                  "id": "folder_a",
                  "name": "Folder A",
                  "children": ["upload", "upload"]
                }
              ]
            }
            """.trimIndent()

        val decoded =
            decodeMetroTileFolders(
                encoded = encoded,
                availableLeafTileIds =
                    availableLeafTileIds,
            )

        assertNull(decoded)
    }

    @Test
    fun oneChildFolder_isRejectedDuringDecode() {
        val encoded =
            """
            {
              "version": 1,
              "folders": [
                {
                  "id": "folder_a",
                  "name": "Folder A",
                  "children": ["upload"]
                }
              ]
            }
            """.trimIndent()

        val decoded =
            decodeMetroTileFolders(
                encoded = encoded,
                availableLeafTileIds =
                    availableLeafTileIds,
            )

        assertNull(decoded)
    }

    @Test
    fun unknownChild_isRejectedDuringDecode() {
        val encoded =
            """
            {
              "version": 1,
              "folders": [
                {
                  "id": "folder_a",
                  "name": "Folder A",
                  "children": ["upload", "unknown_tile"]
                }
              ]
            }
            """.trimIndent()

        val decoded =
            decodeMetroTileFolders(
                encoded = encoded,
                availableLeafTileIds =
                    availableLeafTileIds,
            )

        assertNull(decoded)
    }


}
