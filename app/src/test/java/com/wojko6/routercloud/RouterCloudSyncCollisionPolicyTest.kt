package com.wojko6.routercloud

import org.junit.Assert.assertEquals
import org.junit.Test

class RouterCloudSyncCollisionPolicyTest {

    @Test
    fun untrackedRemoteFileMustNotBeOverwritten() {
        assertEquals(
            RouterCloudSyncTargetAction.CONFLICT,
            decideRouterCloudSyncTargetAction(
                previous = null,
                remoteEntryExists = true,
            ),
        )
    }

    @Test
    fun newFileCanBeUploaded() {
        assertEquals(
            RouterCloudSyncTargetAction.UPLOAD,
            decideRouterCloudSyncTargetAction(
                previous = null,
                remoteEntryExists = false,
            ),
        )
    }

    @Test
    fun trackedExistingFileCanEnterReplacementFlow() {
        val previous = RouterCloudSyncFingerprint(
            size = 1024L,
            modifiedAt = 123456L,
        )

        assertEquals(
            RouterCloudSyncTargetAction.REPLACE,
            decideRouterCloudSyncTargetAction(
                previous = previous,
                remoteEntryExists = true,
            ),
        )
    }

    @Test
    fun missingRemoteFileCanBeUploadedAgain() {
        val previous = RouterCloudSyncFingerprint(
            size = 1024L,
            modifiedAt = 123456L,
        )

        assertEquals(
            RouterCloudSyncTargetAction.UPLOAD,
            decideRouterCloudSyncTargetAction(
                previous = previous,
                remoteEntryExists = false,
            ),
        )
    }
}
