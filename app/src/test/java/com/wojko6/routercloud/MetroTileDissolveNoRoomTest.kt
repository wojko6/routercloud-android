package com.wojko6.routercloud

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test


class MetroTileDissolveNoRoomTest {

    @Test
    fun dissolveLayoutIsRejectedWhenChildrenCannotFitWorkspace() {
        /*
         * 6 x 4 workspace = 24 cells.
         *
         * Large = 4 x 4 = 16
         * Wide  = 4 x 2 =  8
         * Small = 1 x 1 =  1
         *
         * Total = 25 cells, therefore the dissolved
         * top-level layout cannot possibly fit.
         */
        val result =
            buildBoundedMetroTilePositions(
                order =
                    listOf(
                        "storage",
                        "upload",
                        "directory",
                    ),
                sizes =
                    mapOf(
                        "storage" to
                            MetroTileSize.Small,
                        "upload" to
                            MetroTileSize.Large,
                        "directory" to
                            MetroTileSize.Wide,
                    ),
                gridUnits = 6,
                workspaceRows = 4,
            )

        assertNull(result)
    }


    @Test
    fun dissolveLayoutStillAcceptsNearbyFittingCase() {
        /*
         * 16 + 4 + 1 = 21 cells.
         * This nearby control case must still fit.
         */
        val result =
            buildBoundedMetroTilePositions(
                /*
                 * The bounded packer is first-fit and
                 * order-sensitive. Place the largest tile first
                 * so this is a genuinely packable control case.
                 */
                order =
                    listOf(
                        "upload",
                        "directory",
                        "storage",
                    ),
                sizes =
                    mapOf(
                        "storage" to
                            MetroTileSize.Small,
                        "upload" to
                            MetroTileSize.Large,
                        "directory" to
                            MetroTileSize.Medium,
                    ),
                gridUnits = 6,
                workspaceRows = 4,
            )

        assertNotNull(result)
    }
}
