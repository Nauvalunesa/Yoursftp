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
    val supportsQuery: Boolean = true
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

    fun selectTable(table: String) {
        val r = reader ?: return
        _state.value = _state.value.copy(loading = true, selectedTable = table, error = null)
        viewModelScope.launch {
            try {
                val (result, count) = withContext(Dispatchers.IO) {
                    r.tableRows(table, limit = 200) to r.rowCount(table)
                }
                _state.value = _state.value.copy(
                    loading = false,
                    columns = result.columns,
                    rows = result.rows,
                    truncated = result.truncated,
                    rowInfo = "${result.rows.size} dari $count baris",
                    query = "SELECT * FROM \"$table\" LIMIT 200"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Gagal membaca tabel")
            }
        }
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
