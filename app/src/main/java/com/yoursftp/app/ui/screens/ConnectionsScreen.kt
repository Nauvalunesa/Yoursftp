package com.yoursftp.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoursftp.app.data.Connection
import com.yoursftp.app.data.Protocol
import com.yoursftp.app.ui.ConnectionsViewModel
import kotlinx.coroutines.delay

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.yoursftp.app.ota.OtaState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    vm: ConnectionsViewModel,
    otaVm: com.yoursftp.app.ota.OtaViewModel,
    onAdd: () -> Unit,
    onEdit: (Connection) -> Unit,
    onConnect: (Connection) -> Unit,
    onOpenTerminal: (Connection) -> Unit,
    onOpenCleaner: () -> Unit = {},
    onOpenTransferHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val connections by vm.connections.collectAsState()
    val otaState by otaVm.state.collectAsState()
    var isManualChecking by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredConnections = remember(connections, searchQuery) {
        if (searchQuery.isBlank()) {
            connections
        } else {
            connections.filter { conn ->
                conn.name.contains(searchQuery, ignoreCase = true) ||
                conn.host.contains(searchQuery, ignoreCase = true) ||
                conn.username.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val sftpCount = remember(connections) { connections.count { it.protocol == Protocol.SFTP } }
    val ftpCount = remember(connections) { connections.count { it.protocol == Protocol.FTP || it.protocol == Protocol.FTPS } }
    val s3Count = remember(connections) { connections.count { it.protocol == Protocol.S3 } }

    LaunchedEffect(Unit) {
        otaVm.checkForUpdate()
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("YoursFTP", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                        val stats = "SFTP: $sftpCount | FTP/S: $ftpCount | S3: $s3Count"
                        Text(
                            "Kelola Koneksi Anda ($stats)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = onOpenCleaner) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = "Bersihkan Sampah",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onOpenTransferHistory) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Riwayat Transfer",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info & Donasi",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        isManualChecking = true
                        otaVm.checkForUpdate()
                    }) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Periksa Update",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Koneksi Baru", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (connections.isEmpty()) {
            EmptyStateView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onAdd = onAdd
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Spacer(Modifier.height(4.dp))
                }
                
                // Connection Search Field
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Cari koneksi...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (filteredConnections.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Koneksi tidak ditemukan",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    itemsIndexed(filteredConnections, key = { _, conn -> conn.id }) { index, conn ->
                        AnimateFadeInUp(delayMillis = index * 100) {
                            ConnectionCard(
                                conn = conn,
                                onConnect = { onConnect(conn) },
                                onEdit = { onEdit(conn) },
                                onDelete = { vm.delete(conn) },
                                onOpenTerminal = { onOpenTerminal(conn) }
                            )
                        }
                    }
                }
                
                item {
                    Spacer(Modifier.height(80.dp)) // padding for FAB
                }
            }
        }
    }

    // Render Ota dialogs
    when (val state = otaState) {
        is OtaState.Checking -> {
            if (isManualChecking) {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Memeriksa Update") },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Menghubungi server GitHub...")
                        }
                    },
                    confirmButton = {}
                )
            }
        }
        is OtaState.UpToDate -> {
            if (isManualChecking) {
                AlertDialog(
                    onDismissRequest = {
                        otaVm.resetState()
                        isManualChecking = false
                    },
                    title = { Text("Aplikasi Terkini") },
                    text = { Text("Anda sudah menggunakan versi terbaru (${otaVm.currentVersion}).") },
                    confirmButton = {
                        TextButton(onClick = {
                            otaVm.resetState()
                            isManualChecking = false
                        }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
        is OtaState.UpdateAvailable -> {
            AlertDialog(
                onDismissRequest = {
                    otaVm.resetState()
                    isManualChecking = false
                },
                title = { Text("Update Baru Tersedia!") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Versi baru ${state.version} sudah dirilis. Apakah Anda ingin mengunduh dan memasangnya sekarang?")
                        Spacer(Modifier.height(4.dp))
                        Text("Catatan Rilis:", fontWeight = FontWeight.Bold)
                        Box(
                          modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = state.changelog,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            otaVm.downloadAndInstall(state.downloadUrl)
                        }
                    ) {
                        Text("Unduh & Pasang")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            otaVm.resetState()
                            isManualChecking = false
                        }
                    ) {
                        Text("Nanti")
                    }
                }
            )
        }
        is OtaState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Mengunduh Update") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val pct = (state.progress * 100).toInt()
                        Text("Sedang mengunduh file APK: $pct%")
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {}
            )
        }
        is OtaState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = {
                    otaVm.resetState()
                    isManualChecking = false
                },
                title = { Text("Unduhan Selesai") },
                text = { Text("File update APK berhasil diunduh. Pasang sekarang?") },
                confirmButton = {
                    Button(
                        onClick = {
                            otaVm.triggerInstall(state.apkFile)
                        }
                    ) {
                        Text("Pasang")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            otaVm.resetState()
                            isManualChecking = false
                        }
                    ) {
                        Text("Batal")
                    }
                }
            )
        }
        is OtaState.Error -> {
            AlertDialog(
                onDismissRequest = {
                    otaVm.resetState()
                    isManualChecking = false
                },
                title = { Text("Gagal Memperbarui") },
                text = { Text("Terjadi kesalahan: ${state.message}") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            otaVm.resetState()
                            isManualChecking = false
                        }
                    ) {
                        Text("OK")
                    }
                }
            )
        }
        OtaState.Idle -> { /* do nothing */ }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Hubungi & Dukung", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Jika Anda membutuhkan bantuan atau konsultasi, silakan hubungi pengembang:")
                    
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/6281776348790"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("WhatsApp: 081776348790", color = Color.White)
                    }
                    
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Nvlunesa"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088cc)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Telegram: @Nvlunesa", color = Color.White)
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    Text("Dukung pengembangan aplikasi ini dengan donasi:", fontWeight = FontWeight.Bold)
                    
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mustikapayment.com/l/Payment"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Dukung via Mustika Payment", color = Color.White)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun ConnectionCard(
    conn: Connection,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenTerminal: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConnect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = conn.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val badgeColor = when (conn.protocol) {
                    Protocol.SFTP -> MaterialTheme.colorScheme.primary
                    Protocol.FTP -> Color(0xFFF97316) // Vibrant Orange
                    Protocol.FTPS -> Color(0xFF10B981) // Emerald Green
                    Protocol.S3 -> Color(0xFF8B5CF6) // Royal Purple/S3
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = conn.protocol.name,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Info rows
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = conn.host,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = conn.username,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (conn.protocol == Protocol.SFTP) {
                    IconButton(onClick = onOpenTerminal, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = "SSH Terminal",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Hapus",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateView(
    modifier: Modifier = Modifier,
    onAdd: () -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val gradient = Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary
            )
        )
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(brush = gradient, alpha = 0.15f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CloudQueue,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Belum Ada Koneksi",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Tambahkan koneksi SFTP, FTP, atau FTPS untuk mulai mengelola file VPS Anda.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onAdd,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Tambah Koneksi Pertama")
        }
    }
}

@Composable
private fun AnimateFadeInUp(
    delayMillis: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(500)) + slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = tween(500, easing = EaseOutBack)
        )
    ) {
        content()
    }
}
