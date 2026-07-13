package com.yoursftp.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_history")
data class TransferHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val sourcePath: String,
    val destPath: String,
    val size: Long,
    val timestamp: Long,
    val status: String // SUCCESS, FAILED, CANCELLED
)
