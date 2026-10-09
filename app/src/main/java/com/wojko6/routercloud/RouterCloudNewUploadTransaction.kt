package com.wojko6.routercloud

import com.wojko6.routercloud.network.RouterCloudHttpException

internal fun performRouterCloudNewUpload(
    upload: () -> Unit,
    promote: () -> Unit,
    cleanup: () -> Unit,
    onCleanupFailure: (Exception) -> Unit = {},
) {
    try {
        upload()
        promote()
    } catch (failure: Exception) {
        try {
            cleanup()
        } catch (cleanupFailure: Exception) {
            val alreadyAbsent =
                cleanupFailure is RouterCloudHttpException &&
                    cleanupFailure.statusCode == 404

            if (!alreadyAbsent) {
                failure.addSuppressed(cleanupFailure)
                runCatching {
                    onCleanupFailure(cleanupFailure)
                }
            }
        }

        throw failure
    }
}
