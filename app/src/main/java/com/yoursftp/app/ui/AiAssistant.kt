package com.yoursftp.app.ui

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

enum class AiMode {
    OFFLINE,
    ONLINE
}

class AiAssistant(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)

    var aiMode: AiMode
        get() {
            val modeStr = prefs.getString("ai_mode", AiMode.OFFLINE.name)
            return try { AiMode.valueOf(modeStr ?: AiMode.OFFLINE.name) } catch (e: Exception) { AiMode.OFFLINE }
        }
        set(value) {
            prefs.edit().putString("ai_mode", value.name).apply()
        }

    var geminiApiKey: String
        get() = prefs.getString("gemini_api_key", "") ?: ""
        set(value) {
            prefs.edit().putString("gemini_api_key", value.trim()).apply()
        }

    /** Menghasilkan perintah Linux berdasarkan Prompt. */
    suspend fun generateCommand(prompt: String): Pair<String, String> = withContext(Dispatchers.IO) {
        if (aiMode == AiMode.ONLINE) {
            val key = geminiApiKey
            if (key.isBlank()) {
                return@withContext "Error" to "Silakan masukkan Gemini API Key Anda di pengaturan AI terlebih dahulu."
            }
            try {
                return@withContext queryGeminiApi(prompt, key)
            } catch (e: Exception) {
                return@withContext "Error" to "Gagal menghubungi Gemini API: ${e.localizedMessage ?: e.message}. Pastikan koneksi internet aktif dan API Key valid."
            }
        } else {
            return@withContext generateOfflineCommand(prompt)
        }
    }

    private fun queryGeminiApi(prompt: String, apiKey: String): Pair<String, String> {
        val systemInstruction = "Anda adalah asisten pengembang sistem Linux. Berikan rekomendasi perintah terminal berdasarkan input pengguna. Tanggapan Anda HARUS dalam format JSON mentah (raw JSON) yang valid tanpa kode blok markdown (seperti ```json) atau teks pengantar lainnya. Gunakan format persis seperti ini: {\"command\": \"perintah_linux\", \"explanation\": \"penjelasan singkat dalam bahasa Indonesia\"}. Jangan ada teks lain."
        
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val requestBody = JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().apply {
                    put("parts", org.json.JSONArray().put(
                        JSONObject().apply {
                            put("text", "$systemInstruction\n\nPermintaan Pengguna: $prompt")
                        }
                    ))
                }
            ))
        }

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(requestBody.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val responseJson = JSONObject(responseText)
            
            // Extract the generated text
            val candidates = responseJson.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                if (content != null) {
                    val parts = content.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val rawText = parts.getJSONObject(0).optString("text", "").trim()
                        
                        // Bersihkan blok markdown ```json jika ada
                        val cleanJsonText = rawText
                            .removePrefix("```json")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()

                        try {
                            val parsedResult = JSONObject(cleanJsonText)
                            val command = parsedResult.optString("command", "echo \"Perintah kosong\"")
                            val explanation = parsedResult.optString("explanation", "Tidak ada penjelasan.")
                            return command to explanation
                        } catch (e: Exception) {
                            return rawText to "Gagal mengurai respon JSON dari Gemini. Menampilkan teks mentah."
                        }
                    }
                }
            }
            return "echo \"Error\"" to "Respon dari Gemini kosong."
        } else {
            val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            return "Error ($responseCode)" to errorText
        }
    }

    private fun generateOfflineCommand(prompt: String): Pair<String, String> {
        val q = prompt.lowercase().trim()
        return when {
            q.contains("memory") || q.contains("ram") || q.contains("memori") -> 
                "free -h" to "Menampilkan penggunaan memori RAM sistem dalam format yang mudah dibaca."
            q.contains("disk") || q.contains("penyimpanan") || q.contains("storage") || q.contains("harddisk") -> 
                "df -h" to "Menampilkan ruang penyimpanan disk yang tersedia dan terpakai di seluruh partisi."
            q.contains("cpu") || q.contains("proses") || q.contains("prosesor") || q.contains("task") -> 
                "top -n 1" to "Menampilkan daftar proses aktif saat ini dan persentase penggunaan CPU."
            q.contains("port") || q.contains("netstat") || q.contains("koneksi") -> 
                "ss -tulpn" to "Melihat daftar port TCP/UDP yang sedang mendengarkan (listening) beserta aplikasinya."
            q.contains("ip") || q.contains("ifconfig") || q.contains("jaringan") || q.contains("network") -> 
                "ip a" to "Menampilkan konfigurasi alamat IP jaringan pada seluruh interface."
            q.contains("cari file") || q.contains("find file") || q.contains("search file") || q.contains("temukan file") -> {
                val filePart = q.substringAfter("file", "").trim().replace("'", "").replace("\"", "")
                val query = if (filePart.isNotEmpty()) filePart else "nama_file"
                "find . -name \"*$query*\"" to "Mencari berkas bernama '$query' di dalam direktori aktif saat ini secara rekursif."
            }
            q.contains("ukuran folder") || q.contains("size folder") || q.contains("du") -> 
                "du -sh *" to "Menghitung dan menampilkan total ukuran setiap folder di direktori saat ini."
            q.contains("restart nginx") -> 
                "systemctl restart nginx" to "Memulai ulang (restart) layanan web server Nginx."
            q.contains("status nginx") -> 
                "systemctl status nginx" to "Memeriksa status berjalan atau tidaknya layanan Nginx."
            q.contains("logs") || q.contains("log") || q.contains("catatan log") -> 
                "tail -f /var/log/syslog" to "Membaca catatan sistem (syslog) secara real-time."
            q.contains("ping") -> 
                "ping -c 4 google.com" to "Mengirim 4 paket PING ke google.com untuk mengecek latency jaringan."
            q.contains("restart apache") || q.contains("restart httpd") -> 
                "systemctl restart apache2" to "Memulai ulang layanan web server Apache."
            q.contains("install docker") -> 
                "curl -fsSL https://get.docker.com -o get-docker.sh && sh get-docker.sh" to "Mengunduh skrip resmi dan menginstal Docker Engine secara otomatis."
            q.contains("update") || q.contains("upgrade") -> 
                "sudo apt update && sudo apt upgrade -y" to "Memperbarui daftar paket repositori dan meng-upgrade seluruh paket di Debian/Ubuntu."
            q.contains("buat folder") || q.contains("mkdir") -> 
                "mkdir -p folder_baru" to "Membuat direktori baru bernama 'folder_baru'."
            q.contains("hak akses") || q.contains("chmod") -> 
                "chmod -R 755 nama_file" to "Mengubah izin akses file/folder menjadi 755 (Pemilik bisa semua, grup & publik hanya baca-eksekusi)."
            q.contains("pemilik") || q.contains("chown") -> 
                "chown -R www-data:www-data nama_folder" to "Mengubah kepemilikan user & group folder menjadi www-data."
            else -> 
                "echo \"$prompt\"" to "Menampilkan teks input kustom Anda."
        }
    }
}
