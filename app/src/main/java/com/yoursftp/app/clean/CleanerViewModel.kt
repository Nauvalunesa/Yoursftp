package com.yoursftp.app.clean

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CleanerState(
    val scanning: Boolean = false,
    val deleting: Boolean = false,
    val progressPath: String = "",
    val result: ScanResult? = null,
    val selectedPaths: Set<String> = emptySet(),
    val appCacheSize: Long = 0L,
    val message: String? = null
)

class CleanerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(CleanerState())
    val state = _state.asStateFlow()

    private val scanner = JunkScanner()

    init {
        refreshAppCacheSize()
    }

    fun scan() {
        if (_state.value.scanning) return
        _state.value = _state.value.copy(scanning = true, result = null, selectedPaths = emptySet(), message = null)
        viewModelScope.launch {
            try {
                var lastUpdate = 0L
                val raw = withContext(Dispatchers.IO) {
                    scanner.scan(onProgress = { path ->
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 100) {
                            lastUpdate = now
                            _state.value = _state.value.copy(progressPath = path)
                        }
                    }, maxDepth = 4)
                }
                // Ganti nama paket dgn nama aplikasi asli utk kategori APP_CACHE.
                val result = withContext(Dispatchers.IO) { resolveAppNames(raw) }
                // Default: pilih otomatis kategori yang jelas aman dihapus (WA DB, temp, log, thumbnail, folder kosong).
                val autoSelect = result.groups
                    .filter {
                        it.category in setOf(
                            JunkCategory.WHATSAPP_DB, JunkCategory.TEMP_CACHE,
                            JunkCategory.LOG, JunkCategory.THUMBNAIL, JunkCategory.EMPTY_FOLDER
                        )
                    }
                    .flatMap { g -> g.items.map { it.path } }
                    .toSet()
                _state.value = _state.value.copy(
                    scanning = false,
                    result = result,
                    selectedPaths = autoSelect,
                    progressPath = ""
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(scanning = false, message = e.message ?: "Gagal memindai")
            }
        }
    }

    fun toggle(path: String) {
        val cur = _state.value.selectedPaths
        _state.value = _state.value.copy(
            selectedPaths = if (path in cur) cur - path else cur + path
        )
    }

    fun toggleGroup(group: JunkGroup) {
        val cur = _state.value.selectedPaths
        val paths = group.items.map { it.path }
        val allSelected = paths.all { it in cur }
        _state.value = _state.value.copy(
            selectedPaths = if (allSelected) cur - paths.toSet() else cur + paths.toSet()
        )
    }

    fun selectedSize(): Long {
        val result = _state.value.result ?: return 0L
        val sel = _state.value.selectedPaths
        return result.groups.flatMap { it.items }.filter { it.path in sel }.sumOf { it.size }
    }

    fun deleteSelected() {
        val result = _state.value.result ?: return
        val sel = _state.value.selectedPaths
        if (sel.isEmpty()) return
        val targets = result.groups.flatMap { it.items }.filter { it.path in sel }

        _state.value = _state.value.copy(deleting = true, message = null)
        viewModelScope.launch {
            var freed = 0L
            var failed = 0
            withContext(Dispatchers.IO) {
                for (item in targets) {
                    if (item.category == JunkCategory.APP_CACHE) {
                        // Item ini punya beberapa folder (dipisah newline). Hapus ISI-nya
                        // agar aplikasi tetap berfungsi, bukan folder induknya.
                        var okAny = false
                        for (dirPath in item.path.split('\n')) {
                            val dir = File(dirPath)
                            dir.listFiles()?.forEach { child ->
                                if (runCatching { child.deleteRecursively() }.getOrDefault(false)) okAny = true
                            }
                        }
                        if (okAny) freed += item.size else failed++
                    } else {
                        val f = File(item.path)
                        val ok = runCatching {
                            if (item.isDirectory) f.deleteRecursively() else f.delete()
                        }.getOrDefault(false)
                        if (ok) freed += item.size else failed++
                    }
                }
            }
            // Buang item terpilih (yang sudah dihapus) dari hasil.
            val remaining = result.groups.map { g ->
                g.copy(items = g.items.filter { it.path !in sel })
            }.filter { it.items.isNotEmpty() }

            _state.value = _state.value.copy(
                deleting = false,
                result = ScanResult(remaining),
                selectedPaths = emptySet(),
                message = "Dibersihkan ${humanSize(freed)}" + if (failed > 0) " · $failed gagal" else ""
            )
        }
    }

    /** Bersihkan cache internal aplikasi ini (folder cache + file db temp). */
    fun cleanAppCache() {
        _state.value = _state.value.copy(deleting = true, message = null)
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) {
                val ctx = getApplication<Application>()
                var total = 0L
                val dirs = listOfNotNull(ctx.cacheDir, ctx.externalCacheDir)
                for (d in dirs) {
                    d.listFiles()?.forEach { f ->
                        total += sizeOf(f)
                        runCatching { if (f.isDirectory) f.deleteRecursively() else f.delete() }
                    }
                }
                total
            }
            refreshAppCacheSize()
            _state.value = _state.value.copy(
                deleting = false,
                message = "Cache aplikasi dibersihkan (${humanSize(freed)})"
            )
        }
    }

    private fun refreshAppCacheSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                val ctx = getApplication<Application>()
                listOfNotNull(ctx.cacheDir, ctx.externalCacheDir).sumOf { sizeOf(it) }
            }
            _state.value = _state.value.copy(appCacheSize = size)
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun sizeOf(file: File): Long =
        if (file.isDirectory) file.listFiles()?.sumOf { sizeOf(it) } ?: 0L else file.length()

    /** Ganti nama paket dgn label aplikasi asli (mis. "com.whatsapp" -> "WhatsApp"). */
    private fun resolveAppNames(result: ScanResult): ScanResult {
        val pm = getApplication<Application>().packageManager
        val newGroups = result.groups.map { g ->
            if (g.category != JunkCategory.APP_CACHE) return@map g
            g.copy(items = g.items.map inner@{ item ->
                val pkg = item.packageName ?: return@inner item
                val label = runCatching {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(ai).toString()
                }.getOrNull()
                if (label != null && label != pkg) item.copy(name = "$label ($pkg)") else item
            })
        }
        return ScanResult(newGroups)
    }

    companion object {
        fun humanSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.1f MB", mb)
            return String.format("%.2f GB", mb / 1024.0)
        }
    }
}
