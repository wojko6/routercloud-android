package com.wojko6.routercloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetroTileFolderModelTest {

    @Test
    fun f21_leafPresentInTwoFolders_isInvalid() {
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
                MetroTileFolder(
                    id = "folder_b",
                    name = "Folder B",
                    childTileIds =
                        listOf(
                            "upload",
                            "storage",
                        ),
                ),
            )

        assertFalse(
            validateMetroTileFolderState(
                folders = folders,
                availableLeafTileIds =
                    setOf(
                        "upload",
                        "directory",
                        "storage",
                    ),
            ),
        )
    }

    @Test
    fun f22_folderContainingFolder_isInvalid() {
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
                MetroTileFolder(
                    id = "folder_b",
                    name = "Folder B",
                    childTileIds =
                        listOf(
                            "folder_a",
                            "storage",
                        ),
                ),
            )

        assertFalse(
            validateMetroTileFolderState(
                folders = folders,
                availableLeafTileIds =
                    setOf(
                        "upload",
                        "directory",
                        "storage",
                    ),
            ),
        )
    }

    @Test
    fun validFolderWithTwoDistinctLeafTiles_isValid() {
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
            validateMetroTileFolderState(
                folders = folders,
                availableLeafTileIds =
                    setOf(
                        "upload",
                        "directory",
                        "storage",
                    ),
            ),
        )
    }

    @Test
    fun f03_createFolderFromTwoLeaves_createsExactlyTwoChildren() {
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

        val result =
            createMetroTileFolder(
                state = state,
                sourceTileId = "upload",
                targetTileId = "directory",
                folderId = "folder_a",
                folderName = "Folder A",
                availableLeafTileIds =
                    setOf(
                        "upload",
                        "directory",
                        "storage",
                    ),
            )

        assertNotNull(result)

        result!!

        assertEquals(
            listOf(
                "upload",
                "directory",
            ),
            result.folders.single().childTileIds,
        )
    }

    @Test
    fun f04_createFolder_removesBothLeavesFromTopLevel() {
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

        val result =
            createMetroTileFolder(
                state = state,
                sourceTileId = "upload",
                targetTileId = "directory",
                folderId = "folder_a",
                folderName = "Folder A",
                availableLeafTileIds =
                    setOf(
                        "upload",
                        "directory",
                        "storage",
                    ),
            )

        assertNotNull(result)

        result!!

        assertEquals(
            listOf(
                "folder_a",
                "storage",
            ),
            result.topLevelTileIds,
        )

        assertFalse(
            "upload" in result.topLevelTileIds,
        )

        assertFalse(
            "directory" in result.topLevelTileIds,
        )
    }

    @Test
    fun f07_addLeafToExistingFolder_addsExactlyOnceAndRemovesTopLevelLeaf() {
        val state =
            MetroTileFolderDashboardState(
                folders =
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
                    ),
                topLevelTileIds =
                    listOf(
                        "folder_a",
                        "storage",
                    ),
            )

        val result =
            addLeafToMetroTileFolder(
                state = state,
                sourceTileId = "storage",
                targetFolderId = "folder_a",
                availableLeafTileIds =
                    setOf(
                        "upload",
                        "directory",
                        "storage",
                    ),
            )

        assertNotNull(result)

        result!!

        assertEquals(
            listOf(
                "upload",
                "directory",
                "storage",
            ),
            result.folders.single().childTileIds,
        )

        assertEquals(
            1,
            result.folders
                .single()
                .childTileIds
                .count {
                    it == "storage"
                },
        )

        assertEquals(
            listOf(
                "folder_a",
            ),
            result.topLevelTileIds,
        )
    }


    @Test
    fun createFolderWithSourceNotTopLevel_isRejected() {
        val state =
            MetroTileFolderDashboardState(
                folders =
                    listOf(
                        MetroTileFolder(
                            id = "folder_existing",
                            name = "Existing",
                            childTileIds =
                                listOf(
                                    "upload",
                                    "directory",
                                ),
                        ),
                    ),
                topLevelTileIds =
                    listOf(
                        "folder_existing",
                        "storage",
                    ),
            )

        val result =
            createMetroTileFolder(
                state = state,
                sourceTileId = "upload",
                targetTileId = "storage",
                folderId = "folder_new",
                folderName = "New",
                availableLeafTileIds =
                    setOf(
                        "upload",
                        "directory",
                        "storage",
                    ),
            )

        assertEquals(
            null,
            result,
        )
    }

    @Test
    fun createFolderWithDuplicateFolderId_isRejected() {
        val state =
            MetroTileFolderDashboardState(
                folders =
                    listOf(
                        MetroTileFolder(
                            id = "folder_existing",
                            name = "Existing",
                            childTileIds =
                                listOf(
                                    "upload",
                                    "directory",
                                ),
                        ),
                    ),
                topLevelTileIds =
                    listOf(
                        "folder_existing",
                        "storage",
                    ),
            )

        val result =
            createMetroTileFolder(
                state = state,
                sourceTileId = "storage",
                targetTileId = "folder_existing",
                folderId = "folder_existing",
                folderName = "Duplicate",
                availableLeafTileIds =
                    setOf(
                        "upload",
                        "directory",
                        "storage",
                    ),
            )

        assertEquals(
            null,
            result,
        )
    }

    @Test
    fun addLeafToMissingFolder_isRejected() {
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

        val result =
            addLeafToMetroTileFolder(
                state = state,
                sourceTileId = "upload",
                targetFolderId = "folder_missing",
                availableLeafTileIds =
                    setOf(
                        "upload",
                        "directory",
                        "storage",
                    ),
            )

        assertEquals(
            null,
            result,
        )
    }

    @Test
    fun addLeafAlreadyInsideFolder_isRejected() {
        val state =
            MetroTileFolderDashboardState(
                folders =
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
                    ),
                topLevelTileIds =
                    listOf(
                        "folder_a",
                        "storage",
                    ),
            )

        val result =
            addLeafToMetroTileFolder(
                state = state,
                sourceTileId = "upload",
                targetFolderId = "folder_a",
                availableLeafTileIds =
                    setOf(
                        "upload",
                        "directory",
                        "storage",
                    ),
            )

        assertEquals(
            null,
            result,
        )
    }


}
