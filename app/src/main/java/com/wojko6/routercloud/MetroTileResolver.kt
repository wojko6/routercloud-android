package com.wojko6.routercloud

internal data class MetroTileLayoutChangeResult(
    val positions: Map<String, MetroTilePosition>,
    val sizes: Map<String, MetroTileSize>,
)

private fun metroTileRectanglesOverlap(
    firstPosition: MetroTilePosition,
    firstSize: MetroTileSize,
    secondPosition: MetroTilePosition,
    secondSize: MetroTileSize,
): Boolean {
    val (
        firstWidth,
        firstHeight,
    ) =
        metroTileSpan(firstSize)

    val (
        secondWidth,
        secondHeight,
    ) =
        metroTileSpan(secondSize)

    val firstLeft =
        firstPosition.column

    val firstTop =
        firstPosition.row

    val firstRight =
        firstLeft + firstWidth

    val firstBottom =
        firstTop + firstHeight

    val secondLeft =
        secondPosition.column

    val secondTop =
        secondPosition.row

    val secondRight =
        secondLeft + secondWidth

    val secondBottom =
        secondTop + secondHeight

    return firstLeft < secondRight &&
        firstRight > secondLeft &&
        firstTop < secondBottom &&
        firstBottom > secondTop
}

private fun metroTileFitsWorkspace(
    position: MetroTilePosition,
    size: MetroTileSize,
    gridUnits: Int,
    workspaceRows: Int,
): Boolean {
    val (
        width,
        height,
    ) =
        metroTileSpan(size)

    return position.column >= 0 &&
        position.row >= 0 &&
        position.column + width <= gridUnits &&
        position.row + height <= workspaceRows
}

private fun canPlaceMetroTileInWorkingLayout(
    tileId: String,
    position: MetroTilePosition,
    positions: Map<String, MetroTilePosition>,
    sizes: Map<String, MetroTileSize>,
    gridUnits: Int,
    workspaceRows: Int,
): Boolean {
    val tileSize =
        sizes[tileId]
            ?: return false

    if (
        !metroTileFitsWorkspace(
            position = position,
            size = tileSize,
            gridUnits = gridUnits,
            workspaceRows = workspaceRows,
        )
    ) {
        return false
    }

    positions.forEach {
            (otherId, otherPosition),
        ->

        if (otherId == tileId) {
            return@forEach
        }

        val otherSize =
            sizes[otherId]
                ?: return false

        if (
            metroTileRectanglesOverlap(
                firstPosition = position,
                firstSize = tileSize,
                secondPosition = otherPosition,
                secondSize = otherSize,
            )
        ) {
            return false
        }
    }

    return true
}

private fun findNearestFreeMetroPositionBounded(
    tileId: String,
    preferredPosition: MetroTilePosition,
    positions: Map<String, MetroTilePosition>,
    sizes: Map<String, MetroTileSize>,
    gridUnits: Int,
    workspaceRows: Int,
): MetroTilePosition? {
    val tileSize =
        sizes[tileId]
            ?: return null

    val (
        width,
        height,
    ) =
        metroTileSpan(tileSize)

    val maxColumn =
        gridUnits - width

    val maxRow =
        workspaceRows - height

    if (
        maxColumn < 0 ||
        maxRow < 0
    ) {
        return null
    }

    val candidates =
        buildList {
            for (row in 0..maxRow) {
                for (column in 0..maxColumn) {
                    add(
                        MetroTilePosition(
                            column = column,
                            row = row,
                        ),
                    )
                }
            }
        }
            .sortedWith(
                compareBy<MetroTilePosition>(
                    {
                        kotlin.math.abs(
                            it.column -
                                preferredPosition.column,
                        ) +
                            kotlin.math.abs(
                                it.row -
                                    preferredPosition.row,
                            )
                    },
                    { it.row },
                    { it.column },
                ),
            )

    return candidates.firstOrNull { candidate ->
        canPlaceMetroTileInWorkingLayout(
            tileId = tileId,
            position = candidate,
            positions = positions,
            sizes = sizes,
            gridUnits = gridUnits,
            workspaceRows = workspaceRows,
        )
    }
}

