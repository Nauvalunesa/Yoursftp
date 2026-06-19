package com.yoursftp.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yoursftp.app.data.Connection
import com.yoursftp.app.data.RemoteFile
import com.yoursftp.app.ui.BrowserViewModel
import com.yoursftp.app.ui.FilterType
import com.yoursftp.app.ui.SortOrder
import com.yoursftp.app.ui.TabState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun looksEditable(name: String): Boolean {
    val lower = name.lowercase()
    val exts = listOf(".txt", ".md", ".json", ".xml", ".html", ".htm", ".css", ".js",
        ".ts", ".kt", ".java", ".py", ".sh", ".yml", ".yaml", ".ini", ".conf", ".log",
        ".csv", ".php", ".rb", ".go", ".c", ".cpp", ".h", ".sql", ".env", ".properties",
        ".gradle", ".toml", ".gitignore")
    return exts.any { lower.endsWith(it) } || !lower.contains(".")
}

sealed class FileIcon {
    data class Vector(val imageVector: ImageVector) : FileIcon()
    data class Resource(val resId: Int) : FileIcon()
}

private fun getFileColorAndIcon(name: String, isDirectory: Boolean): Pair<FileIcon, Color> {
    if (isDirectory) {
        return FileIcon.Vector(Icons.Default.Folder) to Color(0xFFFFB300) // Deep warm amber for folders
    }
    val lower = name.lowercase()
    return when {
        // Kotlin
        lower.endsWith(".kt") -> FileIcon.Resource(com.yoursftp.app.R.drawable.ic_kotlin) to Color(0xFF8F54F9)
        // Java
        lower.endsWith(".java") -> FileIcon.Resource(com.yoursftp.app.R.drawable.ic_java) to Color(0xFF5382A1)
        // Python
        lower.endsWith(".py") -> FileIcon.Resource(com.yoursftp.app.R.drawable.ic_python) to Color(0xFF3776AB)
        // JavaScript
        lower.endsWith(".js") -> FileIcon.Resource(com.yoursftp.app.R.drawable.ic_javascript) to Color(0xFFF7DF1E)
        // TypeScript
        lower.endsWith(".ts") -> FileIcon.Resource(com.yoursftp.app.R.drawable.ic_typescript) to Color(0xFF3178C6)
        // Golang
        lower.endsWith(".go") -> FileIcon.Resource(com.yoursftp.app.R.drawable.ic_golang) to Color(0xFF00ADD8)
        // PHP
        lower.endsWith(".php") -> FileIcon.Resource(com.yoursftp.app.R.drawable.ic_php) to Color(0xFF777BB4)
        // C/C++
        lower.endsWith(".c") -> FileIcon.Resource(com.yoursftp.app.R.drawable.ic_c) to Color(0xFF00599C)
        lower.endsWith(".cpp") || lower.endsWith(".h") || lower.endsWith(".hpp") -> 
            FileIcon.Resource(com.yoursftp.app.R.drawable.ic_cpp) to Color(0xFF00599C)
        // HTML
        lower.endsWith(".html") || lower.endsWith(".xhtml") -> FileIcon.Resource(com.yoursftp.app.R.drawable.ic_html) to Color(0xFFE34F26)
        // CSS
        lower.endsWith(".css") -> FileIcon.Resource(com.yoursftp.app.R.drawable.ic_css) to Color(0xFF1572B6)

        // Other programming languages (fallback to default code icon)
        lower.endsWith(".cs") || lower.endsWith(".rb") || lower.endsWith(".swift") || 
        lower.endsWith(".rs") || lower.endsWith(".scala") || lower.endsWith(".dart") -> 
            FileIcon.Vector(Icons.Default.Code) to Color(0xFF818CF8)

        // Terminal Scripts
        lower.endsWith(".sh") || lower.endsWith(".bat") || lower.endsWith(".ps1") || 
        lower.endsWith(".cmd") || lower.endsWith(".bash") -> FileIcon.Vector(Icons.Default.Terminal) to Color(0xFF34D399)

        // Structured Data / Config files
        lower.endsWith(".json") || lower.endsWith(".toml") || lower.endsWith(".yaml") || lower.endsWith(".yml") -> 
            FileIcon.Vector(Icons.Default.DataObject) to Color(0xFFFF9800)

        // Web layout fallback (like xml)
        lower.endsWith(".xml") -> FileIcon.Vector(Icons.Default.Html) to Color(0xFFFB7185)

        // Archives
        lower.endsWith(".zip") || lower.endsWith(".tar") || lower.endsWith(".gz") || 
        lower.endsWith(".rar") || lower.endsWith(".7z") || lower.endsWith(".tgz") || 
        lower.endsWith(".bz2") || lower.endsWith(".xz") -> FileIcon.Vector(Icons.Default.Archive) to Color(0xFFC084FC)

        // Images
        lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || 
        lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp") || 
        lower.endsWith(".ico") || lower.endsWith(".svg") || lower.endsWith(".tiff") -> 
            FileIcon.Vector(Icons.Default.Image) to Color(0xFF38BDF8)

        // Audio
        lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg") || 
        lower.endsWith(".flac") || lower.endsWith(".m4a") || lower.endsWith(".aac") || 
        lower.endsWith(".wma") || lower.endsWith(".mid") -> FileIcon.Vector(Icons.Default.AudioFile) to Color(0xFF2DD4BF)

        // Videos
        lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") || 
        lower.endsWith(".mov") || lower.endsWith(".webm") || lower.endsWith(".flv") || 
        lower.endsWith(".wmv") || lower.endsWith(".3gp") -> FileIcon.Vector(Icons.Default.VideoFile) to Color(0xFF60A5FA)

        // PDF & eBooks
        lower.endsWith(".pdf") || lower.endsWith(".epub") || lower.endsWith(".mobi") || 
        lower.endsWith(".fb2") -> FileIcon.Vector(Icons.Default.PictureAsPdf) to Color(0xFFF43F5E)

        // Android packages
        lower.endsWith(".apk") || lower.endsWith(".aab") -> FileIcon.Vector(Icons.Default.Android) to Color(0xFF4CAF50)

        // Text files & generic logs
        lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".log") || 
        lower.endsWith(".conf") || lower.endsWith(".ini") || lower.endsWith(".properties") || 
        lower.endsWith(".env") || lower.endsWith(".info") || name.startsWith(".") -> 
            FileIcon.Vector(Icons.Default.Description) to Color(0xFF38BDF8)

        // Word documents
        lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".odt") || 
        lower.endsWith(".rtf") -> FileIcon.Vector(Icons.Default.Article) to Color(0xFF2B579A)

        // Spreadsheets
        lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv") || 
        lower.endsWith(".ods") -> FileIcon.Vector(Icons.Default.TableChart) to Color(0xFF217346)

        // Presentations
        lower.endsWith(".ppt") || lower.endsWith(".pptx") || lower.endsWith(".odp") -> 
            FileIcon.Vector(Icons.Default.Slideshow) to Color(0xFFD24726)

        // Databases
        lower.endsWith(".db") || lower.endsWith(".sql") || lower.endsWith(".sqlite") || 
        lower.endsWith(".mdb") || lower.endsWith(".sqlitedb") -> FileIcon.Vector(Icons.Default.Storage) to Color(0xFF64748B)

        // Encryption keys & certificates
        lower.endsWith(".key") || lower.endsWith(".pub") || lower.endsWith(".pem") || 
        lower.endsWith(".crt") || lower.endsWith(".der") || lower.endsWith(".p12") || 
        lower.endsWith(".cer") -> FileIcon.Vector(Icons.Default.VpnKey) to Color(0xFFF59E0B)

        // Executables / Installers
        lower.endsWith(".exe") || lower.endsWith(".msi") || lower.endsWith(".bin") || 
        lower.endsWith(".run") || lower.endsWith(".dmg") || lower.endsWith(".pkg") || 
        lower.endsWith(".app") || lower.endsWith(".jar") -> FileIcon.Vector(Icons.Default.Settings) to Color(0xFFEF4444)

        // Default fallback file
        else -> FileIcon.Vector(Icons.Default.InsertDriveFile) to Color(0xFF94A3B8)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    vm: BrowserViewModel,
    connectionId: Long,
    onBack: () -> Unit,
    onEditFile: (String, String) -> Unit,
    onOpenTerminal: (Long) -> Unit,
    onOpenDb: (String, String) -> Unit = { _, _ -> }
) {
    val state by vm.state.collectAsState()
    val connections by vm.connections.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var dialog by remember { mutableStateOf<BrowserDialog?>(null) }
    var isSplitMode by remember { mutableStateOf(true) } // Default to split pane (2 panels) as requested

    // Permission state
    var hasStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasStoragePermission = permissions.values.all { it }
    }

    val requestStoragePermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                context.startActivity(intent)
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasStoragePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                }
                if (hasStoragePermission) {
                    if (state.tab1.connectionId == -1L) vm.refresh(1)
                    if (state.tab2.connectionId == -1L) vm.refresh(2)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(connectionId) {
        vm.connect(1, -1L)
        vm.connect(2, connectionId)
    }

    LaunchedEffect(state.globalError, state.tab1.error, state.tab2.error) {
        state.globalError?.let {
            snackbar.showSnackbar(it)
            vm.clearGlobalError()
        }
        state.tab1.error?.let {
            snackbar.showSnackbar("Tab Kiri: $it")
            vm.clearTabError(1)
        }
        state.tab2.error?.let {
            snackbar.showSnackbar("Tab Kanan: $it")
            vm.clearTabError(2)
        }
    }

    val activeTab = state.activeTab

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Dual File Manager", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = { vm.disconnect(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    val activeTabState = if (activeTab == 1) state.tab1 else state.tab2
                    if (activeTabState.connectionId > 0 && isSftp(activeTabState.connectionId, connections)) {
                        IconButton(onClick = { onOpenTerminal(activeTabState.connectionId) }) {
                            Icon(Icons.Default.Terminal, contentDescription = "SSH Terminal")
                        }
                    }
                    if (!isLandscape) {
                        IconButton(onClick = { isSplitMode = !isSplitMode }) {
                            Icon(
                                imageVector = if (isSplitMode) Icons.Default.Fullscreen else Icons.Default.Splitscreen,
                                contentDescription = if (isSplitMode) "Mode Layar Penuh" else "Mode Layar Belah",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { dialog = BrowserDialog.NewFolder(activeTab) }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Folder baru")
                    }
                    IconButton(onClick = { dialog = BrowserDialog.NewFile(activeTab) }) {
                        Icon(Icons.Default.NoteAdd, contentDescription = "File baru")
                    }
                    IconButton(onClick = { vm.refresh(activeTab) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Muat ulang")
                    }
                }
            )
        }
    ) { padding ->
        val showSplit = isSplitMode || isLandscape

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (showSplit) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Left Pane (Tab 1)
                    val leftBorder = if (activeTab == 1) {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .then(leftBorder)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        FilePane(
                            tabIndex = 1,
                            isActive = activeTab == 1,
                            tabState = state.tab1,
                            connections = connections,
                            hasStoragePermission = hasStoragePermission,
                            onOpen = { file ->
                                vm.open(
                                    tabIndex = 1,
                                    file = file,
                                    context = context,
                                    onEditFile = onEditFile,
                                    onOpenExternal = { localFile, mime ->
                                        openLocalFileWithIntent(context, localFile, mime)
                                    },
                                    onOpenDb = onOpenDb
                                )
                            },
                            onRename = { file -> dialog = BrowserDialog.Rename(1, file) },
                            onDelete = { file -> dialog = BrowserDialog.ConfirmDelete(1, file) },
                            onEdit = { file ->
                                vm.open(
                                    tabIndex = 1,
                                    file = file,
                                    context = context,
                                    onEditFile = onEditFile,
                                    onOpenExternal = { localFile, mime ->
                                        openLocalFileWithIntent(context, localFile, mime)
                                    },
                                    onOpenDb = onOpenDb
                                )
                            },
                            onTransfer = { file ->
                                if (vm.hasTransferConflict(1, file)) {
                                    dialog = BrowserDialog.TransferConflict(1, file)
                                } else {
                                    vm.transferFile(1, file, com.yoursftp.app.ui.OverwriteMode.OVERWRITE)
                                }
                            },
                            onNavigateUp = { vm.navigateUp(1) },
                            onSwitchConnection = { connId -> vm.connect(1, connId) },
                            onFilterQueryChange = { q -> vm.setFilterQuery(1, q) },
                            onSortOrderChange = { order -> vm.setSortOrder(1, order) },
                            onFilterTypeChange = { type -> vm.setFilterType(1, type) },
                            onFoldersFirstChange = { foldersFirst -> vm.setFoldersFirst(1, foldersFirst) },
                            onRequestPermission = requestStoragePermission,
                            onFocusPane = { vm.setActiveTab(1) }
                        )
                    }

                    Spacer(Modifier.width(3.dp))

                    // Right Pane (Tab 2)
                    val rightBorder = if (activeTab == 2) {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .then(rightBorder)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        FilePane(
                            tabIndex = 2,
                            isActive = activeTab == 2,
                            tabState = state.tab2,
                            connections = connections,
                            hasStoragePermission = hasStoragePermission,
                            onOpen = { file ->
                                vm.open(
                                    tabIndex = 2,
                                    file = file,
                                    context = context,
                                    onEditFile = onEditFile,
                                    onOpenExternal = { localFile, mime ->
                                        openLocalFileWithIntent(context, localFile, mime)
                                    },
                                    onOpenDb = onOpenDb
                                )
                            },
                            onRename = { file -> dialog = BrowserDialog.Rename(2, file) },
                            onDelete = { file -> dialog = BrowserDialog.ConfirmDelete(2, file) },
                            onEdit = { file ->
                                vm.open(
                                    tabIndex = 2,
                                    file = file,
                                    context = context,
                                    onEditFile = onEditFile,
                                    onOpenExternal = { localFile, mime ->
                                        openLocalFileWithIntent(context, localFile, mime)
                                    },
                                    onOpenDb = onOpenDb
                                )
                            },
                            onTransfer = { file ->
                                if (vm.hasTransferConflict(2, file)) {
                                    dialog = BrowserDialog.TransferConflict(2, file)
                                } else {
                                    vm.transferFile(2, file, com.yoursftp.app.ui.OverwriteMode.OVERWRITE)
                                }
                            },
                            onNavigateUp = { vm.navigateUp(2) },
                            onSwitchConnection = { connId -> vm.connect(2, connId) },
                            onFilterQueryChange = { q -> vm.setFilterQuery(2, q) },
                            onSortOrderChange = { order -> vm.setSortOrder(2, order) },
                            onFilterTypeChange = { type -> vm.setFilterType(2, type) },
                            onFoldersFirstChange = { foldersFirst -> vm.setFoldersFirst(2, foldersFirst) },
                            onRequestPermission = requestStoragePermission,
                            onFocusPane = { vm.setActiveTab(2) }
                        )
                    }
                }
            } else {
                // Single Pane Mode with beautiful Tab Layout
                TabRow(
                    selectedTabIndex = activeTab - 1,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = activeTab == 1,
                        onClick = { vm.setActiveTab(1) },
                        text = { Text(state.tab1.connectionName, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = { vm.setActiveTab(2) },
                        text = { Text(state.tab2.connectionName, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }

                Spacer(Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (activeTab == 1) {
                        FilePane(
                            tabIndex = 1,
                            isActive = true,
                            tabState = state.tab1,
                            connections = connections,
                            hasStoragePermission = hasStoragePermission,
                            onOpen = { file ->
                                vm.open(
                                    tabIndex = 1,
                                    file = file,
                                    context = context,
                                    onEditFile = onEditFile,
                                    onOpenExternal = { localFile, mime ->
                                        openLocalFileWithIntent(context, localFile, mime)
                                    },
                                    onOpenDb = onOpenDb
                                )
                            },
                            onRename = { file -> dialog = BrowserDialog.Rename(1, file) },
                            onDelete = { file -> dialog = BrowserDialog.ConfirmDelete(1, file) },
                            onEdit = { file ->
                                vm.open(
                                    tabIndex = 1,
                                    file = file,
                                    context = context,
                                    onEditFile = onEditFile,
                                    onOpenExternal = { localFile, mime ->
                                        openLocalFileWithIntent(context, localFile, mime)
                                    },
                                    onOpenDb = onOpenDb
                                )
                            },
                            onTransfer = { file ->
                                if (vm.hasTransferConflict(1, file)) {
                                    dialog = BrowserDialog.TransferConflict(1, file)
                                } else {
                                    vm.transferFile(1, file, com.yoursftp.app.ui.OverwriteMode.OVERWRITE)
                                }
                            },
                            onNavigateUp = { vm.navigateUp(1) },
                            onSwitchConnection = { connId -> vm.connect(1, connId) },
                            onFilterQueryChange = { q -> vm.setFilterQuery(1, q) },
                            onSortOrderChange = { order -> vm.setSortOrder(1, order) },
                            onFilterTypeChange = { type -> vm.setFilterType(1, type) },
                            onFoldersFirstChange = { foldersFirst -> vm.setFoldersFirst(1, foldersFirst) },
                            onRequestPermission = requestStoragePermission,
                            onFocusPane = { vm.setActiveTab(1) }
                        )
                    } else {
                        FilePane(
                            tabIndex = 2,
                            isActive = true,
                            tabState = state.tab2,
                            connections = connections,
                            hasStoragePermission = hasStoragePermission,
                            onOpen = { file ->
                                vm.open(
                                    tabIndex = 2,
                                    file = file,
                                    context = context,
                                    onEditFile = onEditFile,
                                    onOpenExternal = { localFile, mime ->
                                        openLocalFileWithIntent(context, localFile, mime)
                                    },
                                    onOpenDb = onOpenDb
                                )
                            },
                            onRename = { file -> dialog = BrowserDialog.Rename(2, file) },
                            onDelete = { file -> dialog = BrowserDialog.ConfirmDelete(2, file) },
                            onEdit = { file ->
                                vm.open(
                                    tabIndex = 2,
                                    file = file,
                                    context = context,
                                    onEditFile = onEditFile,
                                    onOpenExternal = { localFile, mime ->
                                        openLocalFileWithIntent(context, localFile, mime)
                                    },
                                    onOpenDb = onOpenDb
                                )
                            },
                            onTransfer = { file ->
                                if (vm.hasTransferConflict(2, file)) {
                                    dialog = BrowserDialog.TransferConflict(2, file)
                                } else {
                                    vm.transferFile(2, file, com.yoursftp.app.ui.OverwriteMode.OVERWRITE)
                                }
                            },
                            onNavigateUp = { vm.navigateUp(2) },
                            onSwitchConnection = { connId -> vm.connect(2, connId) },
                            onFilterQueryChange = { q -> vm.setFilterQuery(2, q) },
                            onSortOrderChange = { order -> vm.setSortOrder(2, order) },
                            onFilterTypeChange = { type -> vm.setFilterType(2, type) },
                            onFoldersFirstChange = { foldersFirst -> vm.setFoldersFirst(2, foldersFirst) },
                            onRequestPermission = requestStoragePermission,
                            onFocusPane = { vm.setActiveTab(2) }
                        )
                    }
                }
            }
        }
    }

    // Beautiful Slate Transfer Progress Card Dialog (Expanded Size)
    if (state.transferProgress.isActive) {
        val progress = state.transferProgress
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Transfer File",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Text(
                        text = progress.currentFileName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )

                    val progressVal = if (progress.totalBytes > 0) progress.percent / 100f else 0f
                    if (progress.totalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { progressVal },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                        )
                    } else {
                        LinearProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val transferredText = if (progress.totalBytes > 0) {
                            "${humanSize(progress.bytesTransferred)} / ${humanSize(progress.totalBytes)}"
                        } else {
                            humanSize(progress.bytesTransferred)
                        }
                        Text(
                            text = transferredText,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (progress.totalBytes > 0) {
                            Text(
                                text = "${progress.percent}%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = formatSpeed(progress.speedBytesPerSec),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (progress.totalBytes > 0 && progress.etaSeconds >= 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = formatEta(progress.etaSeconds),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        is BrowserDialog.NewFolder -> TextInputDialog(
            title = "Folder Baru di ${if (d.tabIndex == 1) "Tab Kiri" else "Tab Kanan"}",
            label = "Nama folder",
            onConfirm = { vm.createFolder(d.tabIndex, it); dialog = null },
            onDismiss = { dialog = null }
        )
        is BrowserDialog.NewFile -> TextInputDialog(
            title = "File Baru di ${if (d.tabIndex == 1) "Tab Kiri" else "Tab Kanan"}",
            label = "Nama file",
            onConfirm = { vm.createTextFile(d.tabIndex, it); dialog = null },
            onDismiss = { dialog = null }
        )
        is BrowserDialog.Rename -> TextInputDialog(
            title = "Ganti Nama",
            label = "Nama baru",
            initial = d.file.name,
            onConfirm = { vm.rename(d.tabIndex, d.file, it); dialog = null },
            onDismiss = { dialog = null }
        )
        is BrowserDialog.ConfirmDelete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Hapus") },
            text = { Text("Apakah Anda yakin ingin menghapus \"${d.file.name}\"?") },
            confirmButton = {
                TextButton(onClick = { vm.delete(d.tabIndex, d.file); dialog = null }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("Batal") }
            }
        )
        is BrowserDialog.TransferConflict -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("File Sudah Ada", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("File \"${d.file.name}\" sudah ada di direktori tujuan. Pilih tindakan yang ingin Anda lakukan.") },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = { dialog = null }) {
                        Text("Batal", color = MaterialTheme.colorScheme.outline)
                    }
                    TextButton(onClick = {
                        vm.transferFile(d.sourceTabIndex, d.file, com.yoursftp.app.ui.OverwriteMode.RENAME)
                        dialog = null
                    }) {
                        Text("Bikin Baru")
                    }
                    Button(onClick = {
                        vm.transferFile(d.sourceTabIndex, d.file, com.yoursftp.app.ui.OverwriteMode.OVERWRITE)
                        dialog = null
                    }) {
                        Text("Timpa")
                    }
                }
            }
        )
        null -> {}
    }
}

