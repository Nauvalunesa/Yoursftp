package com.yoursftp.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yoursftp.app.data.Connection
import com.yoursftp.app.data.Protocol
import com.yoursftp.app.ui.EditConnectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditConnectionScreen(
    vm: EditConnectionViewModel,
    connectionId: Long,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(Protocol.SFTP) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var initialPath by remember { mutableStateOf("/") }
    var passive by remember { mutableStateOf(true) }
    var privateKey by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var portEditedManually by remember { mutableStateOf(false) }

    var testingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    LaunchedEffect(connectionId) {
        val c = vm.load(connectionId)
        if (c != null) {
            name = c.name; protocol = c.protocol; host = c.host
            port = c.port.toString(); username = c.username; password = c.password
            initialPath = c.initialPath; passive = c.passiveMode
            privateKey = c.privateKey ?: ""
            passphrase = c.passphrase ?: ""
            portEditedManually = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (connectionId > 0) "Edit Koneksi" else "Koneksi Baru", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Protocol Selector Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Protokol", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Protocol.entries.forEach { p ->
                            val selected = protocol == p
                            val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            val borderModifier = if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .then(borderModifier)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(containerColor)
                                    .clickable {
                                        protocol = p
                                        if (!portEditedManually) port = Connection.defaultPort(p).toString()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                  Text(p.name, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Server Address Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Detail Server", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Nama Koneksi (opsional)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = host, onValueChange = { host = it },
                            label = { Text(if (protocol == Protocol.S3) "Endpoint S3 / Host" else "Host / IP") },
                            placeholder = { if (protocol == Protocol.S3) Text("e.g. accountid.r2.cloudflarestorage.com") },
                            supportingText = {
                                if (protocol == Protocol.S3) {
                                    Text("Contoh: account.r2.cloudflarestorage.com atau s3.amazonaws.com")
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(3f)
                        )
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it.filter(Char::isDigit); portEditedManually = true },
                            label = { Text("Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }
            }

            // Credentials Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Kredensial", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    
                    OutlinedTextField(
                        value = username, onValueChange = { username = it },
                        label = { Text(if (protocol == Protocol.S3) "Access Key ID" else "Username") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text(if (protocol == Protocol.S3) "Secret Access Key" else "Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (protocol == Protocol.SFTP) {
                        OutlinedTextField(
                            value = privateKey, onValueChange = { privateKey = it },
                            label = { Text("Private Key (SSH PEM/OpenSSH, opsional)") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 5,
                            placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----\n...") }
                        )
                        OutlinedTextField(
                            value = passphrase, onValueChange = { passphrase = it },
                            label = { Text("Passphrase Private Key (opsional)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Advanced Settings Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pengaturan Lanjutan", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    
                    OutlinedTextField(
                        value = initialPath, onValueChange = { initialPath = it },
                        label = { Text(if (protocol == Protocol.S3) "Nama Bucket S3" else "Path Awal") },
                        placeholder = { if (protocol == Protocol.S3) Text("e.g. nama-bucket") },
                        supportingText = {
                            if (protocol == Protocol.S3) {
                                Text("Masukkan nama bucket penyimpanan Anda")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (protocol == Protocol.FTP || protocol == Protocol.FTPS) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Switch(checked = passive, onCheckedChange = { passive = it })
                            Spacer(Modifier.width(12.dp))
                            Text("Mode Pasif (FTP)", fontSize = 13.sp)
                        }
                    }
                }
            }

            // Connection Test Result Card
            testResult?.let { (success, msg) ->
                val containerColor = if (success) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                val contentColor = if (success) Color(0xFF2E7D32) else Color(0xFFC62828)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = contentColor
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = msg,
                            color = contentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Dual Action Buttons (Test & Save)
            val credentialsValid = if (protocol == Protocol.SFTP) {
                password.isNotBlank() || privateKey.isNotBlank()
            } else {
                password.isNotBlank()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        testingConnection = true
                        testResult = null
                        vm.testConnection(
                            protocol = protocol, host = host,
                            port = port.toIntOrNull() ?: Connection.defaultPort(protocol),
                            username = username, password = password,
                            initialPath = initialPath, passiveMode = passive,
                            privateKey = privateKey, passphrase = passphrase,
                            onResult = { success, msg ->
                                testingConnection = false
                                testResult = success to msg
                            }
                        )
                    },
                    enabled = !testingConnection && host.isNotBlank() && username.isNotBlank() && credentialsValid,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    if (testingConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Uji Koneksi", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                Button(
                    onClick = {
                        vm.save(
                            existingId = if (connectionId > 0) connectionId else 0L,
                            name = name, protocol = protocol, host = host,
                            port = port.toIntOrNull() ?: Connection.defaultPort(protocol),
                            username = username, password = password,
                            initialPath = initialPath, passiveMode = passive,
                            privateKey = privateKey, passphrase = passphrase,
                            onDone = onBack
                        )
                    },
                    enabled = host.isNotBlank() && username.isNotBlank() && credentialsValid,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.5f).fillMaxHeight()
                ) {
                    Text("Simpan Koneksi", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
