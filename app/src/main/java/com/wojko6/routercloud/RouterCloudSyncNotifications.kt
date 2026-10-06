package com.wojko6.routercloud

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal class RouterCloudSyncNotifications(
    context: Context,
) {
    private val appContext =
        context.applicationContext

    init {
        createChannel()
    }

    fun notifyUploaded(
        uploadedFiles: Int,
    ) {
        if (uploadedFiles <= 0) {
            return
        }

        notify(
            id = NOTIFICATION_ID_SUCCESS,
            title = "RouterCloud",
            text =
                if (uploadedFiles == 1) {
                    "Synchronizacja zakończona. Wysłano 1 plik."
                } else {
                    "Synchronizacja zakończona. Wysłano $uploadedFiles plików."
                },
        )
    }

    fun notifyActionRequired(
        message: String,
    ) {
        notify(
            id = NOTIFICATION_ID_ERROR,
            title = "RouterCloud — synchronizacja",
            text = message,
        )
    }

    private fun notify(
        id: Int,
        title: String,
        text: String,
    ) {
        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification =
            NotificationCompat.Builder(
                appContext,
                CHANNEL_ID,
            )
                .setSmallIcon(
                    android.R.drawable.stat_sys_upload_done,
                )
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(text)
                )
                .setAutoCancel(true)
                .setPriority(
                    NotificationCompat.PRIORITY_DEFAULT,
                )
                .build()

        runCatching {
            NotificationManagerCompat
                .from(appContext)
                .notify(
                    id,
                    notification,
                )
        }
    }

    private fun createChannel() {
        if (
            Build.VERSION.SDK_INT <
                Build.VERSION_CODES.O
        ) {
            return
        }

        val manager =
            appContext.getSystemService(
                NotificationManager::class.java,
            )

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Synchronizacja RouterCloud",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description =
                    "Informacje o synchronizacji plików RouterCloud"
            }

        manager.createNotificationChannel(
            channel,
        )
    }

    private companion object {
        const val CHANNEL_ID =
            "routercloud_sync"

        const val NOTIFICATION_ID_SUCCESS =
            1001

        const val NOTIFICATION_ID_ERROR =
            1002
    }
}
