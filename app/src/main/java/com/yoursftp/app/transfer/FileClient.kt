package com.yoursftp.app.transfer

import com.yoursftp.app.data.RemoteFile

typealias ProgressListener = (bytesTransferred: Long, totalBytes: Long) -> Unit

/**
 * Abstraksi klien transfer file. Semua operasi memblok (blocking) dan harus
 * dipanggil dari dispatcher IO.
 */
interface FileClient {
    fun connect()
    fun disconnect()
    val isConnected: Boolean

    /** Daftar isi direktori. */
    fun list(path: String): List<RemoteFile>

    /** Unduh isi file sebagai byte. */
    fun download(path: String, progressListener: ProgressListener? = null): ByteArray

    /** Unggah/timpa file dengan byte. */
    fun upload(path: String, data: ByteArray, progressListener: ProgressListener? = null)

    fun rename(from: String, to: String)
    fun deleteFile(path: String)
    fun deleteDirectory(path: String)
    fun makeDirectory(path: String)
}
