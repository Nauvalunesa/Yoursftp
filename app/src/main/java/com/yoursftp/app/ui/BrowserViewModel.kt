package com.yoursftp.app.ui

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yoursftp.app.YoursFtpApp
import com.yoursftp.app.data.Connection
import com.yoursftp.app.data.RemoteFile
import com.yoursftp.app.transfer.FileClient
import com.yoursftp.app.transfer.FileClientFactory
import com.yoursftp.app.transfer.LocalFileClient
import com.yoursftp.app.transfer.SessionManager
import com.yoursftp.app.transfer.joinPath
import com.yoursftp.app.transfer.parentPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.yoursftp.app.data.AppDatabase
import com.yoursftp.app.data.TransferHistory
import java.io.File

enum class SortOrder {
    NAME_ASC, NAME_DESC,
    SIZE_ASC, SIZE_DESC,
    DATE_ASC, DATE_DESC
}

enum class FilterType {
    ALL, FOLDERS_ONLY, FILES_ONLY
}

enum class OverwriteMode {
    OVERWRITE, SKIP, RENAME
}

data class TabState(
    val connectionId: Long = -1L,
    val connectionName: String = "Penyimpanan Lokal",
    val currentPath: String = "/storage/emulated/0",
    val files: List<RemoteFile> = emptyList(),
    val filterQuery: String = "",
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val filterType: FilterType = FilterType.ALL,
    val foldersFirst: Boolean = true,
    val showHidden: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val loading: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
    val client: FileClient? = null
) {
    val filteredFiles: List<RemoteFile>
        get() {
            // 0. Sembunyikan file tersembunyi (diawali titik) kecuali diminta.
            var result = if (showHidden) files else files.filter { !it.name.startsWith(".") }

            // 1. Filter by Name/Extension
            result = if (filterQuery.isBlank()) {
                result
            } else {
                result.filter { it.name.contains(filterQuery, ignoreCase = true) }
            }

            // 2. Filter by Type
            result = when (filterType) {
                FilterType.ALL -> result
                FilterType.FOLDERS_ONLY -> result.filter { it.isDirectory }
                FilterType.FILES_ONLY -> result.filter { !it.isDirectory }
            }

            // 3. Sort
            val comparator = when (sortOrder) {
                SortOrder.NAME_ASC -> compareBy<RemoteFile> { it.name.lowercase() }
                SortOrder.NAME_DESC -> compareByDescending<RemoteFile> { it.name.lowercase() }
                SortOrder.SIZE_ASC -> compareBy<RemoteFile> { it.size }
                SortOrder.SIZE_DESC -> compareByDescending<RemoteFile> { it.size }
                SortOrder.DATE_ASC -> compareBy<RemoteFile> { it.lastModified }
                SortOrder.DATE_DESC -> compareByDescending<RemoteFile> { it.lastModified }
            }

            // 4. Folders first
            return if (foldersFirst) {
                result.sortedWith { f1, f2 ->
                    if (f1.isDirectory && !f2.isDirectory) {
                        -1
                    } else if (!f1.isDirectory && f2.isDirectory) {
                        1
                    } else {
                        comparator.compare(f1, f2)
                    }
                }
            } else {
                result.sortedWith(comparator)
            }
        }
}

data class TransferProgress(
    val isActive: Boolean = false,
    val currentFileName: String = "",
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val percent: Int = 0,
    val etaSeconds: Int = -1
)

/** Item yang sedang "dipotong" untuk dipindah (move) di dalam satu tab/koneksi. */
data class MoveClipboard(
    val sourceTabIndex: Int,
    val files: List<RemoteFile>
)

data class HostKeyChangedEvent(val host: String, val newKey: String)

