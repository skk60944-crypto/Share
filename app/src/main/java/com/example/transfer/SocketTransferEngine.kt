package com.example.transfer

import android.content.Context
import android.media.MediaScannerConnection
import com.example.data.local.HistoryRepository
import com.example.data.local.TransferHistoryEntity
import com.example.data.model.ConnectionPhase
import com.example.data.model.FileTransferProgress
import com.example.data.model.FileTransferStatus
import com.example.data.model.QrConnectionPayload
import com.example.data.model.TransferDirection
import com.example.data.model.TransferItem
import com.example.data.model.TransferSessionState
import com.example.data.repository.AppRepository
import kotlinx.coroutines.CancellationException
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
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.CRC32

class SocketTransferEngine(
    private val context: Context,
    private val appRepository: AppRepository,
    private val historyRepository: HistoryRepository
) {
    companion object {
        const val DEFAULT_PORT = 8988
        private const val PROTOCOL_MAGIC = "FSH1"
        private const val CHUNK_SIZE = 128 * 1024 // 128 KB chunks for high throughput
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null

    private val pendingQueue = CopyOnWriteArrayList<TransferItem>()

    private val _sessionState = MutableStateFlow(TransferSessionState())
    val sessionState: StateFlow<TransferSessionState> = _sessionState.asStateFlow()

    fun addMoreFilesToQueue(items: List<TransferItem>) {
        val existingIds = pendingQueue.map { it.id }.toSet()
        val fresh = items.filter { it.id !in existingIds }
        if (fresh.isEmpty()) return
        pendingQueue.addAll(fresh)
        _sessionState.update { state ->
            val extraProgress = fresh.map {
                FileTransferProgress(
                    itemId = it.id,
                    fileName = it.effectiveTransferName,
                    totalBytes = it.sizeBytes,
                    transferredBytes = 0L,
                    status = FileTransferStatus.QUEUED
                )
            }
            state.copy(files = state.files + extraProgress)
        }
    }

    fun startSenderServer(
        items: List<TransferItem>,
        deviceName: String,
        qrPayload: QrConnectionPayload,
        onProgressNotification: (title: String, detail: String, progressPct: Int) -> Unit,
        onFinished: () -> Unit
    ) {
        cancelTransfer()
        pendingQueue.clear()
        pendingQueue.addAll(items)

        val initialFiles = items.map {
            FileTransferProgress(
                itemId = it.id,
                fileName = it.effectiveTransferName,
                totalBytes = it.sizeBytes,
                transferredBytes = 0L,
                status = FileTransferStatus.QUEUED
            )
        }

        _sessionState.value = TransferSessionState(
            phase = ConnectionPhase.WAITING_FOR_RECEIVER,
            direction = TransferDirection.SENT,
            qrPayload = qrPayload,
            files = initialFiles,
            statusMessage = "Waiting for receiver on ${qrPayload.hostIp}:${qrPayload.port}"
        )

        activeJob = scope.launch {
            try {
                val ss = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(qrPayload.port))
                    soTimeout = 0
                }
                serverSocket = ss
                val socket = ss.accept()
                clientSocket = socket
                socket.tcpNoDelay = true
                socket.sendBufferSize = 256 * 1024

                val input = DataInputStream(BufferedInputStream(socket.getInputStream(), 65536))
                val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 256 * 1024))

                // Handshake
                output.writeUTF(PROTOCOL_MAGIC)
                output.writeUTF(deviceName)
                output.flush()

                val peerMagic = input.readUTF()
                if (peerMagic != PROTOCOL_MAGIC) {
                    throw IllegalStateException("Incompatible peer protocol")
                }
                val peerName = input.readUTF()

                _sessionState.update {
                    it.copy(
                        phase = ConnectionPhase.TRANSFERRING,
                        peerName = peerName,
                        statusMessage = "Connected to $peerName"
                    )
                }

                var index = 0
                var sessionBytesSent = 0L
                val sessionStartMs = System.currentTimeMillis()

                while (isActive && index < pendingQueue.size) {
                    val item = pendingQueue[index]
                    val sourceFile = resolveReadableFile(item)
                    val actualSize = sourceFile?.length() ?: item.sizeBytes
                    val fileName = item.effectiveTransferName

                    // Tell receiver another file is starting (1 = file follows)
                    output.writeByte(1)
                    output.writeUTF(fileName)
                    output.writeUTF(item.category.name)
                    output.writeLong(actualSize)
                    output.flush()

                    // Read receiver's resume offset (0 for fresh, >0 for chunk resume)
                    val resumeOffset = input.readLong().coerceIn(0L, actualSize)
                    updateFileProgress(
                        itemId = item.id,
                        transferred = resumeOffset,
                        total = actualSize,
                        status = FileTransferStatus.TRANSFERRING,
                        resumedFrom = resumeOffset
                    )

                    val crc = CRC32()
                    var fileSent = resumeOffset
                    val fileStartMs = System.currentTimeMillis()
                    var lastReportMs = fileStartMs

                    if (sourceFile != null && sourceFile.exists()) {
                        RandomAccessFile(sourceFile, "r").use { raf ->
                            raf.seek(resumeOffset)
                            val buffer = ByteArray(CHUNK_SIZE)
                            while (isActive && fileSent < actualSize) {
                                val toRead = minOf(buffer.size.toLong(), actualSize - fileSent).toInt()
                                val read = raf.read(buffer, 0, toRead)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                crc.update(buffer, 0, read)
                                fileSent += read
                                sessionBytesSent += read

                                val now = System.currentTimeMillis()
                                if (now - lastReportMs >= 200L || fileSent >= actualSize) {
                                    lastReportMs = now
                                    val elapsedSec = ((now - sessionStartMs).coerceAtLeast(1L)) / 1000.0
                                    val speed = (sessionBytesSent / elapsedSec).toLong()
                                    updateFileProgress(
                                        itemId = item.id,
                                        transferred = fileSent,
                                        total = actualSize,
                                        status = FileTransferStatus.TRANSFERRING,
                                        resumedFrom = resumeOffset,
                                        speedBps = speed
                                    )
                                    val pct = (_sessionState.value.overallProgress * 100).toInt()
                                    onProgressNotification(
                                        "Sending to $peerName",
                                        "$fileName (${pct}%)",
                                        pct
                                    )
                                }
                            }
                        }
                    } else if (item.uri != null) {
                        context.contentResolver.openInputStream(item.uri)?.use { stream ->
                            if (resumeOffset > 0L) stream.skip(resumeOffset)
                            val buffer = ByteArray(CHUNK_SIZE)
                            while (isActive && fileSent < actualSize) {
                                val toRead = minOf(buffer.size.toLong(), actualSize - fileSent).toInt()
                                val read = stream.read(buffer, 0, toRead)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                crc.update(buffer, 0, read)
                                fileSent += read
                                sessionBytesSent += read
                                updateFileProgress(
                                    itemId = item.id,
                                    transferred = fileSent,
                                    total = actualSize,
                                    status = FileTransferStatus.TRANSFERRING,
                                    resumedFrom = resumeOffset
                                )
                            }
                        }
                    }

                    output.writeLong(crc.value)
                    output.flush()
                    val ack = input.readByte().toInt()
                    val completed = ack == 1 && fileSent >= actualSize

                    val fileElapsedSec = ((System.currentTimeMillis() - fileStartMs).coerceAtLeast(1L)) / 1000.0
                    val fileSpeed = ((fileSent - resumeOffset) / fileElapsedSec).toLong().coerceAtLeast(0L)

                    updateFileProgress(
                        itemId = item.id,
                        transferred = fileSent,
                        total = actualSize,
                        status = if (completed) FileTransferStatus.COMPLETED else FileTransferStatus.FAILED,
                        resumedFrom = resumeOffset
                    )

                    historyRepository.recordTransfer(
                        TransferHistoryEntity(
                            fileName = fileName,
                            filePath = sourceFile?.absolutePath ?: item.path,
                            fileSizeBytes = actualSize,
                            category = item.category.name,
                            direction = "SENT",
                            speedBytesPerSec = fileSpeed,
                            peerName = peerName,
                            status = if (completed) "COMPLETED" else "FAILED"
                        )
                    )
                    index++
                }

                // Signal end of batch (0 = done)
                output.writeByte(0)
                output.flush()

                _sessionState.update {
                    it.copy(
                        phase = ConnectionPhase.COMPLETED,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L,
                        statusMessage = "Transfer complete (${it.completedCount}/${it.files.size})"
                    )
                }
            } catch (_: CancellationException) {
                _sessionState.update {
                    it.copy(phase = ConnectionPhase.IDLE, statusMessage = "Transfer cancelled")
                }
            } catch (e: Exception) {
                _sessionState.update {
                    it.copy(
                        phase = ConnectionPhase.ERROR,
                        errorMessage = e.localizedMessage ?: "Socket transfer error"
                    )
                }
            } finally {
                closeSockets()
                onFinished()
            }
        }
    }

    fun startReceiverClient(
        payload: QrConnectionPayload,
        deviceName: String,
        outputDir: File,
        onProgressNotification: (title: String, detail: String, progressPct: Int) -> Unit,
        onFinished: () -> Unit
    ) {
        cancelTransfer()
        _sessionState.value = TransferSessionState(
            phase = ConnectionPhase.CONNECTING_TO_SENDER,
            direction = TransferDirection.RECEIVED,
            qrPayload = payload,
            peerName = payload.deviceName,
            statusMessage = "Connecting to ${payload.hostIp}:${payload.port}..."
        )

        activeJob = scope.launch {
            try {
                outputDir.mkdirs()
                val socket = Socket()
                clientSocket = socket
                socket.tcpNoDelay = true
                socket.receiveBufferSize = 256 * 1024
                socket.connect(InetSocketAddress(payload.hostIp, payload.port), 15_000)

                val input = DataInputStream(BufferedInputStream(socket.getInputStream(), 256 * 1024))
                val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream(), 65536))

                val senderMagic = input.readUTF()
                if (senderMagic != PROTOCOL_MAGIC) {
                    throw IllegalStateException("Invalid FlashShare sender")
                }
                val senderName = input.readUTF()
                output.writeUTF(PROTOCOL_MAGIC)
                output.writeUTF(deviceName)
                output.flush()

                _sessionState.update {
                    it.copy(
                        phase = ConnectionPhase.TRANSFERRING,
                        peerName = senderName,
                        statusMessage = "Receiving from $senderName"
                    )
                }

                var sessionBytesReceived = 0L
                val sessionStartMs = System.currentTimeMillis()
                var fileSeq = 0

                while (isActive) {
                    val hasNext = input.readByte().toInt()
                    if (hasNext == 0) break

                    val fileName = sanitizeFileName(input.readUTF())
                    val categoryName = input.readUTF()
                    val totalBytes = input.readLong()
                    val itemId = "rx_${fileSeq++}_$fileName"

                    val partFile = File(outputDir, "$fileName.fspart")
                    val finalFile = uniqueDestinationFile(outputDir, fileName)

                    // Chunked resume check: resume from existing partial file if valid
                    val resumeOffset = if (partFile.exists() && partFile.length() in 1L until totalBytes) {
                        partFile.length()
                    } else {
                        0L
                    }

                    output.writeLong(resumeOffset)
                    output.flush()

                    _sessionState.update { state ->
                        state.copy(
                            files = state.files + FileTransferProgress(
                                itemId = itemId,
                                fileName = fileName,
                                totalBytes = totalBytes,
                                transferredBytes = resumeOffset,
                                status = FileTransferStatus.TRANSFERRING,
                                resumedFromBytes = resumeOffset
                            )
                        )
                    }

                    val crc = CRC32()
                    var received = resumeOffset
                    val fileStartMs = System.currentTimeMillis()
                    var lastReportMs = fileStartMs

                    RandomAccessFile(partFile, "rw").use { raf ->
                        raf.seek(resumeOffset)
                        val buffer = ByteArray(CHUNK_SIZE)
                        while (isActive && received < totalBytes) {
                            val toRead = minOf(buffer.size.toLong(), totalBytes - received).toInt()
                            val read = input.read(buffer, 0, toRead)
                            if (read <= 0) throw IllegalStateException("Connection dropped mid-file")
                            raf.write(buffer, 0, read)
                            crc.update(buffer, 0, read)
                            received += read
                            sessionBytesReceived += read

                            val now = System.currentTimeMillis()
                            if (now - lastReportMs >= 200L || received >= totalBytes) {
                                lastReportMs = now
                                val elapsedSec = ((now - sessionStartMs).coerceAtLeast(1L)) / 1000.0
                                val speed = (sessionBytesReceived / elapsedSec).toLong()
                                updateFileProgress(
                                    itemId = itemId,
                                    transferred = received,
                                    total = totalBytes,
                                    status = FileTransferStatus.TRANSFERRING,
                                    resumedFrom = resumeOffset,
                                    speedBps = speed
                                )
                                val pct = if (totalBytes > 0L) ((received * 100L) / totalBytes).toInt() else 100
                                onProgressNotification("Receiving from $senderName", "$fileName ($pct%)", pct)
                            }
                        }
                    }

                    val senderCrc = input.readLong()
                    val integrityOk = (senderCrc == crc.value) && received == totalBytes
                    if (integrityOk) {
                        if (finalFile.exists()) finalFile.delete()
                        partFile.renameTo(finalFile)
                        MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), null, null)
                    }

                    output.writeByte(if (integrityOk) 1 else 0)
                    output.flush()

                    val fileElapsedSec = ((System.currentTimeMillis() - fileStartMs).coerceAtLeast(1L)) / 1000.0
                    val fileSpeed = ((received - resumeOffset) / fileElapsedSec).toLong().coerceAtLeast(0L)

                    updateFileProgress(
                        itemId = itemId,
                        transferred = received,
                        total = totalBytes,
                        status = if (integrityOk) FileTransferStatus.COMPLETED else FileTransferStatus.FAILED,
                        resumedFrom = resumeOffset,
                        savedPath = if (integrityOk) finalFile.absolutePath else null
                    )

                    historyRepository.recordTransfer(
                        TransferHistoryEntity(
                            fileName = finalFile.name,
                            filePath = finalFile.absolutePath,
                            fileSizeBytes = totalBytes,
                            category = categoryName,
                            direction = "RECEIVED",
                            speedBytesPerSec = fileSpeed,
                            peerName = senderName,
                            status = if (integrityOk) "COMPLETED" else "FAILED"
                        )
                    )
                }

                _sessionState.update {
                    it.copy(
                        phase = ConnectionPhase.COMPLETED,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L,
                        statusMessage = "Received ${it.completedCount} file(s) safely"
                    )
                }
            } catch (_: CancellationException) {
                _sessionState.update {
                    it.copy(phase = ConnectionPhase.IDLE, statusMessage = "Receive cancelled")
                }
            } catch (e: Exception) {
                _sessionState.update {
                    it.copy(
                        phase = ConnectionPhase.ERROR,
                        errorMessage = e.localizedMessage ?: "Failed to connect to sender"
                    )
                }
            } finally {
                closeSockets()
                onFinished()
            }
        }
    }

    fun setMinimized(minimized: Boolean) {
        _sessionState.update { it.copy(isMinimized = minimized) }
    }

    fun cancelTransfer() {
        activeJob?.cancel()
        activeJob = null
        closeSockets()
        _sessionState.value = TransferSessionState(phase = ConnectionPhase.IDLE)
    }

    private suspend fun resolveReadableFile(item: TransferItem): File? {
        if (item.isSplitApk) {
            return appRepository.prepareApkFileForTransfer(item)
        }
        val direct = File(item.path)
        if (direct.exists() && direct.canRead()) return direct
        return null
    }

    private fun updateFileProgress(
        itemId: String,
        transferred: Long,
        total: Long,
        status: FileTransferStatus,
        resumedFrom: Long = 0L,
        speedBps: Long? = null,
        savedPath: String? = null
    ) {
        _sessionState.update { state ->
            val updatedFiles = state.files.map { f ->
                if (f.itemId == itemId) {
                    f.copy(
                        totalBytes = total,
                        transferredBytes = transferred,
                        status = status,
                        resumedFromBytes = resumedFrom,
                        savedPath = savedPath ?: f.savedPath
                    )
                } else {
                    f
                }
            }
            val effectiveSpeed = speedBps ?: state.speedBytesPerSec
            val remainingBytes = updatedFiles.sumOf { (it.totalBytes - it.transferredBytes).coerceAtLeast(0L) }
            val eta = if (effectiveSpeed > 0L) remainingBytes / effectiveSpeed else 0L
            state.copy(
                files = updatedFiles,
                speedBytesPerSec = effectiveSpeed,
                etaSeconds = eta
            )
        }
    }

    private fun closeSockets() {
        try {
            clientSocket?.close()
        } catch (_: Exception) {
        }
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        clientSocket = null
        serverSocket = null
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[<>:\"|?*]"), "_")
            .ifBlank { "file_${System.currentTimeMillis()}" }
    }

    private fun uniqueDestinationFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) ".${name.substringAfterLast('.')}" else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${base}_($counter)$ext")
            counter++
        }
        return candidate
    }
}
