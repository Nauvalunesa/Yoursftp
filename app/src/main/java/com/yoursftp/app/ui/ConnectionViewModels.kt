package com.yoursftp.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yoursftp.app.YoursFtpApp
import com.yoursftp.app.data.Connection
import com.yoursftp.app.data.ConnectionRepository
import com.yoursftp.app.data.Protocol
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun app(application: Application) = (application as YoursFtpApp)

/** Daftar koneksi tersimpan. */
class ConnectionsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: ConnectionRepository = app(application).repository

    val connections = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(connection: Connection) = viewModelScope.launch { repo.delete(connection) }
}

/** Form tambah/edit koneksi. */
class EditConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: ConnectionRepository = app(application).repository

    var loaded: Connection? = null
        private set

    suspend fun load(id: Long): Connection? {
        val c = if (id <= 0L) null else repo.getById(id)
        loaded = c
        return c
    }

    fun testConnection(
        protocol: Protocol,
        host: String,
        port: Int,
        username: String,
        password: String,
        initialPath: String,
        passiveMode: Boolean,
        privateKey: String? = null,
        passphrase: String? = null,
        onResult: (success: Boolean, message: String) -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val testConn = Connection(
                id = 0L,
                name = "Test",
                protocol = protocol,
                host = host.trim(),
                port = port,
                username = username.trim(),
                password = password,
                initialPath = initialPath,
                passiveMode = passiveMode,
                privateKey = privateKey?.takeIf { it.isNotBlank() },
                passphrase = passphrase?.takeIf { it.isNotBlank() }
            )
            val client = com.yoursftp.app.transfer.FileClientFactory.create(getApplication<Application>(), testConn)
            client.connect()
            client.disconnect()
            withContext(Dispatchers.Main) {
                onResult(true, "Koneksi berhasil terhubung!")
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onResult(false, e.localizedMessage ?: e.message ?: "Gagal terhubung.")
            }
        }
    }

    fun save(
        existingId: Long,
        name: String,
        protocol: Protocol,
        host: String,
        port: Int,
        username: String,
        password: String,
        initialPath: String,
        passiveMode: Boolean,
        privateKey: String? = null,
        passphrase: String? = null,
        onDone: () -> Unit
    ) = viewModelScope.launch {
        repo.save(
            Connection(
                id = existingId,
                name = name.ifBlank { host },
                protocol = protocol,
                host = host.trim(),
                port = port,
                username = username.trim(),
                password = password,
                initialPath = initialPath.ifBlank { "/" },
                passiveMode = passiveMode,
                privateKey = privateKey?.takeIf { it.isNotBlank() },
                passphrase = passphrase?.takeIf { it.isNotBlank() }
            )
        )
        onDone()
    }
}

class AppViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(ConnectionsViewModel::class.java) ->
            ConnectionsViewModel(application) as T
        modelClass.isAssignableFrom(EditConnectionViewModel::class.java) ->
            EditConnectionViewModel(application) as T
        modelClass.isAssignableFrom(BrowserViewModel::class.java) ->
            BrowserViewModel(application) as T
        modelClass.isAssignableFrom(EditorViewModel::class.java) ->
            EditorViewModel(application) as T
        modelClass.isAssignableFrom(com.yoursftp.app.editor.LargeEditorViewModel::class.java) ->
            com.yoursftp.app.editor.LargeEditorViewModel(application) as T
        modelClass.isAssignableFrom(com.yoursftp.app.db.DbViewModel::class.java) ->
            com.yoursftp.app.db.DbViewModel(application) as T
        modelClass.isAssignableFrom(TerminalViewModel::class.java) ->
            TerminalViewModel(application) as T
        modelClass.isAssignableFrom(com.yoursftp.app.ota.OtaViewModel::class.java) ->
            com.yoursftp.app.ota.OtaViewModel(application) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