internal fun resolveMetroTileLayoutChange(
    tileId: String,
    requestedPosition: MetroTilePosition,
    requestedSize: MetroTileSize,
    positions: Map<String, MetroTilePosition>,
    sizes: Map<String, MetroTileSize>,
    tileIds: Collection<String>,
    gridUnits: Int,
    workspaceRows: Int,
): MetroTileLayoutChangeResult? {
    val originalPosition =
        positions[tileId]
            ?: return null

    if (sizes[tileId] == null) {
        return null
    }

    val candidateSizes =
        sizes +
            (
                tileId to
                    requestedSize
            )

    /*
     * Moving tile itself must fit inside the bounded workspace.
     */
    if (
        !metroTileFitsWorkspace(
            position = requestedPosition,
            size = requestedSize,
            gridUnits = gridUnits,
            workspaceRows = workspaceRows,
        )
    ) {
        return null
    }

    /*
     * T01:
     * If the requested layout is already valid,
     * no other tile moves.
     */
    val directPositions =
        positions +
            (
                tileId to
                    requestedPosition
            )

    if (
        validateMetroTileLayout(
            positions = directPositions,
            sizes = candidateSizes,
            tileIds = tileIds,
            gridUnits = gridUnits,
            workspaceRows = workspaceRows,
        )
    ) {
        return MetroTileLayoutChangeResult(
            positions = directPositions,
            sizes = candidateSizes,
        )
    }

    /*
     * Find every tile geometrically displaced by the moving tile.
     *
     * Sorting makes the result deterministic.
     */
    val displacedTileIds =
        tileIds
            .filter { otherId ->
                if (otherId == tileId) {
                    return@filter false
                }

                val otherPosition =
                    positions[otherId]
                        ?: return@filter false

                val otherSize =
                    sizes[otherId]
                        ?: return@filter false

                metroTileRectanglesOverlap(
                    firstPosition = requestedPosition,
                    firstSize = requestedSize,
                    secondPosition = otherPosition,
                    secondSize = otherSize,
                )
            }
            .sortedWith(
                compareBy(
                    { positions[it]?.row ?: Int.MAX_VALUE },
                    { positions[it]?.column ?: Int.MAX_VALUE },
                    { it },
                ),
            )

    if (displacedTileIds.isEmpty()) {
        return null
    }

    /*
     * Atomic working copy.
     *
     * Nothing from this map is returned until the entire
     * candidate layout has been resolved and validated.
     */
    val workingPositions =
        positions.toMutableMap()

    workingPositions.remove(tileId)

    displacedTileIds.forEach { displacedId ->
        workingPositions.remove(displacedId)
    }

    /*
     * The moving tile owns the requested target.
     */
    workingPositions[tileId] =
        requestedPosition

    displacedTileIds.forEachIndexed {
            index,
            displacedId,
        ->

        val oldPosition =
            positions[displacedId]
                ?: return null

        /*
         * Priority A:
         * first displaced tile tries the slot vacated by
         * the moving tile.
         */
        val swapPosition =
            if (index == 0) {
                originalPosition
            } else {
                null
            }

        val resolvedPosition =
            swapPosition
                ?.takeIf { candidate ->
                    canPlaceMetroTileInWorkingLayout(
                        tileId = displacedId,
                        position = candidate,
                        positions = workingPositions,
                        sizes = candidateSizes,
                        gridUnits = gridUnits,
                        workspaceRows = workspaceRows,
                    )
                }
                ?: oldPosition
                    .takeIf { candidate ->
                        canPlaceMetroTileInWorkingLayout(
                            tileId = displacedId,
                            position = candidate,
                            positions = workingPositions,
                            sizes = candidateSizes,
                            gridUnits = gridUnits,
                            workspaceRows = workspaceRows,
                        )
                    }
                ?: findNearestFreeMetroPositionBounded(
                    tileId = displacedId,
                    preferredPosition = oldPosition,
                    positions = workingPositions,
                    sizes = candidateSizes,
                    gridUnits = gridUnits,
                    workspaceRows = workspaceRows,
                )
                ?: return null

        workingPositions[displacedId] =
            resolvedPosition
    }

    /*
     * Final atomic gate.
     */
    if (
        !validateMetroTileLayout(
            positions = workingPositions,
            sizes = candidateSizes,
            tileIds = tileIds,
            gridUnits = gridUnits,
            workspaceRows = workspaceRows,
        )
    ) {
        return null
    }

    return MetroTileLayoutChangeResult(
        positions = workingPositions,
        sizes = candidateSizes,
    )
}
