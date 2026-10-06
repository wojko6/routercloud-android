package com.wojko6.routercloud.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.wojko6.routercloud.network.RouterCloudSessionCookie
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class RouterCloudBackgroundSessionStore(
    context: Context,
) {
    private val sessionFile =
        File(
            context.noBackupFilesDir,
            SESSION_FILE,
        )

    private val atomicFile =
        AtomicFile(sessionFile)

    fun hasSavedSession(): Boolean =
        sessionFile.isFile &&
            sessionFile.length() > 0L

    fun save(
        cookies: List<RouterCloudSessionCookie>,
    ) {
        require(cookies.isNotEmpty()) {
            "Brak aktywnej sesji RouterCloud do zapisania."
        }

        val cipher =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.ENCRYPT_MODE,
                    getOrCreateKey(),
                )
            }

        val plaintext =
            encodeCookies(cookies)

        val ciphertext =
            cipher.doFinal(plaintext)

        val envelope =
            JSONObject()
                .put(
                    "version",
                    FORMAT_VERSION,
                )
                .put(
                    "iv",
                    Base64.encodeToString(
                        cipher.iv,
                        Base64.NO_WRAP,
                    ),
                )
                .put(
                    "ciphertext",
                    Base64.encodeToString(
                        ciphertext,
                        Base64.NO_WRAP,
                    ),
                )
                .toString()
                .toByteArray(
                    StandardCharsets.UTF_8,
                )

        val output =
            atomicFile.startWrite()

        try {
            output.write(envelope)
            atomicFile.finishWrite(output)
        } catch (e: Exception) {
            atomicFile.failWrite(output)
            throw e
        }
    }

    fun restore(): List<RouterCloudSessionCookie> {
        check(hasSavedSession()) {
            "Brak zapisanej sesji synchronizacji w tle."
        }

        val envelope =
            readEnvelope()

        require(
            envelope.optInt(
                "version",
                -1,
            ) == FORMAT_VERSION,
        ) {
            "Nieobsługiwana wersja zapisanej sesji synchronizacji."
        }

        val iv =
            Base64.decode(
                envelope.getString("iv"),
                Base64.NO_WRAP,
            )

        val ciphertext =
            Base64.decode(
                envelope.getString("ciphertext"),
                Base64.NO_WRAP,
            )

        val cipher =
            Cipher.getInstance(
                TRANSFORMATION,
            ).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateKey(),
                    GCMParameterSpec(
                        GCM_TAG_LENGTH_BITS,
                        iv,
                    ),
                )
            }

        val plaintext =
            cipher.doFinal(ciphertext)

        return decodeCookies(plaintext)
    }

    fun clear() {
        atomicFile.delete()
    }

    fun resetKeyAndSession() {
        clear()

        val keyStore = keyStore()

        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun readEnvelope(): JSONObject {
        val text =
            atomicFile
                .openRead()
                .bufferedReader(
                    StandardCharsets.UTF_8,
                )
                .use {
                    it.readText()
                }

        return JSONObject(text)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore =
            keyStore()

        (keyStore.getKey(
            KEY_ALIAS,
            null,
        ) as? SecretKey)
            ?.let {
                return it
            }

        val keyGenerator =
            KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE,
            )

        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or
                    KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(
                    KeyProperties.BLOCK_MODE_GCM,
                )
                .setEncryptionPaddings(
                    KeyProperties.ENCRYPTION_PADDING_NONE,
                )
                .setUserAuthenticationRequired(false)
                .build()
        )

        return keyGenerator.generateKey()
    }

    private fun keyStore(): KeyStore =
        KeyStore
            .getInstance(
                ANDROID_KEYSTORE,
            )
            .apply {
                load(null)
            }

    private fun encodeCookies(
        cookies: List<RouterCloudSessionCookie>,
    ): ByteArray {
        val array =
            JSONArray()

        cookies.forEach { cookie ->
            array.put(
                JSONObject()
                    .put(
                        "name",
                        cookie.name,
                    )
                    .put(
                        "value",
                        cookie.value,
                    )
                    .put(
                        "expiresAt",
                        cookie.expiresAt,
                    )
                    .put(
                        "domain",
                        cookie.domain,
                    )
                    .put(
                        "path",
                        cookie.path,
                    )
                    .put(
                        "secure",
                        cookie.secure,
                    )
                    .put(
                        "httpOnly",
                        cookie.httpOnly,
                    )
                    .put(
                        "hostOnly",
                        cookie.hostOnly,
                    )
                    .put(
                        "persistent",
                        cookie.persistent,
                    )
            )
        }

        return JSONObject()
            .put(
                "cookies",
                array,
            )
            .toString()
            .toByteArray(
                StandardCharsets.UTF_8,
            )
    }

    private fun decodeCookies(
        plaintext: ByteArray,
    ): List<RouterCloudSessionCookie> {
        val root =
            JSONObject(
                plaintext.toString(
                    StandardCharsets.UTF_8,
                )
            )

        val array =
            root.getJSONArray(
                "cookies",
            )

        return buildList {
            for (
                index in
                0 until array.length()
            ) {
                val cookie =
                    array.getJSONObject(
                        index,
                    )

                add(
                    RouterCloudSessionCookie(
                        name =
                            cookie.getString(
                                "name",
                            ),
                        value =
                            cookie.getString(
                                "value",
                            ),
                        expiresAt =
                            cookie.getLong(
                                "expiresAt",
                            ),
                        domain =
                            cookie.getString(
                                "domain",
                            ),
                        path =
                            cookie.getString(
                                "path",
                            ),
                        secure =
                            cookie.getBoolean(
                                "secure",
                            ),
                        httpOnly =
                            cookie.getBoolean(
                                "httpOnly",
                            ),
                        hostOnly =
                            cookie.getBoolean(
                                "hostOnly",
                            ),
                        persistent =
                            cookie.getBoolean(
                                "persistent",
                            ),
                    )
                )
            }
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE =
            "AndroidKeyStore"

        const val KEY_ALIAS =
            "routercloud.background.session.v1"

        const val SESSION_FILE =
            "routercloud-background-session-v1.json"

        const val TRANSFORMATION =
            "AES/GCM/NoPadding"

        const val GCM_TAG_LENGTH_BITS =
            128

        const val FORMAT_VERSION =
            1
    }
}
