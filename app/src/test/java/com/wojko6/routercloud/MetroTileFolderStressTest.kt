package com.wojko6.routercloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test


class MetroTileFolderStressTest {

    private val leafIds =
        setOf(
            "upload",
            "directory",
            "storage",
        )

    private val initialState =
        MetroTileFolderDashboardState(
            folders = emptyList(),
            topLevelTileIds =
                listOf(
                    "upload",
                    "directory",
                    "storage",
                ),
        )


    @Test
    fun repeatedCreateAddPersistPackAndDissolvePreservesInvariants() {
        var state = initialState

        repeat(500) { iteration ->
            val folderId =
                "folder:stress:$iteration"

            state =
                createMetroTileFolder(
                    state = state,
                    sourceTileId = "upload",
                    targetTileId = "directory",
                    folderId = folderId,
                    folderName = "Stress $iteration",
                    availableLeafTileIds = leafIds,
                )
                    ?: error(
                        "Create failed at iteration $iteration"
                    )

            assertTrue(
                validateMetroTileFolderDashboardState(
                    state = state,
                    availableLeafTileIds = leafIds,
                ),
            )

            state =
                addLeafToMetroTileFolder(
                    state = state,
                    sourceTileId = "storage",
                    targetFolderId = folderId,
                    availableLeafTileIds = leafIds,
                )
                    ?: error(
                        "Add failed at iteration $iteration"
                    )

            assertTrue(
                validateMetroTileFolderDashboardState(
                    state = state,
                    availableLeafTileIds = leafIds,
                ),
            )

            val encoded =
                encodeMetroTileFolders(
                    state.folders,
                )

            val decodedFolders =
                decodeMetroTileFolders(
                    encoded = encoded,
                    availableLeafTileIds = leafIds,
                )
                    ?: error(
                        "Persistence decode failed at iteration $iteration"
                    )

            assertEquals(
                state.folders,
                decodedFolders,
            )

            val restoredState =
                MetroTileFolderDashboardState(
                    folders = decodedFolders,
                    topLevelTileIds =
                        state.topLevelTileIds,
                )

            assertTrue(
                validateMetroTileFolderDashboardState(
                    state = restoredState,
                    availableLeafTileIds = leafIds,
                ),
            )

            for (gridUnits in listOf(6, 8)) {
                val folderSizes =
                    mapOf(
                        folderId to
                            MetroTileSize.Medium,
                    )

                val folderPositions =
                    buildBoundedMetroTilePositions(
                        order =
                            restoredState.topLevelTileIds,
                        sizes = folderSizes,
                        gridUnits = gridUnits,
                        workspaceRows = 4,
                    )

                assertNotNull(
                    "Folder layout failed at iteration $iteration on grid $gridUnits",
                    folderPositions,
                )

                assertTrue(
                    validateMetroTileLayout(
                        positions =
                            requireNotNull(
                                folderPositions
                            ),
                        sizes = folderSizes,
                        tileIds =
                            restoredState.topLevelTileIds,
                        gridUnits = gridUnits,
                        workspaceRows = 4,
                    ),
                )
            }

            state =
                dissolveMetroTileFolder(
                    state = restoredState,
                    folderId = folderId,
                    availableLeafTileIds = leafIds,
                )
                    ?: error(
                        "Dissolve failed at iteration $iteration"
                    )

            assertTrue(
                validateMetroTileFolderDashboardState(
                    state = state,
                    availableLeafTileIds = leafIds,
                ),
            )

            val leafSizes =
                mapOf(
                    "upload" to MetroTileSize.Small,
                    "directory" to MetroTileSize.Medium,
                    "storage" to MetroTileSize.Small,
                )

            for (gridUnits in listOf(6, 8)) {
                val leafPositions =
                    buildBoundedMetroTilePositions(
                        order = state.topLevelTileIds,
                        sizes = leafSizes,
                        gridUnits = gridUnits,
                        workspaceRows = 4,
                    )

                assertNotNull(
                    "Leaf layout failed at iteration $iteration on grid $gridUnits",
                    leafPositions,
                )

                assertTrue(
                    validateMetroTileLayout(
                        positions =
                            requireNotNull(
                                leafPositions
                            ),
                        sizes = leafSizes,
                        tileIds =
                            state.topLevelTileIds,
                        gridUnits = gridUnits,
                        workspaceRows = 4,
                    ),
                )
            }

            assertEquals(
                initialState,
                state,
            )
        }
    }
}