data class BrowserState(
    val tab1: TabState = TabState(),
    val tab2: TabState = TabState(),
    val activeTab: Int = 1,
    val globalError: String? = null,
    val transferProgress: TransferProgress = TransferProgress(),
    val moveClipboard: MoveClipboard? = null,
    val hostKeyChangedEvent: HostKeyChangedEvent? = null
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as YoursFtpApp).repository

    private val _state = MutableStateFlow(BrowserState(
        tab1 = TabState(connectionId = -1L, connectionName = "Penyimpanan Lokal", currentPath = getLocalRootPath()),
        tab2 = TabState(connectionId = -1L, connectionName = "Penyimpanan Lokal", currentPath = getLocalRootPath())
    ))
    val state = _state.asStateFlow()

    val connections = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var pendingEditText: String? = null
        private set

    companion object {
        fun getLocalRootPath(): String {
            val externalDir = Environment.getExternalStorageDirectory()
            return if (externalDir.exists() && externalDir.canRead()) {
                externalDir.absolutePath
            } else {
                "/storage/emulated/0"
            }
        }
    }



    fun connect(tabIndex: Int, connectionId: Long) {
        val currentTabState = if (tabIndex == 1) _state.value.tab1 else _state.value.tab2
        
        // Disconnect old client of this tab if exists
        currentTabState.client?.let {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { it.disconnect() }
            }
        }

        updateTab(tabIndex) { 
            it.copy(
                connectionId = connectionId, 
                loading = true, 
                error = null, 
                connected = false,
                client = null
            ) 
        }

        viewModelScope.launch {
            try {
                val client = withContext(Dispatchers.IO) {
                    if (connectionId == -1L) {
                        LocalFileClient().apply { connect() }
                    } else {
                        val conn = repo.getById(connectionId)
                            ?: throw IllegalStateException("Koneksi tidak ditemukan")
                        FileClientFactory.create(getApplication<Application>(), conn).apply { connect() }
                    }
                }

                val name = if (connectionId == -1L) {
                    "Penyimpanan Lokal"
                } else {
                    repo.getById(connectionId)?.name ?: "Server"
                }

                val initialPath = if (connectionId == -1L) {
                    getLocalRootPath()
                } else {
                    repo.getById(connectionId)?.initialPath ?: "/"
                }

                updateTab(tabIndex) {
                    it.copy(
                        connectionName = name,
                        currentPath = initialPath,
                        client = client,
                        connected = true
                    )
                }
                
                refresh(tabIndex, initialPath)
            } catch (e: com.yoursftp.app.transfer.HostKeyChangedException) {
                _state.value = _state.value.copy(
                    hostKeyChangedEvent = HostKeyChangedEvent(e.host, e.newKey),
                    globalError = e.message
                )
                updateTab(tabIndex) {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Gagal terhubung"
                    )
                }
            } catch (e: Exception) {
                val cause = generateSequence((e as? Throwable)) { it.cause }.find { it is com.yoursftp.app.transfer.HostKeyChangedException } as? com.yoursftp.app.transfer.HostKeyChangedException
                if (cause != null) {
                    _state.value = _state.value.copy(
                        hostKeyChangedEvent = HostKeyChangedEvent(cause.host, cause.newKey),
                        globalError = cause.message
                    )
                    updateTab(tabIndex) {
                        it.copy(
                            loading = false,
                            error = cause.message
                        )
                    }
                } else {
                    updateTab(tabIndex) {
                        it.copy(
                            loading = false,
                            error = e.message ?: "Gagal terhubung"
                        )
                    }
                }
            }
        }
    }

    fun open(
        tabIndex: Int,
        file: RemoteFile,
        context: android.content.Context,
        onEditFile: (String, String) -> Unit,
        onOpenExternal: (java.io.File, String) -> Unit,
        onOpenDb: (String, String) -> Unit = { _, _ -> }
    ) {
        if (file.isDirectory) {
            refresh(tabIndex, file.path)
        } else {
            val tabState = if (tabIndex == 1) _state.value.tab1 else _state.value.tab2
            val client = tabState.client ?: return

            // File database SQLite → buka di penampil DB.
            if (looksLikeDatabase(file.name) && file.size <= 200L * 1024 * 1024) {
                updateTab(tabIndex) { it.copy(loading = true, error = null) }
                viewModelScope.launch {
                    try {
                        val localFile = withContext(Dispatchers.IO) {
                            if (tabState.connectionId == -1L) java.io.File(file.path)
                            else {
                                val data = client.download(file.path)
                                java.io.File(context.cacheDir, "db_" + file.name).apply { writeBytes(data) }
                            }
                        }
                        updateTab(tabIndex) { it.copy(loading = false) }
                        onOpenDb(localFile.absolutePath, file.name)
                    } catch (e: Exception) {
                        updateTab(tabIndex) { it.copy(loading = false, error = e.message ?: "Gagal membuka database") }
                    }
                }
                return
            }

            // Routing editor berdasarkan ukuran:
            //  < 64 KB        → editor kaya (engine "rich") dengan seleksi multi-baris native
            //  64 KB – 25 MB  → editor besar ter-virtualisasi (engine "large") agar tetap mulus
            //  > 25 MB        → buka eksternal (hindari risiko OOM)
            // Editor lama me-layout seluruh isi sebagai satu node, jadi file ratusan KB pun
            // bisa membuat UI freeze; karena itu ambang "rich" dibuat kecil.
            val richCap = 64L * 1024
            val largeCap = 25L * 1024 * 1024
            val editable = looksEditable(file.name) && file.size <= largeCap
            val engine = if (file.size < richCap) "rich" else "large"

            updateTab(tabIndex) { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                try {
                    if (editable) {
                        // Kedua editor mengunduh sendiri lewat SessionManager; cukup set sesi & navigasi.
                        SessionManager.editingClient = client
                        updateTab(tabIndex) { it.copy(loading = false) }
                        onEditFile(file.path, engine)
                    } else {
                        // Prevent downloading extremely large files into memory for preview
                        if (tabState.connectionId != -1L && file.size > 50 * 1024 * 1024) {
                            throw IllegalStateException("File terlalu besar untuk pratinjau (maksimal 50 MB)")
                        }

                        val mimeType = getMimeType(file.name)
                        val localFile = withContext(Dispatchers.IO) {
                            if (tabState.connectionId == -1L) {
                                java.io.File(file.path)
                            } else {
                                val data = client.download(file.path)
                                val tempFile = java.io.File(context.cacheDir, file.name)
                                tempFile.writeBytes(data)
                                tempFile
                            }
                        }
                        updateTab(tabIndex) { it.copy(loading = false) }
                        onOpenExternal(localFile, mimeType)
                    }
                } catch (e: Exception) {
                    updateTab(tabIndex) {
                        it.copy(
                            loading = false,
                            error = e.message ?: "Gagal membuka file"
                        )
                    }
                }
            }
        }
    }

    fun navigateUp(tabIndex: Int) {
        val tabState = if (tabIndex == 1) _state.value.tab1 else _state.value.tab2
        val parent = parentPath(tabState.currentPath)
        if (parent != tabState.currentPath) {
            refresh(tabIndex, parent)
        }
    }

    fun refresh(tabIndex: Int, path: String? = null) {
        val tabState = if (tabIndex == 1) _state.value.tab1 else _state.value.tab2
        val targetPath = path ?: tabState.currentPath
        val client = tabState.client ?: return

        updateTab(tabIndex) { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val files = withContext(Dispatchers.IO) { client.list(targetPath) }
                updateTab(tabIndex) {
                    it.copy(
                        currentPath = targetPath,
                        files = files,
                        loading = false
                    )
                }
            } catch (e: Exception) {
                updateTab(tabIndex) {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Gagal memuat folder"
                    )
                }
            }
        }
    }

    fun setFilterQuery(tabIndex: Int, query: String) {
        updateTab(tabIndex) { it.copy(filterQuery = query) }
    }

    fun setSortOrder(tabIndex: Int, sortOrder: SortOrder) {
        updateTab(tabIndex) { it.copy(sortOrder = sortOrder) }
    }

    fun setFilterType(tabIndex: Int, filterType: FilterType) {
        updateTab(tabIndex) { it.copy(filterType = filterType) }
    }

    fun setFoldersFirst(tabIndex: Int, foldersFirst: Boolean) {
        updateTab(tabIndex) { it.copy(foldersFirst = foldersFirst) }
    }

    fun toggleShowHidden(tabIndex: Int) {
        updateTab(tabIndex) { it.copy(showHidden = !it.showHidden) }
    }

    /** Loncat langsung ke sebuah path (mis. ruang clone /storage/emulated/999). */
    fun navigateTo(tabIndex: Int, path: String) {
        refresh(tabIndex, path)
    }

    // ---- Mode seleksi (multi-select) ----

    fun enterSelection(tabIndex: Int, file: RemoteFile) {
        updateTab(tabIndex) {
            it.copy(selectionMode = true, selectedPaths = setOf(file.path))
        }
        setActiveTab(tabIndex)
    }

    fun toggleSelected(tabIndex: Int, file: RemoteFile) {
        updateTab(tabIndex) {
            val newSet = if (file.path in it.selectedPaths) {
                it.selectedPaths - file.path
            } else {
                it.selectedPaths + file.path
            }
            it.copy(selectedPaths = newSet, selectionMode = newSet.isNotEmpty())
        }
    }

    fun selectAll(tabIndex: Int) {
        val tabState = if (tabIndex == 1) _state.value.tab1 else _state.value.tab2
        val all = tabState.filteredFiles.map { it.path }.toSet()
        updateTab(tabIndex) { it.copy(selectedPaths = all, selectionMode = true) }
    }

    fun clearSelection(tabIndex: Int) {
        updateTab(tabIndex) { it.copy(selectionMode = false, selectedPaths = emptySet()) }
    }

    private fun selectedFiles(tabIndex: Int): List<RemoteFile> {
        val tabState = if (tabIndex == 1) _state.value.tab1 else _state.value.tab2
        return tabState.files.filter { it.path in tabState.selectedPaths }
    }

    fun deleteSelected(tabIndex: Int) {
        val targets = selectedFiles(tabIndex)
        if (targets.isEmpty()) return
        val tabState = if (tabIndex == 1) _state.value.tab1 else _state.value.tab2
        val client = tabState.client ?: return

        updateTab(tabIndex) { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    for (f in targets) {
                        if (f.isDirectory) client.deleteDirectory(f.path) else client.deleteFile(f.path)
                    }
                }
                clearSelection(tabIndex)
                refresh(tabIndex)
            } catch (e: Exception) {
                updateTab(tabIndex) { it.copy(loading = false, error = e.message ?: "Gagal menghapus") }
            }
        }
    }

    fun transferSelected(sourceTabIndex: Int) {
        val targets = selectedFiles(sourceTabIndex)
        if (targets.isEmpty()) return

        val destTabIndex = if (sourceTabIndex == 1) 2 else 1
        val sourceState = if (sourceTabIndex == 1) _state.value.tab1 else _state.value.tab2
        val destState = if (destTabIndex == 1) _state.value.tab1 else _state.value.tab2
        val sourceClient = sourceState.client ?: return
        val destClient = destState.client ?: return

        _state.value = _state.value.copy(
            globalError = null,
            transferProgress = TransferProgress(isActive = true, currentFileName = targets.first().name)
        )
        clearSelection(sourceTabIndex)

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val dao = AppDatabase.get(getApplication<Application>()).transferHistoryDao()
                    for (file in targets) {
                        val destName = getUniqueDestName(destState.files, file.name)
                        val destPath = joinPath(destState.currentPath, destName)
                        _state.value = _state.value.copy(
                            transferProgress = _state.value.transferProgress.copy(currentFileName = file.name)
                        )
                        
                        var status = "SUCCESS"
                        try {
                            if (file.isDirectory) {
                                copyRecursive(sourceClient, destClient, file.path, destPath)
                            } else {
                                val startTime = System.currentTimeMillis()
                                val data = sourceClient.download(file.path) { b, t -> updateProgress(b, t, startTime, false) }
                                val upStart = System.currentTimeMillis()
                                destClient.upload(destPath, data) { b, t -> updateProgress(b, t, upStart, true) }
                            }
                        } catch (e: Exception) {
                            status = "FAILED"
                            throw e
                        } finally {
                            dao.insert(
                                TransferHistory(
                                    fileName = file.name,
                                    sourcePath = file.path,
                                    destPath = destPath,
                                    size = file.size,
                                    timestamp = System.currentTimeMillis(),
                                    status = status
                                )
                            )
                        }
                    }
                }
                refresh(sourceTabIndex)
                refresh(destTabIndex)
            } catch (e: Exception) {
                _state.value = _state.value.copy(globalError = e.message ?: "Gagal mentransfer")
            } finally {
                _state.value = _state.value.copy(transferProgress = TransferProgress(isActive = false))
            }
        }
    }

    // ---- Potong / Tempel (move) di dalam satu tab ----

    fun cutSelected(tabIndex: Int) {
        val targets = selectedFiles(tabIndex)
        if (targets.isEmpty()) return
        _state.value = _state.value.copy(moveClipboard = MoveClipboard(tabIndex, targets))
        clearSelection(tabIndex)
    }

    fun cutSingle(tabIndex: Int, file: RemoteFile) {
        _state.value = _state.value.copy(moveClipboard = MoveClipboard(tabIndex, listOf(file)))
    }

    fun clearMoveClipboard() {
        _state.value = _state.value.copy(moveClipboard = null)
    }

    /** Tempel item yang dipotong ke folder aktif pada [destTabIndex]. */
    fun pasteMove(destTabIndex: Int) {
        val clip = _state.value.moveClipboard ?: return
        val destState = if (destTabIndex == 1) _state.value.tab1 else _state.value.tab2
        val client = destState.client ?: return

        updateTab(destTabIndex) { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    for (f in clip.files) {
                        val destName = getUniqueDestName(destState.files, f.name)
                        val destPath = joinPath(destState.currentPath, destName)
                        if (destPath != f.path) client.rename(f.path, destPath)
                    }
                }
                _state.value = _state.value.copy(moveClipboard = null)
                refresh(destTabIndex)
                // Segarkan tab sumber bila berbeda agar item hilang dari tempat asal.
                if (clip.sourceTabIndex != destTabIndex) refresh(clip.sourceTabIndex)
            } catch (e: Exception) {
                updateTab(destTabIndex) { it.copy(loading = false, error = e.message ?: "Gagal memindahkan") }
            }
        }
    }

    fun setActiveTab(tabIndex: Int) {
        _state.value = _state.value.copy(activeTab = tabIndex)
    }

    fun createFolder(tabIndex: Int, name: String) = mutate(tabIndex) { client, path ->
        client.makeDirectory(joinPath(path, name))
    }

    fun createTextFile(tabIndex: Int, name: String) = mutate(tabIndex) { client, path ->
        client.upload(joinPath(path, name), ByteArray(0))
    }

    fun rename(tabIndex: Int, file: RemoteFile, newName: String) = mutate(tabIndex) { client, path ->
        client.rename(file.path, joinPath(path, newName))
    }

    fun delete(tabIndex: Int, file: RemoteFile) = mutate(tabIndex) { client, _ ->
        if (file.isDirectory) {
            client.deleteDirectory(file.path)
        } else {
            client.deleteFile(file.path)
        }
    }

    fun transferFile(sourceTabIndex: Int, file: RemoteFile, mode: OverwriteMode = OverwriteMode.OVERWRITE) {
        if (mode == OverwriteMode.SKIP) return

        val destTabIndex = if (sourceTabIndex == 1) 2 else 1
        val sourceState = if (sourceTabIndex == 1) _state.value.tab1 else _state.value.tab2
        val destState = if (destTabIndex == 1) _state.value.tab1 else _state.value.tab2

        val sourceClient = sourceState.client ?: return
        val destClient = destState.client ?: return

        _state.value = _state.value.copy(
            globalError = null,
            transferProgress = TransferProgress(isActive = true, currentFileName = file.name)
        )

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val destName = if (mode == OverwriteMode.RENAME) {
                        getUniqueDestName(destState.files, file.name)
                    } else {
                        file.name
                    }
                    val destPath = joinPath(destState.currentPath, destName)
                    if (file.isDirectory) {
                        copyRecursive(sourceClient, destClient, file.path, destPath)
                    } else {
                        val startTime = System.currentTimeMillis()
                        val data = sourceClient.download(file.path) { bytesRead, total ->
                            updateProgress(bytesRead, total, startTime, isUploading = false)
                        }
                        val uploadStartTime = System.currentTimeMillis()
                        destClient.upload(destPath, data) { bytesWritten, total ->
                            updateProgress(bytesWritten, total, uploadStartTime, isUploading = true)
                        }
                    }
                }
                refresh(sourceTabIndex)
                refresh(destTabIndex)
            } catch (e: Exception) {
                _state.value = _state.value.copy(globalError = e.message ?: "Gagal mentransfer")
            } finally {
                _state.value = _state.value.copy(
                    transferProgress = TransferProgress(isActive = false)
                )
            }
        }
    }

    private suspend fun copyRecursive(sourceClient: FileClient, destClient: FileClient, srcPath: String, destPath: String) {
        withContext(Dispatchers.IO) {
            destClient.makeDirectory(destPath)
            val files = sourceClient.list(srcPath)
            for (f in files) {
                val subSrc = f.path
                val subDest = joinPath(destPath, f.name)
                
                // Update progress with active item name
                _state.value = _state.value.copy(
                    transferProgress = _state.value.transferProgress.copy(
                        currentFileName = f.name,
                        bytesTransferred = 0L,
                        totalBytes = 0L,
                        speedBytesPerSec = 0L,
                        percent = 0,
                        etaSeconds = -1
                    )
                )

                if (f.isDirectory) {
                    copyRecursive(sourceClient, destClient, subSrc, subDest)
                } else {
                    val startTime = System.currentTimeMillis()
                    val data = sourceClient.download(subSrc) { bytesRead, total ->
                        updateProgress(bytesRead, total, startTime, isUploading = false)
                    }
                    val uploadStartTime = System.currentTimeMillis()
                    destClient.upload(subDest, data) { bytesWritten, total ->
                        updateProgress(bytesWritten, total, uploadStartTime, isUploading = true)
                    }
                }
            }
        }
    }

    private fun updateProgress(bytesTransferred: Long, totalBytes: Long, startTime: Long, isUploading: Boolean) {
        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
        val speed = if (elapsed > 0) (bytesTransferred / elapsed).toLong() else 0L
        val percent = if (totalBytes > 0) ((bytesTransferred * 100) / totalBytes).toInt() else 0
        val eta = if (speed > 0 && totalBytes > bytesTransferred) ((totalBytes - bytesTransferred) / speed).toInt() else -1

        _state.value = _state.value.copy(
            transferProgress = _state.value.transferProgress.copy(
                bytesTransferred = bytesTransferred,
                totalBytes = totalBytes,
                speedBytesPerSec = speed,
                percent = percent,
                etaSeconds = eta
            )
        )
    }

    fun consumePendingEditText(): String? {
        val t = pendingEditText
        pendingEditText = null
        return t
    }

    fun zipSelected(tabIndex: Int) {
        val targets = selectedFiles(tabIndex)
        if (targets.isEmpty()) return
        val tabState = if (tabIndex == 1) _state.value.tab1 else _state.value.tab2
        val client = tabState.client ?: return
        
        val archiveName = if (targets.size == 1) {
            "${targets.first().name}.zip"
        } else {
            "archive_${System.currentTimeMillis()}.zip"
        }
        
        _state.value = _state.value.copy(
            globalError = null,
            transferProgress = TransferProgress(isActive = true, currentFileName = "Mengompres ke $archiveName...")
        )
        clearSelection(tabIndex)
        
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val ctx = getApplication<Application>()
                    val tempDir = File(ctx.cacheDir, "zip_temp_${System.currentTimeMillis()}").apply { mkdirs() }
                    val outZip = File(ctx.cacheDir, "out_${System.currentTimeMillis()}.zip")
                    
                    val localClient = com.yoursftp.app.transfer.LocalFileClient()
                    
                    for (file in targets) {
                        val destPath = joinPath(tempDir.absolutePath, file.name)
                        if (file.isDirectory) {
                            copyRecursive(client, localClient, file.path, destPath)
                        } else {
                            val data = client.download(file.path) { _, _ -> }
                            localClient.upload(destPath, data) { _, _ -> }
                        }
                    }
                    
                    com.yoursftp.app.transfer.ArchiveUtils.zip(targets.map { File(tempDir, it.name) }, tempDir, outZip)
                    
                    val destZipPath = joinPath(tabState.currentPath, archiveName)
                    val data = localClient.download(outZip.absolutePath) { _, _ -> }
                    client.upload(destZipPath, data) { _, _ -> }
                    
                    tempDir.deleteRecursively()
                    outZip.delete()
                }
                refresh(tabIndex)
            } catch (e: Exception) {
                _state.value = _state.value.copy(globalError = e.message ?: "Gagal membuat zip")
            } finally {
                _state.value = _state.value.copy(transferProgress = TransferProgress(isActive = false))
            }
        }
    }

    fun unzipSingle(tabIndex: Int, file: RemoteFile) {
        val tabState = if (tabIndex == 1) _state.value.tab1 else _state.value.tab2
        val client = tabState.client ?: return
        
        _state.value = _state.value.copy(
            globalError = null,
            transferProgress = TransferProgress(isActive = true, currentFileName = "Mengekstrak ${file.name}...")
        )
        
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val ctx = getApplication<Application>()
                    val tempZip = File(ctx.cacheDir, "temp_${System.currentTimeMillis()}.zip")
                    val outDir = File(ctx.cacheDir, "unzip_${System.currentTimeMillis()}").apply { mkdirs() }
                    
                    val localClient = com.yoursftp.app.transfer.LocalFileClient()
                    
                    val zipData = client.download(file.path) { _, _ -> }
                    localClient.upload(tempZip.absolutePath, zipData) { _, _ -> }
                    
                    com.yoursftp.app.transfer.ArchiveUtils.unzip(tempZip, outDir)
                    
                    val destBase = tabState.currentPath
                    outDir.listFiles()?.forEach { child ->
                        val destPath = joinPath(destBase, child.name)
                        if (child.isDirectory) {
                            copyRecursive(localClient, client, child.absolutePath, destPath)
                        } else {
                            val data = localClient.download(child.absolutePath) { _, _ -> }
                            client.upload(destPath, data) { _, _ -> }
                        }
                    }
                    
                    tempZip.delete()
                    outDir.deleteRecursively()
                }
                refresh(tabIndex)
            } catch (e: Exception) {
                _state.value = _state.value.copy(globalError = e.message ?: "Gagal mengekstrak zip")
            } finally {
                _state.value = _state.value.copy(transferProgress = TransferProgress(isActive = false))
            }
        }
    }

    private fun mutate(tabIndex: Int, block: suspend (FileClient, String) -> Unit) {
        val tabState = if (tabIndex == 1) _state.value.tab1 else _state.value.tab2
        val client = tabState.client ?: return
        
        updateTab(tabIndex) { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block(client, tabState.currentPath) }
                refresh(tabIndex)
            } catch (e: Exception) {
                updateTab(tabIndex) {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Operasi gagal"
                    )
                }
            }
        }
    }

    private fun updateTab(tabIndex: Int, update: (TabState) -> TabState) {
        _state.value = if (tabIndex == 1) {
            _state.value.copy(tab1 = update(_state.value.tab1))
        } else {
            _state.value.copy(tab2 = update(_state.value.tab2))
        }
    }

    fun clearTabError(tabIndex: Int) {
        updateTab(tabIndex) { it.copy(error = null) }
    }

    fun clearGlobalError() {
        _state.value = _state.value.copy(globalError = null)
    }

    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { _state.value.tab1.client?.disconnect() }
            runCatching { _state.value.tab2.client?.disconnect() }
        }
        _state.value = BrowserState()
    }
    
    fun clearHostKeyChangedEvent() {
        _state.value = _state.value.copy(hostKeyChangedEvent = null)
    }

    fun acceptNewHostKey(host: String, newKey: String, connectionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.get(getApplication<Application>()).knownHostDao()
            dao.deleteHost(host, 22)
            dao.insert(com.yoursftp.app.data.KnownHost(host = host, port = 22, hostKey = newKey, trustedAt = System.currentTimeMillis()))
            
            withContext(Dispatchers.Main) {
                clearHostKeyChangedEvent()
                connect(1, connectionId)
            }
        }
    }

    fun hasTransferConflict(sourceTabIndex: Int, file: RemoteFile): Boolean {
        val destTabIndex = if (sourceTabIndex == 1) 2 else 1
        val destState = if (destTabIndex == 1) _state.value.tab1 else _state.value.tab2
        return destState.files.any { it.name.equals(file.name, ignoreCase = true) }
    }

    private fun getUniqueDestName(destFiles: List<RemoteFile>, originalName: String): String {
        if (destFiles.none { it.name.equals(originalName, ignoreCase = true) }) return originalName
        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex != -1) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex != -1) originalName.substring(dotIndex) else ""
        var counter = 1
        var newName = "${baseName}_$counter$extension"
        while (destFiles.any { it.name.equals(newName, ignoreCase = true) }) {
            counter++
            newName = "${baseName}_$counter$extension"
        }
        return newName
    }

    private fun looksEditable(name: String): Boolean {
        val lower = name.lowercase()
        val exts = listOf(".txt", ".md", ".json", ".xml", ".html", ".htm", ".css", ".js",
            ".ts", ".kt", ".java", ".py", ".sh", ".yml", ".yaml", ".ini", ".conf", ".log",
            ".csv", ".php", ".rb", ".go", ".c", ".cpp", ".h", ".env", ".properties",
            ".gradle", ".toml", ".gitignore")
        return exts.any { lower.endsWith(it) } || !lower.contains(".")
    }

    /** File data yang bisa dibuka di penampil DB. */
    private fun looksLikeDatabase(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".db") || lower.endsWith(".sqlite") ||
            lower.endsWith(".sqlite3") || lower.endsWith(".db3") ||
            lower.endsWith(".sqlitedb") || lower.endsWith(".bson") ||
            lower.endsWith(".json") || lower.endsWith(".jsonl") ||
            lower.endsWith(".ndjson") || lower.endsWith(".geojson") ||
            lower.endsWith(".csv") || lower.endsWith(".tsv") ||
            lower.endsWith(".sql") || lower.endsWith(".xml")
    }

    private fun getMimeType(fileName: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        val ext = if (dotIndex != -1) fileName.substring(dotIndex + 1).lowercase() else ""
        if (ext == "apk") return "application/vnd.android.package-archive"
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
