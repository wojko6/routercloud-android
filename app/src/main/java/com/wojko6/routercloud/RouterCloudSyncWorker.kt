package com.wojko6.routercloud

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.wojko6.routercloud.network.RouterCloudAuthException
import com.wojko6.routercloud.network.RouterCloudClient
import com.wojko6.routercloud.network.RouterCloudHttpException
import com.wojko6.routercloud.security.RouterCloudBackgroundSessionStore
import java.io.IOException

internal class RouterCloudSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(
    appContext,
    workerParams,
) {
    override fun doWork(): Result {
        Log.i(TAG, "START")

        val syncStore =
            RouterCloudSyncStore(
                applicationContext,
            )

        if (!syncStore.isBackgroundEnabled()) {
            Log.i(
                TAG,
                "BACKGROUND_DISABLED",
            )

            return Result.success()
        }

        val config =
            syncStore.load()
                ?: run {
                    Log.w(TAG, "NO_SYNC_CONFIG")

                    return Result.failure(
                        output(
                            "Brak konfiguracji synchronizacji.",
                        )
                    )
                }

        val sessionStore =
            RouterCloudBackgroundSessionStore(
                applicationContext,
            )

        val notifications =
            RouterCloudSyncNotifications(
                applicationContext,
            )

        if (!sessionStore.hasSavedSession()) {
            Log.w(TAG, "NO_BACKGROUND_SESSION")

            notifications.notifyActionRequired(
                "Otwórz RouterCloud i zaloguj się ponownie."
            )

            return Result.failure(
                output(
                    "Brak sesji dla synchronizacji w tle.",
                )
            )
        }

        val client =
            RouterCloudClient()

        return try {
            Log.i(TAG, "SESSION_RESTORE_BEGIN")

            val cookies =
                sessionStore.restore()

            Log.i(TAG, "SESSION_RESTORE_OK")

            client.importSessionCookies(
                cookies,
            )

            Log.i(TAG, "SYNC_BEGIN")

            val result =
                RouterCloudSyncEngine(
                    context = applicationContext,
                    client = client,
                ).sync(config)

            Log.i(
                TAG,
                "SYNC_OK uploaded=${result.uploadedFiles} " +
                    "skipped=${result.skippedFiles} " +
                    "scanned=${result.scannedFiles}",
            )

            syncStore.markSuccessfulSync()

            notifications.notifyUploaded(
                result.uploadedFiles,
            )

            Log.i(TAG, "SUCCESS")

            Result.success(
                Data.Builder()
                    .putInt(
                        KEY_SCANNED_FILES,
                        result.scannedFiles,
                    )
                    .putInt(
                        KEY_UPLOADED_FILES,
                        result.uploadedFiles,
                    )
                    .putInt(
                        KEY_SKIPPED_FILES,
                        result.skippedFiles,
                    )
                    .putInt(
                        KEY_CREATED_DIRECTORIES,
                        result.createdDirectories,
                    )
                    .build()
            )
        } catch (e: RouterCloudAuthException) {
            Log.w(TAG, "AUTH_EXPIRED")

            sessionStore.clear()

            notifications.notifyActionRequired(
                "Sesja wygasła. Otwórz RouterCloud i zaloguj się ponownie."
            )

            Result.failure(
                output(
                    "Sesja RouterCloud wygasła. " +
                        "Otwórz aplikację i zaloguj się ponownie.",
                )
            )
        } catch (e: RouterCloudHttpException) {
            Log.w(
                TAG,
                "HTTP_ERROR status=${e.statusCode}",
            )

            if (
                e.statusCode == 408 ||
                e.statusCode == 429 ||
                e.statusCode >= 500
            ) {
                Log.i(TAG, "HTTP_RETRY")
                Result.retry()
            } else {
                notifications.notifyActionRequired(
                    "Synchronizacja zatrzymana: błąd HTTP ${e.statusCode}."
                )

                Result.failure(
                    output(
                        "RouterCloud zwrócił błąd HTTP ${e.statusCode}.",
                    )
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SAF_PERMISSION")

            notifications.notifyActionRequired(
                "RouterCloud utracił dostęp do folderu synchronizacji."
            )

            Result.failure(
                output(
                    "Brak dostępu do wybranego folderu. " +
                        "Wybierz folder ponownie w RouterCloud.",
                )
            )
        } catch (e: IOException) {
            Log.w(
                TAG,
                "IO_RETRY type=${e.javaClass.simpleName}",
            )

            Result.retry()
        } catch (e: Exception) {
            Log.e(
                TAG,
                "UNEXPECTED_FAILURE type=${e.javaClass.simpleName}",
            )

            notifications.notifyActionRequired(
                "Synchronizacja RouterCloud nie powiodła się."
            )

            Result.failure(
                output(
                    "Synchronizacja w tle nie powiodła się.",
                )
            )
        }
    }

    private fun output(
        message: String,
    ): Data =
        Data.Builder()
            .putString(
                KEY_MESSAGE,
                message,
            )
            .build()

    internal companion object {
        private const val TAG =
            "RouterCloudSyncWorker"

        const val KEY_MESSAGE =
            "message"

        const val KEY_SCANNED_FILES =
            "scanned_files"

        const val KEY_UPLOADED_FILES =
            "uploaded_files"

        const val KEY_SKIPPED_FILES =
            "skipped_files"

        const val KEY_CREATED_DIRECTORIES =
            "created_directories"
    }
}
