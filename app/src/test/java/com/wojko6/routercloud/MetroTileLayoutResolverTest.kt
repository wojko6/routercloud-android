package com.wojko6.routercloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetroTileLayoutResolverTest {

    @Test
    fun t01_mediumDraggedToEmptySlot_movesExactlyThere() {
        val positions =
            mapOf(
                "a" to
                    MetroTilePosition(
                        column = 0,
                        row = 0,
                    ),
                "b" to
                    MetroTilePosition(
                        column = 2,
                        row = 0,
                    ),
            )

        val sizes =
            mapOf(
                "a" to MetroTileSize.Medium,
                "b" to MetroTileSize.Medium,
            )

        val result =
            resolveMetroTileLayoutChange(
                tileId = "b",
                requestedPosition =
                    MetroTilePosition(
                        column = 4,
                        row = 0,
                    ),
                requestedSize =
                    MetroTileSize.Medium,
                positions = positions,
                sizes = sizes,
                tileIds =
                    listOf(
                        "a",
                        "b",
                    ),
                gridUnits = 6,
                workspaceRows = 4,
            )

        assertNotNull(result)

        result!!

        assertEquals(
            MetroTilePosition(
                column = 4,
                row = 0,
            ),
            result.positions["b"],
        )

        assertEquals(
            MetroTilePosition(
                column = 0,
                row = 0,
            ),
            result.positions["a"],
        )

        assertEquals(
            sizes,
            result.sizes,
        )

        assertTrue(
            validateMetroTileLayout(
                positions = result.positions,
                sizes = result.sizes,
                tileIds =
                    listOf(
                        "a",
                        "b",
                    ),
                gridUnits = 6,
                workspaceRows = 4,
            ),
        )
    }

    @Test
    fun t02_mediumDraggedOntoMedium_swapsIntoVacatedSlot() {
        val positions =
            mapOf(
                "a" to
                    MetroTilePosition(
                        column = 0,
                        row = 0,
                    ),
                "b" to
                    MetroTilePosition(
                        column = 2,
                        row = 0,
                    ),
            )

        val sizes =
            mapOf(
                "a" to MetroTileSize.Medium,
                "b" to MetroTileSize.Medium,
            )

        val result =
            resolveMetroTileLayoutChange(
                tileId = "b",
                requestedPosition =
                    MetroTilePosition(
                        column = 0,
                        row = 0,
                    ),
                requestedSize =
                    MetroTileSize.Medium,
                positions = positions,
                sizes = sizes,
                tileIds =
                    listOf(
                        "a",
                        "b",
                    ),
                gridUnits = 6,
                workspaceRows = 4,
            )

        assertNotNull(result)

        result!!

        assertEquals(
            MetroTilePosition(
                column = 0,
                row = 0,
            ),
            result.positions["b"],
        )

        assertEquals(
            MetroTilePosition(
                column = 2,
                row = 0,
            ),
            result.positions["a"],
        )

        assertEquals(
            sizes,
            result.sizes,
        )

        assertTrue(
            validateMetroTileLayout(
                positions = result.positions,
                sizes = result.sizes,
                tileIds =
                    listOf(
                        "a",
                        "b",
                    ),
                gridUnits = 6,
                workspaceRows = 4,
            ),
        )
    }

    @Test
    fun t12_mediumDraggedBelowWorkspace_isRejected() {
        val positions =
            mapOf(
                "a" to
                    MetroTilePosition(
                        column = 0,
                        row = 0,
                    ),
                "b" to
                    MetroTilePosition(
                        column = 2,
                        row = 0,
                    ),
            )

        val sizes =
            mapOf(
                "a" to MetroTileSize.Medium,
                "b" to MetroTileSize.Medium,
            )

        val result =
            resolveMetroTileLayoutChange(
                tileId = "b",
                requestedPosition =
                    MetroTilePosition(
                        column = 2,
                        row = 3,
                    ),
                requestedSize =
                    MetroTileSize.Medium,
                positions = positions,
                sizes = sizes,
                tileIds =
                    listOf(
                        "a",
                        "b",
                    ),
                gridUnits = 6,
                workspaceRows = 4,
            )

        assertNull(result)

        assertEquals(
            MetroTilePosition(
                column = 2,
                row = 0,
            ),
            positions["b"],
        )

        assertTrue(
            validateMetroTileLayout(
                positions = positions,
                sizes = sizes,
                tileIds =
                    listOf(
                        "a",
                        "b",
                    ),
                gridUnits = 6,
                workspaceRows = 4,
            ),
        )
    }


    @Test
    fun t08_mediumPartiallyOverlapsMedium_displacesIntoVacatedSlot() {
        val positions =
            mapOf(
                "a" to
                    MetroTilePosition(
                        column = 0,
                        row = 0,
                    ),
                "b" to
                    MetroTilePosition(
                        column = 4,
                        row = 0,
                    ),
            )

        val sizes =
            mapOf(
                "a" to MetroTileSize.Medium,
                "b" to MetroTileSize.Medium,
            )

        val result =
            resolveMetroTileLayoutChange(
                tileId = "b",
                requestedPosition =
                    MetroTilePosition(
                        column = 1,
                        row = 0,
                    ),
                requestedSize =
                    MetroTileSize.Medium,
                positions = positions,
                sizes = sizes,
                tileIds =
                    listOf(
                        "a",
                        "b",
                    ),
                gridUnits = 6,
                workspaceRows = 4,
            )

        assertNotNull(result)

        result!!

        assertEquals(
            MetroTilePosition(
                column = 1,
                row = 0,
            ),
            result.positions["b"],
        )

        assertEquals(
            MetroTilePosition(
                column = 4,
                row = 0,
            ),
            result.positions["a"],
        )

        assertTrue(
            validateMetroTileLayout(
                positions = result.positions,
                sizes = result.sizes,
                tileIds =
                    listOf(
                        "a",
                        "b",
                    ),
                gridUnits = 6,
                workspaceRows = 4,
            ),
        )
    }


    @Test
    fun t07_largeOverlapsTwoMedium_reflowsAtomically() {
        val positions =
            mapOf(
                "top" to
                    MetroTilePosition(
                        column = 0,
                        row = 0,
                    ),
                "bottom" to
                    MetroTilePosition(
                        column = 0,
                        row = 2,
                    ),
                "large" to
                    MetroTilePosition(
                        column = 4,
                        row = 0,
                    ),
            )

        val sizes =
            mapOf(
                "top" to MetroTileSize.Medium,
                "bottom" to MetroTileSize.Medium,
                "large" to MetroTileSize.Large,
            )

        val result =
            resolveMetroTileLayoutChange(
                tileId = "large",
                requestedPosition =
                    MetroTilePosition(
                        column = 0,
                        row = 0,
                    ),
                requestedSize =
                    MetroTileSize.Large,
                positions = positions,
                sizes = sizes,
                tileIds =
                    listOf(
                        "top",
                        "bottom",
                        "large",
                    ),
                gridUnits = 8,
                workspaceRows = 4,
            )

        assertNotNull(result)

        result!!

        assertEquals(
            MetroTilePosition(
                column = 0,
                row = 0,
            ),
            result.positions["large"],
        )

        assertEquals(
            MetroTilePosition(
                column = 4,
                row = 0,
            ),
            result.positions["top"],
        )

        assertEquals(
            MetroTilePosition(
                column = 4,
                row = 2,
            ),
            result.positions["bottom"],
        )

        assertTrue(
            validateMetroTileLayout(
                positions = result.positions,
                sizes = result.sizes,
                tileIds =
                    listOf(
                        "top",
                        "bottom",
                        "large",
                    ),
                gridUnits = 8,
                workspaceRows = 4,
            ),
        )
    }


    @Test
    fun t14_noSpaceForReflow_rejectsEntireMove() {
        val positions =
            mapOf(
                "large" to
                    MetroTilePosition(
                        column = 0,
                        row = 0,
                    ),
                "moving" to
                    MetroTilePosition(
                        column = 4,
                        row = 0,
                    ),
            )

        val sizes =
            mapOf(
                "large" to MetroTileSize.Large,
                "moving" to MetroTileSize.Medium,
            )

        assertTrue(
            validateMetroTileLayout(
                positions = positions,
                sizes = sizes,
                tileIds =
                    listOf(
                        "large",
                        "moving",
                    ),
                gridUnits = 8,
                workspaceRows = 4,
            ),
        )

        val result =
            resolveMetroTileLayoutChange(
                tileId = "moving",
                requestedPosition =
                    MetroTilePosition(
                        column = 3,
                        row = 0,
                    ),
                requestedSize =
                    MetroTileSize.Medium,
                positions = positions,
                sizes = sizes,
                tileIds =
                    listOf(
                        "large",
                        "moving",
                    ),
                gridUnits = 8,
                workspaceRows = 4,
            )

        assertNull(result)

        assertEquals(
            MetroTilePosition(
                column = 0,
                row = 0,
            ),
            positions["large"],
        )

        assertEquals(
            MetroTilePosition(
                column = 4,
                row = 0,
            ),
            positions["moving"],
        )

        assertTrue(
            validateMetroTileLayout(
                positions = positions,
                sizes = sizes,
                tileIds =
                    listOf(
                        "large",
                        "moving",
                    ),
                gridUnits = 8,
                workspaceRows = 4,
            ),
        )
    }

}
