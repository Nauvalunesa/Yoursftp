package com.yoursftp.app.transfer

/** Gabungkan direktori induk dengan nama anak menjadi path POSIX yang rapi. */
fun joinPath(parent: String, child: String): String {
    val base = if (parent.endsWith("/")) parent.dropLast(1) else parent
    return when {
        base.isEmpty() -> "/$child"
        else -> "$base/$child"
    }
}

/** Path induk dari sebuah path absolut. */
fun parentPath(path: String): String {
    val trimmed = path.trimEnd('/')
    if (trimmed.isEmpty() || trimmed == "/") return "/"
    val idx = trimmed.lastIndexOf('/')
    return if (idx <= 0) "/" else trimmed.substring(0, idx)
}
