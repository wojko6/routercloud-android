package com.wojko6.routercloud

internal data class MetroTileSizePreferenceResolution(
    val size: MetroTileSize,
    val shouldMigrateLegacy: Boolean,
)

internal fun metroTileSizePreferenceKey(
    tileId: String,
    gridUnits: Int,
): String =
    "tile_size_v2_${gridUnits}_$tileId"

private fun parseMetroTileSizePreference(
    stored: String?,
): MetroTileSize? =
    MetroTileSize
        .values()
        .firstOrNull {
            it.name == stored
        }

internal fun resolveMetroTileSizePreference(
    perGridStored: String?,
    legacyStored: String?,
    default: MetroTileSize,
): MetroTileSizePreferenceResolution {
    if (perGridStored != null) {
        return MetroTileSizePreferenceResolution(
            size =
                parseMetroTileSizePreference(
                    perGridStored,
                ) ?: default,
            shouldMigrateLegacy = false,
        )
    }

    val legacySize =
        parseMetroTileSizePreference(
            legacyStored,
        )

    return if (legacySize != null) {
        MetroTileSizePreferenceResolution(
            size = legacySize,
            shouldMigrateLegacy = true,
        )
    } else {
        MetroTileSizePreferenceResolution(
            size = default,
            shouldMigrateLegacy = false,
        )
    }
}
