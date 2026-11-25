package com.example.transfer

import android.content.Context
import android.media.MediaScannerConnection
import com.example.data.local.HistoryRepository
import com.example.data.local.TransferHistoryEntity
import com.example.data.model.TransferItem
import com.example.data.model.WebPortalMode
import com.example.data.model.WebServerState
import com.example.data.repository.AppRepository
import com.example.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Pure-Kotlin multi-threaded local HTTP/1.1 server for 100% offline:
 * - Connect to PC (modern dark-emerald web dashboard with drag-and-drop upload & batch download)
 * - Connect to KaiOS / Browser (zero-JS ultra-lightweight HTML portal for feature phones)
 * - Share This App (direct self-APK download portal)
 * Supports HTTP Range (206 Partial Content) resumable downloads & streaming multipart uploads.
 */
class LocalWebServer(
    private val context: Context,
    private val appRepository: AppRepository,
    private val historyRepository: HistoryRepository
) {
    companion object {
        const val DEFAULT_HTTP_PORT = 8080
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val sharedItems = CopyOnWriteArrayList<TransferItem>()
    private var uploadDir: File = context.filesDir

    private val _state = MutableStateFlow(WebServerState())
    val state: StateFlow<WebServerState> = _state.asStateFlow()

    fun updateSharedItems(items: List<TransferItem>) {
        sharedItems.clear()
        sharedItems.addAll(items)
    }

    fun start(
        mode: WebPortalMode,
        ipAddress: String,
        items: List<TransferItem>,
        downloadDir: File,
        port: Int = DEFAULT_HTTP_PORT
    ) {
        stop()
        uploadDir = downloadDir.apply { mkdirs() }
        sharedItems.clear()
        if (mode == WebPortalMode.SHARE_SELF_APK) {
            appRepository.getSelfApkTransferItem()?.let { sharedItems.add(it) }
        } else {
            sharedItems.addAll(items)
        }

        val pathSuffix = when (mode) {
            WebPortalMode.PC_DASHBOARD -> ""
            WebPortalMode.KAIOS_BROWSER -> "/kaios"
            WebPortalMode.SHARE_SELF_APK -> "/apk"
        }

        acceptJob = scope.launch {
            try {
                val ss = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
                serverSocket = ss
                val url = "http://$ipAddress:$port$pathSuffix"
                _state.value = WebServerState(
                    isRunning = true,
                    mode = mode,
                    url = url,
                    ipAddress = ipAddress,
                    port = port
                )

                while (isActive && !ss.isClosed) {
                    val client = try {
                        ss.accept()
                    } catch (_: Exception) {
                        break
                    }
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isRunning = false,
                        errorMessage = e.localizedMessage ?: "Failed to start local HTTP server"
                    )
                }
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        _state.value = WebServerState(isRunning = false)
    }

    private suspend fun handleClient(socket: Socket) {
        _state.update { it.copy(connectedClients = it.connectedClients + 1) }
        try {
            socket.soTimeout = 30_000
            val input = BufferedInputStream(socket.getInputStream(), 65536)
            val output = BufferedOutputStream(socket.getOutputStream(), 65536)

            val requestLine = readLineAscii(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val rawPath = parts[1].substringBefore("?")

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLineAscii(input) ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
            }

            when {
                method == "GET" && (rawPath == "/" || rawPath == "/index.html") -> {
                    sendHtml(output, buildPcDashboardHtml())
                }
                method == "GET" && rawPath == "/kaios" -> {
                    sendHtml(output, buildKaiOsPortalHtml())
                }
                method == "GET" && rawPath == "/apk" -> {
                    sendHtml(output, buildShareAppHtml())
                }
                method == "GET" && rawPath.startsWith("/download/") -> {
                    val idx = rawPath.removePrefix("/download/").toIntOrNull()
                    if (idx != null && idx in sharedItems.indices) {
                        serveFileDownload(output, sharedItems[idx], headers["range"])
                    } else {
                        sendNotFound(output)
                    }
                }
                method == "POST" && (rawPath == "/upload" || rawPath == "/kaios/upload") -> {
                    val uploaded = handleMultipartUpload(input, headers)
                    if (rawPath == "/kaios/upload") {
                        sendRedirect(output, "/kaios")
                    } else {
                        sendJson(output, """{"ok":true,"uploaded":$uploaded}""")
                    }
                }
                else -> sendNotFound(output)
            }
            output.flush()
        } catch (_: Exception) {
            // Client closed connection
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
            _state.update { it.copy(connectedClients = (it.connectedClients - 1).coerceAtLeast(0)) }
        }
    }

    private suspend fun serveFileDownload(
        output: OutputStream,
        item: TransferItem,
        rangeHeader: String?
    ) {
        val file = if (item.isSplitApk) {
            appRepository.prepareApkFileForTransfer(item)
        } else {
            File(item.path)
        }
        if (!file.exists()) {
            sendNotFound(output)
            return
        }

        val totalLength = file.length()
        var start = 0L
        var end = totalLength - 1
        var isPartial = false

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val rangeSpec = rangeHeader.removePrefix("bytes=").substringBefore(",")
            val rangeParts = rangeSpec.split("-")
            start = rangeParts.getOrNull(0)?.toLongOrNull()?.coerceIn(0L, totalLength - 1) ?: 0L
            end = rangeParts.getOrNull(1)?.toLongOrNull()?.coerceIn(start, totalLength - 1) ?: (totalLength - 1)
            isPartial = true
        }

        val contentLength = (end - start + 1).coerceAtLeast(0L)
        val encodedName = URLEncoder.encode(item.effectiveTransferName, "UTF-8").replace("+", "%20")

        val headerBuilder = StringBuilder().apply {
            append(if (isPartial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
            append("Content-Type: ${item.mimeType}\r\n")
            append("Content-Disposition: attachment; filename=\"${item.effectiveTransferName}\"; filename*=UTF-8''$encodedName\r\n")
            append("Accept-Ranges: bytes\r\n")
            if (isPartial) {
                append("Content-Range: bytes $start-$end/$totalLength\r\n")
            }
            append("Content-Length: $contentLength\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))

        val startMs = System.currentTimeMillis()
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val buffer = ByteArray(65536)
            var remaining = contentLength
            while (remaining > 0L) {
                val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
        }
        output.flush()

        val elapsedSec = ((System.currentTimeMillis() - startMs).coerceAtLeast(1L)) / 1000.0
        _state.update { it.copy(downloadedCount = it.downloadedCount + 1) }
        historyRepository.recordTransfer(
            TransferHistoryEntity(
                fileName = item.effectiveTransferName,
                filePath = file.absolutePath,
                fileSizeBytes = totalLength,
                category = item.category.name,
                direction = "SENT",
                speedBytesPerSec = (contentLength / elapsedSec).toLong(),
                peerName = "Web Browser",
                status = "COMPLETED"
            )
        )
    }

    private suspend fun handleMultipartUpload(
        input: BufferedInputStream,
        headers: Map<String, String>
    ): Int {
        val contentType = headers["content-type"] ?: return 0
        val contentLength = headers["content-length"]?.toLongOrNull() ?: return 0
        val boundaryMatch = Regex("boundary=([^;\\s]+)").find(contentType) ?: return 0
        val boundary = "--" + boundaryMatch.groupValues[1].trim('"')
        val boundaryBytes = ("\r\n$boundary").toByteArray(Charsets.ISO_8859_1)

        var remaining = contentLength
        var uploadedFiles = 0

        // Bounded stream reader
        val limitedInput = object : InputStream() {
            override fun read(): Int {
                if (remaining <= 0L) return -1
                val b = input.read()
                if (b != -1) remaining--
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0L) return -1
                val toRead = minOf(len.toLong(), remaining).toInt()
                val n = input.read(b, off, toRead)
                if (n > 0) remaining -= n
                return n
            }
        }

        val buffered = BufferedInputStream(limitedInput, 65536)
        // Skip preamble to first boundary
        while (true) {
            val line = readLineAscii(buffered) ?: return uploadedFiles
            if (line.startsWith(boundary)) {
                if (line.endsWith("--")) return uploadedFiles
                break
            }
        }

        while (true) {
            var fileName: String? = null
            while (true) {
                val hLine = readLineAscii(buffered) ?: return uploadedFiles
                if (hLine.isEmpty()) break
                if (hLine.lowercase().startsWith("content-disposition")) {
                    val fnMatch = Regex("filename=\"([^\"]+)\"").find(hLine)
                    if (fnMatch != null) {
                        fileName = fnMatch.groupValues[1]
                            .substringAfterLast('/').substringAfterLast('\\')
                            .replace(Regex("[<>:\"|?*]"), "_")
                    }
                }
            }

            val destFile = fileName?.takeIf { it.isNotBlank() }?.let { safeName ->
                var f = File(uploadDir, safeName)
                var c = 1
                while (f.exists()) {
                    val base = safeName.substringBeforeLast('.', safeName)
                    val ext = if (safeName.contains('.')) ".${safeName.substringAfterLast('.')}" else ""
                    f = File(uploadDir, "${base}_($c)$ext")
                    c++
                }
                f
            }

            val startMs = System.currentTimeMillis()
            val outStream: OutputStream? = destFile?.let { BufferedOutputStream(FileOutputStream(it), 65536) }
            val tailWindow = ByteArray(boundaryBytes.size)
            var tailLen = 0
            var writtenBytes = 0L
            var foundBoundary = false

            try {
                while (true) {
                    val b = buffered.read()
                    if (b == -1) break
                    if (tailLen < tailWindow.size) {
                        tailWindow[tailLen++] = b.toByte()
                    } else {
                        outStream?.write(tailWindow[0].toInt())
                        writtenBytes++
                        System.arraycopy(tailWindow, 1, tailWindow, 0, tailWindow.size - 1)
                        tailWindow[tailWindow.size - 1] = b.toByte()
                    }
                    if (tailLen == tailWindow.size && tailWindow.contentEquals(boundaryBytes)) {
                        foundBoundary = true
                        break
                    }
                }
                if (!foundBoundary && tailLen > 0) {
                    outStream?.write(tailWindow, 0, tailLen)
                    writtenBytes += tailLen
                }
            } finally {
                outStream?.flush()
                outStream?.close()
            }

            if (destFile != null && destFile.exists() && writtenBytes > 0L) {
                uploadedFiles++
                val elapsedSec = ((System.currentTimeMillis() - startMs).coerceAtLeast(1L)) / 1000.0
                MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
                _state.update { it.copy(uploadedCount = it.uploadedCount + 1) }
                historyRepository.recordTransfer(
                    TransferHistoryEntity(
                        fileName = destFile.name,
                        filePath = destFile.absolutePath,
                        fileSizeBytes = destFile.length(),
                        category = "OTHER",
                        direction = "RECEIVED",
                        speedBytesPerSec = (writtenBytes / elapsedSec).toLong(),
                        peerName = "Web Upload",
                        status = "COMPLETED"
                    )
                )
            } else {
                destFile?.delete()
            }

            if (!foundBoundary) break
            // Read trailing "--" or "\r\n" after boundary
            val endMarker = readLineAscii(buffered) ?: break
            if (endMarker.startsWith("--")) break
        }
        return uploadedFiles
    }

    private fun buildPcDashboardHtml(): String {
        val rows = if (sharedItems.isEmpty()) {
            """<div class="empty">No files queued on phone yet. Upload files from PC below, or select files on FlashShare.</div>"""
        } else {
            sharedItems.mapIndexed { idx, item ->
                """
                <div class="file-row">
                    <div>
                        <div class="fname">${escapeHtml(item.effectiveTransferName)}</div>
                        <div class="fmeta">${item.category.label} &bull; ${FormatUtils.formatSize(item.sizeBytes)}</div>
                    </div>
                    <a class="btn" href="/download/$idx">Download</a>
                </div>
                """.trimIndent()
            }.joinToString("\n")
        }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width,initial-scale=1"/>
            <title>FlashShare PC Portal</title>
            <style>
              body{margin:0;font-family:system-ui,-apple-system,sans-serif;background:#121212;color:#f5f7f6}
              header{background:#00894d;padding:18px 24px;display:flex;justify-content:space-between;align-items:center}
              header h1{margin:0;font-size:20px}
              .badge{background:#005c33;padding:4px 10px;border-radius:999px;font-size:12px}
              main{max-width:780px;margin:24px auto;padding:0 16px}
              .card{background:#1e1e1e;border:1px solid #2f3532;border-radius:16px;padding:20px;margin-bottom:20px}
              .drop{border:2px dashed #00a859;border-radius:14px;padding:28px;text-align:center;cursor:pointer;background:#0d2e1e44}
              .btn{background:#00a859;color:#fff;border:none;border-radius:999px;padding:10px 18px;font-weight:600;text-decoration:none;cursor:pointer;display:inline-block}
              .file-row{display:flex;justify-content:space-between;align-items:center;padding:12px 0;border-bottom:1px solid #2c2c2c}
              .file-row:last-child{border-bottom:none}
              .fname{font-weight:600;word-break:break-all}
              .fmeta{font-size:12px;color:#9ea7a2;margin-top:3px}
              .empty{color:#9ea7a2;padding:12px 0}
              #status{margin-top:12px;color:#1ed760;font-size:14px}
            </style>
            </head>
            <body>
            <header><h1>FlashShare &bull; Local PC Hub</h1><span class="badge">100% OFFLINE LAN</span></header>
            <main>
              <div class="card">
                <h2 style="margin-top:0">Send Files from PC to Phone</h2>
                <form id="upForm" method="POST" action="/upload" enctype="multipart/form-data">
                  <label class="drop" style="display:block">
                    <div style="font-size:16px;font-weight:600">Click to select files to upload to Phone</div>
                    <div style="color:#9ea7a2;font-size:13px;margin-top:6px">Direct local Wi-Fi transfer • Zero internet used</div>
                    <input id="fileInput" type="file" name="file" multiple style="display:none"/>
                  </label>
                  <div style="margin-top:14px"><button class="btn" type="submit">Upload to Phone</button></div>
                  <div id="status"></div>
                </form>
              </div>
              <div class="card">
                <h2 style="margin-top:0">Shared from Phone (${sharedItems.size})</h2>
                $rows
              </div>
            </main>
            <script>
              const f=document.getElementById('upForm'),inp=document.getElementById('fileInput'),st=document.getElementById('status');
              inp.addEventListener('change',()=>{st.textContent=inp.files.length+' file(s) ready to upload';});
              f.addEventListener('submit',async(e)=>{
                e.preventDefault();
                if(!inp.files.length){st.textContent='Please choose at least one file.';return;}
                const fd=new FormData();
                for(const file of inp.files) fd.append('file',file);
                st.textContent='Uploading...';
                const r=await fetch('/upload',{method:'POST',body:fd});
                const j=await r.json();
                st.textContent='Uploaded '+j.uploaded+' file(s) to FlashShare!';
                inp.value='';
              });
            </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildKaiOsPortalHtml(): String {
        val listHtml = if (sharedItems.isEmpty()) {
            "<p>No files selected on sender phone.</p>"
        } else {
            sharedItems.mapIndexed { idx, item ->
                """<li><a href="/download/$idx">${escapeHtml(item.effectiveTransferName)}</a> (${FormatUtils.formatSize(item.sizeBytes)})</li>"""
            }.joinToString("")
        }
        return """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/>
            <title>FlashShare Lite</title>
            <style>body{font-family:sans-serif;background:#121212;color:#fff;margin:8px}a{color:#1ed760}h2{color:#00a859;margin:6px 0}input,button{margin:6px 0;padding:6px;width:100%}</style>
            </head><body>
            <h2>FlashShare Lite (KaiOS)</h2>
            <form method="POST" action="/kaios/upload" enctype="multipart/form-data">
              <label>Upload to Phone:</label><br/>
              <input type="file" name="file"/><br/>
              <button type="submit">Send File</button>
            </form>
            <hr/>
            <h3>Download Files:</h3>
            <ul>$listHtml</ul>
            </body></html>
        """.trimIndent()
    }

    private fun buildShareAppHtml(): String {
        val apkName = sharedItems.firstOrNull()?.effectiveTransferName ?: "FlashShare.apk"
        val apkSize = sharedItems.firstOrNull()?.let { FormatUtils.formatSize(it.sizeBytes) } ?: ""
        return """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/>
            <title>Install FlashShare</title>
            <style>body{font-family:system-ui,sans-serif;background:#121212;color:#fff;text-align:center;padding:36px 16px}.card{max-width:420px;margin:0 auto;background:#1e1e1e;padding:28px;border-radius:18px}.btn{display:inline-block;background:#00a859;color:#fff;padding:14px 28px;border-radius:999px;text-decoration:none;font-weight:700;margin-top:16px}</style>
            </head><body>
            <div class="card">
              <h1 style="color:#00a859;margin-top:0">FlashShare</h1>
              <p>100% Offline, Ad-Free File Sharing App</p>
              <p style="color:#9ea7a2">$apkName &bull; $apkSize</p>
              <a class="btn" href="/download/0">Download APK</a>
            </div>
            </body></html>
        """.trimIndent()
    }

    private fun sendHtml(output: OutputStream, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val hdr = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        output.write(hdr.toByteArray(Charsets.UTF_8))
        output.write(bytes)
    }

    private fun sendJson(output: OutputStream, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val hdr = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        output.write(hdr.toByteArray(Charsets.UTF_8))
        output.write(bytes)
    }

    private fun sendRedirect(output: OutputStream, location: String) {
        val hdr = "HTTP/1.1 303 See Other\r\nLocation: $location\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        output.write(hdr.toByteArray(Charsets.UTF_8))
    }

    private fun sendNotFound(output: OutputStream) {
        val body = "Not Found".toByteArray(Charsets.UTF_8)
        val hdr = "HTTP/1.1 404 Not Found\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        output.write(hdr.toByteArray(Charsets.UTF_8))
        output.write(body)
    }

    private fun readLineAscii(input: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var prev = -1
        while (true) {
            val cur = input.read()
            if (cur == -1) {
                return if (baos.size() == 0) null else baos.toString("ISO-8859-1")
            }
            if (cur == '\n'.code) {
                val bytes = baos.toByteArray()
                val len = if (prev == '\r'.code && bytes.isNotEmpty()) bytes.size - 1 else bytes.size
                return String(bytes, 0, len, Charsets.ISO_8859_1)
            }
            baos.write(cur)
            prev = cur
        }
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
