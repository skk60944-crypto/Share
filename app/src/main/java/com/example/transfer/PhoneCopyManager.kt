package com.example.transfer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.data.model.ItemCategory
import com.example.data.model.PhoneCopyCategoryItem
import com.example.data.model.PhoneCopyCategoryType
import com.example.data.model.SmartBucketType
import com.example.data.model.TransferItem
import com.example.data.repository.AppRepository
import com.example.data.repository.FileRepository
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class PhoneCopyManager(
    private val context: Context,
    private val appRepository: AppRepository,
    private val mediaRepository: MediaRepository,
    private val fileRepository: FileRepository
) {

    suspend fun scanPhoneCopySummary(): List<PhoneCopyCategoryItem> = withContext(Dispatchers.IO) {
        val (localApps, _) = appRepository.loadInstalledApps()
        val photos = mediaRepository.loadPhotos()
        val videos = mediaRepository.loadVideos()
        val music = mediaRepository.loadMusic()
        val buckets = fileRepository.scanSmartBuckets()
        val docs = buckets[SmartBucketType.DOCUMENTS]?.files.orEmpty()

        val hasContactsPerm = hasPermission(Manifest.permission.READ_CONTACTS)
        val hasCallsPerm = hasPermission(Manifest.permission.READ_CALL_LOG)
        val hasSmsPerm = hasPermission(Manifest.permission.READ_SMS)

        val contactsCount = if (hasContactsPerm) countCursorRows(ContactsContract.Contacts.CONTENT_URI) else 0
        val callLogCount = if (hasCallsPerm) countCursorRows(CallLog.Calls.CONTENT_URI) else 0
        val smsCount = if (hasSmsPerm) countCursorRows(Telephony.Sms.CONTENT_URI) else 0

        listOf(
            PhoneCopyCategoryItem(
                type = PhoneCopyCategoryType.APPS,
                count = localApps.size,
                totalBytes = localApps.sumOf { it.totalSizeBytes }
            ),
            PhoneCopyCategoryItem(
                type = PhoneCopyCategoryType.PHOTOS,
                count = photos.size,
                totalBytes = photos.sumOf { it.sizeBytes }
            ),
            PhoneCopyCategoryItem(
                type = PhoneCopyCategoryType.VIDEOS,
                count = videos.size,
                totalBytes = videos.sumOf { it.sizeBytes }
            ),
            PhoneCopyCategoryItem(
                type = PhoneCopyCategoryType.MUSIC,
                count = music.size,
                totalBytes = music.sumOf { it.sizeBytes }
            ),
            PhoneCopyCategoryItem(
                type = PhoneCopyCategoryType.DOCUMENTS,
                count = docs.size,
                totalBytes = docs.sumOf { it.sizeBytes }
            ),
            PhoneCopyCategoryItem(
                type = PhoneCopyCategoryType.CONTACTS,
                count = contactsCount,
                totalBytes = contactsCount * 256L,
                permissionGranted = hasContactsPerm
            ),
            PhoneCopyCategoryItem(
                type = PhoneCopyCategoryType.CALL_LOGS,
                count = callLogCount,
                totalBytes = callLogCount * 160L,
                permissionGranted = hasCallsPerm
            ),
            PhoneCopyCategoryItem(
                type = PhoneCopyCategoryType.SMS,
                count = smsCount,
                totalBytes = smsCount * 220L,
                permissionGranted = hasSmsPerm
            )
        )
    }

    suspend fun buildMigrationBatch(selectedTypes: Set<PhoneCopyCategoryType>): List<TransferItem> =
        withContext(Dispatchers.IO) {
            val batch = mutableListOf<TransferItem>()
            val backupDir = File(context.cacheDir, "phone_copy_exports").apply { mkdirs() }

            if (PhoneCopyCategoryType.APPS in selectedTypes) {
                val (localApps, _) = appRepository.loadInstalledApps()
                batch.addAll(localApps.map { it.toTransferItem() })
            }
            if (PhoneCopyCategoryType.PHOTOS in selectedTypes) {
                batch.addAll(mediaRepository.loadPhotos().map { it.toTransferItem() })
            }
            if (PhoneCopyCategoryType.VIDEOS in selectedTypes) {
                batch.addAll(mediaRepository.loadVideos().map { it.toTransferItem() })
            }
            if (PhoneCopyCategoryType.MUSIC in selectedTypes) {
                batch.addAll(mediaRepository.loadMusic().map { it.toTransferItem() })
            }
            if (PhoneCopyCategoryType.DOCUMENTS in selectedTypes) {
                val docs = fileRepository.scanSmartBuckets()[SmartBucketType.DOCUMENTS]?.files.orEmpty()
                batch.addAll(docs.map { it.toTransferItem() })
            }
            if (PhoneCopyCategoryType.CONTACTS in selectedTypes && hasPermission(Manifest.permission.READ_CONTACTS)) {
                exportContactsToVcf(File(backupDir, "FlashShare_Contacts.vcf"))?.let { vcf ->
                    batch.add(
                        TransferItem(
                            id = "backup_contacts",
                            name = vcf.name,
                            path = vcf.absolutePath,
                            sizeBytes = vcf.length(),
                            mimeType = "text/vcard",
                            category = ItemCategory.BACKUP
                        )
                    )
                }
            }
            if (PhoneCopyCategoryType.CALL_LOGS in selectedTypes && hasPermission(Manifest.permission.READ_CALL_LOG)) {
                exportCallLogsToJson(File(backupDir, "FlashShare_CallLogs.json"))?.let { f ->
                    batch.add(
                        TransferItem(
                            id = "backup_call_logs",
                            name = f.name,
                            path = f.absolutePath,
                            sizeBytes = f.length(),
                            mimeType = "application/json",
                            category = ItemCategory.BACKUP
                        )
                    )
                }
            }
            if (PhoneCopyCategoryType.SMS in selectedTypes && hasPermission(Manifest.permission.READ_SMS)) {
                exportSmsToJson(File(backupDir, "FlashShare_SMS.json"))?.let { f ->
                    batch.add(
                        TransferItem(
                            id = "backup_sms",
                            name = f.name,
                            path = f.absolutePath,
                            sizeBytes = f.length(),
                            mimeType = "application/json",
                            category = ItemCategory.BACKUP
                        )
                    )
                }
            }
            batch
        }

    private fun exportContactsToVcf(dest: File): File? {
        return try {
            val sb = StringBuilder()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Contact" else "Contact"
                    val number = if (numCol >= 0) cursor.getString(numCol) ?: "" else ""
                    sb.append("BEGIN:VCARD\r\n")
                    sb.append("VERSION:3.0\r\n")
                    sb.append("FN:").append(name).append("\r\n")
                    if (number.isNotBlank()) {
                        sb.append("TEL;TYPE=CELL:").append(number).append("\r\n")
                    }
                    sb.append("END:VCARD\r\n")
                }
            }
            if (sb.isEmpty()) return null
            dest.writeText(sb.toString())
            dest
        } catch (_: Exception) {
            null
        }
    }

    private fun exportCallLogsToJson(dest: File): File? {
        return try {
            val arr = JSONArray()
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    arr.put(
                        JSONObject().apply {
                            put("number", cursor.getString(0) ?: "")
                            put("name", cursor.getString(1) ?: "")
                            put("type", cursor.getInt(2))
                            put("date", cursor.getLong(3))
                            put("duration", cursor.getLong(4))
                        }
                    )
                }
            }
            if (arr.length() == 0) return null
            dest.writeText(arr.toString(2))
            dest
        } catch (_: Exception) {
            null
        }
    }

    private fun exportSmsToJson(dest: File): File? {
        return try {
            val arr = JSONArray()
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                ),
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    arr.put(
                        JSONObject().apply {
                            put("address", cursor.getString(0) ?: "")
                            put("body", cursor.getString(1) ?: "")
                            put("date", cursor.getLong(2))
                            put("type", cursor.getInt(3))
                        }
                    )
                }
            }
            if (arr.length() == 0) return null
            dest.writeText(arr.toString(2))
            dest
        } catch (_: Exception) {
            null
        }
    }

    private fun countCursorRows(uri: android.net.Uri): Int {
        return try {
            context.contentResolver.query(uri, arrayOf("_id"), null, null, null)?.use { it.count } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
