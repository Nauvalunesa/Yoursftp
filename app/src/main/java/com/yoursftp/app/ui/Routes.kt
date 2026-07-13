package com.yoursftp.app.ui

/** Definisi rute navigasi. */
object Routes {
    const val CONNECTIONS = "connections"
    const val EDIT_CONNECTION = "edit_connection"      // ?id={id}
    const val BROWSER = "browser"                       // /{connectionId}
    const val EDITOR = "editor"                         // ?path={path}
    const val TERMINAL = "terminal"                     // /{connectionId}
    const val DB_VIEWER = "db_viewer"                   // ?path={path}&title={title}
    const val TRANSFER_HISTORY = "transfer_history"

    fun editConnection(id: Long?) =
        if (id == null) "$EDIT_CONNECTION?id=-1" else "$EDIT_CONNECTION?id=$id"

    fun browser(connectionId: Long) = "$BROWSER/$connectionId"

    fun terminal(connectionId: Long) = "$TERMINAL/$connectionId"

    fun dbViewer(localPath: String, title: String): String {
        val encodedPath = java.net.URLEncoder.encode(localPath, "UTF-8")
        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
        return "$DB_VIEWER?path=$encodedPath&title=$encodedTitle"
    }
}
