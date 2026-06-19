package com.yoursftp.app.transfer

import com.yoursftp.app.data.Connection
import com.yoursftp.app.data.Protocol
import com.yoursftp.app.data.RemoteFile
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Klien FTP / FTPS berbasis Apache Commons Net. */
class FtpFileClient(private val conn: Connection) : FileClient {

    private val client: FTPClient =
        if (conn.protocol == Protocol.FTPS) FTPSClient(false) else FTPClient()

    override val isConnected: Boolean get() = client.isConnected

    override fun connect() {
        client.connectTimeout = 15_000
        client.connect(conn.host, conn.port)
        val reply = client.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
            client.disconnect()
            throw IOException("Server menolak koneksi: $reply")
        }
        if (!client.login(conn.username, conn.password)) {
            client.disconnect()
            throw IOException("Login gagal untuk ${conn.username}")
        }
        if (conn.protocol == Protocol.FTPS) {
            (client as FTPSClient).execPBSZ(0)
            client.execPROT("P")
        }
        if (conn.passiveMode) client.enterLocalPassiveMode()
        client.setFileType(FTP.BINARY_FILE_TYPE)
        client.controlEncoding = "UTF-8"
    }

    override fun disconnect() {
        runCatching {
            if (client.isConnected) {
                client.logout()
                client.disconnect()
            }
        }
    }

    override fun list(path: String): List<RemoteFile> {
        val files = client.listFiles(path) ?: emptyArray()
        return files
            .filter { it.name != "." && it.name != ".." }
            .map {
                RemoteFile(
                    name = it.name,
                    path = joinPath(path, it.name),
                    isDirectory = it.isDirectory,
                    size = it.size,
                    lastModified = it.timestamp?.timeInMillis ?: 0L
                )
            }
            .sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override fun download(path: String, progressListener: ProgressListener?): ByteArray {
        var totalBytes = 0L
        runCatching {
            val f = client.mlistFile(path)
            if (f != null) totalBytes = f.size
        }
        
        val out = if (totalBytes > 0) ByteArrayOutputStream(totalBytes.toInt()) else ByteArrayOutputStream()
        val input = client.retrieveFileStream(path) ?: throw IOException("Gagal mengunduh $path (${client.replyString})")
        try {
            val buffer = ByteArray(65536) // 64 KB optimized buffer size
            var bytesRead: Int
            var totalRead = 0L
            while (input.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                progressListener?.invoke(totalRead, totalBytes)
            }
        } finally {
            input.close()
            if (!client.completePendingCommand()) {
                throw IOException("Gagal menyelesaikan unduhan $path (${client.replyString})")
            }
        }
        return out.toByteArray()
    }

    override fun upload(path: String, data: ByteArray, progressListener: ProgressListener?): Unit {
        val totalBytes = data.size.toLong()
        val output = client.storeFileStream(path) ?: throw IOException("Gagal mengunggah $path (${client.replyString})")
        try {
            val buffer = ByteArray(8192)
            val input = ByteArrayInputStream(data)
            var bytesRead: Int
            var totalWritten = 0L
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                totalWritten += bytesRead
                progressListener?.invoke(totalWritten, totalBytes)
            }
        } finally {
            output.close()
            if (!client.completePendingCommand()) {
                throw IOException("Gagal menyelesaikan unggahan $path (${client.replyString})")
            }
        }
    }

    override fun rename(from: String, to: String) {
        if (!client.rename(from, to)) throw IOException("Gagal rename: ${client.replyString}")
    }

    override fun deleteFile(path: String) {
        if (!client.deleteFile(path)) throw IOException("Gagal hapus file: ${client.replyString}")
    }

    override fun deleteDirectory(path: String) {
        if (!client.removeDirectory(path)) throw IOException("Gagal hapus folder: ${client.replyString}")
    }

    override fun makeDirectory(path: String) {
        if (!client.makeDirectory(path)) throw IOException("Gagal buat folder: ${client.replyString}")
    }
}
