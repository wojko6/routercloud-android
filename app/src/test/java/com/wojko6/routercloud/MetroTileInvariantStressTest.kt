package com.wojko6.routercloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetroTileInvariantStressTest {

    @Test
    fun `T24 repeated drag and resize preserves invariants`() {
        for (gridUnits in listOf(6, 8)) {
            val tileIds =
                listOf(
                    "upload",
                    "directory",
                    "storage",
                )

            var positions: Map<String, MetroTilePosition> =
                mapOf(
                    "upload" to
                        MetroTilePosition(
                            column = 0,
                            row = 0,
                        ),
                    "directory" to
                        MetroTilePosition(
                            column = 2,
                            row = 0,
                        ),
                    "storage" to
                        MetroTilePosition(
                            column = 0,
                            row = 2,
                        ),
                )

            var sizes: Map<String, MetroTileSize> =
                mapOf(
                    "upload" to MetroTileSize.Medium,
                    "directory" to MetroTileSize.Medium,
                    "storage" to MetroTileSize.Wide,
                )

            assertTrue(
                validateMetroTileLayout(
                    positions = positions,
                    sizes = sizes,
                    tileIds = tileIds,
                    gridUnits = gridUnits,
                    workspaceRows = 4,
                ),
            )

            var accepted = 0
            var rejected = 0

            repeat(240) { step ->
                val tileId =
                    tileIds[
                        step % tileIds.size
                    ]

                val requestedSize =
                    when (
                        (step / tileIds.size) % 4
                    ) {
                        0 -> MetroTileSize.Small
                        1 -> MetroTileSize.Medium
                        2 -> MetroTileSize.Wide
                        else -> MetroTileSize.Large
                    }

                /*
                 * Deliberately generate both legal and illegal
                 * coordinates.
                 *
                 * This exercises:
                 * - normal drag;
                 * - collision reflow;
                 * - resize;
                 * - edge handling;
                 * - negative coordinates;
                 * - below-workspace coordinates;
                 * - atomic rejection.
                 */
                val requestedPosition =
                    MetroTilePosition(
                        column =
                            (
                                step * 5 %
                                    (gridUnits + 3)
                            ) - 1,
                        row =
                            (
                                (step * 3 + 2) % 7
                            ) - 1,
                    )

                val inputPositions =
                    positions.toMap()

                val inputSizes =
                    sizes.toMap()

                val resolved =
                    resolveMetroTileLayoutChange(
                        tileId = tileId,
                        requestedPosition =
                            requestedPosition,
                        requestedSize =
                            requestedSize,
                        positions = positions,
                        sizes = sizes,
                        tileIds = tileIds,
                        gridUnits = gridUnits,
                        workspaceRows = 4,
                    )

                /*
                 * Resolver must never mutate caller state.
                 */
                assertEquals(
                    inputPositions,
                    positions,
                )

                assertEquals(
                    inputSizes,
                    sizes,
                )

                if (resolved == null) {
                    rejected += 1
                } else {
                    accepted += 1

                    assertTrue(
                        "Invalid accepted layout at step " +
                            "$step in grid $gridUnits",
                        validateMetroTileLayout(
                            positions =
                                resolved.positions,
                            sizes =
                                resolved.sizes,
                            tileIds = tileIds,
                            gridUnits = gridUnits,
                            workspaceRows = 4,
                        ),
                    )

                    positions =
                        resolved.positions.toMap()

                    sizes =
                        resolved.sizes.toMap()
                }
            }

            assertTrue(
                "Stress test accepted no operations " +
                    "for grid $gridUnits",
                accepted > 0,
            )

            assertTrue(
                "Stress test rejected no operations " +
                    "for grid $gridUnits",
                rejected > 0,
            )
        }
    }
}
