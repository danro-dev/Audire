package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AudiobookDao {
    @Query("SELECT * FROM audiobooks ORDER BY lastListenedTime DESC, title ASC")
    fun getAllAudiobooks(): Flow<List<Audiobook>>

    @Query("SELECT * FROM audiobooks WHERE id = :id")
    fun getAudiobookById(id: Int): Flow<Audiobook?>

    @Query("SELECT * FROM audiobooks")
    suspend fun getAllAudiobooksSync(): List<Audiobook>

    @Query("SELECT * FROM audiobooks WHERE id = :id")
    suspend fun getAudiobookByIdSync(id: Int): Audiobook?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudiobook(audiobook: Audiobook)

    @Update
    suspend fun updateAudiobook(audiobook: Audiobook)

    @Query("DELETE FROM audiobooks WHERE id = :id")
    suspend fun deleteAudiobookById(id: Int)

    @Query("DELETE FROM audiobooks")
    suspend fun deleteAllAudiobooks()

    @Query("DELETE FROM scan_directories")
    suspend fun deleteAllScanDirectories()

    @Query("SELECT * FROM scan_directories")
    suspend fun getAllScanDirectoriesSync(): List<ScanDirectory>

    @Query("SELECT * FROM scan_directories")
    fun getAllScanDirectories(): Flow<List<ScanDirectory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScanDirectory(directory: ScanDirectory)

    @Query("DELETE FROM scan_directories WHERE path = :path")
    suspend fun deleteScanDirectory(path: String)

    @Query("SELECT * FROM listening_logs ORDER BY date DESC")
    fun getAllListeningLogs(): Flow<List<ListeningLog>>

    @Query("SELECT * FROM listening_logs WHERE date = :date")
    suspend fun getListeningLogByDate(date: String): ListeningLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListeningLog(log: ListeningLog)

    @Query("DELETE FROM listening_logs")
    suspend fun deleteAllListeningLogs()
}
