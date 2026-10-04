package com.wojko6.routercloud.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import okio.BufferedSink
import java.io.File
import java.io.IOException
import java.io.InputStream
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

    fun listDirectory(path: String = ""): RouterCloudDirectory {
        val request = Request.Builder()
            .url(buildUrl(path, json = true))
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

            return parseDirectory(response.body.string())
        }
    }

    fun downloadFile(
        path: String,
        destination: File,
    ) {
        val request = Request.Builder()
            .url(buildUrl(path))
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw RouterCloudAuthException()
            }

            if (!response.isSuccessful) {
                throw RouterCloudHttpException(
                    response.code,
                    "Pobieranie pliku: HTTP ${response.code}",
                )
            }

            destination.parentFile?.mkdirs()

            response.body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }





    fun rename(
        sourcePath: String,
        destinationPath: String,
    ) {
        val destinationUrl = buildUrl(destinationPath)

        val request = Request.Builder()
            .url(buildUrl(sourcePath))
            .header("Destination", destinationUrl.toString())
            .method("MOVE", null)
            .build()

        client.newCall(request).execute().use { response ->
            when (response.code) {
                204 -> Unit

                401 -> throw RouterCloudAuthException()

                409 -> throw RouterCloudHttpException(
                    response.code,
                    "Element o tej nazwie już istnieje.",
                )

                403 -> throw RouterCloudHttpException(
                    response.code,
                    "Zmiana nazwy w tej lokalizacji jest niedozwolona.",
                )

                else -> {
                    if (!response.isSuccessful) {
                        throw RouterCloudHttpException(
                            response.code,
                            "Zmiana nazwy: HTTP ${response.code}",
                        )
                    }
                }
            }
        }
    }

    fun delete(path: String) {
        val request = Request.Builder()
            .url(buildUrl(path))
            .delete()
            .build()

        client.newCall(request).execute().use { response ->
            when (response.code) {
                200, 204 -> Unit

                401 -> throw RouterCloudAuthException()

                403 -> throw RouterCloudHttpException(
                    response.code,
                    "Brak uprawnień do usunięcia elementu.",
                )

                404 -> throw RouterCloudHttpException(
                    response.code,
                    "Element już nie istnieje.",
                )

                else -> throw RouterCloudHttpException(
                    response.code,
                    "Nie udało się usunąć elementu (HTTP ${response.code}).",
                )
            }
        }
    }

    fun createDirectory(path: String) {
        val request = Request.Builder()
            .url(buildUrl(path))
            .method("MKCOL", null)
            .build()

        client.newCall(request).execute().use { response ->
            when (response.code) {
                201 -> Unit

                401 -> throw RouterCloudAuthException()

                405 -> throw RouterCloudHttpException(
                    response.code,
                    "Katalog już istnieje.",
                )

                else -> {
                    if (!response.isSuccessful) {
                        throw RouterCloudHttpException(
                            response.code,
                            "Tworzenie katalogu: HTTP ${response.code}",
                        )
                    }
                }
            }
        }
    }

    fun uploadFile(
        path: String,
        inputStreamProvider: () -> InputStream,
        contentLength: Long?,
        mediaType: String?,
    ) {
        val requestBody = object : RequestBody() {
            override fun contentType() =
                mediaType?.toMediaTypeOrNull()

            override fun contentLength(): Long =
                contentLength ?: -1L

            override fun writeTo(sink: BufferedSink) {
                inputStreamProvider().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                    while (true) {
                        val read = input.read(buffer)

                        if (read == -1) {
                            break
                        }

                        sink.write(buffer, 0, read)
                    }
                }
            }
        }

        val request = Request.Builder()
            .url(buildUrl(path))
            .put(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw RouterCloudAuthException()
            }

            if (!response.isSuccessful) {
                throw RouterCloudHttpException(
                    response.code,
                    "Wysyłanie pliku: HTTP ${response.code}",
                )
            }
        }
    }

    fun readTextFile(
        path: String,
        maxBytes: Long = 1024 * 1024,
    ): String {
        val request = Request.Builder()
            .url(buildUrl(path))
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw RouterCloudAuthException()
            }

            if (!response.isSuccessful) {
                throw RouterCloudHttpException(
                    response.code,
                    "Podgląd pliku: HTTP ${response.code}",
                )
            }

            val body = response.body
            val declaredLength = body.contentLength()

            if (declaredLength > maxBytes) {
                throw IOException(
                    "Plik jest za duży do podglądu w aplikacji."
                )
            }

            val charset =
                body.contentType()?.charset(Charsets.UTF_8)
                    ?: Charsets.UTF_8

            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L

            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)

                    if (read == -1) {
                        break
                    }

                    total += read

                    if (total > maxBytes) {
                        throw IOException(
                            "Plik jest za duży do podglądu w aplikacji."
                        )
                    }

                    output.write(buffer, 0, read)
                }
            }

            return output.toByteArray().toString(charset)
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

    private fun buildUrl(
        path: String,
        json: Boolean = false,
    ): HttpUrl {
        val builder = baseUrl.toHttpUrl()
            .newBuilder()
            .encodedPath("/")

        path.trim('/')
            .split('/')
            .filter { it.isNotEmpty() }
            .forEach { builder.addPathSegment(it) }

        if (json) {
            builder.addQueryParameter("json", null)
        }

        return builder.build()
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
