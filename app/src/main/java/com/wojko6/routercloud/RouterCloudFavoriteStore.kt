package com.wojko6.routercloud

import android.content.Context
import com.wojko6.routercloud.network.RouterCloudEntry
import org.json.JSONArray
import org.json.JSONObject

internal data class RouterCloudFavorite(
    val path: String,
    val entry: RouterCloudEntry,
)

internal class RouterCloudFavoriteStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    fun load(): List<RouterCloudFavorite> {
        val encoded =
            preferences.getString(
                KEY_ITEMS,
                null,
            ) ?: return emptyList()

        return runCatching {
            val array = JSONArray(encoded)

            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)

                    val path =
                        item.getString("path")

                    val entry =
                        RouterCloudEntry(
                            name =
                                item.getString("name"),
                            pathType =
                                item.getString("pathType"),
                            mtime =
                                item.optLong(
                                    "mtime",
                                    0L,
                                ),
                            size =
                                item.optLong(
                                    "size",
                                    0L,
                                ),
                        )

                    add(
                        RouterCloudFavorite(
                            path = path,
                            entry = entry,
                        ),
                    )
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    fun save(
        favorites: Collection<RouterCloudFavorite>,
    ) {
        val array = JSONArray()

        favorites
            .sortedBy { it.path.lowercase() }
            .forEach { favorite ->
                array.put(
                    JSONObject()
                        .put(
                            "path",
                            favorite.path,
                        )
                        .put(
                            "name",
                            favorite.entry.name,
                        )
                        .put(
                            "pathType",
                            favorite.entry.pathType,
                        )
                        .put(
                            "mtime",
                            favorite.entry.mtime,
                        )
                        .put(
                            "size",
                            favorite.entry.size,
                        ),
                )
            }

        preferences
            .edit()
            .putString(
                KEY_ITEMS,
                array.toString(),
            )
            .apply()
    }

    private companion object {
        const val PREFS_NAME =
            "routercloud-favorites-v1"

        const val KEY_ITEMS =
            "items"
    }
}
