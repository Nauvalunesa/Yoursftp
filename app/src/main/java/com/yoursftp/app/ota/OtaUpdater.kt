package com.yoursftp.app.ota

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class OtaState {
    object Idle : OtaState()
    object Checking : OtaState()
    object UpToDate : OtaState()
    data class UpdateAvailable(val version: String, val changelog: String, val downloadUrl: String) : OtaState()
    data class Downloading(val progress: Float) : OtaState()
    data class ReadyToInstall(val apkFile: File) : OtaState()
    data class Error(val message: String) : OtaState()
}

class OtaUpdater(private val context: Context) {

    private val _state = MutableStateFlow<OtaState>(OtaState.Idle)
    val state = _state.asStateFlow()

    // Configurable repo
    private val repoOwner = "Nauvalunesa"
    private val repoName = "Yoursftp"

    fun resetState() {
        _state.value = OtaState.Idle
    }

    suspend fun checkForUpdate(currentVersion: String) {
        _state.value = OtaState.Checking
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                if (connection.responseCode == 200) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseText)
                    val tagName = json.getString("tag_name")
                    val changelog = json.optString("body", "Tidak ada catatan rilis.")
                    
                    // Temukan asset APK
                    val assets = json.optJSONArray("assets")
                    var downloadUrl = ""
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            val name = asset.getString("name")
                            if (name.endsWith(".apk")) {
                                downloadUrl = asset.getString("browser_download_url")
                                break
                            }
                        }
                    }

                    if (isNewerVersion(currentVersion, tagName)) {
                        if (downloadUrl.isNotEmpty()) {
                            _state.value = OtaState.UpdateAvailable(
                                version = tagName,
                                changelog = changelog,
                                downloadUrl = downloadUrl
                            )
                        } else {
                            _state.value = OtaState.Error("Update tersedia tetapi file APK tidak ditemukan di rilis.")
                        }
                    } else {
                        _state.value = OtaState.UpToDate
                    }
                } else {
                    _state.value = OtaState.Error("Gagal memeriksa update (HTTP ${connection.responseCode})")
                }
            } catch (e: Exception) {
                _state.value = OtaState.Error(e.message ?: "Koneksi gagal saat memeriksa update")
            }
        }
    }

    suspend fun downloadAndInstall(downloadUrl: String) {
        _state.value = OtaState.Downloading(0f)
        withContext(Dispatchers.IO) {
            try {
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK || 
                    connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    connection.responseCode == HttpURLConnection.HTTP_MOVED_PERM) {
                    
                    // Tangani redirect manual jika HttpURLConnection tidak menanganinya secara otomatis
                    var redirectUrl = downloadUrl
                    var conn = connection
                    var status = conn.responseCode
                    var redirectCount = 0
                    while ((status == HttpURLConnection.HTTP_MOVED_TEMP || 
                            status == HttpURLConnection.HTTP_MOVED_PERM || 
                            status == 307 || status == 308) && redirectCount < 5) {
                        redirectUrl = conn.getHeaderField("Location")
                        conn = URL(redirectUrl).openConnection() as HttpURLConnection
                        conn.connectTimeout = 15000
                        conn.readTimeout = 15000
                        status = conn.responseCode
                        redirectCount++
                    }

                    val totalSize = conn.contentLength.toLong()
                    val apkFile = File(context.cacheDir, "update.apk")
                    if (apkFile.exists()) apkFile.delete()

                    conn.inputStream.use { input ->
                        FileOutputStream(apkFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytesRead = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (totalSize > 0) {
                                    val progress = totalBytesRead.toFloat() / totalSize.toFloat()
                                    _state.value = OtaState.Downloading(progress)
                                }
                            }
                        }
                    }

                    _state.value = OtaState.ReadyToInstall(apkFile)
                } else {
                    _state.value = OtaState.Error("Gagal mengunduh APK (HTTP ${connection.responseCode})")
                }
            } catch (e: Exception) {
                _state.value = OtaState.Error(e.message ?: "Gagal mengunduh update")
            }
        }
    }

    fun triggerInstall(apkFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _state.value = OtaState.Error("Gagal membuka installer APK: ${e.message}")
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val cleanCurrent = current.removePrefix("v").trim()
        val cleanLatest = latest.removePrefix("v").trim()
        val currParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: -1 }
        val lateParts = cleanLatest.split(".").map { it.toIntOrNull() ?: -1 }
        
        // If either version name is non-semantic (contains letters, e.g. "nightly"),
        // check if they are simply different.
        if (currParts.any { it == -1 } || lateParts.any { it == -1 }) {
            return !cleanCurrent.equals(cleanLatest, ignoreCase = true)
        }
        
        val maxLen = maxOf(currParts.size, lateParts.size)
        for (i in 0 until maxLen) {
            val currVal = currParts.getOrElse(i) { 0 }
            val lateVal = lateParts.getOrElse(i) { 0 }
            if (lateVal > currVal) return true
            if (currVal > lateVal) return false
        }
        return false
    }
}
