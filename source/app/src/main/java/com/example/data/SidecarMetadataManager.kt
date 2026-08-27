package com.example.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SidecarMetadataManager {
    private const val TAG = "SidecarMetadata"
    const val METADATA_EXT = ".audire.meta"
    const val FOLDER_INDEX_FILE = ".audire_library_meta.json"

    data class SidecarData(
        val title: String,
        val author: String,
        val durationMillis: Long,
        val currentPositionMillis: Long,
        val isCompleted: Boolean,
        val isFavorite: Boolean,
        val lastListenedTime: Long,
        val coverUrl: String = "",
        val quotes: List<QuoteItem> = emptyList()
    )

    data class QuoteItem(
        val quoteText: String,
        val pageReference: String,
        val timestamp: Long
    )

    /**
     * Resolves a physical on-disk absolute file path from various Android URI formats.
     */
    fun resolvePhysicalFilePath(context: Context, pathOrUri: String): File? {
        try {
            if (pathOrUri.isBlank() || pathOrUri.startsWith("demo://") || pathOrUri.startsWith("http://") || pathOrUri.startsWith("https://")) {
                return null
            }

            // 1. Direct path / file:// scheme
            if (pathOrUri.startsWith("/")) {
                val f = File(pathOrUri)
                return f
            }
            if (pathOrUri.startsWith("file://")) {
                val path = Uri.parse(pathOrUri).path
                if (!path.isNullOrBlank()) return File(path)
            }

            // 2. content:// URI decoding
            if (pathOrUri.startsWith("content://")) {
                val decoded = Uri.decode(pathOrUri)

                // 2a. Raw file paths embedded in URI (common in Download provider)
                val rawIdx = decoded.indexOf("raw:")
                if (rawIdx != -1) {
                    val rawPath = decoded.substring(rawIdx + 4)
                    val f = File(rawPath)
                    if (f.exists() || f.parentFile?.exists() == true) {
                        return f
                    }
                }

                val uri = Uri.parse(pathOrUri)
                val auth = uri.authority ?: ""

                // 2b. External Storage Documents Provider (primary:Path/To/Book.epub)
                if (auth == "com.android.externalstorage.documents") {
                    val docId = try {
                        if (DocumentsContract.isDocumentUri(context, uri)) {
                            DocumentsContract.getDocumentId(uri)
                        } else if (DocumentsContract.isTreeUri(uri)) {
                            DocumentsContract.getTreeDocumentId(uri)
                        } else {
                            uri.lastPathSegment ?: ""
                        }
                    } catch (e: Throwable) {
                        uri.lastPathSegment ?: ""
                    }

                    val parts = docId.split(":")
                    if (parts.size >= 2) {
                        val type = parts[0]
                        val relPath = parts[1]
                        val basePath = if ("primary".equals(type, ignoreCase = true)) {
                            Environment.getExternalStorageDirectory().absolutePath
                        } else {
                            "/storage/$type"
                        }
                        val candidate = File(basePath, relPath)
                        return candidate
                    }
                }

                // 2c. Query MediaStore DATA column
                try {
                    val proj = arrayOf(MediaStore.MediaColumns.DATA)
                    context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                        val col = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (col != -1 && cursor.moveToFirst()) {
                            val data = cursor.getString(col)
                            if (!data.isNullOrBlank()) {
                                return File(data)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    // Silently ignore
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error resolving physical path for $pathOrUri: ${e.message}")
        }
        return null
    }

    /**
     * Resolves the display file name of a book given its URI or path.
     */
    fun resolveFileName(context: Context, pathOrUri: String): String {
        try {
            if (pathOrUri.startsWith("content://")) {
                val uri = Uri.parse(pathOrUri)
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (col != -1 && cursor.moveToFirst()) {
                        val name = cursor.getString(col)
                        if (!name.isNullOrBlank()) return name
                    }
                }
                val docId = try {
                    if (DocumentsContract.isDocumentUri(context, uri)) {
                        DocumentsContract.getDocumentId(uri)
                    } else {
                        uri.lastPathSegment ?: ""
                    }
                } catch (e: Throwable) {
                    uri.lastPathSegment ?: ""
                }
                val lastPart = docId.substringAfterLast("/").substringAfterLast(":")
                if (lastPart.isNotBlank()) return lastPart
            }
            val clean = if (pathOrUri.startsWith("file://")) Uri.parse(pathOrUri).path ?: pathOrUri else pathOrUri
            val f = File(clean)
            if (f.name.isNotBlank()) return f.name
        } catch (e: Throwable) {
            // Silently fallback
        }
        return "book"
    }

    /**
     * Saves sidecar companion metadata for a book into its local file directory or Scoped Storage location.
     */
    suspend fun saveBookMetadata(
        context: Context,
        book: Audiobook,
        quotes: List<BookQuote> = emptyList()
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("formatVersion", 1)
                    put("appName", "Audire")
                    put("title", book.title)
                    put("author", book.author)
                    put("durationMillis", book.durationMillis)
                    put("currentPositionMillis", book.currentPositionMillis)
                    put("isCompleted", book.isCompleted)
                    put("isFavorite", book.isFavorite)
                    put("lastListenedTime", book.lastListenedTime)
                    put("coverUrl", book.coverUrl)
                    put("updatedAt", System.currentTimeMillis())

                    val quotesArray = JSONArray()
                    for (q in quotes) {
                        val qObj = JSONObject().apply {
                            put("quoteText", q.quoteText)
                            put("pageReference", q.pageReference)
                            put("timestamp", q.timestamp)
                        }
                        quotesArray.put(qObj)
                    }
                    put("quotes", quotesArray)
                }

                val jsonString = json.toString(2)
                val path = book.filePath
                var wroteDirectly = false

                val fileName = resolveFileName(context, path)
                val sidecarName = "$fileName$METADATA_EXT"

                // 1. Direct file write if physical path is known
                val physicalFile = resolvePhysicalFilePath(context, path)
                if (physicalFile != null) {
                    try {
                        val parentDir = physicalFile.parentFile
                        if (parentDir != null) {
                            if (!parentDir.exists()) parentDir.mkdirs()
                            val sidecarFile = File(parentDir, sidecarName)
                            sidecarFile.writeText(jsonString, Charsets.UTF_8)
                            if (sidecarFile.exists() && sidecarFile.length() > 0) {
                                wroteDirectly = true
                                Log.d(TAG, "Wrote sidecar to physical file: ${sidecarFile.absolutePath}")
                            }
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Direct write to physical file failed: ${e.message}")
                    }
                }

                // 2. SAF Tree Storage write if granted via Scoped Storage
                if (path.startsWith("content://")) {
                    val uri = Uri.parse(path)
                    try {
                        // Check persisted tree permissions
                        val persistedTrees = context.contentResolver.persistedUriPermissions
                        for (perm in persistedTrees) {
                            if (perm.isWritePermission) {
                                val treeDoc = DocumentFile.fromTreeUri(context, perm.uri)
                                if (treeDoc != null && treeDoc.canWrite()) {
                                    // Check if this tree is the container of our file
                                    val treeId = try { DocumentsContract.getTreeDocumentId(perm.uri) } catch (e: Throwable) { "" }
                                    val docId = try { DocumentsContract.getDocumentId(uri) } catch (e: Throwable) { "" }
                                    
                                    if (treeId.isNotEmpty() && docId.startsWith(treeId)) {
                                        // Construct parent doc ID
                                        val parentDocId = docId.substringBeforeLast("/")
                                        val parentUri = DocumentsContract.buildDocumentUriUsingTree(perm.uri, parentDocId)
                                        val parentDoc = DocumentFile.fromTreeUri(context, parentUri)
                                        
                                        val targetDir = if (parentDoc != null && parentDoc.exists()) parentDoc else treeDoc
                                        val existing = targetDir.findFile(sidecarName)
                                        val targetSidecarDoc = existing ?: targetDir.createFile("application/json", sidecarName)
                                        
                                        if (targetSidecarDoc != null) {
                                            context.contentResolver.openOutputStream(targetSidecarDoc.uri, "wt")?.use { out ->
                                                out.write(jsonString.toByteArray(Charsets.UTF_8))
                                            }
                                            wroteDirectly = true
                                            Log.d(TAG, "Wrote sidecar via SAF tree: ${targetSidecarDoc.uri}")
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "SAF tree write failed: ${e.message}")
                    }
                }

                // 3. App local storage mirror (always kept synchronized as fallback)
                val mirrorDir = File(context.filesDir, "sidecars")
                if (!mirrorDir.exists()) mirrorDir.mkdirs()
                val safeName = (path.hashCode().toString().replace("-", "n")) + METADATA_EXT
                val mirrorFile = File(mirrorDir, safeName)
                mirrorFile.writeText(jsonString, Charsets.UTF_8)

                wroteDirectly || mirrorFile.exists()
            } catch (e: Throwable) {
                Log.w(TAG, "Could not write sidecar metadata for ${book.title}: ${e.message}")
                false
            }
        }
    }

    /**
     * Reads companion metadata for a book file if it exists.
     */
    suspend fun readBookMetadata(
        context: Context,
        filePath: String
    ): SidecarData? {
        return withContext(Dispatchers.IO) {
            try {
                var jsonContent: String? = null
                val fileName = resolveFileName(context, filePath)
                val sidecarName = "$fileName$METADATA_EXT"

                // 1. Direct physical file check
                val physicalFile = resolvePhysicalFilePath(context, filePath)
                if (physicalFile != null) {
                    val parentDir = physicalFile.parentFile
                    if (parentDir != null) {
                        val sidecar = File(parentDir, sidecarName)
                        if (sidecar.exists() && sidecar.canRead()) {
                            try {
                                jsonContent = sidecar.readText(Charsets.UTF_8)
                            } catch (e: Throwable) {}
                        }
                    }
                }

                // 2. SAF Tree Storage read
                if (jsonContent == null && filePath.startsWith("content://")) {
                    val uri = Uri.parse(filePath)
                    try {
                        val persistedTrees = context.contentResolver.persistedUriPermissions
                        for (perm in persistedTrees) {
                            val treeId = try { DocumentsContract.getTreeDocumentId(perm.uri) } catch (e: Throwable) { "" }
                            val docId = try { DocumentsContract.getDocumentId(uri) } catch (e: Throwable) { "" }
                            if (treeId.isNotEmpty() && docId.startsWith(treeId)) {
                                val parentDocId = docId.substringBeforeLast("/")
                                val parentUri = DocumentsContract.buildDocumentUriUsingTree(perm.uri, parentDocId)
                                val parentDoc = DocumentFile.fromTreeUri(context, parentUri) ?: DocumentFile.fromTreeUri(context, perm.uri)
                                val sidecarDoc = parentDoc?.findFile(sidecarName)
                                if (sidecarDoc != null) {
                                    context.contentResolver.openInputStream(sidecarDoc.uri)?.use { input ->
                                        jsonContent = input.bufferedReader().readText()
                                    }
                                    if (!jsonContent.isNullOrBlank()) break
                                }
                            }
                        }
                    } catch (e: Throwable) {}
                }

                // 3. App private mirror fallback
                if (jsonContent == null) {
                    val mirrorDir = File(context.filesDir, "sidecars")
                    val safeName = (filePath.hashCode().toString().replace("-", "n")) + METADATA_EXT
                    val mirrorFile = File(mirrorDir, safeName)
                    if (mirrorFile.exists() && mirrorFile.canRead()) {
                        try {
                            jsonContent = mirrorFile.readText(Charsets.UTF_8)
                        } catch (e: Throwable) {}
                    }
                }

                if (jsonContent.isNullOrBlank()) return@withContext null

                val obj = JSONObject(jsonContent)
                val quotesList = mutableListOf<QuoteItem>()
                val quotesArr = obj.optJSONArray("quotes")
                if (quotesArr != null) {
                    for (i in 0 until quotesArr.length()) {
                        val qObj = quotesArr.getJSONObject(i)
                        quotesList.add(
                            QuoteItem(
                                quoteText = qObj.optString("quoteText", ""),
                                pageReference = qObj.optString("pageReference", ""),
                                timestamp = qObj.optLong("timestamp", System.currentTimeMillis())
                            )
                        )
                    }
                }

                SidecarData(
                    title = obj.optString("title", ""),
                    author = obj.optString("author", ""),
                    durationMillis = obj.optLong("durationMillis", 0L),
                    currentPositionMillis = obj.optLong("currentPositionMillis", 0L),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    isFavorite = obj.optBoolean("isFavorite", false),
                    lastListenedTime = obj.optLong("lastListenedTime", 0L),
                    coverUrl = obj.optString("coverUrl", ""),
                    quotes = quotesList
                )
            } catch (e: Throwable) {
                Log.w(TAG, "Error reading sidecar for $filePath: ${e.message}")
                null
            }
        }
    }

    /**
     * Synchronizes metadata for all books in the library to their storage directories.
     */
    suspend fun syncAllBooks(
        context: Context,
        books: List<Audiobook>,
        allQuotes: List<BookQuote>
    ): Int {
        return withContext(Dispatchers.IO) {
            var synced = 0
            val quotesByBook = allQuotes.groupBy { it.bookId }
            for (book in books) {
                val bookQuotes = quotesByBook[book.id] ?: emptyList()
                if (saveBookMetadata(context, book, bookQuotes)) {
                    synced++
                }
            }
            synced
        }
    }
}
