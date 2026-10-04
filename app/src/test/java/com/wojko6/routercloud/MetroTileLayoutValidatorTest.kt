package com.wojko6.routercloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetroTileLayoutValidatorTest {

    private fun sizes(
        vararg entries: Pair<String, MetroTileSize>,
    ): Map<String, MetroTileSize> =
        mapOf(*entries)

    private fun positions(
        vararg entries: Pair<String, MetroTilePosition>,
    ): Map<String, MetroTilePosition> =
        mapOf(*entries)

    @Test
    fun v01_twoMediumTilesVertically_areValid() {
        val ids =
            listOf(
                "a",
                "b",
            )

        val result =
            validateMetroTileLayout(
                positions =
                    positions(
                        "a" to
                            MetroTilePosition(
                                column = 0,
                                row = 0,
                            ),
                        "b" to
                            MetroTilePosition(
                                column = 0,
                                row = 2,
                            ),
                    ),
                sizes =
                    sizes(
                        "a" to MetroTileSize.Medium,
                        "b" to MetroTileSize.Medium,
                    ),
                tileIds = ids,
                gridUnits = 6,
                workspaceRows = 4,
            )

        assertTrue(result)
    }

    @Test
    fun v02_twoMediumTilesHorizontally_areValid() {
        val ids =
            listOf(
                "a",
                "b",
            )

        val result =
            validateMetroTileLayout(
                positions =
                    positions(
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
                    ),
                sizes =
                    sizes(
                        "a" to MetroTileSize.Medium,
                        "b" to MetroTileSize.Medium,
                    ),
                tileIds = ids,
                gridUnits = 6,
                workspaceRows = 4,
            )

        assertTrue(result)
    }

    @Test
    fun v03_overlappingTiles_areInvalid() {
        val ids =
            listOf(
                "a",
                "b",
            )

        val result =
            validateMetroTileLayout(
                positions =
                    positions(
                        "a" to
                            MetroTilePosition(
                                column = 0,
                                row = 0,
                            ),
                        "b" to
                            MetroTilePosition(
                                column = 1,
                                row = 1,
                            ),
                    ),
                sizes =
                    sizes(
                        "a" to MetroTileSize.Medium,
                        "b" to MetroTileSize.Medium,
                    ),
                tileIds = ids,
                gridUnits = 6,
                workspaceRows = 4,
            )

        assertFalse(result)
    }

    @Test
    fun v04_negativeCoordinates_areInvalid() {
        val ids =
            listOf("a")

        val result =
            validateMetroTileLayout(
                positions =
                    positions(
                        "a" to
                            MetroTilePosition(
                                column = -1,
                                row = 0,
                            ),
                    ),
                sizes =
                    sizes(
                        "a" to MetroTileSize.Medium,
                    ),
                tileIds = ids,
                gridUnits = 6,
                workspaceRows = 4,
            )

        assertFalse(result)
    }

    @Test
    fun v05_tileBeyondRightEdge_isInvalid() {
        val ids =
            listOf("a")

        val result =
            validateMetroTileLayout(
                positions =
                    positions(
                        "a" to
                            MetroTilePosition(
                                column = 4,
                                row = 0,
                            ),
                    ),
                sizes =
                    sizes(
                        "a" to MetroTileSize.Wide,
                    ),
                tileIds = ids,
                gridUnits = 6,
                workspaceRows = 4,
            )

        assertFalse(result)
    }

    @Test
    fun v06_tileBelowWorkspace_isInvalid() {
        val ids =
            listOf("a")

        val result =
            validateMetroTileLayout(
                positions =
                    positions(
                        "a" to
                            MetroTilePosition(
                                column = 0,
                                row = 3,
                            ),
                    ),
                sizes =
                    sizes(
                        "a" to MetroTileSize.Medium,
                    ),
                tileIds = ids,
                gridUnits = 6,
                workspaceRows = 4,
            )

        assertFalse(result)
    }

    @Test
    fun v07_largeAndTwoMediumTilesInEightUnitGrid_areValid() {
        val ids =
            listOf(
                "large",
                "top",
                "bottom",
            )

        val result =
            validateMetroTileLayout(
                positions =
                    positions(
                        "large" to
                            MetroTilePosition(
                                column = 0,
                                row = 0,
                            ),
                        "top" to
                            MetroTilePosition(
                                column = 4,
                                row = 0,
                            ),
                        "bottom" to
                            MetroTilePosition(
                                column = 4,
                                row = 2,
                            ),
                    ),
                sizes =
                    sizes(
                        "large" to MetroTileSize.Large,
                        "top" to MetroTileSize.Medium,
                        "bottom" to MetroTileSize.Medium,
                    ),
                tileIds = ids,
                gridUnits = 8,
                workspaceRows = 4,
            )

        assertTrue(result)
    }

    @Test
    fun v08_missingPosition_isInvalid() {
        val ids =
            listOf(
                "a",
                "b",
            )

        val result =
            validateMetroTileLayout(
                positions =
                    positions(
                        "a" to
                            MetroTilePosition(
                                column = 0,
                                row = 0,
                            ),
                    ),
                sizes =
                    sizes(
                        "a" to MetroTileSize.Medium,
                        "b" to MetroTileSize.Medium,
                    ),
                tileIds = ids,
                gridUnits = 6,
                workspaceRows = 4,
            )

        assertFalse(result)
    }

    @Test
    fun v09_missingSize_isInvalid() {
        val ids =
            listOf(
                "a",
                "b",
            )

        val result =
            validateMetroTileLayout(
                positions =
                    positions(
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
                    ),
                sizes =
                    sizes(
                        "a" to MetroTileSize.Medium,
                    ),
                tileIds = ids,
                gridUnits = 6,
                workspaceRows = 4,
            )

        assertFalse(result)
    }
}
