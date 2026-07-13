package com.yoursftp.app.transfer

import com.yoursftp.app.data.Connection
import com.yoursftp.app.data.Protocol

import android.content.Context

object FileClientFactory {
    fun create(context: Context, conn: Connection): FileClient = when (conn.protocol) {
        Protocol.FTP, Protocol.FTPS -> FtpFileClient(conn)
        Protocol.SFTP -> SftpFileClient(context, conn)
        Protocol.S3 -> S3FileClient(conn)
    }
}
