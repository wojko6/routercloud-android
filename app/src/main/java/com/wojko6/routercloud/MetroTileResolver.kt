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

    val originalSize =
        sizes[tileId]
            ?: return null

    /*
     * T01:
     * Try the requested position and size directly.
     *
     * If the complete candidate layout is already valid,
     * nothing else is allowed to move.
     */
    val directPositions =
        positions +
            (
                tileId to
                    requestedPosition
            )

    val directSizes =
        sizes +
            (
                tileId to
                    requestedSize
            )

    if (
        validateMetroTileLayout(
            positions = directPositions,
            sizes = directSizes,
            tileIds = tileIds,
            gridUnits = gridUnits,
            workspaceRows = workspaceRows,
        )
    ) {
        return MetroTileLayoutChangeResult(
            positions = directPositions,
            sizes = directSizes,
        )
    }

    /*
     * Detect actual rectangle collisions with the requested
     * geometry instead of comparing only top-left positions.
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
                    firstPosition =
                        requestedPosition,
                    firstSize =
                        requestedSize,
                    secondPosition =
                        otherPosition,
                    secondSize =
                        otherSize,
                )
            }

    /*
     * T02 / T08 stage:
     * exactly one displaced tile is supported.
     *
     * Multi-collision reflow will be implemented by a later
     * test instead of being guessed here.
     */
    if (displacedTileIds.size != 1) {
        return null
    }

    val displacedTileId =
        displacedTileIds.single()

    /*
     * First reflow priority:
     * the displaced tile tries the slot vacated by the
     * moving tile.
     */
    val swapPositions =
        positions.toMutableMap()

    swapPositions[tileId] =
        requestedPosition

    swapPositions[displacedTileId] =
        originalPosition

    val candidateSizes =
        sizes +
            (
                tileId to
                    requestedSize
            )

    if (
        !validateMetroTileLayout(
            positions = swapPositions,
            sizes = candidateSizes,
            tileIds = tileIds,
            gridUnits = gridUnits,
            workspaceRows = workspaceRows,
        )
    ) {
        return null
    }

    return MetroTileLayoutChangeResult(
        positions = swapPositions,
        sizes = candidateSizes,
    )
}
