package com.yoursftp.app.transfer

import com.yoursftp.app.data.Connection
import com.yoursftp.app.data.Protocol

object FileClientFactory {
    fun create(conn: Connection): FileClient = when (conn.protocol) {
        Protocol.FTP, Protocol.FTPS -> FtpFileClient(conn)
        Protocol.SFTP -> SftpFileClient(conn)
        Protocol.S3 -> S3FileClient(conn)
    }
}
