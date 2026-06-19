package com.yoursftp.app.data

/** Entri file/direktori pada server remote. */
data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)
