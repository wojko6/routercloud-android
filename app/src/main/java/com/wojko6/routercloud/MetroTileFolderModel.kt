package com.wojko6.routercloud

internal data class MetroTileFolder(
    val id: String,
    val name: String,
    val childTileIds: List<String>,
)

internal fun validateMetroTileFolderState(
    folders: Collection<MetroTileFolder>,
    availableLeafTileIds: Set<String>,
): Boolean {
    val folderIds =
        folders.map {
            it.id
        }

    if (
        folderIds.size !=
        folderIds.toSet().size
    ) {
        return false
    }

    if (
        folderIds.any {
            it.isBlank()
        }
    ) {
        return false
    }

    if (
        folderIds.any {
            it in availableLeafTileIds
        }
    ) {
        return false
    }

    val assignedLeafTileIds =
        mutableSetOf<String>()

    for (folder in folders) {
        if (folder.childTileIds.size < 2) {
            return false
        }

        if (
            folder.childTileIds.size !=
            folder.childTileIds.toSet().size
        ) {
            return false
        }

        for (childTileId in folder.childTileIds) {
            if (childTileId in folderIds) {
                return false
            }

            if (
                childTileId !in
                availableLeafTileIds
            ) {
                return false
            }

            if (
                !assignedLeafTileIds.add(
                    childTileId,
                )
            ) {
                return false
            }
        }
    }

    return true
}


internal data class MetroTileFolderDashboardState(
    val folders: List<MetroTileFolder>,
    val topLevelTileIds: List<String>,
)


internal fun validateMetroTileFolderDashboardState(
    state: MetroTileFolderDashboardState,
    availableLeafTileIds: Set<String>,
): Boolean {
    if (
        !validateMetroTileFolderState(
            folders = state.folders,
            availableLeafTileIds = availableLeafTileIds,
        )
    ) {
        return false
    }

    if (
        state.topLevelTileIds.size !=
        state.topLevelTileIds.toSet().size
    ) {
        return false
    }

    val folderIds =
        state.folders
            .map {
                it.id
            }
            .toSet()

    val assignedLeafTileIds =
        state.folders
            .flatMap {
                it.childTileIds
            }
            .toSet()

    val expectedTopLevelIds =
        (
            availableLeafTileIds -
                assignedLeafTileIds
        ) + folderIds

    return state.topLevelTileIds.toSet() ==
        expectedTopLevelIds
}


internal fun createMetroTileFolder(
    state: MetroTileFolderDashboardState,
    sourceTileId: String,
    targetTileId: String,
    folderId: String,
    folderName: String,
    availableLeafTileIds: Set<String>,
): MetroTileFolderDashboardState? {
    if (
        !validateMetroTileFolderDashboardState(
            state = state,
            availableLeafTileIds =
                availableLeafTileIds,
        )
    ) {
        return null
    }

    if (
        sourceTileId == targetTileId ||
        sourceTileId !in availableLeafTileIds ||
        targetTileId !in availableLeafTileIds ||
        sourceTileId !in state.topLevelTileIds ||
        targetTileId !in state.topLevelTileIds ||
        folderId.isBlank() ||
        folderId in availableLeafTileIds ||
        folderId in state.topLevelTileIds ||
        state.folders.any {
            it.id == folderId
        }
    ) {
        return null
    }

    val folder =
        MetroTileFolder(
            id = folderId,
            name = folderName,
            childTileIds =
                listOf(
                    sourceTileId,
                    targetTileId,
                ),
        )

    /*
     * Preserve deterministic top-level ordering.
     *
     * Source disappears.
     * Target is replaced in-place by the new folder.
     */
    val newTopLevelTileIds =
        buildList {
            state.topLevelTileIds.forEach {
                    tileId,
                ->

                when (tileId) {
                    sourceTileId -> Unit

                    targetTileId ->
                        add(folderId)

                    else ->
                        add(tileId)
                }
            }
        }

    val result =
        MetroTileFolderDashboardState(
            folders =
                state.folders + folder,
            topLevelTileIds =
                newTopLevelTileIds,
        )

    return result.takeIf {
        validateMetroTileFolderDashboardState(
            state = it,
            availableLeafTileIds =
                availableLeafTileIds,
        )
    }
}


