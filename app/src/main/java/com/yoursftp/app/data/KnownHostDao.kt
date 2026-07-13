package com.yoursftp.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface KnownHostDao {
    @Query("SELECT * FROM known_hosts WHERE host = :host AND port = :port LIMIT 1")
    suspend fun getHost(host: String, port: Int): KnownHost?

    @Insert
    suspend fun insert(knownHost: KnownHost)

    @Query("DELETE FROM known_hosts WHERE host = :host AND port = :port")
    suspend fun deleteHost(host: String, port: Int)
}
