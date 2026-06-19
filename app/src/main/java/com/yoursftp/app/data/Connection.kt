package com.yoursftp.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Profil koneksi tersimpan. */
@Entity(tableName = "connections")
data class Connection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: Protocol,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val initialPath: String = "/",
    /** SFTP only: pasif/aktif tidak relevan; FTP saja. */
    val passiveMode: Boolean = true
) {
    companion object {
        fun defaultPort(protocol: Protocol) = when (protocol) {
            Protocol.SFTP -> 22
            Protocol.FTP, Protocol.FTPS -> 21
            Protocol.S3 -> 443
        }
    }
}
