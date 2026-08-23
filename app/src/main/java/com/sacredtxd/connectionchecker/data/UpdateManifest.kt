package com.sacredtxd.connectionchecker.data

import kotlinx.serialization.Serializable

/** The published description of the newest build, fetched from the release. */
@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String = "",
)

/** What a check concluded, so the UI can say something specific. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val manifest: UpdateManifest) : UpdateStatus
    data class Downloading(val manifest: UpdateManifest, val percent: Int) : UpdateStatus
    data class ReadyToInstall(val manifest: UpdateManifest) : UpdateStatus
    data class Failed(val reason: String) : UpdateStatus
}

object UpdateComparator {
    /**
     * A build is offered only when it outranks the installed one. Equal or lower
     * version codes are ignored, so a rolled-back release cannot loop the user
     * through a downgrade the installer would reject anyway.
     */
    fun isNewer(manifest: UpdateManifest, installedVersionCode: Int): Boolean =
        manifest.versionCode > installedVersionCode
}
