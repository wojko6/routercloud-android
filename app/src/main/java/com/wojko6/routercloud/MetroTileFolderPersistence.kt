package com.wojko6.routercloud

import org.json.JSONArray
import org.json.JSONObject


private const val METRO_TILE_FOLDERS_VERSION = 1


internal fun encodeMetroTileFolders(
    folders: Collection<MetroTileFolder>,
): String {
    val folderArray =
        JSONArray()

    folders.forEach { folder ->
        val children =
            JSONArray()

        folder.childTileIds.forEach { childTileId ->
            children.put(childTileId)
        }

        folderArray.put(
            JSONObject()
                .put(
                    "id",
                    folder.id,
                )
                .put(
                    "name",
                    folder.name,
                )
                .put(
                    "children",
                    children,
                ),
        )
    }

    return JSONObject()
        .put(
            "version",
            METRO_TILE_FOLDERS_VERSION,
        )
        .put(
            "folders",
            folderArray,
        )
        .toString()
}


internal fun decodeMetroTileFolders(
    encoded: String,
    availableLeafTileIds: Set<String>,
): List<MetroTileFolder>? =
    runCatching {
        val root =
            JSONObject(encoded)

        if (
            root.getInt("version") !=
            METRO_TILE_FOLDERS_VERSION
        ) {
            return@runCatching null
        }

        val folderArray =
            root.getJSONArray("folders")

        val folders =
            buildList {
                for (
                    folderIndex in
                    0 until folderArray.length()
                ) {
                    val folderObject =
                        folderArray.getJSONObject(
                            folderIndex,
                        )

                    val childrenArray =
                        folderObject.getJSONArray(
                            "children",
                        )

                    val childTileIds =
                        buildList {
                            for (
                                childIndex in
                                0 until childrenArray.length()
                            ) {
                                add(
                                    childrenArray.getString(
                                        childIndex,
                                    ),
                                )
                            }
                        }

                    add(
                        MetroTileFolder(
                            id =
                                folderObject.getString(
                                    "id",
                                ),
                            name =
                                folderObject.getString(
                                    "name",
                                ),
                            childTileIds =
                                childTileIds,
                        ),
                    )
                }
            }

        if (
            !validateMetroTileFolderState(
                folders = folders,
                availableLeafTileIds =
                    availableLeafTileIds,
            )
        ) {
            return@runCatching null
        }

        folders
    }.getOrNull()
