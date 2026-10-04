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

}
