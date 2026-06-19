package com.yoursftp.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connections ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Connection>>

    @Query("SELECT * FROM connections WHERE id = :id")
    suspend fun getById(id: Long): Connection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(connection: Connection): Long

    @Update
    suspend fun update(connection: Connection)

    @Delete
    suspend fun delete(connection: Connection)
}
