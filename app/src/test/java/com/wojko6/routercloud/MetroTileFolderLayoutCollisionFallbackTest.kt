package com.wojko6.routercloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test


class MetroTileFolderLayoutCollisionFallbackTest {

    @Test
    fun collidingPersistedFolderLayoutFallsBackToValidPackedLayout() {
        val tileIds =
            listOf(
                "folder:test",
                "storage",
            )

        val sizes =
            mapOf(
                "folder:test" to
                    MetroTileSize.Medium,
                "storage" to
                    MetroTileSize.Medium,
            )

        /*
         * Corrupted persisted state:
         * both top-level tiles occupy exactly the same cells.
         */
        val corruptedPositions =
            mapOf(
                "folder:test" to
                    MetroTilePosition(
                        column = 0,
                        row = 0,
                    ),
                "storage" to
                    MetroTilePosition(
                        column = 0,
                        row = 0,
                    ),
            )

        assertFalse(
            validateMetroTileLayout(
                positions = corruptedPositions,
                sizes = sizes,
                tileIds = tileIds,
                gridUnits = 6,
                workspaceRows = 4,
            ),
        )

        /*
         * This is the same safe packing primitive used by
         * startup after rejecting an invalid saved layout.
         */
        val fallback =
            buildBoundedMetroTilePositions(
                order = tileIds,
                sizes = sizes,
                gridUnits = 6,
                workspaceRows = 4,
            )

        assertNotNull(fallback)

        assertTrue(
            validateMetroTileLayout(
                positions =
                    requireNotNull(fallback),
                sizes = sizes,
                tileIds = tileIds,
                gridUnits = 6,
                workspaceRows = 4,
            ),
        )
    }
}
