package com.yoursftp.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoursftp.app.data.TransferHistory
import com.yoursftp.app.ui.TransferHistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferHistoryScreen(
    vm: TransferHistoryViewModel,
    onBack: () -> Unit
) {
    val history by vm.history.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Transfer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { vm.clearHistory() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Bersihkan Riwayat")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Belum ada riwayat transfer", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(history) { item ->
                    TransferHistoryRow(item)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun TransferHistoryRow(item: TransferHistory) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    
    ListItem(
        headlineContent = {
            Text(item.fileName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text("${item.sourcePath} -> ${item.destPath}", fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(dateFormat.format(Date(item.timestamp)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingContent = {
            if (item.status == "SUCCESS") {
                Icon(Icons.Default.CheckCircle, contentDescription = "Sukses", tint = Color(0xFF4CAF50))
            } else {
                Icon(Icons.Default.Error, contentDescription = "Gagal", tint = MaterialTheme.colorScheme.error)
            }
        }
    )
}
