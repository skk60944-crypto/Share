package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class TransferHistoryEntity(
    val id: Long = 0L,
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val category: String,
    val direction: String, // "SENT" or "RECEIVED"
    val speedBytesPerSec: Long,
    val peerName: String,
    val status: String, // "COMPLETED", "FAILED", "CANCELLED"
    val timestampMs: Long = System.currentTimeMillis()
)

class HistoryDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "flashshare_history.db"
        private const val DB_VERSION = 1
        private const val TABLE = "transfer_history"

        @Volatile
        private var INSTANCE: HistoryDatabase? = null

        fun getInstance(context: Context): HistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HistoryDatabase(context).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fileName TEXT NOT NULL,
                filePath TEXT NOT NULL,
                fileSizeBytes INTEGER NOT NULL,
                category TEXT NOT NULL,
                direction TEXT NOT NULL,
                speedBytesPerSec INTEGER NOT NULL,
                peerName TEXT NOT NULL,
                status TEXT NOT NULL,
                timestampMs INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun queryAll(): List<TransferHistoryEntity> {
        val list = mutableListOf<TransferHistoryEntity>()
        val db = readableDatabase
        db.query(TABLE, null, null, null, null, null, "timestampMs DESC").use { c ->
            while (c.moveToNext()) {
                list.add(
                    TransferHistoryEntity(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        fileName = c.getString(c.getColumnIndexOrThrow("fileName")),
                        filePath = c.getString(c.getColumnIndexOrThrow("filePath")),
                        fileSizeBytes = c.getLong(c.getColumnIndexOrThrow("fileSizeBytes")),
                        category = c.getString(c.getColumnIndexOrThrow("category")),
                        direction = c.getString(c.getColumnIndexOrThrow("direction")),
                        speedBytesPerSec = c.getLong(c.getColumnIndexOrThrow("speedBytesPerSec")),
                        peerName = c.getString(c.getColumnIndexOrThrow("peerName")),
                        status = c.getString(c.getColumnIndexOrThrow("status")),
                        timestampMs = c.getLong(c.getColumnIndexOrThrow("timestampMs"))
                    )
                )
            }
        }
        return list
    }

    fun insertEntry(entry: TransferHistoryEntity): Long {
        val values = ContentValues().apply {
            put("fileName", entry.fileName)
            put("filePath", entry.filePath)
            put("fileSizeBytes", entry.fileSizeBytes)
            put("category", entry.category)
            put("direction", entry.direction)
            put("speedBytesPerSec", entry.speedBytesPerSec)
            put("peerName", entry.peerName)
            put("status", entry.status)
            put("timestampMs", entry.timestampMs)
        }
        return writableDatabase.insert(TABLE, null, values)
    }

    fun deleteById(id: Long) {
        writableDatabase.delete(TABLE, "id = ?", arrayOf(id.toString()))
    }

    fun clearDirection(direction: String) {
        writableDatabase.delete(TABLE, "direction = ?", arrayOf(direction))
    }
}

class HistoryRepository(private val db: HistoryDatabase) {

    private val _allEntries = MutableStateFlow<List<TransferHistoryEntity>>(emptyList())

    init {
        _allEntries.value = runCatching { db.queryAll() }.getOrDefault(emptyList())
    }

    val allHistory: Flow<List<TransferHistoryEntity>> = _allEntries

    val receivedHistory: Flow<List<TransferHistoryEntity>> =
        _allEntries.map { list -> list.filter { it.direction == "RECEIVED" } }

    val sentHistory: Flow<List<TransferHistoryEntity>> =
        _allEntries.map { list -> list.filter { it.direction == "SENT" } }

    suspend fun recordTransfer(entry: TransferHistoryEntity) = withContext(Dispatchers.IO) {
        db.insertEntry(entry)
        _allEntries.value = db.queryAll()
    }

    suspend fun deleteRecord(id: Long) = withContext(Dispatchers.IO) {
        db.deleteById(id)
        _allEntries.value = db.queryAll()
    }

    suspend fun clearDirection(direction: String) = withContext(Dispatchers.IO) {
        db.clearDirection(direction)
        _allEntries.value = db.queryAll()
    }
}
