package com.yoursftp.app.data

import kotlinx.coroutines.flow.Flow

class ConnectionRepository(private val dao: ConnectionDao) {
    fun observeAll(): Flow<List<Connection>> = dao.observeAll()
    suspend fun getById(id: Long): Connection? = dao.getById(id)
    suspend fun save(connection: Connection): Long =
        if (connection.id == 0L) dao.insert(connection)
        else { dao.update(connection); connection.id }
    suspend fun delete(connection: Connection) = dao.delete(connection)
}
