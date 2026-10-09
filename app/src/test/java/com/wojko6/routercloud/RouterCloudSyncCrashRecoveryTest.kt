package com.wojko6.routercloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RouterCloudSyncCrashRecoveryTest {

    private val fingerprint =
        RouterCloudSyncFingerprint(1024L, 123456L)

    @Test
    fun failureOnSecondFileKeepsFirstCheckpoint() {
        var stored =
            emptyMap<String, RouterCloudSyncFingerprint>()

        val progress = RouterCloudSyncManifestProgress(
            initial = stored,
            persist = { stored = it },
        )

        progress.checkpoint("first.txt", fingerprint)

        val failure = runCatching {
            performRouterCloudNewUpload(
                upload = { throw IOException("Network lost") },
                promote = {},
                cleanup = {},
            )
        }.exceptionOrNull()

        assertTrue(failure is IOException)

        val restarted = RouterCloudSyncManifestProgress(
            initial = stored,
            persist = { stored = it },
        )

        assertEquals(
            fingerprint,
            restarted.snapshot()["first.txt"],
        )
        assertFalse("second.txt" in restarted.snapshot())
    }

    @Test
    fun promotionBeforeCheckpointBlocksBlindRetry() {
        var remoteExists = false

        performRouterCloudNewUpload(
            upload = {},
            promote = { remoteExists = true },
            cleanup = {},
        )

        // Symulacja awarii przed checkpoint().
        assertTrue(remoteExists)

        assertEquals(
            RouterCloudSyncTargetAction.CONFLICT,
            decideRouterCloudSyncTargetAction(
                previous = null,
                remoteEntryExists = remoteExists,
            ),
        )
    }

    @Test
    fun failedCheckpointAfterPromotionBlocksBlindRetry() {
        var remoteExists = false

        val progress = RouterCloudSyncManifestProgress(
            initial = emptyMap(),
            persist = {
                throw IOException("Storage unavailable")
            },
        )

        performRouterCloudNewUpload(
            upload = {},
            promote = { remoteExists = true },
            cleanup = {},
        )

        val error = runCatching {
            progress.checkpoint("test.txt", fingerprint)
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(progress.snapshot().isEmpty())

        assertEquals(
            RouterCloudSyncTargetAction.CONFLICT,
            decideRouterCloudSyncTargetAction(
                previous = progress.snapshot()["test.txt"],
                remoteEntryExists = remoteExists,
            ),
        )
    }
}
