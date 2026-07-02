package com.yoursftp.app.ota

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class OtaViewModel(application: Application) : AndroidViewModel(application) {

    private val updater = OtaUpdater(application)
    val state: StateFlow<OtaState> = updater.state

    val currentVersion: String by lazy {
        try {
            val pInfo = application.packageManager.getPackageInfo(application.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            updater.checkForUpdate(currentVersion)
        }
    }

    fun downloadAndInstall(downloadUrl: String) {
        viewModelScope.launch {
            updater.downloadAndInstall(downloadUrl)
        }
    }

    fun triggerInstall(apkFile: File) {
        updater.triggerInstall(apkFile)
    }

    fun resetState() {
        updater.resetState()
    }
}
