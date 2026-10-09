package com.wojko6.routercloud

internal enum class RouterCloudSyncTargetAction {
    UPLOAD,
    REPLACE,
    CONFLICT,
}

internal fun decideRouterCloudSyncTargetAction(
    previous: RouterCloudSyncFingerprint?,
    remoteEntryExists: Boolean,
): RouterCloudSyncTargetAction = when {
    !remoteEntryExists ->
        RouterCloudSyncTargetAction.UPLOAD

    previous == null ->
        RouterCloudSyncTargetAction.CONFLICT

    else ->
        RouterCloudSyncTargetAction.CONFLICT
}
