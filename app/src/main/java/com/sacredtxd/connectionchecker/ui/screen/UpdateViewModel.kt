package com.sacredtxd.connectionchecker.ui.screen

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sacredtxd.connectionchecker.BuildConfig
import com.sacredtxd.connectionchecker.data.UpdateComparator
import com.sacredtxd.connectionchecker.data.UpdateStatus
import com.sacredtxd.connectionchecker.util.ApkInstaller
import com.sacredtxd.connectionchecker.util.DownloadResult
import com.sacredtxd.connectionchecker.util.ManifestResult
import com.sacredtxd.connectionchecker.util.UpdateChecker
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdateViewModel(
    private val checker: UpdateChecker = UpdateChecker(BuildConfig.UPDATE_MANIFEST_URL),
    private val installedVersionCode: Int = BuildConfig.VERSION_CODE,
) : ViewModel() {

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private var downloaded: File? = null

    val installedVersionName: String = BuildConfig.VERSION_NAME

    fun check() {
        _status.value = UpdateStatus.Checking
        viewModelScope.launch {
            _status.value = when (val result = checker.fetch()) {
                is ManifestResult.Failed -> UpdateStatus.Failed(result.reason)
                is ManifestResult.Found ->
                    if (UpdateComparator.isNewer(result.manifest, installedVersionCode)) {
                        UpdateStatus.Available(result.manifest)
                    } else {
                        UpdateStatus.UpToDate
                    }
            }
        }
    }

    fun download(context: Context) {
        val manifest = when (val current = _status.value) {
            is UpdateStatus.Available -> current.manifest
            is UpdateStatus.Failed, is UpdateStatus.Idle -> return
            else -> return
        }

        _status.value = UpdateStatus.Downloading(manifest, 0)
        viewModelScope.launch {
            val result = ApkInstaller.download(context, manifest.apkUrl) { percent ->
                _status.value = UpdateStatus.Downloading(manifest, percent)
            }
            _status.value = when (result) {
                is DownloadResult.Downloaded -> {
                    downloaded = result.file
                    UpdateStatus.ReadyToInstall(manifest)
                }
                is DownloadResult.Failed -> UpdateStatus.Failed(result.reason)
            }
        }
    }

    /** @return false when the app still needs the install permission. */
    fun install(context: Context): Boolean {
        val file = downloaded ?: return true
        if (!ApkInstaller.canRequestInstall(context)) return false
        ApkInstaller.install(context, file)
        return true
    }

    fun dismiss() {
        _status.value = UpdateStatus.Idle
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            UpdateViewModel() as T
    }
}
