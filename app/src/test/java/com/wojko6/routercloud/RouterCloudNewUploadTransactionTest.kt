package com.wojko6.routercloud

import com.wojko6.routercloud.network.RouterCloudHttpException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.IOException

class RouterCloudNewUploadTransactionTest {

    @Test
    fun successfulUploadDoesNotRunCleanup() {
        val events = mutableListOf<String>()

        performRouterCloudNewUpload(
            upload = { events.add("PUT") },
            promote = { events.add("MOVE") },
            cleanup = { events.add("DELETE") },
        )

        assertEquals(listOf("PUT", "MOVE"), events)
    }

    @Test
    fun moveConflictRunsCleanupAndRethrows409() {
        val events = mutableListOf<String>()
        val conflict = RouterCloudHttpException(409, "Conflict")

        val thrown = runCatching {
            performRouterCloudNewUpload(
                upload = { events.add("PUT") },
                promote = {
                    events.add("MOVE")
                    throw conflict
                },
                cleanup = { events.add("DELETE") },
            )
        }.exceptionOrNull()

        assertSame(conflict, thrown)
        assertEquals(
            listOf("PUT", "MOVE", "DELETE"),
            events,
        )
    }

    @Test
    fun uploadFailureAlsoRunsCleanup() {
        val events = mutableListOf<String>()
        val failure = IOException("Connection lost")

        val thrown = runCatching {
            performRouterCloudNewUpload(
                upload = {
                    events.add("PUT")
                    throw failure
                },
                promote = { events.add("MOVE") },
                cleanup = { events.add("DELETE") },
            )
        }.exceptionOrNull()

        assertSame(failure, thrown)
        assertEquals(listOf("PUT", "DELETE"), events)
    }

    @Test
    fun missingTemporaryFileDuringCleanupIsAccepted() {
        val conflict = RouterCloudHttpException(409, "Conflict")

        val thrown = runCatching {
            performRouterCloudNewUpload(
                upload = {},
                promote = { throw conflict },
                cleanup = {
                    throw RouterCloudHttpException(404, "Not Found")
                },
            )
        }.exceptionOrNull()

        assertSame(conflict, thrown)
        assertEquals(0, conflict.suppressed.size)
    }

    @Test
    fun cleanupFailurePreservesOriginalError() {
        val conflict = RouterCloudHttpException(409, "Conflict")
        val cleanupError = IOException("Cleanup failed")
        val reported = mutableListOf<Exception>()

        val thrown = runCatching {
            performRouterCloudNewUpload(
                upload = {},
                promote = { throw conflict },
                cleanup = { throw cleanupError },
                onCleanupFailure = { reported.add(it) },
            )
        }.exceptionOrNull()

        assertSame(conflict, thrown)
        assertEquals(1, conflict.suppressed.size)
        assertSame(cleanupError, conflict.suppressed[0])
        assertEquals(listOf(cleanupError), reported)
    }
}
