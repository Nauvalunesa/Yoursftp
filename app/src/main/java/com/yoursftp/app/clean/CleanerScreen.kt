package com.yoursftp.app.clean

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerScreen(
    vm: CleanerViewModel,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Bersihkan Sampah", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    if (state.result != null && !state.scanning) {
                        IconButton(onClick = { vm.scan() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Pindai ulang")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Kartu ringkasan + tombol aksi.
            SummaryCard(vm, state, onRequestDelete = { confirmDelete = true })

            when {
                state.scanning -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Memindai...", fontWeight = FontWeight.Bold)
                        Text(
                            state.progressPath,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
                state.result == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Pindai penyimpanan untuk menemukan file sampah, cache, database WhatsApp, duplikat, dan file besar.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.scan() }) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Mulai Pindai")
                        }
                    }
                }
                else -> {
                    val result = state.result!!
                    if (result.groups.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Bersih! Tidak ada sampah ditemukan 🎉", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            result.groups.forEach { group ->
                                item(key = "h_${group.category.name}") {
                                    GroupHeader(group, state.selectedPaths, onToggle = { vm.toggleGroup(group) })
                                }
                                items(group.items, key = { it.path }) { item ->
                                    JunkRow(
                                        item = item,
                                        selected = item.path in state.selectedPaths,
                                        onToggle = { vm.toggle(item.path) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Hapus permanen?") },
            text = {
                Text("${state.selectedPaths.size} item (${CleanerViewModel.humanSize(vm.selectedSize())}) akan dihapus permanen dan tidak bisa dikembalikan.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteSelected()
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun SummaryCard(vm: CleanerViewModel, state: CleanerState, onRequestDelete: () -> Unit) {
    val selectedSize = remember(state.selectedPaths, state.result) { vm.selectedSize() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (state.result != null)
                            "${state.result!!.totalCount} item · ${CleanerViewModel.humanSize(state.result!!.totalSize)}"
                        else "Belum dipindai",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Terpilih: ${CleanerViewModel.humanSize(selectedSize)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                if (state.deleting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Button(
                        onClick = onRequestDelete,
                        enabled = state.selectedPaths.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bersihkan")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.cleanAppCache() },
                enabled = !state.deleting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Bersihkan cache aplikasi ini (${CleanerViewModel.humanSize(state.appCacheSize)})", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun GroupHeader(group: JunkGroup, selected: Set<String>, onToggle: () -> Unit) {
    val allSelected = group.items.all { it.path in selected }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable { onToggle() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(iconFor(group.category), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(group.category.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("${group.items.size} item · ${CleanerViewModel.humanSize(group.totalSize)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = if (allSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun JunkRow(item: JunkItem, selected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(start = 32.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                item.name,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.category == JunkCategory.APP_CACHE && item.packageName != null) {
                val folderCount = item.path.count { it == '\n' } + 1
                Text(
                    "${item.packageName} • $folderCount folder",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Show details (rincian path)
            if (item.category == JunkCategory.APP_CACHE || item.category == JunkCategory.DUPLICATE || item.category == JunkCategory.LARGE_FILE) {
                Text(
                    item.path.replace("\n", "\n• "),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 11.sp
                )
            }
        }
        Text(
            CleanerViewModel.humanSize(item.size),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun iconFor(cat: JunkCategory): ImageVector = when (cat) {
    JunkCategory.APP_CACHE -> Icons.Default.Apps
    JunkCategory.WHATSAPP_DB -> Icons.Default.Chat
    JunkCategory.TEMP_CACHE -> Icons.Default.Cached
    JunkCategory.LOG -> Icons.Default.Description
    JunkCategory.EMPTY_FOLDER -> Icons.Default.FolderOff
    JunkCategory.THUMBNAIL -> Icons.Default.Image
    JunkCategory.APK -> Icons.Default.Android
    JunkCategory.LARGE_FILE -> Icons.Default.Straighten
    JunkCategory.DUPLICATE -> Icons.Default.FileCopy
}
