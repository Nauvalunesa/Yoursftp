package com.yoursftp.app.transfer

import com.yoursftp.app.data.Connection
import com.yoursftp.app.data.RemoteFile
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class S3FileClient(private val connection: Connection) : FileClient {
    override var isConnected = false
        private set

    private val region: String
        get() {
            val h = connection.host.lowercase()
            if (h.contains("r2.cloudflarestorage.com")) return "auto"
            
            // Extract region from standard AWS S3 endpoint e.g., s3.us-west-2.amazonaws.com or s3-us-west-2.amazonaws.com
            val awsRegex = Regex("""s3[.-]([a-z0-9-]+)\.amazonaws\.com""")
            val match = awsRegex.find(h)
            if (match != null) {
                return match.groupValues[1]
            }
            if (h.endsWith("s3.amazonaws.com")) return "us-east-1"
            
            // Fallback for custom/local S3 providers like MinIO
            return "us-east-1"
        }
    private val service = "s3"

    override fun connect() {
        isConnected = true
    }

    override fun disconnect() {
        isConnected = false
    }

    private fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = sha256(data)
        return digest.joinToString("") { String.format("%02x", it) }
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key, "HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun getSignatureKey(secretKey: String, dateStamp: String, regionName: String, serviceName: String): ByteArray {
        val kSecret = ("AWS4$secretKey").toByteArray(Charsets.UTF_8)
        val kDate = hmacSha256(kSecret, dateStamp)
        val kRegion = hmacSha256(kDate, regionName)
        val kService = hmacSha256(kRegion, serviceName)
        val kSigning = hmacSha256(kService, "aws4_request")
        return kSigning
    }

    private fun encodePath(path: String): String {
        return path.split('/').joinToString("/") {
            URLEncoder.encode(it, "UTF-8")
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~")
        }
    }

    private fun sendRequest(
        method: String,
        s3Key: String,
        queryParams: Map<String, String> = emptyMap(),
        requestBody: ByteArray? = null,
        progressListener: ProgressListener? = null
    ): ByteArray {
        val bucket = connection.initialPath.trim('/')
        val date = Date()
        val amzDate = getAmzDate(date)
        val dateStamp = getDateStamp(date)

        val host = connection.host.trim()
        val endpoint = "https://$host"
        
        val canonicalUri = "/" + bucket + if (s3Key.isNotEmpty()) "/" + encodePath(s3Key.trim('/')) else ""
        
        val sortedQueryString = queryParams.entries
            .sortedBy { it.key }
            .joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }

        val bodyHash = sha256Hex(requestBody ?: ByteArray(0))

        val headers = mutableMapOf(
            "Host" to host,
            "x-amz-content-sha256" to bodyHash,
            "x-amz-date" to amzDate
        )

        val canonicalHeaders = headers.entries
            .sortedBy { it.key.lowercase(Locale.US) }
            .joinToString("") { "${it.key.lowercase(Locale.US)}:${it.value.trim()}\n" }
        
        val signedHeaders = headers.keys
            .map { it.lowercase(Locale.US) }
            .sorted()
            .joinToString(";")

        val canonicalRequest = "$method\n$canonicalUri\n$sortedQueryString\n$canonicalHeaders\n$signedHeaders\n$bodyHash"
        val hashedCanonicalRequest = sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))

        val credentialScope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$credentialScope\n$hashedCanonicalRequest"

        val signingKey = getSignatureKey(connection.password, dateStamp, region, service)
        val signatureBytes = hmacSha256(signingKey, stringToSign)
        val signature = signatureBytes.joinToString("") { String.format("%02x", it) }

        val authorization = "AWS4-HMAC-SHA256 Credential=${connection.username}/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        val urlString = "$endpoint$canonicalUri" + if (sortedQueryString.isNotEmpty()) "?$sortedQueryString" else ""
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.doInput = true
        
        conn.setRequestProperty("Authorization", authorization)
        conn.setRequestProperty("x-amz-date", amzDate)
        conn.setRequestProperty("x-amz-content-sha256", bodyHash)
        
        if (requestBody != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Length", requestBody.size.toString())
            conn.outputStream.use { out ->
                if (progressListener != null) {
                    var offset = 0
                    val chunkSize = 4096
                    while (offset < requestBody.size) {
                        val length = (requestBody.size - offset).coerceAtMost(chunkSize)
                        out.write(requestBody, offset, length)
                        offset += length
                        progressListener(offset.toLong(), requestBody.size.toLong())
                    }
                } else {
                    out.write(requestBody)
                }
                out.flush()
            }
        }

        val responseCode = conn.responseCode
        if (responseCode in 200..299) {
            val input = conn.inputStream
            val totalBytes = conn.contentLength.toLong()
            return readStream(input, totalBytes, progressListener)
        } else {
            val errorStream = conn.errorStream ?: conn.inputStream
            val errorText = errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown S3 Error"
            throw IllegalStateException("S3 Error ($responseCode): $errorText")
        }
    }

    private fun readStream(input: InputStream, totalBytes: Long, progressListener: ProgressListener?): ByteArray {
        val out = if (totalBytes > 0) ByteArrayOutputStream(totalBytes.toInt()) else ByteArrayOutputStream()
        val buffer = ByteArray(65536) // 64 KB optimized buffer size
        var bytesRead: Int
        var totalRead = 0L
        while (input.read(buffer).also { bytesRead = it } != -1) {
            out.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            progressListener?.invoke(totalRead, totalBytes)
        }
        return out.toByteArray()
    }

    private fun getAmzDate(date: Date): String {
        val sdf = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(date)
    }

    private fun getDateStamp(date: Date): String {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
        val tz = TimeZone.getTimeZone("UTC")
        sdf.timeZone = tz
        return sdf.format(date)
    }

    override fun list(path: String): List<RemoteFile> {
        val rawPrefix = path.trim('/')
        val prefix = if (rawPrefix.isEmpty()) "" else "$rawPrefix/"
        
        val queryParams = mutableMapOf(
            "list-type" to "2",
            "delimiter" to "/"
        )
        if (prefix.isNotEmpty()) {
            queryParams["prefix"] = prefix
        }

        val xmlData = sendRequest("GET", "", queryParams)
        return parseListXml(String(xmlData, Charsets.UTF_8), prefix)
    }

    private fun parseListXml(xmlString: String, prefix: String): List<RemoteFile> {
        val result = mutableListOf<RemoteFile>()
        
        // 1. Parse CommonPrefixes (Folders)
        val prefixRegex = Regex("<CommonPrefixes>\\s*<Prefix>([^<]+)</Prefix>\\s*</CommonPrefixes>")
        prefixRegex.findAll(xmlString).forEach { match ->
            val dirKey = match.groupValues[1].trimEnd('/')
            if (dirKey.isNotEmpty()) {
                val name = dirKey.substringAfterLast('/')
                result.add(RemoteFile(
                    name = name,
                    path = "/$dirKey",
                    isDirectory = true,
                    size = 0L,
                    lastModified = 0L
                ))
            }
        }
        
        // 2. Parse Contents (Files)
        val contentsRegex = Regex("<Contents>(.*?)</Contents>", RegexOption.DOT_MATCHES_ALL)
        val keyRegex = Regex("<Key>([^<]+)</Key>")
        val sizeRegex = Regex("<Size>(\\d+)</Size>")
        val dateRegex = Regex("<LastModified>([^<]+)</LastModified>")
        
        contentsRegex.findAll(xmlString).forEach { match ->
            val contentsBody = match.groupValues[1]
            val key = keyRegex.find(contentsBody)?.groupValues?.get(1) ?: ""
            if (key.isNotEmpty() && !key.endsWith("/") && key != prefix) {
                val size = sizeRegex.find(contentsBody)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val dateStr = dateRegex.find(contentsBody)?.groupValues?.get(1) ?: ""
                var lastModified = 0L
                if (dateStr.isNotEmpty()) {
                    runCatching {
                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                        lastModified = sdf.parse(dateStr)?.time ?: 0L
                    }
                }
                val name = key.substringAfterLast('/')
                result.add(RemoteFile(
                    name = name,
                    path = "/$key",
                    isDirectory = false,
                    size = size,
                    lastModified = lastModified
                ))
            }
        }
        
        return result
    }

    override fun download(path: String, progressListener: ProgressListener?): ByteArray {
        val s3Key = path.trim('/')
        return sendRequest("GET", s3Key, progressListener = progressListener)
    }

    override fun upload(path: String, data: ByteArray, progressListener: ProgressListener?) {
        val s3Key = path.trim('/')
        sendRequest("PUT", s3Key, requestBody = data, progressListener = progressListener)
    }

    override fun rename(from: String, to: String) {
        val fromKey = from.trim('/')
        val toKey = to.trim('/')
        
        val bucket = connection.initialPath.trim('/')
        val copySource = "/$bucket/$fromKey"
        
        val date = Date()
        val amzDate = getAmzDate(date)
        val dateStamp = getDateStamp(date)
        val host = connection.host.trim()
        val endpoint = "https://$host"
        val canonicalUri = "/" + bucket + "/" + encodePath(toKey)
        
        val headers = mutableMapOf(
            "Host" to host,
            "x-amz-content-sha256" to sha256Hex(ByteArray(0)),
            "x-amz-date" to amzDate,
            "x-amz-copy-source" to URLEncoder.encode(copySource, "UTF-8")
        )

        val canonicalHeaders = headers.entries
            .sortedBy { it.key.lowercase(Locale.US) }
            .joinToString("") { "${it.key.lowercase(Locale.US)}:${it.value.trim()}\n" }
        
        val signedHeaders = headers.keys
            .map { it.lowercase(Locale.US) }
            .sorted()
            .joinToString(";")

        val canonicalRequest = "PUT\n$canonicalUri\n\n$canonicalHeaders\n$signedHeaders\n${sha256Hex(ByteArray(0))}"
        val hashedCanonicalRequest = sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))

        val credentialScope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$credentialScope\n$hashedCanonicalRequest"

        val signingKey = getSignatureKey(connection.password, dateStamp, region, service)
        val signatureBytes = hmacSha256(signingKey, stringToSign)
        val signature = signatureBytes.joinToString("") { String.format("%02x", it) }

        val authorization = "AWS4-HMAC-SHA256 Credential=${connection.username}/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"

        val url = URL("$endpoint$canonicalUri")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "PUT"
        conn.doInput = true
        conn.setRequestProperty("Authorization", authorization)
        conn.setRequestProperty("x-amz-date", amzDate)
        conn.setRequestProperty("x-amz-content-sha256", sha256Hex(ByteArray(0)))
        conn.setRequestProperty("x-amz-copy-source", copySource)

        val code = conn.responseCode
        if (code !in 200..299) {
            val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Copy error"
            throw IllegalStateException("S3 Copy Error ($code): $errorText")
        }

        deleteFile(from)
    }

    override fun deleteFile(path: String) {
        val s3Key = path.trim('/')
        sendRequest("DELETE", s3Key)
    }

    override fun deleteDirectory(path: String) {
        val files = list(path)
        for (f in files) {
            deleteFile(f.path)
        }
        val dirKey = path.trim('/') + "/"
        sendRequest("DELETE", dirKey)
    }

    override fun makeDirectory(path: String) {
        val dirKey = path.trim('/') + "/"
        sendRequest("PUT", dirKey, requestBody = ByteArray(0))
    }
}