internal fun addLeafToMetroTileFolder(
    state: MetroTileFolderDashboardState,
    sourceTileId: String,
    targetFolderId: String,
    availableLeafTileIds: Set<String>,
): MetroTileFolderDashboardState? {
    if (
        !validateMetroTileFolderDashboardState(
            state = state,
            availableLeafTileIds =
                availableLeafTileIds,
        )
    ) {
        return null
    }

    if (
        sourceTileId !in availableLeafTileIds ||
        sourceTileId !in state.topLevelTileIds ||
        targetFolderId !in state.topLevelTileIds
    ) {
        return null
    }

    val targetFolder =
        state.folders
            .firstOrNull {
                it.id == targetFolderId
            }
            ?: return null

    if (
        sourceTileId in
        targetFolder.childTileIds
    ) {
        return null
    }

    val updatedFolder =
        targetFolder.copy(
            childTileIds =
                targetFolder.childTileIds +
                    sourceTileId,
        )

    val result =
        MetroTileFolderDashboardState(
            folders =
                state.folders.map {
                    if (it.id == targetFolderId) {
                        updatedFolder
                    } else {
                        it
                    }
                },
            topLevelTileIds =
                state.topLevelTileIds
                    .filterNot {
                        it == sourceTileId
                    },
        )

    return result.takeIf {
        validateMetroTileFolderDashboardState(
            state = it,
            availableLeafTileIds =
                availableLeafTileIds,
        )
    }
}


internal fun dissolveMetroTileFolder(
    state: MetroTileFolderDashboardState,
    folderId: String,
    availableLeafTileIds: Set<String>,
): MetroTileFolderDashboardState? {
    if (
        !validateMetroTileFolderDashboardState(
            state = state,
            availableLeafTileIds =
                availableLeafTileIds,
        )
    ) {
        return null
    }

    val folder =
        state.folders
            .firstOrNull {
                it.id == folderId
            }
            ?: return null

    if (folderId !in state.topLevelTileIds) {
        return null
    }

    /*
     * Restore the children exactly where the folder existed
     * in the deterministic top-level order.
     *
     * Child ordering is preserved from the folder model.
     */
    val newTopLevelTileIds =
        buildList {
            state.topLevelTileIds.forEach {
                    tileId,
                ->

                if (tileId == folderId) {
                    addAll(folder.childTileIds)
                } else {
                    add(tileId)
                }
            }
        }

    val result =
        MetroTileFolderDashboardState(
            folders =
                state.folders.filterNot {
                    it.id == folderId
                },
            topLevelTileIds =
                newTopLevelTileIds,
        )

    return result.takeIf {
        validateMetroTileFolderDashboardState(
            state = it,
            availableLeafTileIds =
                availableLeafTileIds,
        )
    }
}


internal fun resolveMetroTileTopLevelIds(
    availableLeafTileIds: List<String>,
    folders: Collection<MetroTileFolder>,
): List<String> {
    val assignedLeafTileIds =
        folders
            .flatMap {
                it.childTileIds
            }
            .toSet()

    val topLevelLeafTileIds =
        availableLeafTileIds.filterNot {
            it in assignedLeafTileIds
        }

    val folderIds =
        folders.map {
            it.id
        }

    return (
        topLevelLeafTileIds +
            folderIds
    ).distinct()
}


internal fun restoreMetroTileOrder(
    savedOrder: List<String>,
    legalTopLevelTileIds: List<String>,
): List<String> {
    val legalIds =
        legalTopLevelTileIds.toSet()

    val restored =
        savedOrder
            .filter {
                it in legalIds
            }
            .distinct()

    val missing =
        legalTopLevelTileIds
            .filterNot {
                it in restored
            }

    return restored + missing
}
