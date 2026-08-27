package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object ThumbnailManager {
    private const val TAG = "ThumbnailManager"
    private const val THUMB_WIDTH = 480
    private const val THUMB_HEIGHT = 680

    fun getCoversDirectory(context: Context): File {
        val dir = File(context.filesDir, "covers")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Resolves an existing thumbnail or generates a new one on Dispatchers.IO.
     * Returns the absolute file path of the thumbnail image.
     */
    suspend fun getOrCreateThumbnail(context: Context, book: Audiobook, forceRegenerate: Boolean = false): String {
        return withContext(Dispatchers.IO) {
            try {
                // If existing coverUrl is valid and file exists, return it
                if (!forceRegenerate && book.coverUrl.isNotEmpty()) {
                    if (book.coverUrl.startsWith("http://") || book.coverUrl.startsWith("https://")) {
                        return@withContext book.coverUrl
                    }
                    val existingFile = File(book.coverUrl)
                    if (existingFile.exists() && existingFile.length() > 0) {
                        return@withContext book.coverUrl
                    }
                }

                val coversDir = getCoversDirectory(context)
                val safeHash = (book.filePath + "_" + book.title).hashCode().toString().replace("-", "n")
                val targetFile = File(coversDir, "thumb_$safeHash.jpg")

                if (!forceRegenerate && targetFile.exists() && targetFile.length() > 0) {
                    return@withContext targetFile.absolutePath
                }

                // Generate based on file format
                val generatedBitmap: Bitmap? = generateThumbnailBitmap(context, book)
                if (generatedBitmap != null) {
                    FileOutputStream(targetFile).use { fos ->
                        generatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    }
                    generatedBitmap.recycle()
                    return@withContext targetFile.absolutePath
                }

                // Fallback: procedural stylish vector book cover
                val proceduralBitmap = createProceduralCover(book)
                FileOutputStream(targetFile).use { fos ->
                    proceduralBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                }
                proceduralBitmap.recycle()
                targetFile.absolutePath
            } catch (e: Throwable) {
                Log.e(TAG, "Error resolving thumbnail for ${book.title}: ${e.message}", e)
                ""
            }
        }
    }

    private fun generateThumbnailBitmap(context: Context, book: Audiobook): Bitmap? {
        val path = book.filePath

        // 1. Demo assets
        if (path.startsWith("demo://")) {
            val assetName = path.removePrefix("demo://")
            return try {
                if (assetName.endsWith(".pdf", ignoreCase = true)) {
                    val tempFile = File(context.cacheDir, "demo_thumb_$assetName")
                    if (!tempFile.exists()) {
                        context.assets.open(assetName).use { input ->
                            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                        }
                    }
                    renderPdfPage0(tempFile)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to render demo thumbnail for $assetName: ${e.message}")
                null
            }
        }

        // 2. PDF Documents & Comics in PDF format
        if (path.endsWith(".pdf", ignoreCase = true) || book.author.contains("PDF", ignoreCase = true)) {
            try {
                val pfd = openParcelFileDescriptor(context, path)
                if (pfd != null) {
                    pfd.use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            if (renderer.pageCount > 0) {
                                renderer.openPage(0).use { page ->
                                    val origW = page.width.toFloat().coerceAtLeast(100f)
                                    val origH = page.height.toFloat().coerceAtLeast(100f)
                                    val scale = (THUMB_HEIGHT.toFloat() / origH).coerceAtMost(THUMB_WIDTH.toFloat() / origW)
                                    val w = (origW * scale).toInt().coerceIn(120, THUMB_WIDTH)
                                    val h = (origH * scale).toInt().coerceIn(160, THUMB_HEIGHT)

                                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    val canvas = Canvas(bitmap)
                                    canvas.drawColor(Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    return bitmap
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to render PDF thumbnail: ${e.message}")
            }
        }

        // 3. Audiobooks (MP3, M4A, M4B, AAC, FLAC, WAV, OGG)
        if (isAudioFormat(path)) {
            val audioArt = extractAudioArtSafely(context, path)
            if (audioArt != null) {
                return audioArt
            }
        }

        // 4. CBZ / CBR / ZIP comic books
        if (path.endsWith(".cbz", ignoreCase = true) || path.endsWith(".zip", ignoreCase = true)) {
            try {
                val inputStream = if (path.startsWith("content://")) {
                    context.contentResolver.openInputStream(Uri.parse(path))
                } else {
                    File(path).inputStream()
                }
                if (inputStream != null) {
                    ZipInputStream(inputStream).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val entryName = entry.name.lowercase()
                            if (!entry.isDirectory && (entryName.endsWith(".jpg") || entryName.endsWith(".jpeg") || entryName.endsWith(".png") || entryName.endsWith(".webp"))) {
                                val bytes = zis.readBytes()
                                if (bytes.isNotEmpty()) {
                                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    if (bmp != null) return bmp
                                }
                                break
                            }
                            entry = zis.nextEntry
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to extract CBZ cover: ${e.message}")
            }
        }

        return null
    }

    private fun renderPdfPage0(file: File): Bitmap? {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            val origW = page.width.toFloat()
                            val origH = page.height.toFloat()
                            val scale = (THUMB_HEIGHT.toFloat() / origH).coerceAtMost(THUMB_WIDTH.toFloat() / origW)
                            val w = (origW * scale).toInt().coerceIn(120, THUMB_WIDTH)
                            val h = (origH * scale).toInt().coerceIn(160, THUMB_HEIGHT)

                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    } else null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun openParcelFileDescriptor(context: Context, uriString: String): ParcelFileDescriptor? {
        return try {
            if (uriString.startsWith("content://")) {
                context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")
            } else {
                val file = if (uriString.startsWith("file://")) {
                    File(Uri.parse(uriString).path ?: uriString)
                } else {
                    File(uriString)
                }
                if (file.exists()) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun isAudioFormat(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".m4b") ||
                lower.endsWith(".aac") || lower.endsWith(".flac") || lower.endsWith(".wav") ||
                lower.endsWith(".ogg") || lower.endsWith(".opus")
    }

    private fun extractAudioArtSafely(context: Context, path: String): Bitmap? {
        val lower = path.lowercase()
        // Fast direct ID3 / MP4 / FLAC byte check first
        try {
            val stream = openInputStream(context, path)
            if (stream != null) {
                stream.use { input ->
                    val bytes = if (lower.endsWith(".mp3")) {
                        extractId3Picture(input)
                    } else if (lower.endsWith(".m4a") || lower.endsWith(".m4b") || lower.endsWith(".aac")) {
                        extractMp4Covr(input)
                    } else if (lower.endsWith(".flac")) {
                        extractFlacPicture(input)
                    } else {
                        null
                    }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) return bmp
                    }
                }
            }
        } catch (e: Throwable) {
            // Silently ignore
        }

        // Safe fallback using MediaMetadataRetriever only if pure parser couldn't find it but file exists
        var retriever: MediaMetadataRetriever? = null
        try {
            val pfd = openParcelFileDescriptor(context, path) ?: return null
            pfd.use { descriptor ->
                retriever = MediaMetadataRetriever().apply {
                    setDataSource(descriptor.fileDescriptor)
                }
                val hasAudio = retriever?.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                if (hasAudio == "yes") {
                    val picBytes = retriever?.embeddedPicture
                    if (picBytes != null && picBytes.isNotEmpty()) {
                        return android.graphics.BitmapFactory.decodeByteArray(picBytes, 0, picBytes.size)
                    }
                }
            }
        } catch (e: Throwable) {
            // Silently fallback to procedural cover
        } finally {
            try {
                retriever?.release()
            } catch (e: Throwable) {}
        }
        return null
    }

    private fun openInputStream(context: Context, uriString: String): java.io.InputStream? {
        return try {
            if (uriString.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(uriString))
            } else {
                val cleanPath = if (uriString.startsWith("file://")) Uri.parse(uriString).path ?: uriString else uriString
                val file = File(cleanPath)
                if (file.exists() && file.canRead()) {
                    file.inputStream()
                } else {
                    null
                }
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun extractId3Picture(input: java.io.InputStream): ByteArray? {
        val buffered = java.io.BufferedInputStream(input, 32768)
        val header = ByteArray(10)
        if (buffered.read(header, 0, 10) < 10) return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null

        val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                      ((header[7].toInt() and 0x7F) shl 14) or
                      ((header[8].toInt() and 0x7F) shl 7) or
                      (header[9].toInt() and 0x7F)

        if (tagSize <= 0 || tagSize > 10 * 1024 * 1024) return null
        val tagData = ByteArray(tagSize)
        var totalRead = 0
        while (totalRead < tagSize) {
            val read = buffered.read(tagData, totalRead, tagSize - totalRead)
            if (read <= 0) break
            totalRead += read
        }
        if (totalRead < 10) return null

        // Search for "APIC" in tagData
        for (i in 0 until totalRead - 10) {
            if (tagData[i] == 'A'.code.toByte() && tagData[i+1] == 'P'.code.toByte() &&
                tagData[i+2] == 'I'.code.toByte() && tagData[i+3] == 'C'.code.toByte()) {
                val frameSize = ((tagData[i+4].toInt() and 0xFF) shl 24) or
                                ((tagData[i+5].toInt() and 0xFF) shl 16) or
                                ((tagData[i+6].toInt() and 0xFF) shl 8) or
                                (tagData[i+7].toInt() and 0xFF)
                if (frameSize > 0 && i + 10 + frameSize <= totalRead) {
                    val frameStart = i + 10
                    for (j in frameStart until (frameStart + frameSize - 4).coerceAtMost(totalRead - 4)) {
                        if ((tagData[j] == 0xFF.toByte() && tagData[j+1] == 0xD8.toByte()) ||
                            (tagData[j] == 0x89.toByte() && tagData[j+1] == 0x50.toByte() && tagData[j+2] == 0x4E.toByte())) {
                            val imgLen = (frameStart + frameSize) - j
                            if (imgLen > 0) {
                                val imgBytes = ByteArray(imgLen)
                                System.arraycopy(tagData, j, imgBytes, 0, imgLen)
                                return imgBytes
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    private fun extractMp4Covr(input: java.io.InputStream): ByteArray? {
        val buffered = java.io.BufferedInputStream(input, 65536)
        val buffer = ByteArray(65536)
        val read = buffered.read(buffer, 0, 65536)
        if (read < 8) return null
        for (i in 0 until read - 8) {
            if (buffer[i] == 'c'.code.toByte() && buffer[i+1] == 'o'.code.toByte() &&
                buffer[i+2] == 'v'.code.toByte() && buffer[i+3] == 'r'.code.toByte()) {
                for (j in (i + 4) until (read - 4)) {
                    if ((buffer[j] == 0xFF.toByte() && buffer[j+1] == 0xD8.toByte()) ||
                        (buffer[j] == 0x89.toByte() && buffer[j+1] == 0x50.toByte() && buffer[j+2] == 0x4E.toByte())) {
                        val end = (j + 32768).coerceAtMost(read)
                        val imgBytes = ByteArray(end - j)
                        System.arraycopy(buffer, j, imgBytes, 0, end - j)
                        return imgBytes
                    }
                }
            }
        }
        return null
    }

    private fun extractFlacPicture(input: java.io.InputStream): ByteArray? {
        val buffered = java.io.BufferedInputStream(input, 32768)
        val header = ByteArray(4)
        if (buffered.read(header, 0, 4) < 4) return null
        if (header[0] != 'f'.code.toByte() || header[1] != 'L'.code.toByte() || header[2] != 'a'.code.toByte() || header[3] != 'C'.code.toByte()) return null

        var isLast = false
        while (!isLast) {
            val blockHeader = ByteArray(4)
            if (buffered.read(blockHeader, 0, 4) < 4) break
            isLast = (blockHeader[0].toInt() and 0x80) != 0
            val type = blockHeader[0].toInt() and 0x7F
            val length = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                         ((blockHeader[2].toInt() and 0xFF) shl 8) or
                         (blockHeader[3].toInt() and 0xFF)
            if (length <= 0 || length > 10 * 1024 * 1024) break
            if (type == 6) {
                val picData = ByteArray(length)
                var total = 0
                while (total < length) {
                    val r = buffered.read(picData, total, length - total)
                    if (r <= 0) break
                    total += r
                }
                for (j in 0 until (total - 4)) {
                    if ((picData[j] == 0xFF.toByte() && picData[j+1] == 0xD8.toByte()) ||
                        (picData[j] == 0x89.toByte() && picData[j+1] == 0x50.toByte() && picData[j+2] == 0x4E.toByte())) {
                        val imgBytes = ByteArray(total - j)
                        System.arraycopy(picData, j, imgBytes, 0, total - j)
                        return imgBytes
                    }
                }
                return null
            } else {
                buffered.skip(length.toLong())
            }
        }
        return null
    }

    /**
     * Creates an aesthetic, realistic procedural book cover with book spine shading,
     * gradient background, embossed title, author name, and format ribbon.
     */
    fun createProceduralCover(book: Audiobook): Bitmap {
        val bitmap = Bitmap.createBitmap(THUMB_WIDTH, THUMB_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Generate theme colors deterministically from title
        val hash = book.title.hashCode()
        val palette = getCoverPalette(hash)

        // 1. Background gradient
        val gradient = LinearGradient(
            0f, 0f, THUMB_WIDTH.toFloat(), THUMB_HEIGHT.toFloat(),
            palette.first, palette.second, Shader.TileMode.CLAMP
        )
        val bgPaint = Paint().apply {
            shader = gradient
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, THUMB_WIDTH.toFloat(), THUMB_HEIGHT.toFloat(), bgPaint)

        // 2. Spine shadow on left edge for realistic 3D book feel
        val spinePaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 40f, 0f,
                Color.argb(120, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, 40f, THUMB_HEIGHT.toFloat(), spinePaint)

        // 3. Inner decorative border frame
        val framePaint = Paint().apply {
            color = Color.argb(80, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        val frameRect = RectF(30f, 30f, THUMB_WIDTH - 30f, THUMB_HEIGHT - 30f)
        canvas.drawRoundRect(frameRect, 16f, 16f, framePaint)

        // 4. Central decorative emblem
        val emblemBgPaint = Paint().apply {
            color = Color.argb(40, 255, 255, 255)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(THUMB_WIDTH / 2f, THUMB_HEIGHT * 0.38f, 75f, emblemBgPaint)

        val emblemBorder = Paint().apply {
            color = Color.argb(120, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            isAntiAlias = true
        }
        canvas.drawCircle(THUMB_WIDTH / 2f, THUMB_HEIGHT * 0.38f, 75f, emblemBorder)

        // 5. Initial Letter in Emblem
        val initialLetter = book.title.trim().take(1).uppercase()
        val letterPaint = Paint().apply {
            color = Color.WHITE
            textSize = 64f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            setShadowLayer(6f, 2f, 4f, Color.argb(150, 0, 0, 0))
        }
        canvas.drawText(initialLetter, THUMB_WIDTH / 2f, THUMB_HEIGHT * 0.38f + 22f, letterPaint)

        // 6. Book Title (multi-line wrapping)
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            setShadowLayer(4f, 1f, 2f, Color.argb(180, 0, 0, 0))
        }

        val lines = wrapText(book.title, titlePaint, THUMB_WIDTH - 90f)
        var startY = THUMB_HEIGHT * 0.62f
        for (line in lines.take(3)) {
            canvas.drawText(line, THUMB_WIDTH / 2f, startY, titlePaint)
            startY += 34f
        }

        // 7. Author Name
        val authorPaint = Paint().apply {
            color = Color.argb(220, 255, 255, 255)
            textSize = 20f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val authorText = if (book.author.isNotEmpty()) book.author else "Audire Library"
        canvas.drawText(authorText.take(28), THUMB_WIDTH / 2f, THUMB_HEIGHT - 65f, authorPaint)

        // 8. Bottom format badge pill
        val formatText = when {
            book.filePath.endsWith(".pdf", true) || book.author.contains("PDF", true) -> "PDF BOOK"
            book.filePath.endsWith(".epub", true) || book.author.contains("EPUB", true) -> "EPUB"
            book.filePath.endsWith(".cbz", true) || book.filePath.endsWith(".cbr", true) -> "COMIC"
            else -> "AUDIOBOOK"
        }
        val badgeBg = Paint().apply {
            color = Color.argb(100, 0, 0, 0)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val badgeRect = RectF(THUMB_WIDTH / 2f - 65f, THUMB_HEIGHT - 45f, THUMB_WIDTH / 2f + 65f, THUMB_HEIGHT - 18f)
        canvas.drawRoundRect(badgeRect, 12f, 12f, badgeBg)

        val badgeTextPaint = Paint().apply {
            color = Color.argb(230, 255, 255, 255)
            textSize = 14f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText(formatText, THUMB_WIDTH / 2f, THUMB_HEIGHT - 27f, badgeTextPaint)

        return bitmap
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return if (lines.isEmpty()) listOf(text) else lines
    }

    private fun getCoverPalette(hash: Int): Pair<Int, Int> {
        val palettes = listOf(
            Pair(Color.rgb(26, 42, 108), Color.rgb(178, 31, 31)),      // Deep Royal Blue to Crimson
            Pair(Color.rgb(15, 32, 39), Color.rgb(44, 83, 100)),       // Midnight Blue
            Pair(Color.rgb(44, 62, 80), Color.rgb(76, 161, 175)),      // Slate Teal
            Pair(Color.rgb(74, 0, 224), Color.rgb(142, 45, 226)),      // Royal Violet
            Pair(Color.rgb(20, 30, 48), Color.rgb(36, 59, 85)),        // Dark Steel
            Pair(Color.rgb(219, 57, 99), Color.rgb(255, 154, 68)),     // Warm Sunset
            Pair(Color.rgb(19, 78, 94), Color.rgb(113, 178, 128)),     // Emerald Forest
            Pair(Color.rgb(96, 108, 56), Color.rgb(40, 54, 24)),       // Sage Moss
            Pair(Color.rgb(106, 17, 203), Color.rgb(37, 117, 252)),    // Twilight Indigo
            Pair(Color.rgb(186, 73, 73), Color.rgb(139, 44, 44))       // Classic Burgundy
        )
        val index = Math.abs(hash) % palettes.size
        return palettes[index]
    }
}