@Composable
fun FilePane(
    tabIndex: Int,
    isActive: Boolean,
    tabState: TabState,
    connections: List<Connection>,
    hasStoragePermission: Boolean,
    onOpen: (RemoteFile) -> Unit,
    onRename: (RemoteFile) -> Unit,
    onDelete: (RemoteFile) -> Unit,
    onEdit: (RemoteFile) -> Unit,
    onTransfer: (RemoteFile) -> Unit,
    onNavigateUp: () -> Unit,
    onSwitchConnection: (Long) -> Unit,
    onFilterQueryChange: (String) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onFilterTypeChange: (FilterType) -> Unit,
    onFoldersFirstChange: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
    onFocusPane: () -> Unit
) {
    var connectionMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFocusPane
            )
    ) {
        val headerBg = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
        val headerContentColor = if (isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBg)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { connectionMenuOpen = true }
            ) {
                Icon(
                    if (tabState.connectionId == -1L) Icons.Default.PhoneAndroid else Icons.Default.Dns,
                    contentDescription = null,
                    tint = headerContentColor,
                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                )
                Text(
                    text = tabState.connectionName,
                    color = headerContentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = headerContentColor, modifier = Modifier.size(16.dp))
            }

            DropdownMenu(
                expanded = connectionMenuOpen,
                onDismissRequest = { connectionMenuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Penyimpanan Lokal", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        connectionMenuOpen = false
                        onSwitchConnection(-1L)
                    }
                )
                connections.forEach { conn ->
                    DropdownMenuItem(
                        text = { Text(conn.name, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            connectionMenuOpen = false
                            onSwitchConnection(conn.id)
                        }
                    )
                }
            }
        }

        // Filter / Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = tabState.filterQuery,
                onValueChange = onFilterQueryChange,
                placeholder = { Text("Filter...", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp)) },
                trailingIcon = {
                    if (tabState.filterQuery.isNotEmpty()) {
                        IconButton(onClick = { onFilterQueryChange("") }, modifier = Modifier.size(16.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                textStyle = TextStyle(fontSize = 12.sp),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                singleLine = true
            )
        }

        // Premium Sort & Filter Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Sort by Name Chip
            val isNameActive = tabState.sortOrder == SortOrder.NAME_ASC || tabState.sortOrder == SortOrder.NAME_DESC
            val nameIcon = when (tabState.sortOrder) {
                SortOrder.NAME_ASC -> "↑"
                SortOrder.NAME_DESC -> "↓"
                else -> "⇅"
            }
            SortChip(
                label = "Nama $nameIcon",
                isActive = isNameActive,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (tabState.sortOrder == SortOrder.NAME_ASC) {
                        onSortOrderChange(SortOrder.NAME_DESC)
                    } else {
                        onSortOrderChange(SortOrder.NAME_ASC)
                    }
                }
            )

            // Sort by Size Chip
            val isSizeActive = tabState.sortOrder == SortOrder.SIZE_ASC || tabState.sortOrder == SortOrder.SIZE_DESC
            val sizeIcon = when (tabState.sortOrder) {
                SortOrder.SIZE_ASC -> "↑"
                SortOrder.SIZE_DESC -> "↓"
                else -> "⇅"
            }
            SortChip(
                label = "Ukuran $sizeIcon",
                isActive = isSizeActive,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (tabState.sortOrder == SortOrder.SIZE_DESC) {
                        onSortOrderChange(SortOrder.SIZE_ASC)
                    } else {
                        onSortOrderChange(SortOrder.SIZE_DESC)
                    }
                }
            )

            // Sort by Date Chip
            val isDateActive = tabState.sortOrder == SortOrder.DATE_ASC || tabState.sortOrder == SortOrder.DATE_DESC
            val dateIcon = when (tabState.sortOrder) {
                SortOrder.DATE_ASC -> "↑"
                SortOrder.DATE_DESC -> "↓"
                else -> "⇅"
            }
            SortChip(
                label = "Tgl $dateIcon",
                isActive = isDateActive,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (tabState.sortOrder == SortOrder.DATE_DESC) {
                        onSortOrderChange(SortOrder.DATE_ASC)
                    } else {
                        onSortOrderChange(SortOrder.DATE_DESC)
                    }
                }
            )



            // Filter Type Toggle
            val filterTypeIcon = when (tabState.filterType) {
                FilterType.ALL -> Icons.Default.FilterAlt
                FilterType.FOLDERS_ONLY -> Icons.Default.FolderOpen
                FilterType.FILES_ONLY -> Icons.Default.InsertDriveFile
            }
            val isFilterActive = tabState.filterType != FilterType.ALL
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isFilterActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable {
                        val nextType = when (tabState.filterType) {
                            FilterType.ALL -> FilterType.FOLDERS_ONLY
                            FilterType.FOLDERS_ONLY -> FilterType.FILES_ONLY
                            FilterType.FILES_ONLY -> FilterType.ALL
                        }
                        onFilterTypeChange(nextType)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = filterTypeIcon,
                    contentDescription = "Filter tipe",
                    tint = if (isFilterActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val rootPath = if (tabState.connectionId == -1L) BrowserViewModel.getLocalRootPath() else "/"
                IconButton(
                    onClick = onNavigateUp,
                    enabled = tabState.currentPath != rootPath,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Ke atas", modifier = Modifier.size(16.dp))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = tabState.currentPath,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        maxLines = 1
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            if (tabState.connectionId == -1L && !hasStoragePermission) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Butuh Izin File",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Izinkan akses file untuk folder lokal.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onRequestPermission,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Izinkan", fontSize = 10.sp)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(tabState.filteredFiles, key = { it.path }) { file ->
                        FileRow(
                            file = file,
                            tabIndex = tabIndex,
                            onOpen = { onOpen(file) },
                            onRename = { onRename(file) },
                            onDelete = { onDelete(file) },
                            onEdit = { onEdit(file) },
                            onTransfer = { onTransfer(file) }
                        )
                    }
                }

                if (!tabState.loading && tabState.filteredFiles.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (tabState.files.isEmpty()) "Folder kosong" else "Tidak ditemukan",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (tabState.loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    file: RemoteFile,
    tabIndex: Int,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onTransfer: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val (icon, tint) = getFileColorAndIcon(file.name, file.isDirectory)

    ListItem(
        modifier = Modifier
            .clickable { onOpen() }
            .padding(horizontal = 2.dp, vertical = 1.dp),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                when (icon) {
                    is FileIcon.Vector -> {
                        Icon(
                            icon.imageVector,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    is FileIcon.Resource -> {
                        Icon(
                            painter = painterResource(id = icon.resId),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        headlineContent = {
            Text(
                file.name,
                maxLines = 1,
                fontSize = 12.sp,
                color = if (file.isDirectory) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                fontWeight = if (file.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.basicMarquee()
            )
        },
        supportingContent = {
            val date = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(file.lastModified))
            val details = if (file.isDirectory) {
                date
            } else {
                "${humanSize(file.size)} · $date"
            }
            Text(details, fontSize = 9.sp)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(
                        onClick = { menu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Aksi", modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (!file.isDirectory && looksEditable(file.name)) {
                            DropdownMenuItem(
                                text = { Text("Edit File", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                onClick = { menu = false; onEdit() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Kirim ke sebelah", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (tabIndex == 1) Icons.AutoMirrored.Filled.ArrowForward else Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            onClick = { menu = false; onTransfer() }
                        )
                        DropdownMenuItem(
                            text = { Text("Ganti Nama", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            onClick = { menu = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Hapus", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            onClick = { menu = false; onDelete() }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(label, fontSize = 12.sp) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 13.sp)
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

private fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
    return String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0 B/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB/s", kb)
    val mb = kb / 1024.0
    return String.format(Locale.getDefault(), "%.1f MB/s", mb)
}

private fun formatEta(seconds: Int): String {
    if (seconds < 0) return "Sisa waktu: --:--"
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.getDefault(), "Sisa waktu: %02d:%02d", m, s)
}

private fun isSftp(connectionId: Long, connections: List<Connection>): Boolean {
    val conn = connections.find { it.id == connectionId }
    return conn?.protocol == com.yoursftp.app.data.Protocol.SFTP
}

private sealed interface BrowserDialog {
    data class NewFolder(val tabIndex: Int) : BrowserDialog
    data class NewFile(val tabIndex: Int) : BrowserDialog
    data class Rename(val tabIndex: Int, val file: RemoteFile) : BrowserDialog
    data class ConfirmDelete(val tabIndex: Int, val file: RemoteFile) : BrowserDialog
    data class TransferConflict(val sourceTabIndex: Int, val file: RemoteFile) : BrowserDialog
}

@Composable
private fun SortChip(
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val fg = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    
    Box(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = fg,
            maxLines = 1
        )
    }
}

private fun openLocalFileWithIntent(context: android.content.Context, file: java.io.File, mimeType: String) {
    try {
        val authority = "${context.packageName}.fileprovider"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        if (mimeType == "application/vnd.android.package-archive") {
            context.startActivity(intent)
        } else {
            context.startActivity(android.content.Intent.createChooser(intent, "Buka dengan...").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Tidak dapat membuka file: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}
