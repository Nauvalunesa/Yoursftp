package com.yoursftp.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "known_hosts")
data class KnownHost(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val host: String,
    val port: Int,
    val hostKey: String,
    val trustedAt: Long
)
