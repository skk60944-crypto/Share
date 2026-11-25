package com.example.transfer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

data class ExtractionResult(
    val success: Boolean,
    val outputFile: File? = null,
    val message: String
)

class VideoAudioExtractor(private val context: Context) {

    /**
     * Extracts the embedded audio stream from a local video completely offline
     * using Android's native MediaExtractor + MediaMuxer pipeline.
     */
    suspend fun extractAudio(
        videoUri: Uri,
        videoDisplayName: String,
        outputDir: File,
        onProgress: (Float) -> Unit = {}
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, videoUri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return@withContext ExtractionResult(
                    success = false,
                    message = "No audio track found in $videoDisplayName"
                )
            }

            extractor.selectTrack(audioTrackIndex)
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            val audioDir = File(outputDir, "ExtractedAudio").apply { mkdirs() }
            val baseName = videoDisplayName.substringBeforeLast(".")
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val outFile = File(audioDir, "${baseName}_${System.currentTimeMillis() % 10000}.m4a")

            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val maxInputSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(256 * 1024)
            } else {
                512 * 1024
            }
            val buffer = ByteBuffer.allocate(maxInputSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                if (durationUs > 0L) {
                    onProgress((bufferInfo.presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f))
                }
                extractor.advance()
            }

            onProgress(1f)
            muxer.stop()
            muxer.release()
            muxer = null

            MediaScannerConnection.scanFile(
                context,
                arrayOf(outFile.absolutePath),
                arrayOf("audio/mp4"),
                null
            )

            ExtractionResult(
                success = true,
                outputFile = outFile,
                message = "Extracted audio saved to ${outFile.name}"
            )
        } catch (e: Exception) {
            ExtractionResult(
                success = false,
                message = "Extraction failed: ${e.localizedMessage ?: "Unsupported audio codec"}"
            )
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
        }
    }
}
