package com.wojko6.routercloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RouterCloudSyncManifestProgressTest {

    private val first = RouterCloudSyncFingerprint(100L, 1000L)
    private val second = RouterCloudSyncFingerprint(200L, 2000L)
    private val older = RouterCloudSyncFingerprint(300L, 3000L)

    @Test
    fun checkpointPreservesPreviouslyTrackedFiles() {
        var persisted = mapOf("old.txt" to older)

        val progress = RouterCloudSyncManifestProgress(
            initial = persisted,
            persist = { persisted = it },
        )

        progress.checkpoint("first.txt", first)

        assertEquals(
            mapOf(
                "old.txt" to older,
                "first.txt" to first,
            ),
            persisted,
        )
    }

    @Test
    fun interruptedRunDoesNotMarkUnsentFiles() {
        var persisted = emptyMap<String, RouterCloudSyncFingerprint>()

        val progress = RouterCloudSyncManifestProgress(
            initial = persisted,
            persist = { persisted = it },
        )

        progress.checkpoint("first.txt", first)

        assertTrue("first.txt" in persisted)
        assertTrue("second.txt" !in persisted)
    }

    @Test
    fun restartedRunRestoresCheckpoint() {
        var persisted = emptyMap<String, RouterCloudSyncFingerprint>()

        val firstRun = RouterCloudSyncManifestProgress(
            initial = persisted,
            persist = { persisted = it },
        )

        firstRun.checkpoint("first.txt", first)

        val secondRun = RouterCloudSyncManifestProgress(
            initial = persisted,
            persist = { persisted = it },
        )

        secondRun.checkpoint("second.txt", second)

        assertEquals(
            mapOf(
                "first.txt" to first,
                "second.txt" to second,
            ),
            persisted,
        )
    }

    @Test
    fun failedPersistenceDoesNotAdvanceCheckpoint() {
        val progress = RouterCloudSyncManifestProgress(
            initial = mapOf("old.txt" to older),
            persist = {
                throw IOException("Storage failure")
            },
        )

        val error = runCatching {
            progress.checkpoint("first.txt", first)
        }.exceptionOrNull()

        assertTrue(error is IOException)

        assertEquals(
            mapOf("old.txt" to older),
            progress.snapshot(),
        )
    }

    @Test
    fun completedRunPrunesOldEntries() {
        var persisted = mapOf("deleted.txt" to older)

        val progress = RouterCloudSyncManifestProgress(
            initial = persisted,
            persist = { persisted = it },
        )

        progress.checkpoint("first.txt", first)

        assertTrue("deleted.txt" in persisted)

        progress.complete(
            mapOf("first.txt" to first),
        )

        assertEquals(
            mapOf("first.txt" to first),
            persisted,
        )
    }
}
