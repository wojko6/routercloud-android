package com.wojko6.routercloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test


class MetroTileFolderDissolveTest {

    private val availableLeafTileIds =
        setOf(
            "upload",
            "directory",
            "storage",
        )


    @Test
    fun dissolveRestoresChildrenAtFolderPosition() {
        val folder =
            MetroTileFolder(
                id = "folder:test",
                name = "Folder",
                childTileIds =
                    listOf(
                        "upload",
                        "directory",
                    ),
            )

        val state =
            MetroTileFolderDashboardState(
                folders = listOf(folder),
                topLevelTileIds =
                    listOf(
                        "storage",
                        "folder:test",
                    ),
            )

        val result =
            dissolveMetroTileFolder(
                state = state,
                folderId = "folder:test",
                availableLeafTileIds =
                    availableLeafTileIds,
            )
                ?: error(
                    "Expected valid dissolve result"
                )

        assertTrue(result.folders.isEmpty())

        assertEquals(
            listOf(
                "storage",
                "upload",
                "directory",
            ),
            result.topLevelTileIds,
        )

        assertTrue(
            validateMetroTileFolderDashboardState(
                state = result,
                availableLeafTileIds =
                    availableLeafTileIds,
            ),
        )
    }


    @Test
    fun dissolvePreservesOtherFolders() {
        val firstFolder =
            MetroTileFolder(
                id = "folder:first",
                name = "Pierwszy",
                childTileIds =
                    listOf(
                        "upload",
                        "directory",
                    ),
            )

        /*
         * Use a separate leaf registry here so the second
         * folder can remain valid after dissolving the first.
         */
        val leafIds =
            setOf(
                "upload",
                "directory",
                "storage",
                "extra1",
                "extra2",
            )

        val secondFolder =
            MetroTileFolder(
                id = "folder:second",
                name = "Drugi",
                childTileIds =
                    listOf(
                        "extra1",
                        "extra2",
                    ),
            )

        val state =
            MetroTileFolderDashboardState(
                folders =
                    listOf(
                        firstFolder,
                        secondFolder,
                    ),
                topLevelTileIds =
                    listOf(
                        "storage",
                        "folder:first",
                        "folder:second",
                    ),
            )

        val result =
            dissolveMetroTileFolder(
                state = state,
                folderId = "folder:first",
                availableLeafTileIds = leafIds,
            )
                ?: error(
                    "Expected valid dissolve result"
                )

        assertEquals(
            listOf(secondFolder),
            result.folders,
        )

        assertEquals(
            listOf(
                "storage",
                "upload",
                "directory",
                "folder:second",
            ),
            result.topLevelTileIds,
        )
    }


    @Test
    fun dissolveUnknownFolderIsRejected() {
        val state =
            MetroTileFolderDashboardState(
                folders = emptyList(),
                topLevelTileIds =
                    listOf(
                        "upload",
                        "directory",
                        "storage",
                    ),
            )

        assertNull(
            dissolveMetroTileFolder(
                state = state,
                folderId = "folder:missing",
                availableLeafTileIds =
                    availableLeafTileIds,
            ),
        )
    }


    @Test
    fun dissolveInvalidInputStateIsRejected() {
        val folder =
            MetroTileFolder(
                id = "folder:test",
                name = "Folder",
                childTileIds =
                    listOf(
                        "upload",
                        "directory",
                    ),
            )

        /*
         * Invalid on purpose:
         * folder exists but is missing from top-level state.
         */
        val invalidState =
            MetroTileFolderDashboardState(
                folders = listOf(folder),
                topLevelTileIds =
                    listOf("storage"),
            )

        assertNull(
            dissolveMetroTileFolder(
                state = invalidState,
                folderId = "folder:test",
                availableLeafTileIds =
                    availableLeafTileIds,
            ),
        )
    }
}
