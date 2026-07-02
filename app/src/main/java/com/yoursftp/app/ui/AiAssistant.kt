package com.yoursftp.app.ui

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

enum class AiMode {
    OFFLINE,
    ONLINE
}

enum class AiProvider {
    GEMINI,
    OPENAI,
    CLAUDE,
    CUSTOM
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

    var aiProvider: AiProvider
        get() {
            val providerStr = prefs.getString("ai_provider", AiProvider.GEMINI.name)
            return try { AiProvider.valueOf(providerStr ?: AiProvider.GEMINI.name) } catch (e: Exception) { AiProvider.GEMINI }
        }
        set(value) {
            prefs.edit().putString("ai_provider", value.name).apply()
        }

    // Gemini Settings
    var geminiApiKey: String
        get() = prefs.getString("gemini_api_key", "") ?: ""
        set(value) { prefs.edit().putString("gemini_api_key", value.trim()).apply() }

    // OpenAI Settings
    var openaiApiKey: String
        get() = prefs.getString("openai_api_key", "") ?: ""
        set(value) { prefs.edit().putString("openai_api_key", value.trim()).apply() }

    var openaiBaseUrl: String
        get() = prefs.getString("openai_base_url", "https://api.openai.com/v1/chat/completions") ?: "https://api.openai.com/v1/chat/completions"
        set(value) { prefs.edit().putString("openai_base_url", value.trim()).apply() }

    var openaiModel: String
        get() = prefs.getString("openai_model", "gpt-4o-mini") ?: "gpt-4o-mini"
        set(value) { prefs.edit().putString("openai_model", value.trim()).apply() }

    // Claude Settings
    var claudeApiKey: String
        get() = prefs.getString("claude_api_key", "") ?: ""
        set(value) { prefs.edit().putString("claude_api_key", value.trim()).apply() }

    var claudeModel: String
        get() = prefs.getString("claude_model", "claude-3-5-sonnet-20241022") ?: "claude-3-5-sonnet-20241022"
        set(value) { prefs.edit().putString("claude_model", value.trim()).apply() }

    // Custom API Settings (Ollama, DeepSeek, Groq, OpenRouter, dll.)
    var customApiKey: String
        get() = prefs.getString("custom_api_key", "") ?: ""
        set(value) { prefs.edit().putString("custom_api_key", value.trim()).apply() }

    var customBaseUrl: String
        get() = prefs.getString("custom_base_url", "") ?: ""
        set(value) { prefs.edit().putString("custom_base_url", value.trim()).apply() }

    var customModel: String
        get() = prefs.getString("custom_model", "") ?: ""
        set(value) { prefs.edit().putString("custom_model", value.trim()).apply() }


    /** Menghasilkan perintah Linux berdasarkan Prompt. */
    suspend fun generateCommand(prompt: String): Pair<String, String> = withContext(Dispatchers.IO) {
        if (aiMode == AiMode.OFFLINE) {
            return@withContext generateOfflineCommand(prompt)
        }

        try {
            return@withContext when (aiProvider) {
                AiProvider.GEMINI -> {
                    val key = geminiApiKey
                    if (key.isBlank()) "Error" to "Silakan masukkan Gemini API Key Anda."
                    else queryGeminiApi(prompt, key)
                }
                AiProvider.OPENAI -> {
                    val key = openaiApiKey
                    if (key.isBlank()) "Error" to "Silakan masukkan OpenAI API Key Anda."
                    else queryOpenAiApi(prompt, key, openaiBaseUrl, openaiModel)
                }
                AiProvider.CLAUDE -> {
                    val key = claudeApiKey
                    if (key.isBlank()) "Error" to "Silakan masukkan Claude API Key Anda."
                    else queryClaudeApi(prompt, key, claudeModel)
                }
                AiProvider.CUSTOM -> {
                    val key = customApiKey
                    val url = customBaseUrl
                    val model = customModel
                    if (url.isBlank()) "Error" to "Silakan masukkan Base URL Endpoint Custom."
                    else queryOpenAiApi(prompt, key, url, model)
                }
            }
        } catch (e: Exception) {
            return@withContext "Error" to "Gagal menghubungi API AI (${aiProvider.name}): ${e.localizedMessage ?: e.message}."
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
            val candidates = responseJson.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                if (content != null) {
                    val parts = content.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val rawText = parts.getJSONObject(0).optString("text", "").trim()
                        return parseJsonResponse(rawText)
                    }
                }
            }
            return "echo \"Error\"" to "Respon dari Gemini kosong."
        } else {
            val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            return "Error ($responseCode)" to errorText
        }
    }

    private fun queryOpenAiApi(prompt: String, apiKey: String, endpointUrl: String, modelName: String): Pair<String, String> {
        val systemInstruction = "Anda adalah asisten pengembang sistem Linux. Berikan rekomendasi perintah terminal berdasarkan input pengguna. Tanggapan Anda HARUS dalam format JSON mentah (raw JSON) yang valid tanpa kode blok markdown atau teks pengantar lainnya. Gunakan format persis seperti ini: {\"command\": \"perintah_linux\", \"explanation\": \"penjelasan singkat dalam bahasa Indonesia\"}. Jangan ada teks lain."
        
        val url = URL(endpointUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        if (apiKey.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
        }
        conn.doOutput = true

        val messagesArray = org.json.JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemInstruction)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val requestBody = JSONObject().apply {
            put("model", modelName)
            put("messages", messagesArray)
            // Paksa format JSON jika penyedia mendukung
            put("response_format", JSONObject().apply { put("type", "json_object") })
        }

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(requestBody.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val responseJson = JSONObject(responseText)
            val choices = responseJson.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.optJSONObject("message")
                if (message != null) {
                    val rawContent = message.optString("content", "").trim()
                    return parseJsonResponse(rawContent)
                }
            }
            return "echo \"Error\"" to "Respon dari OpenAI-compatible API kosong."
        } else {
            val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            return "Error ($responseCode)" to errorText
        }
    }

    private fun queryClaudeApi(prompt: String, apiKey: String, modelName: String): Pair<String, String> {
        val systemInstruction = "Anda adalah asisten pengembang sistem Linux. Berikan rekomendasi perintah terminal berdasarkan input pengguna. Tanggapan Anda HARUS dalam format JSON mentah (raw JSON) yang valid tanpa kode blok markdown atau teks pengantar lainnya. Gunakan format persis seperti ini: {\"command\": \"perintah_linux\", \"explanation\": \"penjelasan singkat dalam bahasa Indonesia\"}. Jangan ada teks lain."
        
        val url = URL("https://api.anthropic.com/v1/messages")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-api-key", apiKey)
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        conn.doOutput = true

        val messagesArray = org.json.JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val requestBody = JSONObject().apply {
            put("model", modelName)
            put("max_tokens", 1024)
            put("system", systemInstruction)
            put("messages", messagesArray)
        }

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(requestBody.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val responseJson = JSONObject(responseText)
            val contentArray = responseJson.optJSONArray("content")
            if (contentArray != null && contentArray.length() > 0) {
                val firstContent = contentArray.getJSONObject(0)
                val rawText = firstContent.optString("text", "").trim()
                return parseJsonResponse(rawText)
            }
            return "echo \"Error\"" to "Respon dari Claude kosong."
        } else {
            val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            return "Error ($responseCode)" to errorText
        }
    }

    private fun parseJsonResponse(rawText: String): Pair<String, String> {
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
            return cleanJsonText to "Gagal mengurai respon JSON dari model. Menampilkan teks mentah."
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
