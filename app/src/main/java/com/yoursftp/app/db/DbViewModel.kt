package com.yoursftp.app.db

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DbState(
    val title: String = "",
    val loading: Boolean = false,
    val tables: List<String> = emptyList(),
    val selectedTable: String? = null,
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
    val truncated: Boolean = false,
    val rowInfo: String = "",
    val query: String = "",
    val error: String? = null,
    val format: DbFormat? = null,
    val supportsQuery: Boolean = true,
    // Pagination fields
    val page: Int = 0,
    val pageSize: Int = 100,
    val totalRows: Long = 0
)

class DbViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(DbState())
    val state = _state.asStateFlow()

    private var reader: DbReader? = null
    private var openedPath: String? = null

    fun openFile(localPath: String, title: String) {
        if (openedPath == localPath && reader != null) return
        openedPath = localPath
        val format = detectDbFormat(title)
        _state.value = DbState(title = title, loading = true, format = format)
        viewModelScope.launch {
            try {
                val (tables, supportsQuery) = withContext(Dispatchers.IO) {
                    reader?.close()
                    val r = createDbReader(localPath, format)
                    r.open()
                    reader = r
                    r.tables() to r.supportsCustomQuery
                }
                _state.value = _state.value.copy(loading = false, tables = tables, supportsQuery = supportsQuery)
                tables.firstOrNull()?.let { selectTable(it) }
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Gagal membuka database")
            }
        }
    }

    fun setFormatOverride(format: DbFormat) {
        val path = openedPath ?: return
        _state.value = _state.value.copy(loading = true, format = format, error = null)
        viewModelScope.launch {
            try {
                val (tables, supportsQuery) = withContext(Dispatchers.IO) {
                    reader?.close()
                    val r = createDbReader(path, format)
                    r.open()
                    reader = r
                    r.tables() to r.supportsCustomQuery
                }
                _state.value = _state.value.copy(
                    loading = false,
                    tables = tables,
                    supportsQuery = supportsQuery,
                    selectedTable = tables.firstOrNull(),
                    columns = emptyList(),
                    rows = emptyList(),
                    rowInfo = ""
                )
                tables.firstOrNull()?.let { selectTable(it) }
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Gagal membuka database dengan format ${format.label}")
            }
        }
    }

    fun selectTable(table: String) {
        _state.value = _state.value.copy(selectedTable = table, page = 0)
        loadCurrentTablePage()
    }

    private fun loadCurrentTablePage() {
        val r = reader ?: return
        val table = _state.value.selectedTable ?: return
        val page = _state.value.page
        val pageSize = _state.value.pageSize
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val (result, count) = withContext(Dispatchers.IO) {
                    r.tableRows(table, limit = pageSize, offset = page * pageSize) to r.rowCount(table)
                }
                val startRow = if (count > 0) page * pageSize + 1 else 0
                val endRow = minOf((page + 1) * pageSize, count.toInt())
                _state.value = _state.value.copy(
                    loading = false,
                    columns = result.columns,
                    rows = result.rows,
                    truncated = result.truncated,
                    totalRows = count,
                    rowInfo = "Menampilkan baris $startRow - $endRow dari $count baris",
                    query = "SELECT * FROM \"$table\" LIMIT $pageSize OFFSET ${page * pageSize}"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Gagal membaca tabel")
            }
        }
    }

    fun nextPage() {
        val currentPage = _state.value.page
        val total = _state.value.totalRows
        val size = _state.value.pageSize
        if ((currentPage + 1) * size < total) {
            _state.value = _state.value.copy(page = currentPage + 1)
            loadCurrentTablePage()
        }
    }

    fun prevPage() {
        val currentPage = _state.value.page
        if (currentPage > 0) {
            _state.value = _state.value.copy(page = currentPage - 1)
            loadCurrentTablePage()
        }
    }

    fun jumpToPage(targetPage: Int) {
        val size = _state.value.pageSize
        val total = _state.value.totalRows
        val maxPage = if (total > 0) ((total - 1) / size).toInt() else 0
        val page = targetPage.coerceIn(0, maxPage)
        _state.value = _state.value.copy(page = page)
        loadCurrentTablePage()
    }

    fun setPageSize(size: Int) {
        _state.value = _state.value.copy(pageSize = size, page = 0)
        loadCurrentTablePage()
    }

    fun setQuery(q: String) { _state.value = _state.value.copy(query = q) }

    fun runQuery() {
        val r = reader ?: return
        val sql = _state.value.query.trim()
        if (sql.isEmpty()) return
        if (!r.supportsCustomQuery) {
            _state.value = _state.value.copy(error = "Format ini tidak mendukung query SQL")
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { r.query(sql, limit = 500) }
                _state.value = _state.value.copy(
                    loading = false,
                    columns = result.columns,
                    rows = result.rows,
                    truncated = result.truncated,
                    rowInfo = "${result.rows.size} baris" + if (result.truncated) " (dipotong)" else ""
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Query gagal")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        reader?.close()
    }
}
