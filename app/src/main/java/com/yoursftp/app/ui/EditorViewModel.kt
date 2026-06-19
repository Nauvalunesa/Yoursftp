package com.yoursftp.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yoursftp.app.transfer.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorState(
    val path: String = "",
    val content: String = "",
    val loading: Boolean = false,
    val saving: Boolean = false,
    val savedMessage: String? = null,
    val error: String? = null
)

/** Editor teks: unduh isi file, sunting, lalu simpan kembali ke server. */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()

    fun load(path: String) {
        if (_state.value.path == path && _state.value.content.isNotEmpty()) return
        _state.value = EditorState(path = path, loading = true)
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    String(SessionManager.require().download(path), Charsets.UTF_8)
                }
                _state.value = _state.value.copy(content = text, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Gagal memuat file")
            }
        }
    }

    fun onContentChange(text: String) {
        _state.value = _state.value.copy(content = text)
    }

    fun save() {
        val s = _state.value
        _state.value = s.copy(saving = true, error = null, savedMessage = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SessionManager.require().upload(s.path, s.content.toByteArray(Charsets.UTF_8))
                }
                _state.value = _state.value.copy(saving = false, savedMessage = "Tersimpan")
            } catch (e: Exception) {
                _state.value = _state.value.copy(saving = false, error = e.message ?: "Gagal menyimpan")
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(savedMessage = null, error = null)
    }
}
