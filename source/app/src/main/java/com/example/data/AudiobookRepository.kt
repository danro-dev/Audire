package com.example.data

import android.util.Log
import kotlinx.coroutines.flow.Flow

class AudiobookRepository(private val dao: AudiobookDao) {

    val allAudiobooks: Flow<List<Audiobook>> = dao.getAllAudiobooks()
    val allScanDirectories: Flow<List<ScanDirectory>> = dao.getAllScanDirectories()

    fun getAudiobookById(id: Int): Flow<Audiobook?> = dao.getAudiobookById(id)

    suspend fun insertAudiobook(audiobook: Audiobook) {
        dao.insertAudiobook(audiobook)
    }

    suspend fun deleteAudiobookById(id: Int) {
        dao.deleteAudiobookById(id)
    }

    suspend fun getAllAudiobooksSync(): List<Audiobook> = dao.getAllAudiobooksSync()

    suspend fun deleteAllAudiobooks() {
        dao.deleteAllAudiobooks()
    }

    suspend fun deleteAllScanDirectories() {
        dao.deleteAllScanDirectories()
    }

    suspend fun getAllScanDirectoriesSync(): List<ScanDirectory> = dao.getAllScanDirectoriesSync()

    suspend fun insertScanDirectory(directory: ScanDirectory) {
        dao.insertScanDirectory(directory)
    }

    suspend fun deleteScanDirectory(path: String) {
        dao.deleteScanDirectory(path)
    }

    val allListeningLogs: Flow<List<ListeningLog>> = dao.getAllListeningLogs()

    suspend fun addListeningDuration(date: String, durationMillis: Long) {
        val existingLog = dao.getListeningLogByDate(date)
        val currentDuration = existingLog?.durationMillis ?: 0L
        val updatedLog = ListeningLog(date, currentDuration + durationMillis)
        dao.insertListeningLog(updatedLog)
    }

    suspend fun deleteAllListeningLogs() {
        dao.deleteAllListeningLogs()
    }

    /**
     * Updates playback progress adhering to the "Inmutabilidad del Estado de Reproducción" rule.
     * Saved position must never be less than previously saved position, unless [isExplicitRewind] is true.
     */
    suspend fun updatePlaybackPosition(
        audiobookId: Int,
        newPositionMillis: Long,
        isExplicitRewind: Boolean = false
    ) {
        val existing = dao.getAudiobookByIdSync(audiobookId) ?: return

        // Core Domain Rule: Inmutabilidad del Estado de Reproducción
        if (!isExplicitRewind && newPositionMillis < existing.currentPositionMillis) {
            Log.d("AudiobookRepository", "Ignored progress update to prevent decreasing progress (Inmutable Progress Rule)")
            return
        }

        val completed = newPositionMillis >= existing.durationMillis - 2000 // mark as completed if within 2 seconds of end
        val updated = existing.copy(
            currentPositionMillis = newPositionMillis.coerceIn(0, existing.durationMillis),
            lastListenedTime = System.currentTimeMillis(),
            isCompleted = completed
        )
        dao.updateAudiobook(updated)
    }

    val allBookQuotes: Flow<List<BookQuote>> = dao.getAllBookQuotes()

    fun getBookQuotesForBook(bookId: Int): Flow<List<BookQuote>> = dao.getBookQuotesForBook(bookId)

    suspend fun insertBookQuote(quote: BookQuote) {
        dao.insertBookQuote(quote)
    }

    suspend fun deleteBookQuoteById(id: Int) {
        dao.deleteBookQuoteById(id)
    }

    suspend fun deleteAllBookQuotes() {
        dao.deleteAllBookQuotes()
    }
}
