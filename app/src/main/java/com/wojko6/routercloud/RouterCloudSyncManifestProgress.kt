package com.wojko6.routercloud

internal class RouterCloudSyncManifestProgress(
    initial: Map<String, RouterCloudSyncFingerprint>,
    private val persist: (
        Map<String, RouterCloudSyncFingerprint>
    ) -> Unit,
) {
    private var accepted = initial.toMap()

    fun checkpoint(
        path: String,
        fingerprint: RouterCloudSyncFingerprint,
    ) {
        require(path.isNotBlank())

        val next = accepted + (path to fingerprint)

        persist(next)
        accepted = next
    }

    fun complete(
        files: Map<String, RouterCloudSyncFingerprint>,
    ) {
        val next = files.toMap()

        persist(next)
        accepted = next
    }

    fun snapshot(): Map<String, RouterCloudSyncFingerprint> =
        accepted.toMap()
}
