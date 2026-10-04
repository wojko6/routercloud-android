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


    @Test
    fun t15_resizeWithoutCollision_keepsPosition() {
        val positions =
            mapOf(
                "resized" to
                    MetroTilePosition(
                        column = 0,
                        row = 0,
                    ),
                "other" to
                    MetroTilePosition(
                        column = 4,
                        row = 0,
                    ),
            )

        val sizes =
            mapOf(
                "resized" to MetroTileSize.Small,
                "other" to MetroTileSize.Medium,
            )

        val result =
            resolveMetroTileLayoutChange(
                tileId = "resized",
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
                        "resized",
                        "other",
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
            result.positions["resized"],
        )

        assertEquals(
            MetroTileSize.Medium,
            result.sizes["resized"],
        )

        assertEquals(
            MetroTilePosition(
                column = 4,
                row = 0,
            ),
            result.positions["other"],
        )

        assertTrue(
            validateMetroTileLayout(
                positions = result.positions,
                sizes = result.sizes,
                tileIds =
                    listOf(
                        "resized",
                        "other",
                    ),
                gridUnits = 6,
                workspaceRows = 4,
            ),
        )
    }


    @Test
    fun t16_resizeWithOneCollision_movesNeighbor() {
        val positions =
            mapOf(
                "resized" to
                    MetroTilePosition(
                        column = 0,
                        row = 0,
                    ),
                "neighbor" to
                    MetroTilePosition(
                        column = 1,
                        row = 0,
                    ),
            )

        val sizes =
            mapOf(
                "resized" to MetroTileSize.Small,
                "neighbor" to MetroTileSize.Medium,
            )

        assertTrue(
            validateMetroTileLayout(
                positions = positions,
                sizes = sizes,
                tileIds =
                    listOf(
                        "resized",
                        "neighbor",
                    ),
                gridUnits = 6,
                workspaceRows = 4,
            ),
        )

        val result =
            resolveMetroTileLayoutChange(
                tileId = "resized",
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
                        "resized",
                        "neighbor",
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
            result.positions["resized"],
        )

        assertEquals(
            MetroTileSize.Medium,
            result.sizes["resized"],
        )

        assertEquals(
            MetroTilePosition(
                column = 2,
                row = 0,
            ),
            result.positions["neighbor"],
        )

        assertEquals(
            MetroTileSize.Medium,
            result.sizes["neighbor"],
        )

        assertTrue(
            validateMetroTileLayout(
                positions = result.positions,
                sizes = result.sizes,
                tileIds =
                    listOf(
                        "resized",
                        "neighbor",
                    ),
                gridUnits = 6,
                workspaceRows = 4,
            ),
        )
    }


    @Test
    fun t17_resizeWithMultipleCollisions_reflowsAtomically() {
        val positions =
            mapOf(
                "resized" to
                    MetroTilePosition(
                        column = 0,
                        row = 0,
                    ),
                "top" to
                    MetroTilePosition(
                        column = 2,
                        row = 0,
                    ),
                "bottom" to
                    MetroTilePosition(
                        column = 2,
                        row = 2,
                    ),
            )

        val sizes =
            mapOf(
                "resized" to MetroTileSize.Medium,
                "top" to MetroTileSize.Medium,
                "bottom" to MetroTileSize.Medium,
            )

        assertTrue(
            validateMetroTileLayout(
                positions = positions,
                sizes = sizes,
                tileIds =
                    listOf(
                        "resized",
                        "top",
                        "bottom",
                    ),
                gridUnits = 8,
                workspaceRows = 4,
            ),
        )

        val result =
            resolveMetroTileLayoutChange(
                tileId = "resized",
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
                        "resized",
                        "top",
                        "bottom",
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
            result.positions["resized"],
        )

        assertEquals(
            MetroTileSize.Large,
            result.sizes["resized"],
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
                        "resized",
                        "top",
                        "bottom",
                    ),
                gridUnits = 8,
                workspaceRows = 4,
            ),
        )
    }


    @Test
    fun t18_resizeOutsideWorkspace_isRejected() {
        val positions =
            mapOf(
                "resized" to
                    MetroTilePosition(
                        column = 0,
                        row = 2,
                    ),
                "other" to
                    MetroTilePosition(
                        column = 4,
                        row = 0,
                    ),
            )

        val sizes =
            mapOf(
                "resized" to MetroTileSize.Medium,
                "other" to MetroTileSize.Medium,
            )

        assertTrue(
            validateMetroTileLayout(
                positions = positions,
                sizes = sizes,
                tileIds =
                    listOf(
                        "resized",
                        "other",
                    ),
                gridUnits = 6,
                workspaceRows = 4,
            ),
        )

        val result =
            resolveMetroTileLayoutChange(
                tileId = "resized",
                requestedPosition =
                    MetroTilePosition(
                        column = 0,
                        row = 2,
                    ),
                requestedSize =
                    MetroTileSize.Large,
                positions = positions,
                sizes = sizes,
                tileIds =
                    listOf(
                        "resized",
                        "other",
                    ),
                gridUnits = 6,
                workspaceRows = 4,
            )

        assertNull(result)

        assertEquals(
            MetroTilePosition(
                column = 0,
                row = 2,
            ),
            positions["resized"],
        )

        assertEquals(
            MetroTileSize.Medium,
            sizes["resized"],
        )

        assertEquals(
            MetroTilePosition(
                column = 4,
                row = 0,
            ),
            positions["other"],
        )

        assertTrue(
            validateMetroTileLayout(
                positions = positions,
                sizes = sizes,
                tileIds =
                    listOf(
                        "resized",
                        "other",
                    ),
                gridUnits = 6,
                workspaceRows = 4,
            ),
        )
    }

}
