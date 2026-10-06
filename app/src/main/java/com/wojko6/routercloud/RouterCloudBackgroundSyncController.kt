package com.wojko6.routercloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.wojko6.routercloud.network.RouterCloudSessionCookie
import com.wojko6.routercloud.security.RouterCloudBackgroundSessionStore
import java.util.concurrent.TimeUnit

internal class RouterCloudBackgroundSyncController(
    context: Context,
) {
    private val appContext =
        context.applicationContext

    private val syncStore =
        RouterCloudSyncStore(
            appContext,
        )

    private val sessionStore =
        RouterCloudBackgroundSessionStore(
            appContext,
        )

    private val workManager =
        WorkManager.getInstance(
            appContext,
        )

    fun isEnabled(): Boolean =
        syncStore.isBackgroundEnabled()

    fun enable(
        cookies: List<RouterCloudSessionCookie>,
    ) {
        require(cookies.isNotEmpty()) {
            "Brak aktywnej sesji RouterCloud."
        }

        check(syncStore.load() != null) {
            "Najpierw wybierz folder synchronizacji."
        }

        sessionStore.save(cookies)

        syncStore.setBackgroundEnabled(
            true,
        )

        schedule()
    }

    fun refreshSession(
        cookies: List<RouterCloudSessionCookie>,
    ) {
        if (
            !isEnabled() ||
            cookies.isEmpty()
        ) {
            return
        }

        sessionStore.save(cookies)
    }

    fun disable() {
        syncStore.setBackgroundEnabled(
            false,
        )

        sessionStore.clear()

        workManager.cancelUniqueWork(
            UNIQUE_PERIODIC_WORK,
        )

        workManager.cancelUniqueWork(
            UNIQUE_IMMEDIATE_WORK,
        )
    }

    fun runNow() {
        check(isEnabled()) {
            "Synchronizacja w tle jest wyłączona."
        }

        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(
                    NetworkType.CONNECTED,
                )
                .build()

        val request =
            OneTimeWorkRequestBuilder<
                RouterCloudSyncWorker
            >()
                .setConstraints(
                    constraints,
                )
                .build()

        workManager.enqueueUniqueWork(
            UNIQUE_IMMEDIATE_WORK,
            androidx.work.ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun schedule() {
        if (!isEnabled()) {
            return
        }

        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(
                    NetworkType.CONNECTED,
                )
                .build()

        val request =
            PeriodicWorkRequestBuilder<
                RouterCloudSyncWorker
            >(
                REPEAT_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            )
                .setConstraints(
                    constraints,
                )
                .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    internal companion object {
        const val UNIQUE_PERIODIC_WORK =
            "routercloud-background-sync-v1"

        const val UNIQUE_IMMEDIATE_WORK =
            "routercloud-background-sync-now-v1"

        const val REPEAT_INTERVAL_MINUTES =
            15L
    }
}
