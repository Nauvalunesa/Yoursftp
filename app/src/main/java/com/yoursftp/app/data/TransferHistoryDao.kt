package com.yoursftp.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferHistoryDao {
    @Query("SELECT * FROM transfer_history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransferHistory>>

    @Insert
    suspend fun insert(history: TransferHistory)

    @Query("DELETE FROM transfer_history")
    suspend fun clearHistory()
}
