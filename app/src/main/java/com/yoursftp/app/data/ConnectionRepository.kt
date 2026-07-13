package com.yoursftp.app.data

import kotlinx.coroutines.flow.Flow

import com.yoursftp.app.security.CryptoManager
import kotlinx.coroutines.flow.map

class ConnectionRepository(private val dao: ConnectionDao) {
    fun observeAll(): Flow<List<Connection>> = dao.observeAll().map { list ->
        list.map { it.decrypt() }
    }
    
    suspend fun getById(id: Long): Connection? {
        val conn = dao.getById(id) ?: return null
        return conn.decrypt()
    }
    
    suspend fun save(connection: Connection): Long {
        val encryptedConn = connection.encrypt()
        return if (encryptedConn.id == 0L) dao.insert(encryptedConn)
        else { dao.update(encryptedConn); encryptedConn.id }
    }
    
    suspend fun delete(connection: Connection) = dao.delete(connection)
    
    private fun Connection.encrypt(): Connection {
        // Only encrypt if it's not already encrypted (i.e. doesn't have ":")
        val newPassword = if (password.isNotBlank() && !password.contains(":")) CryptoManager.encrypt(password) else password
        val newPrivateKey = if (!privateKey.isNullOrBlank() && !privateKey.contains(":")) CryptoManager.encrypt(privateKey) else privateKey
        val newPassphrase = if (!passphrase.isNullOrBlank() && !passphrase.contains(":")) CryptoManager.encrypt(passphrase) else passphrase
        
        return copy(password = newPassword, privateKey = newPrivateKey, passphrase = newPassphrase)
    }
    
    private fun Connection.decrypt(): Connection {
        val newPassword = if (password.isNotBlank() && password.contains(":")) CryptoManager.decrypt(password) else password
        val newPrivateKey = if (!privateKey.isNullOrBlank() && privateKey.contains(":")) CryptoManager.decrypt(privateKey) else privateKey
        val newPassphrase = if (!passphrase.isNullOrBlank() && passphrase.contains(":")) CryptoManager.decrypt(passphrase) else passphrase
        
        return copy(password = newPassword, privateKey = newPrivateKey, passphrase = newPassphrase)
    }
}
