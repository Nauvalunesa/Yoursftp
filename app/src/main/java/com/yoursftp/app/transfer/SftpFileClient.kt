package com.yoursftp.app.transfer

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.yoursftp.app.data.Connection
import com.yoursftp.app.data.RemoteFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties

/** Klien SFTP berbasis JSch (fork mwiede). */
class SftpFileClient(private val conn: Connection) : FileClient {

    private var session: Session? = null
    private var channel: ChannelSftp? = null

    override val isConnected: Boolean
        get() = channel?.isConnected == true && session?.isConnected == true

    override fun connect() {
        val jsch = JSch()
        if (!conn.privateKey.isNullOrBlank()) {
            val prvKeyBytes = conn.privateKey.toByteArray(Charsets.UTF_8)
            val passBytes = if (!conn.passphrase.isNullOrEmpty()) conn.passphrase.toByteArray(Charsets.UTF_8) else null
            jsch.addIdentity(conn.name, prvKeyBytes, null, passBytes)
        }
        val s = jsch.getSession(conn.username, conn.host, conn.port)
        if (conn.privateKey.isNullOrBlank()) {
            s.setPassword(conn.password)
        }
        val config = Properties().apply {
            put("StrictHostKeyChecking", "no")
            // Jangan simpan/cek host key (server berubah setelah reinstall VPS).
            put("CheckHostIP", "no")
            put("HashKnownHosts", "no")
            // Terima semua tipe host key umum (RSA, ECDSA, ED25519) agar tak gagal
            // saat server menawarkan tipe yang berbeda setelah reinstall.
            put("server_host_key",
                "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa")
            // Aktifkan kembali ssh-rsa (SHA-1) untuk kompatibilitas server lama.
            put("PubkeyAcceptedAlgorithms",
                "ssh-ed25519,ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256,ssh-rsa")
            put("PreferredAuthentications", "publickey,password,keyboard-interactive")
            put("TcpNoDelay", "yes")
            put("compression.s2c", "zlib@openssh.com,zlib,none")
            put("compression.c2s", "zlib@openssh.com,zlib,none")
            put("compression_level", "6")
        }
        s.setConfig(config)
        s.timeout = 15_000
        s.setServerAliveInterval(5000)
        s.setServerAliveCountMax(3)
        s.connect()
        val ch = s.openChannel("sftp") as ChannelSftp
        ch.connect()
        session = s
        channel = ch
    }

    override fun disconnect() {
        runCatching { channel?.disconnect() }
        runCatching { session?.disconnect() }
        channel = null
        session = null
    }

    private fun requireChannel(): ChannelSftp =
        channel ?: throw IllegalStateException("SFTP belum terhubung")

    @Suppress("UNCHECKED_CAST")
    override fun list(path: String): List<RemoteFile> {
        val ch = requireChannel()
        val entries = ch.ls(path) as java.util.Vector<ChannelSftp.LsEntry>
        return entries
            .filter { it.filename != "." && it.filename != ".." }
            .map {
                val attrs = it.attrs
                RemoteFile(
                    name = it.filename,
                    path = joinPath(path, it.filename),
                    isDirectory = attrs.isDir,
                    size = attrs.size,
                    lastModified = attrs.mTime.toLong() * 1000L
                )
            }
            .sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override fun download(path: String, progressListener: ProgressListener?): ByteArray {
        val ch = requireChannel()
        var totalBytes = 0L
        runCatching {
            val stat = ch.stat(path)
            totalBytes = stat.size
        }
        val out = if (totalBytes > 0) ByteArrayOutputStream(totalBytes.toInt()) else ByteArrayOutputStream()
        val inputStream = java.io.BufferedInputStream(ch.get(path), 65536)
        try {
            val buffer = ByteArray(65536) // 64 KB optimized buffer size
            var bytesRead: Int
            var totalRead = 0L
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                progressListener?.invoke(totalRead, if (totalBytes <= 0) 1L else totalBytes)
            }
        } finally {
            inputStream.close()
        }
        return out.toByteArray()
    }

    override fun upload(path: String, data: ByteArray, progressListener: ProgressListener?) {
        val ch = requireChannel()
        val outStream = ch.put(path, ChannelSftp.OVERWRITE)
        try {
            val totalBytes = data.size.toLong()
            var offset = 0
            val bufferSize = 65536 // 64 KB optimized buffer size
            while (offset < data.size) {
                val len = (data.size - offset).coerceAtMost(bufferSize)
                outStream.write(data, offset, len)
                offset += len
                progressListener?.invoke(offset.toLong(), totalBytes)
            }
            outStream.flush()
        } finally {
            outStream.close()
        }
    }

    override fun rename(from: String, to: String) {
        requireChannel().rename(from, to)
    }

    override fun deleteFile(path: String) {
        requireChannel().rm(path)
    }

    override fun deleteDirectory(path: String) {
        requireChannel().rmdir(path)
    }

    override fun makeDirectory(path: String) {
        requireChannel().mkdir(path)
    }
}
