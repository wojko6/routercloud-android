package com.wojko6.routercloud.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class RouterCloudEntry(
    val name: String,
    val pathType: String,
    val mtime: Long,
    val size: Long,
) {
    val isDirectory: Boolean
        get() = pathType == "Dir" || pathType == "SymlinkDir"
}

data class RouterCloudDirectory(
    val href: String,
    val entries: List<RouterCloudEntry>,
    val allowUpload: Boolean,
    val allowMove: Boolean,
    val allowDelete: Boolean,
    val allowSearch: Boolean,
    val allowArchive: Boolean,
    val storagePresent: Boolean,
)

class RouterCloudHttpException(
    val statusCode: Int,
    message: String,
) : IOException(message)

class RouterCloudAuthException :
    IOException("Nieprawidłowy login lub hasło")

private class MemoryCookieJar : CookieJar {
    private val store = mutableListOf<Cookie>()

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        synchronized(store) {
            val now = System.currentTimeMillis()

            store.removeAll { existing ->
                existing.expiresAt <= now ||
                    cookies.any { incoming ->
                        incoming.name == existing.name &&
                            incoming.domain == existing.domain &&
                            incoming.path == existing.path
                    }
            }

            store += cookies.filter { it.expiresAt > now }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(store) {
            val now = System.currentTimeMillis()

            store.removeAll { it.expiresAt <= now }

            return store.filter { it.matches(url) }
        }
    }

    fun clear() {
        synchronized(store) {
            store.clear()
        }
    }
}

class RouterCloudClient(
    baseUrl: String = "https://cloud.home.arpa",
) {
    private val baseUrl = baseUrl.trimEnd('/')

    private val cookieJar = MemoryCookieJar()

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun login(
        username: String,
        password: String,
    ) {
        require(username.isNotBlank()) {
            "Login nie może być pusty"
        }

        require(password.isNotEmpty()) {
            "Hasło nie może być puste"
        }

        val body = FormBody.Builder()
            .add("username", username.trim())
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url("$baseUrl/__routercloud/login")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            when (response.code) {
                204 -> Unit

                401 -> throw RouterCloudAuthException()

                else -> throw RouterCloudHttpException(
                    response.code,
                    "Logowanie RouterCloud: HTTP ${response.code}",
                )
            }
        }
    }

    fun listRoot(): RouterCloudDirectory {
        val request = Request.Builder()
            .url("$baseUrl/?json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw RouterCloudAuthException()
            }

            if (!response.isSuccessful) {
                throw RouterCloudHttpException(
                    response.code,
                    "Lista plików RouterCloud: HTTP ${response.code}",
                )
            }

            val payload = response.body.string()

            return parseDirectory(payload)
        }
    }

    fun logout() {
        val request = Request.Builder()
            .url("$baseUrl/__routercloud/logout")
            .post(FormBody.Builder().build())
            .build()

        try {
            client.newCall(request).execute().close()
        } finally {
            cookieJar.clear()
        }
    }

    private fun parseDirectory(payload: String): RouterCloudDirectory {
        val root = JSONObject(payload)
        val paths = root.getJSONArray("paths")

        val entries = buildList {
            for (index in 0 until paths.length()) {
                val item = paths.getJSONObject(index)

                add(
                    RouterCloudEntry(
                        name = item.getString("name"),
                        pathType = item.getString("path_type"),
                        mtime = item.getLong("mtime"),
                        size = item.getLong("size"),
                    )
                )
            }
        }

        return RouterCloudDirectory(
            href = root.optString("href", "/"),
            entries = entries,
            allowUpload = root.optBoolean("allow_upload"),
            allowMove = root.optBoolean("allow_move"),
            allowDelete =
                root.optBoolean("routercloud_allow_delete") ||
                    root.optBoolean("allow_delete"),
            allowSearch = root.optBoolean("allow_search"),
            allowArchive = root.optBoolean("allow_archive"),
            storagePresent =
                root.has("storage") &&
                    !root.isNull("storage"),
        )
    }
}
