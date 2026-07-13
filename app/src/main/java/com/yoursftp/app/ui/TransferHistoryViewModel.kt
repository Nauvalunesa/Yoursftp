package com.yoursftp.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yoursftp.app.data.AppDatabase
import com.yoursftp.app.data.TransferHistory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TransferHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).transferHistoryDao()
    
    private val _history = MutableStateFlow<List<TransferHistory>>(emptyList())
    val history = _history.asStateFlow()
    
    init {
        viewModelScope.launch {
            dao.observeAll().collect { 
                _history.value = it
            }
        }
    }
    
    fun clearHistory() {
        viewModelScope.launch {
            dao.clearHistory()
        }
    }
}
