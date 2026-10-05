package com.wojko6.routercloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MetroTilePersistenceTest {

    @Test
    fun `tile size preference key is isolated by grid width`() {
        val grid6 =
            metroTileSizePreferenceKey(
                tileId = "upload",
                gridUnits = 6,
            )

        val grid8 =
            metroTileSizePreferenceKey(
                tileId = "upload",
                gridUnits = 8,
            )

        assertEquals(
            "tile_size_v2_6_upload",
            grid6,
        )

        assertEquals(
            "tile_size_v2_8_upload",
            grid8,
        )

        assertNotEquals(
            grid6,
            grid8,
        )
    }

    @Test
    fun `tile size preference key is isolated by tile id`() {
        val upload =
            metroTileSizePreferenceKey(
                tileId = "upload",
                gridUnits = 6,
            )

        val storage =
            metroTileSizePreferenceKey(
                tileId = "storage",
                gridUnits = 6,
            )

        assertNotEquals(
            upload,
            storage,
        )
    }
    @Test
    fun `per grid size wins over legacy value`() {
        val result =
            resolveMetroTileSizePreference(
                perGridStored = "Large",
                legacyStored = "Small",
                default = MetroTileSize.Medium,
            )

        assertEquals(
            MetroTileSize.Large,
            result.size,
        )
        assertEquals(
            false,
            result.shouldMigrateLegacy,
        )
    }

    @Test
    fun `legacy size seeds missing per grid value`() {
        val result =
            resolveMetroTileSizePreference(
                perGridStored = null,
                legacyStored = "Wide",
                default = MetroTileSize.Medium,
            )

        assertEquals(
            MetroTileSize.Wide,
            result.size,
        )
        assertEquals(
            true,
            result.shouldMigrateLegacy,
        )
    }

    @Test
    fun `missing values use default without migration`() {
        val result =
            resolveMetroTileSizePreference(
                perGridStored = null,
                legacyStored = null,
                default = MetroTileSize.Medium,
            )

        assertEquals(
            MetroTileSize.Medium,
            result.size,
        )
        assertEquals(
            false,
            result.shouldMigrateLegacy,
        )
    }

    @Test
    fun `invalid per grid value falls back safely`() {
        val result =
            resolveMetroTileSizePreference(
                perGridStored = "BROKEN",
                legacyStored = "Large",
                default = MetroTileSize.Small,
            )

        assertEquals(
            MetroTileSize.Small,
            result.size,
        )
        assertEquals(
            false,
            result.shouldMigrateLegacy,
        )
    }

    @Test
    fun `invalid legacy value falls back safely`() {
        val result =
            resolveMetroTileSizePreference(
                perGridStored = null,
                legacyStored = "BROKEN",
                default = MetroTileSize.Wide,
            )

        assertEquals(
            MetroTileSize.Wide,
            result.size,
        )
        assertEquals(
            false,
            result.shouldMigrateLegacy,
        )
    }


}
