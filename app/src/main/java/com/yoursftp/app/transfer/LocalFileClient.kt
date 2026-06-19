package com.yoursftp.app.transfer

import com.yoursftp.app.data.RemoteFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class LocalFileClient : FileClient {
    override fun connect() {}
    override fun disconnect() {}
    override val isConnected: Boolean get() = true

    override fun list(path: String): List<RemoteFile> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = dir.listFiles() ?: return emptyList()
        return files
            .map {
                RemoteFile(
                    name = it.name,
                    path = it.absolutePath,
                    isDirectory = it.isDirectory,
                    size = if (it.isDirectory) 0L else it.length(),
                    lastModified = it.lastModified()
                )
            }
            .sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override fun download(path: String, progressListener: ProgressListener?): ByteArray {
        val file = File(path)
        val totalBytes = file.length()
        val out = ByteArrayOutputStream()
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = 0L
            while (input.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                progressListener?.invoke(totalRead, totalBytes)
            }
        }
        return out.toByteArray()
    }

    override fun upload(path: String, data: ByteArray, progressListener: ProgressListener?) {
        val file = File(path)
        file.parentFile?.mkdirs()
        val totalBytes = data.size.toLong()
        file.outputStream().use { output ->
            val buffer = ByteArray(8192)
            val input = ByteArrayInputStream(data)
            var bytesRead: Int
            var totalWritten = 0L
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalWritten += bytesRead
                progressListener?.invoke(totalWritten, totalBytes)
            }
        }
    }

    override fun rename(from: String, to: String) {
        val f = File(from)
        val t = File(to)
        f.renameTo(t)
    }

    override fun deleteFile(path: String) {
        File(path).delete()
    }

    override fun deleteDirectory(path: String) {
        File(path).deleteRecursively()
    }

    override fun makeDirectory(path: String) {
        File(path).mkdirs()
    }
}
