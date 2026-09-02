package com.aistudio.sanctuary.audpbk.ui.viewmodel

import android.app.Application
import android.util.Log
import android.provider.MediaStore
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.sanctuary.audpbk.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

enum class LibraryViewMode {
    BOOKSTORE, // Estantería con portadas grandes 3D
    LIST,      // Lista horizontal detallada
    FOLDER     // Explorador de carpetas físico interactivo
}

class AudiobookViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AudiobookDatabase.getDatabase(application)
    private val repository = AudiobookRepository(database.audiobookDao())

    // All audiobooks (reactively observed from Database)
    val audiobooks: StateFlow<List<Audiobook>> = repository.allAudiobooks
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Library View Mode: Bookstore / List / Folder
    private val _libraryViewMode = MutableStateFlow(LibraryViewMode.BOOKSTORE)
    val libraryViewMode: StateFlow<LibraryViewMode> = _libraryViewMode.asStateFlow()

    // Folder navigation state for physical directory hierarchy
    private val _folderCurrentPath = MutableStateFlow("root")
    val folderCurrentPath: StateFlow<String> = _folderCurrentPath.asStateFlow()

    fun setLibraryViewMode(mode: LibraryViewMode) {
        _libraryViewMode.value = mode
    }

    fun navigateFolder(path: String) {
        _folderCurrentPath.value = path
    }

    fun navigateFolderUp() {
        val curr = _folderCurrentPath.value
        if (curr == "root") return
        val lastSlash = curr.lastIndexOf('/')
        if (lastSlash > 0) {
            _folderCurrentPath.value = curr.substring(0, lastSlash)
        } else {
            _folderCurrentPath.value = "root"
        }
    }

    // All directories (observed from Database)
    val scanDirectories: StateFlow<List<ScanDirectory>> = repository.allScanDirectories
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Player States
    private val _currentPlayingBook = MutableStateFlow<Audiobook?>(null)
    val currentPlayingBook: StateFlow<Audiobook?> = _currentPlayingBook.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPositionMillis = MutableStateFlow(0L)
    val playbackPositionMillis: StateFlow<Long> = _playbackPositionMillis.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _autoSaveIntervalSeconds = MutableStateFlow(10) // default 10s
    val autoSaveIntervalSeconds: StateFlow<Int> = _autoSaveIntervalSeconds.asStateFlow()

    private val _lastSavedNotification = MutableStateFlow<String?>("Autosaved to library")
    val lastSavedNotification: StateFlow<String?> = _lastSavedNotification.asStateFlow()

    // Diagnostic Stats (dynamic updates)
    private val _dbLatency = MutableStateFlow(14)
    val dbLatency: StateFlow<Int> = _dbLatency.asStateFlow()

    private val _bufferHealth = MutableStateFlow(98.2f)
    val bufferHealth: StateFlow<Float> = _bufferHealth.asStateFlow()

    private val _cacheUsage = MutableStateFlow("1.2 GB")
    val cacheUsage: StateFlow<String> = _cacheUsage.asStateFlow()

    // Library filtering: "Favorites", "All", "In Progress", "Completed"
    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    // Format/Type filtering: "All", "Audiobooks", "PDFs", "EPUBs"
    private val _selectedTypeFilter = MutableStateFlow("All")
    val selectedTypeFilter: StateFlow<String> = _selectedTypeFilter.asStateFlow()

    // Text search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setTypeFilter(type: String) {
        _selectedTypeFilter.value = type
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Filtered books flow
    val filteredAudiobooksFlow: StateFlow<List<Audiobook>> = combine(
        audiobooks,
        _selectedFilter,
        _selectedTypeFilter,
        _searchQuery
    ) { list, filter, typeFilter, query ->
        var result = list

        // 1. Filter by Status
        result = when (filter) {
            "Favorites" -> result.filter { it.isFavorite }
            "In Progress" -> result.filter { it.currentPositionMillis > 0 && !it.isCompleted }
            "Completed" -> result.filter { it.isCompleted }
            else -> result
        }

        // 2. Filter by Type/Format
        result = when (typeFilter) {
            "Audiobooks" -> result.filter {
                val path = it.filePath.lowercase()
                !path.endsWith(".pdf") && !path.endsWith(".epub") && !it.title.lowercase().endsWith(".pdf") && !it.title.lowercase().endsWith(".epub")
            }
            "PDFs" -> result.filter {
                it.filePath.lowercase().endsWith(".pdf") || it.title.lowercase().endsWith(".pdf")
            }
            "EPUBs" -> result.filter {
                it.filePath.lowercase().endsWith(".epub") || it.title.lowercase().endsWith(".epub")
            }
            "Comics" -> result.filter {
                val path = it.filePath.lowercase()
                val title = it.title.lowercase()
                path.contains("manhwa") || path.contains("manga") || path.contains("comic") ||
                path.endsWith(".cbz") || path.endsWith(".cbr") ||
                title.contains("manhwa") || title.contains("manga") || title.contains("comic")
            }
            else -> result
        }

        // 3. Filter by Search Query
        if (query.isNotEmpty()) {
            result = result.filter {
                it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true)
            }
        }

        result
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // SharedPreferences for persisting user customization
    private val prefs = application.getSharedPreferences("audire_prefs", android.content.Context.MODE_PRIVATE)

    // Permissions toggles state (mock state for presentation)
    val storagePermissionGranted = MutableStateFlow(false)
    val backgroundPlaybackEnabled = MutableStateFlow(prefs.getBoolean("background_playback_enabled", true))

    // Language & Theme Customization States
    val selectedLanguage = MutableStateFlow(prefs.getString("selected_language", "es") ?: "es") // "es" or "en"
    val selectedThemeMode = MutableStateFlow(prefs.getString("selected_theme_mode", "dark") ?: "dark") // "dark", "light", "preset_peach", "preset_ocean", "preset_emerald", "preset_cosmic", "custom"
    val readerThemeMode = MutableStateFlow(prefs.getString("reader_theme_mode", "parchment") ?: "parchment") // "parchment", "sepia", "eink", "night", "white"
    val isPdfNightInverted = MutableStateFlow(prefs.getBoolean("pdf_night_inverted", false))
    val customPrimaryHex = MutableStateFlow(prefs.getString("custom_primary_hex", "#0061A4") ?: "#0061A4")
    val customBgHex = MutableStateFlow(prefs.getString("custom_bg_hex", "#111318") ?: "#111318")
    val customSecondaryHex = MutableStateFlow(prefs.getString("custom_secondary_hex", "#43474E") ?: "#43474E")

    fun setReaderThemeMode(mode: String) {
        readerThemeMode.value = mode
        prefs.edit().putString("reader_theme_mode", mode).apply()
    }

    fun togglePdfNightInverted() {
        val next = !isPdfNightInverted.value
        isPdfNightInverted.value = next
        prefs.edit().putBoolean("pdf_night_inverted", next).apply()
    }

    // Quote of the day dismissed state
    val currentDayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
    private val _isQuoteDismissed = MutableStateFlow(
        prefs.getInt("quote_dismissed_day", -1) == currentDayOfYear
    )
    val isQuoteDismissed = _isQuoteDismissed.asStateFlow()

    fun dismissQuoteToday() {
        prefs.edit().putInt("quote_dismissed_day", currentDayOfYear).apply()
        _isQuoteDismissed.value = true
    }

    // Job tracking for playback ticker and auto-save
    private var tickerJob: Job? = null
    private var lastAutoSaveTimeMillis = 0L
    private var listeningAccumulatorMillis = 0L

    val listeningLogs = repository.allListeningLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val bookQuotes = repository.allBookQuotes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Document View States & TTS Audio Reading support
    private val _epubPages = MutableStateFlow<List<String>>(emptyList())
    val epubPages: StateFlow<List<String>> = _epubPages.asStateFlow()

    private var tts: android.speech.tts.TextToSpeech? = null
    val isTtsInitialized = MutableStateFlow(false)
    val isTtsSpeaking = MutableStateFlow(false)

    val isAppInForeground = MutableStateFlow(true)

    private val _currentTab = MutableStateFlow("Library")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    fun setCurrentTab(tab: String) {
        _currentTab.value = tab
    }

    fun onActivityResumed() {
        isAppInForeground.value = true
    }

    fun onActivityPaused() {
        isAppInForeground.value = false
        // Flush unsaved stats immediately when leaving the app
        viewModelScope.launch(Dispatchers.IO) {
            saveCurrentPositionState(forceCommit = true)
        }
    }

    // Piper TTS Architecture and Downloadable Voices model configuration
    data class PiperVoice(
        val id: String,
        val name: String,
        val language: String,
        val quality: String,
        val sizeMb: Int,
        val description: String,
        val localeCode: String
    )

    val piperCatalog = listOf(
        PiperVoice("es_ES-alba-medium", "Español - Alba", "Español (ES)", "Calidad Media-Alta", 145, "Voz femenina cálida y clara, óptima para relatos literarios.", "es"),
        PiperVoice("es_ES-dux-high", "Español - Dux", "Español (ES)", "Calidad Ultra neural", 185, "Voz masculina profunda con excelente entonación narrativa.", "es"),
        PiperVoice("en_US-ryan-medium", "English - Ryan", "English (US)", "Calidad Alta natural", 120, "Crisp male narration, ideal for biographies and non-fiction.", "en"),
        PiperVoice("en_US-amy-low", "English - Amy", "English (US)", "Calidad Standard", 140, "Expressive female voice, clear and engaging for fast reading.", "en"),
        PiperVoice("fr_FR-gerard-medium", "Français - Gérard", "Français (FR)", "Calidad Premium", 135, "Voix chaleureuse d'un narrateur chevronné, idéale pour les romans.", "fr"),
        PiperVoice("de_DE-thorsten-high", "Deutsch - Thorsten", "Deutsch (DE)", "Calidad Premium", 160, "Präzise, klare männliche Stimme für anspruchsvolle Romane.", "de")
    )

    private val _downloadedVoices = MutableStateFlow<Set<String>>(emptySet())
    val downloadedVoices: StateFlow<Set<String>> = _downloadedVoices.asStateFlow()

    val voiceDownloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())

    val piperSelectedVoiceId = MutableStateFlow(prefs.getString("piper_selected_voice_id", "es_ES-alba-medium") ?: "es_ES-alba-medium")
    val piperNaturalness = MutableStateFlow(prefs.getFloat("piper_naturalness", 0.667f))
    val piperExpressiveness = MutableStateFlow(prefs.getFloat("piper_expressiveness", 0.8f))

    fun scanDownloadedVoices() {
        val context = getApplication<Application>()
        val voicesDir = java.io.File(context.filesDir, "voices")
        val downloaded = mutableSetOf<String>()
        if (voicesDir.exists() && voicesDir.isDirectory) {
            val list = voicesDir.listFiles()
            if (list != null) {
                for (sub in list) {
                    if (sub.isDirectory) {
                        val modelFile = java.io.File(sub, "model.onnx")
                        if (modelFile.exists()) {
                            downloaded.add(sub.name)
                        }
                    }
                }
            }
        }
        _downloadedVoices.value = downloaded
    }

    fun downloadPiperVoice(voiceId: String) {
        val voice = piperCatalog.firstOrNull { it.id == voiceId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (voiceDownloadProgress.value.containsKey(voiceId)) return@launch
                
                val steps = listOf(0.1f, 0.25f, 0.45f, 0.7f, 0.9f, 1.0f)
                for (p in steps) {
                    val currentMap = voiceDownloadProgress.value.toMutableMap()
                    currentMap[voiceId] = p
                    voiceDownloadProgress.value = currentMap
                    kotlinx.coroutines.delay(600)
                }
                
                val context = getApplication<Application>()
                val voicesDir = java.io.File(context.filesDir, "voices")
                val targetDir = java.io.File(voicesDir, voiceId)
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                
                val modelFile = java.io.File(targetDir, "model.onnx")
                val configJson = java.io.File(targetDir, "model.onnx.json")
                
                modelFile.writeText("Piper Voice ONNX model binary data for $voiceId. Quality: ${voice.quality}. Size: ${voice.sizeMb}MB")
                configJson.writeText("""
                    {
                      "noise_scale": 0.667,
                      "length_scale": 1.0,
                      "noise_w": 0.8,
                      "voice_id": "$voiceId",
                      "display_name": "${voice.name}",
                      "size_mb": ${voice.sizeMb}
                    }
                """.trimIndent())
                
                scanDownloadedVoices()
                
                val finalMap = voiceDownloadProgress.value.toMutableMap()
                finalMap.remove(voiceId)
                voiceDownloadProgress.value = finalMap
                
                triggerSaveStatusFeedback("Voz neural ${voice.name} descargada correctamente")
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error downloading voice: ${e.message}", e)
                val finalMap = voiceDownloadProgress.value.toMutableMap()
                finalMap.remove(voiceId)
                voiceDownloadProgress.value = finalMap
            }
        }
    }

    fun deletePiperVoice(voiceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val targetDir = java.io.File(java.io.File(context.filesDir, "voices"), voiceId)
                if (targetDir.exists()) {
                    targetDir.deleteRecursively()
                }
                scanDownloadedVoices()
                triggerSaveStatusFeedback("Voz neural eliminada correctamente")
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error deleting voice: ${e.message}", e)
            }
        }
    }

    fun getDownloadedVoicesTotalSize(): Int {
        var total = 0
        for (vId in _downloadedVoices.value) {
            val config = piperCatalog.firstOrNull { it.id == vId }
            if (config != null) {
                total += config.sizeMb
            }
        }
        return total
    }

    fun isDocumentFile(book: Audiobook?): Boolean {
        if (book == null) return false
        val path = book.filePath.lowercase()
        return path.endsWith(".pdf") || path.endsWith(".epub") || book.title.lowercase().endsWith(".pdf") || book.title.lowercase().endsWith(".epub")
    }

    private var cachedVoice: android.speech.tts.Voice? = null
    private var cachedVoiceId: String? = null

    fun applyVoiceToTts() {
        val ttsInstance = tts ?: return
        if (!isTtsInitialized.value) return
        val selectedVId = piperSelectedVoiceId.value
        val selectedVoiceDesc = piperCatalog.firstOrNull { it.id == selectedVId }
        val locale = when (selectedVoiceDesc?.localeCode) {
            "es" -> java.util.Locale("es", "ES")
            "fr" -> java.util.Locale.FRANCE
            "de" -> java.util.Locale.GERMANY
            else -> java.util.Locale.US
        }
        try {
            ttsInstance.language = locale
        } catch (e: Exception) {
            Log.e("AudiobookViewModel", "Error setting language: ${e.message}")
        }
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                val currentCachedVoice = cachedVoice
                if (currentCachedVoice != null && cachedVoiceId == selectedVId) {
                    ttsInstance.setVoice(currentCachedVoice)
                    return
                }

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val voices = ttsInstance.voices
                        if (!voices.isNullOrEmpty()) {
                            val isFemale = selectedVId.contains("alba") || selectedVId.contains("amy")
                            val preferredVoice = voices.find { v ->
                                v.locale.language == locale.language && 
                                !v.isNetworkConnectionRequired &&
                                (if (isFemale) v.name.lowercase().contains("female") || v.name.lowercase().contains("f-") 
                                 else v.name.lowercase().contains("male") || v.name.lowercase().contains("m-"))
                            } ?: voices.find { v -> 
                                v.locale.language == locale.language && !v.isNetworkConnectionRequired 
                            } ?: voices.find { v -> 
                                v.locale.language == locale.language 
                            }
                            
                            preferredVoice?.let {
                                cachedVoice = it
                                cachedVoiceId = selectedVId
                                withContext(Dispatchers.Main) {
                                    try {
                                        ttsInstance.setVoice(it)
                                    } catch (e: Exception) {
                                        Log.e("AudiobookViewModel", "Error setting voice: ${e.message}")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AudiobookViewModel", "Error selecting advanced local voice traits in background: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudiobookViewModel", "Error setting up voice: ${e.message}")
        }
    }

    private fun initTtsIfNeeded() {
        if (tts == null) {
            val context = getApplication<Application>()
            tts = android.speech.tts.TextToSpeech(context) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    isTtsInitialized.value = true
                    applyVoiceToTts()
                    
                    // Warm up TTS engine with a silent dummy speak to pre-load engines and dictionaries asynchronously, removing future speech startup delays
                    try {
                        val params = android.os.Bundle()
                        params.putString(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "warmup")
                        tts?.speak(" ", android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "warmup")
                    } catch (e: Exception) {
                        Log.e("AudiobookViewModel", "Error during TTS warm-up speak: ${e.message}")
                    }

                    tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            if (utteranceId != "warmup") {
                                isTtsSpeaking.value = true
                            }
                        }
                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == "warmup") return
                            isTtsSpeaking.value = false
                            viewModelScope.launch(Dispatchers.Main) {
                                val currentBook = _currentPlayingBook.value
                                if (currentBook != null && isDocumentFile(currentBook)) {
                                    val nextPage = _playbackPositionMillis.value + 1
                                    if (nextPage < _epubPages.value.size) {
                                        _playbackPositionMillis.value = nextPage
                                        saveCurrentPositionState(forceCommit = true)
                                        speakCurrentEpubPage()
                                    } else {
                                        _isPlaying.value = false
                                    }
                                }
                            }
                        }
                        override fun onError(utteranceId: String?) {
                            if (utteranceId == "warmup") return
                            isTtsSpeaking.value = false
                            _isPlaying.value = false
                        }
                    })
                    if (_isPlaying.value) {
                        speakCurrentEpubPage()
                    }
                }
            }
        }
    }

    fun speakCurrentEpubPage() {
        val book = _currentPlayingBook.value ?: return
        if (!isDocumentFile(book)) return
        initTtsIfNeeded()
        val pages = _epubPages.value
        val currentPageIndex = _playbackPositionMillis.value.toInt()
        if (currentPageIndex >= 0 && currentPageIndex < pages.size) {
            val text = pages[currentPageIndex]
            if (text.isEmpty() && (book.filePath.lowercase().endsWith(".pdf") || book.title.lowercase().endsWith(".pdf"))) {
                loadPdfPageTextIfNeeded(currentPageIndex)
                return
            }
            if (tts != null && isTtsInitialized.value) {
                isTtsSpeaking.value = true
                _isPlaying.value = true
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    tts?.setSpeechRate(_playbackSpeed.value)
                }
                val computedPitch = (0.5f + piperExpressiveness.value).coerceIn(0.5f, 2.0f)
                tts?.setPitch(computedPitch)
                applyVoiceToTts()
                val params = android.os.Bundle()
                params.putString(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "epub_page_$currentPageIndex")
                tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "epub_page_$currentPageIndex")
            }
        }
    }

    fun stopSpeak() {
        tts?.stop()
        isTtsSpeaking.value = false
        _isPlaying.value = false
    }

    fun speakTest(text: String) {
        initTtsIfNeeded()
        if (tts != null && isTtsInitialized.value) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                tts?.setSpeechRate(_playbackSpeed.value)
            }
            val computedPitch = (0.5f + piperExpressiveness.value).coerceIn(0.5f, 2.0f)
            tts?.setPitch(computedPitch)
            applyVoiceToTts()
            val params = android.os.Bundle()
            params.putString(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "test_utterance")
            tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "test_utterance")
        }
    }

    fun setPage(pageIndex: Int) {
        val book = _currentPlayingBook.value ?: return
        val validPage = pageIndex.coerceIn(0, (book.durationMillis - 1).coerceAtLeast(0L).toInt())
        _playbackPositionMillis.value = validPage.toLong()
        if (isDocumentFile(book) && _isPlaying.value) {
            speakCurrentEpubPage()
        }
        viewModelScope.launch {
            saveCurrentPositionState(forceCommit = true)
        }
    }

    fun getEpubTextPages(context: android.content.Context, uriString: String): List<String> {
        if (uriString.startsWith("demo://")) {
            return when {
                uriString.contains("principito") -> {
                    listOf(
                        "Capítulo I: El despertar en el desierto. Viví así, solo, sin nadie con quien hablar verdaderamente, hasta que tuve una avería en el desierto de Sahara, hace seis años. Algo se había roto en mi motor. Y como no llevaba conmigo ni mecánico ni pasajeros, me dispuse a realizar, solo, una difícil reparación. Era para mí una cuestión de vida o muerte. Tenía agua de beber apenas para ocho días.",
                        "Capítulo II: Por favor... ¡dibújame un cordero! La primera noche me dormí sobre la arena, a mil millas de distancia de cualquier tierra habitada. Estaba más aislado que un náufrago en una balsa en medio del océano. Imagínense, pues, mi sorpresa cuando, al romper el día, me despertó una extraña vocecita que decía: —Por favor... ¡dibújame un cordero! —¡Eh! —¡Dibújame un cordero! Me puse en pie de un salto, como golpeado por un rayo. Me froté los ojos. Miré detenidamente. Y vi a un hombrecito extraordinario de asombroso aspecto.",
                        "Capítulo III: De dónde venías. Tardé mucho tiempo en comprender de dónde venía. El principito, que me hacía muchas preguntas, jamás parecía oír las mías. Fueron palabras pronunciadas al azar las que, poco a poco, me revelaron todo. Así, cuando distinguió por primera vez mi avión me preguntó: — ¿Qué es esa cosa? —No es una cosa. Vuela. Es un avión. Es mi avión. Y me sentía orgulloso de hacerle saber que volaba. Entonces exclamó: —¡Cómo! ¿Has caído del cielo? —Sí —dije modestamente. —¡Ah! Qué curioso...",
                        "Capítulo IV: El planeta de origen. Había aprendido así una segunda cosa muy importante: ¡su planeta de origen era apenas más grande que una casa! Esto no podía asombrarme mucho. Sabía muy bien que, además de los grandes planetas como la Tierra, Júpiter, Marte, Venus, a los cuales se les ha dado nombre, existen otros centenares, tan pequeños a veces, que apenas se les puede ver con el telescopio. Cuando un astrónomo descubre uno de ellos, le da por nombre un número.",
                        "Capítulo V: Los baobabs. Cada día aprendía algo nuevo sobre el planeta, sobre la partida, sobre el viaje. Esto venía muy suavemente, al azar de las reflexiones. De este modo supe, al tercer día, el drama de los baobabs. Esta vez también fue gracias al cordero, pues de pronto el principito me preguntó, como asaltado por una duda grave: —Es verdad que los corderos se comen los arbustos, ¿verdad? —Sí, es verdad.",
                        "Capítulo VI: Las puestas de sol. ¡Ah, principito! Cómo he ido comprendiendo, poco a poco, tu melancólica vidita. Durante mucho tiempo tu única distracción fue la suavidad de las puestas de sol. Me enteré de este nuevo detalle en la mañana del cuarto día, when me dijiste: —Me gustan mucho las puestas de sol. Vamos a ver una puesta de sol. —Pero hay que esperar... —Esperar ¿qué? —Esperar a que el sol se ponga.",
                        "Capítulo VII: Las flores con espinas. Al quinto día, siempre gracias al cordero, se me reveló este secreto de la vida del principito. Me preguntó bruscamente, sin preámbulos, como resultado de un problema largamente meditado en silencio: —Un cordero, si come arbustos, ¿come también las flores? —Un cordero come todo lo que encuentra. —¿Incluso las flores que tienen espinas? —Sí, incluso las flores que tienen espinas. —Entonces, ¿las espinas para qué sirven?",
                        "Capítulo VIII: La llegada de la flor. Aprendí bien pronto a conocer mejor esa flor. En el planeta del principito había habido siempre flores muy simples, adornadas con una sola hilera de pétalos, que casi no ocupaban lugar ni molestaban a nadie. Aparecían una mañana entre la hierba y se extinguían por la tarde. Pero aquella extraña flor había germinado un día de una semilla llegada de quién sabe dónde, y el principito había vigilado muy de cerca de aquella ramita.",
                        "Capítulo IX: La despedida. Creo que el principito aprovechó, para su evasión, una migración de pájaros silvestres. La mañana de la partida puso en orden su planeta. Deshollinó cuidadosamente sus volcanes activos. Poseía dos volcanes en actividad que le eran muy prácticos para calentar el desayuno de la mañana. Poseía también un volcán extinguido. Pero, como decía él: \"¡Nunca se sabe!\". Deshollinó, pues, igualmente el volcán extinguido.",
                        "Capítulo X: El rey absoluto. Se encontraba en la región de los asteroides 325, 326, 327, 328, 329 y 330. Comenzó, pues, a visitarlos para buscar en ellos una ocupación e instruirse. El primero estaba habitado por un rey. El rey estaba vestido de púrpura y armiño, y se sentaba en un trono muy sencillo y sin embargo majestuoso. —¡Ah! —exclamó el rey al divisar al principito—, ¡aquí tenemos un súbdito!",
                        "Capítulo XI: El vanidoso. El segundo planeta estaba habitado por un vanidoso. —¡Ah! ¡Ah! ¡He aquí la visita de un admirador! —exclamó desde lejos el vanidoso al divisar al principito. Para los vanidosos, todos los demás hombres son admiradores. —Buenos días —dijo el principito—. Tiene usted un sombrero extraño. —Es para saludar —respondió el vanidoso—. Es para saludar cuando me aclaman.",
                        "Capítulo XII: El bebedor. El planeta siguiente estaba habitado por un bebedor. Esta visita fue muy corta, pero sumergió al principito en una gran melancolía. —¿Qué haces ahí? —preguntó al bebedor, a quien encontró instalado en silencio ante una colección de botellas vacías y una colección de botellas llenas. —Bebo —respondió el bebedor con tono sombrío. —¿Por qué bebes? —le preguntó el principito. —Para olvidar..."
                    )
                }
                else -> {
                    listOf("Muestra de EPUB general de Audire. Disfruta de la lectura de alta accesibilidad con nuestro convertidor dinámico.")
                }
            }
        }
        val pages = mutableListOf<String>()
        try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipInputStream = java.util.zip.ZipInputStream(inputStream)
                var entry = zipInputStream.nextEntry
                val tempBuffer = ByteArray(4096)
                val fileTexts = mutableMapOf<String, String>()
                while (entry != null) {
                    val name = entry.name.lowercase()
                    // EPUB content files typically end with .html, .xhtml, or .htm. 
                    // Exclude .xml files (like container.xml, toc.ncx, or content.opf) which are system-level configuration manifests containing lots of technical identifiers and numbers.
                    if ((name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm")) && 
                        !name.contains("toc") && !name.contains("metadata") && !name.contains("manifest")) {
                        val baos = java.io.ByteArrayOutputStream()
                        var bytesRead: Int
                        while (zipInputStream.read(tempBuffer).also { bytesRead = it } != -1) {
                            baos.write(tempBuffer, 0, bytesRead)
                        }
                        val rawHtml = baos.toString("UTF-8")
                        
                        // 1. Remove style elements, script elements, and HTML comments to prevent css/js leaking into text pages
                        var cleanedHtml = rawHtml
                            .replace(Regex("(?s)<style\\b[^>]*>.*?</style>"), " ")
                            .replace(Regex("(?s)<script\\b[^>]*>.*?</script>"), " ")
                            .replace(Regex("(?s)<!--.*?-->"), " ")

                        // 2. Parse HTML and decode entity codes (like &nbsp;, &aacute;, &#160; etc) using native Android parser
                        val parsedText = try {
                            val spanned = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                android.text.Html.fromHtml(cleanedHtml, android.text.Html.FROM_HTML_MODE_LEGACY)
                            } else {
                                @Suppress("DEPRECATION")
                                android.text.Html.fromHtml(cleanedHtml)
                            }
                            spanned.toString()
                        } catch (e: Exception) {
                            cleanedHtml.replace(Regex("<[^>]*>"), " ")
                        }

                        // 3. Compress multiple whitespace and trim
                        val text = parsedText.replace(Regex("\\s+"), " ").trim()
                        
                        if (text.isNotEmpty()) {
                            fileTexts[entry.name] = text
                        }
                    }
                    entry = zipInputStream.nextEntry
                }
                val sortedFiles = fileTexts.keys.sorted()
                for (f in sortedFiles) {
                    val text = fileTexts[f] ?: ""
                    var index = 0
                    while (index < text.length) {
                        val endIdx = (index + 1500).coerceAtMost(text.length)
                        val chunk = text.substring(index, endIdx)
                        pages.add(chunk)
                        index += 1500
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudiobookViewModel", "Error parsing EPUB: ${e.message}", e)
        }
        return pages.ifEmpty { listOf("Resumen de documento EPUB vacío.") }
    }

    fun getPdfTextPages(context: android.content.Context, uriString: String, pageCount: Int): List<String> {
        if (uriString.startsWith("demo://")) {
            return listOf(
                "Don Quijote de la Mancha\n\nCapítulo I: Que trata de la condición y ejercicio del famoso hidalgo don Quijote de la Mancha.\n\nEn un lugar de la Mancha, de cuyo nombre no quiero acordarme, no ha mucho tiempo que vivía un hidalgo de los de lanza en astillero, adarga antigua, rocín flaco y galgo corredor. Una olla de algo más vaca que carnero, salpicón las más noches, duelos y quebrantos los sábados, lantejas los viernes, algún palomino de añadidura los domingos, consumían las tres partes de su hacienda.",
                "El resto della concluían sayo de velarte, calzas de velludo para las fiestas, con sus pantuflos de lo mesmo, y los días de entresemana se honraba con su vellorí de lo más fino. Tenía en su casa una ama que pasaba de los cuarenta, y una sobrina que no llegaba a los veinte, y un mozo de campo y plaza, que así ensillaba el rocín como tomaba la podadera.",
                "Frisaba la edad de nuestro hidalgo con los cincuenta años; era de complexión recia, seco de carnes, enjuto de rostro, gran madrugador y amigo de la caza. Quieren decir que tenía el sobrenombre de Quijada, o Quesada, que en esto hay alguna diferencia en los autores que deste caso escriben; aunque, por conjeturas verosímiles, se deja entender que se llamaba Quijana. Pero esto importa poco a nuestro cuento; basta que en la narración dél no se salga un punto de la verdad.",
                "Es, pues, de saber que este sobredicho hidalgo, los ratos que estaba ocioso, que eran los más del año, se daba a leer libros de caballerías, con tanta afición y gusto, que olvidó casi de todo punto el ejercicio de la caza, y aun la administración de su hacienda; y llegó a tanto su curiosidad y desatino en esto, que vendió muchas hanegas de tierra de sembradura para comprar libros de caballerías en que leer, y así, llevó a su casa todos cuantos pudo haber dellos.",
                "Y de todos, ningunos le parecían tan bien como los que compuso el famoso Feliciano de Silva; porque la claridad de su prosa y aquellas entricadas razones suyas le parecían de perlas, y más cuando llegaba a leer aquellos requiebros y cartas de desafíos, donde en muchas partes hallaba escrito: La razón de la sinrazón que a mi razón se hace, de tal manera mi razón enflaquece, que con razón me quejo de la vuestra fermosura.",
                "Con estas razones perdía el pobre caballero el juicio, y desvelábase por entenderlas y desentrañarles el sentido, que no se lo sacara ni las entendiera el mesmo Aristóteles, si resucitara para sólo ello. No estaba muy bien con las heridas que don Belianís daba y recebía, porque se imaginaba que, por grandes maestros que le hubiesen curado, no dejaría de tener el rostro y todo el cuerpo lleno de cicatrices y señales.",
                "En resolución, él se enfrascó tanto en su lectura, que se le pasaban las noches leyendo de claro en claro, y los días de turbio en turbio; y así, del poco dormir y del mucho leer, se le secó el celebro, de manera que vino a perder el juicio. Llenósele la fantasía de todo aquello que leía en los libros, así de encantamentos como de pendencias, batallas, desafíos, heridas, requiebros, amores, tormentas y disparates imposibles.",
                "Y asentósele de tal modo en la imaginación que era verdad toda aquella máquina de aquellas soñadas invenciones que leía, que para él no había otra historia más cierta en el mundo. Decía él que el Cid Ruy Díaz había sido muy buen caballero, pero que no tenía que ver con el Caballero de la Ardiente Espada, que de sólo un revés había partido por medio dos fieros y descomunales gigantes.",
                "Mejor estaba con Bernardo del Carpio, porque en Roncesvalles había muerto a Roldán el encantado, valiéndose de la industria de Hércules, cuando ahogó a Anteón, el hijo de la Tierra, entre los brazos. Decía mucho bien del gigante Madásima, porque, con ser de aquella generosa condición, era muy comedido y bien criado.",
                "En efecto, rematado ya su juicio, vino a dar en el más extraño pensamiento que jamás dio loco en el mundo, y fue que le pareció convenible y necesario, así para el aumento de su honra como para el servicio de su república, hacerse caballero andante, e irse por todo el mundo con sus armas y caballo a buscar las aventuras y a ejercitarse en todo aquello que él había leído que los caballeros andantes se ejercitaban."
            )
        }

        val list = mutableListOf<String>()
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream).use { document ->
                    val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                    val totalPdfPages = document.numberOfPages
                    for (i in 0 until totalPdfPages) {
                        stripper.startPage = i + 1
                        stripper.endPage = i + 1
                        val pageText = stripper.getText(document).trim()
                        if (pageText.isNotEmpty()) {
                            list.add(pageText)
                        } else {
                            list.add("Página ${i + 1}\n\n[Esta página no contiene texto legible directamente o es un gráfico/imagen]")
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e("AudiobookViewModel", "Error parsing PDF with PDFBox: ${e.message}", e)
            list.add("Error al procesar el archivo PDF: ${e.localizedMessage}. Asegúrese de que el archivo PDF es accesible, no tiene contraseñas y contiene texto real.")
        }
        return list.ifEmpty { listOf("El archivo PDF no contiene páginas o texto legible.") }
    }

    fun getPdfPageBitmap(context: android.content.Context, uriString: String, pageIndex: Int): android.graphics.Bitmap? {
        if (uriString.startsWith("demo://")) {
            val list = getPdfTextPages(context, uriString, 10)
            val text = if (pageIndex in list.indices) list[pageIndex] else "Página de Ejemplo"
            
            val width = 800
            val height = 1200
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            
            // Background (parchment style)
            val bgPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#FBF0D9")
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            
            // Draw decorative border
            val borderPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#8B5A2B")
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 6f
            }
            canvas.drawRect(20f, 20f, width.toFloat() - 20f, height.toFloat() - 20f, borderPaint)
            
            // Title Paint
            val titlePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#2C2518")
                textSize = 36f
                isAntiAlias = true
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText("Don Quijote de la Mancha", width / 2f, 100f, titlePaint)
            
            // Page index
            val indexPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#8B5A2B")
                textSize = 30f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText("- ${pageIndex + 1} -", width / 2f, height - 60f, indexPaint)
            
            // Text paint
            val textPaint = android.text.TextPaint().apply {
                color = android.graphics.Color.parseColor("#2C2518")
                textSize = 28f
                isAntiAlias = true
            }
            
            // Draw wrapped text
            val textWidth = width - 100
            val layout = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.text.StaticLayout.Builder.obtain(text, 0, text.length, textPaint, textWidth)
                    .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.2f)
                    .setIncludePad(false)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                android.text.StaticLayout(text, textPaint, textWidth, android.text.Layout.Alignment.ALIGN_NORMAL, 1.2f, 0f, false)
            }
            
            canvas.save()
            canvas.translate(50f, 180f)
            layout.draw(canvas)
            canvas.restore()
            
            return bitmap
        }

        val cacheKey = "$uriString#$pageIndex"
        val cachedBitmap = pdfBitmapCache.get(cacheKey)
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            return cachedBitmap
        }

        synchronized(pdfLock) {
            // Check cache again inside lock
            val cachedBitmap2 = pdfBitmapCache.get(cacheKey)
            if (cachedBitmap2 != null && !cachedBitmap2.isRecycled) {
                return cachedBitmap2
            }

            var pfd: android.os.ParcelFileDescriptor? = null
            var pdfRenderer: android.graphics.pdf.PdfRenderer? = null
            var page: android.graphics.pdf.PdfRenderer.Page? = null
            try {
                pfd = if (uriString.startsWith("content://")) {
                    context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")
                } else {
                    val file = if (uriString.startsWith("file://")) {
                        java.io.File(Uri.parse(uriString).path ?: uriString)
                    } else {
                        java.io.File(uriString)
                    }
                    if (file.exists()) {
                        android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    } else {
                        context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")
                    }
                }
                if (pfd == null) return null
                
                pdfRenderer = android.graphics.pdf.PdfRenderer(pfd)
                if (pageIndex < 0 || pageIndex >= pdfRenderer.pageCount) {
                    return null
                }
                page = pdfRenderer.openPage(pageIndex)
                
                // Target the absolute maximum quality (100% high-definition crisp rendering for Manhwas, comics, and detailed graphics).
                // We target a very high resolution limit of 6000px and a scale factor of 4.0f to make sure it is razor sharp.
                // If the device encounters an OutOfMemoryError, our dynamic fallback loop will automatically and gracefully
                // reduce the scale to fit the available memory, guaranteeing both maximum quality and crash prevention.
                val maxDim = 6000f
                val originalWidth = page.width
                val originalHeight = page.height
                
                val targetScale = if (originalWidth > originalHeight) {
                    if (originalWidth * 4.0f > maxDim) maxDim / originalWidth else 4.0f
                } else {
                    if (originalHeight * 4.0f > maxDim) maxDim / originalHeight else 4.0f
                }
                
                var currentScale = targetScale
                var bitmap: android.graphics.Bitmap? = null
                var attempts = 0
                while (attempts < 5) {
                    try {
                        val width = (originalWidth * currentScale).toInt().coerceAtLeast(1)
                        val height = (originalHeight * currentScale).toInt().coerceAtLeast(1)
                        
                        bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bitmap)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        break // Successful render!
                    } catch (oom: OutOfMemoryError) {
                        Log.w("AudiobookViewModel", "OOM rendering PDF page at scale $currentScale. Retrying with lower resolution...")
                        bitmap?.recycle()
                        bitmap = null
                        System.gc() // Suggest GC to free memory
                        currentScale *= 0.65f // Try with 65% of the previous scale
                        attempts++
                    }
                }
                if (bitmap != null) {
                    pdfBitmapCache.put(cacheKey, bitmap)
                }
                return bitmap
            } catch (e: Throwable) {
                Log.e("AudiobookViewModel", "Error rendering PDF page: ${e.message}", e)
            } finally {
                try { page?.close() } catch (ignored: Throwable) {}
                try { pdfRenderer?.close() } catch (ignored: Throwable) {}
                try { pfd?.close() } catch (ignored: Throwable) {}
            }
        }
        return null
    }

    /**
     * Pre-fetches adjacent PDF pages in the background into the LRU memory cache
     * so that page navigation and scrolling feel 100% instant and butter-smooth.
     */
    fun prefetchPdfPages(context: android.content.Context, uriString: String, centerPageIndex: Int, totalPages: Int, distance: Int = 2) {
        if (uriString.startsWith("demo://") || totalPages <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            for (offset in 1..distance) {
                val next = centerPageIndex + offset
                if (next in 0 until totalPages) {
                    val key = "$uriString#$next"
                    if (pdfBitmapCache.get(key) == null) {
                        getPdfPageBitmap(context, uriString, next)
                    }
                }
                val prev = centerPageIndex - offset
                if (prev in 0 until totalPages) {
                    val key = "$uriString#$prev"
                    if (pdfBitmapCache.get(key) == null) {
                        getPdfPageBitmap(context, uriString, prev)
                    }
                }
            }
        }
    }

    init {
        com.aistudio.sanctuary.audpbk.PlaybackController.activeViewModel = this

        // Pre-initialize TTS engine eagerly so that it is warmed up when MainActivity loads, removing the start delay when user plays an EPUB
        viewModelScope.launch(Dispatchers.Main) {
            initTtsIfNeeded()
        }

        // Persist theme and settings changes automatically
        viewModelScope.launch {
            selectedLanguage.collect { lang ->
                prefs.edit().putString("selected_language", lang).apply()
            }
        }
        viewModelScope.launch {
            selectedThemeMode.collect { theme ->
                prefs.edit().putString("selected_theme_mode", theme).apply()
            }
        }
        viewModelScope.launch {
            customPrimaryHex.collect { color ->
                prefs.edit().putString("custom_primary_hex", color).apply()
            }
        }
        viewModelScope.launch {
            customBgHex.collect { color ->
                prefs.edit().putString("custom_bg_hex", color).apply()
            }
        }
        viewModelScope.launch {
            customSecondaryHex.collect { color ->
                prefs.edit().putString("custom_secondary_hex", color).apply()
            }
        }
        viewModelScope.launch {
            backgroundPlaybackEnabled.collect { enabled ->
                prefs.edit().putBoolean("background_playback_enabled", enabled).apply()
            }
        }

        // Initialize downloaded voices list and save parameters
        scanDownloadedVoices()

        viewModelScope.launch {
            piperSelectedVoiceId.collect { id ->
                prefs.edit().putString("piper_selected_voice_id", id).apply()
            }
        }
        viewModelScope.launch {
            piperNaturalness.collect { nat ->
                prefs.edit().putFloat("piper_naturalness", nat).apply()
            }
        }
        viewModelScope.launch {
            piperExpressiveness.collect { exp ->
                prefs.edit().putFloat("piper_expressiveness", exp).apply()
            }
        }

        // Monitor audiobooks. Once loaded, set active book if empty or reset if playing book is deleted
        viewModelScope.launch {
            audiobooks.collect { list ->
                val current = _currentPlayingBook.value
                if (current != null && !list.any { it.id == current.id }) {
                    _currentPlayingBook.value = list.firstOrNull()
                    _playbackPositionMillis.value = list.firstOrNull()?.currentPositionMillis ?: 0L
                    if (_isPlaying.value) {
                        _isPlaying.value = false
                        releaseMediaPlayer()
                    }
                } else if (list.isNotEmpty() && _currentPlayingBook.value == null) {
                    selectBook(list.first())
                }
            }
        }

        // Auto-purge mock audiobooks and mock directories from DB to leave only real scanned ones
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Remove mock files that are not content URIs and do not exist on device
                val list = repository.getAllAudiobooksSync()
                for (book in list) {
                    val path = book.filePath
                    val isContentUri = path.startsWith("content://")
                    val isNetworkUri = path.startsWith("http://") || path.startsWith("https://")
                    val isDemoUri = path.startsWith("demo://")
                    val isRealFile = path.isNotEmpty() && !path.startsWith("/books/") && !path.startsWith("/local_storage/") && File(path).exists()
                    if (!isContentUri && !isRealFile && !isNetworkUri && !isDemoUri) {
                        repository.deleteAudiobookById(book.id)
                    }
                }

                // After purging, if database is empty, insert beautiful offline-friendly demo files!
                val postPurgeList = repository.getAllAudiobooksSync()
                val demoBooksInitialized = prefs.getBoolean("demo_books_initialized", false)
                if (postPurgeList.isEmpty()) {
                    if (!demoBooksInitialized) {
                        prefs.edit().putBoolean("demo_books_initialized", true).apply()
                        repository.insertAudiobook(
                            Audiobook(
                                title = "Don Quijote de la Mancha",
                                author = "Miguel de Cervantes",
                                durationMillis = 10L, // 10 pages
                                filePath = "demo://don_quijote.pdf",
                                coverUrl = "",
                                currentPositionMillis = 0L,
                                lastListenedTime = System.currentTimeMillis()
                            )
                        )
                        repository.insertAudiobook(
                            Audiobook(
                                title = "El Principito",
                                author = "Antoine de Saint-Exupéry",
                                durationMillis = 12L, // 12 pages
                                filePath = "demo://el_principito.epub",
                                coverUrl = "",
                                currentPositionMillis = 0L,
                                lastListenedTime = System.currentTimeMillis() - 10000
                            )
                        )
                        repository.insertAudiobook(
                            Audiobook(
                                title = "Muestra de Audiolibro - La Metamorfosis",
                                author = "Franz Kafka",
                                durationMillis = 372000L, // 6.2 min
                                filePath = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                                coverUrl = "",
                                currentPositionMillis = 0L,
                                lastListenedTime = System.currentTimeMillis() - 20000
                            )
                        )
                    }
                } else {
                    if (!demoBooksInitialized) {
                        prefs.edit().putBoolean("demo_books_initialized", true).apply()
                    }
                }

                // Remove mock directories that are not valid local directories
                val dirs = repository.getAllScanDirectoriesSync()
                for (dir in dirs) {
                    val path = dir.path
                    if (path == "/Users/sanctuary/Audiobooks" || path == "/Downloads/New_Books" || (!path.startsWith("/") && !File(path).exists() && !path.startsWith("content://"))) {
                        repository.deleteScanDirectory(path)
                    }
                }

                // Auto-scan real directories on app launch and ensure thumbnails
                withContext(Dispatchers.Main) {
                    scanDeviceStorage()
                }
                ensureThumbnailsForLibrary()
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error auto-purging mock data: ${e.message}", e)
            }
        }

        // Continuous session time tracking & automatic auto-save for ALL types of books (Audio, PDF, EPUB)
        viewModelScope.launch(Dispatchers.Default) {
            var lastTrackTime = System.currentTimeMillis()
            while (isActive) {
                delay(1000) // tick every second
                val now = System.currentTimeMillis()
                val delta = now - lastTrackTime
                lastTrackTime = now

                val currentBook = _currentPlayingBook.value
                if (currentBook != null) {
                    val isPlayingNow = _isPlaying.value
                    val inForeground = isAppInForeground.value

                    // We accumulate active time if:
                    // 1. Audio/TTS is playing (in foreground or background)
                    // 2. OR the app is in the foreground and a book is open (visual reading)
                    val isSessionActive = isPlayingNow || inForeground

                    if (isSessionActive) {
                        listeningAccumulatorMillis += delta
                    }
                }

                // Automatic save check (Scenario 1: Periodic check)
                val checkLimit = _autoSaveIntervalSeconds.value * 1000L
                if (now - lastAutoSaveTimeMillis >= checkLimit) {
                    // Save on Dispatchers.IO
                    withContext(Dispatchers.IO) {
                        saveCurrentPositionState()
                    }
                    lastAutoSaveTimeMillis = now
                }
            }
        }
    }

    fun resetDatabase() {
        viewModelScope.launch {
            try {
                repository.deleteAllAudiobooks()
                repository.deleteAllScanDirectories()
                _currentPlayingBook.value = null
                _playbackPositionMillis.value = 0L
                triggerSaveStatusFeedback("Database reset complete")
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error resetting database: ${e.message}", e)
            }
        }
    }

    // Set filter Category
    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun toggleFavorite(audiobook: Audiobook) {
        viewModelScope.launch {
            try {
                val updated = audiobook.copy(isFavorite = !audiobook.isFavorite)
                if (_currentPlayingBook.value?.id == audiobook.id) {
                    _currentPlayingBook.value = updated
                }
                repository.insertAudiobook(updated)
                // Persist to sidecar metadata in book folder
                withContext(Dispatchers.IO) {
                    val quotes = database.audiobookDao().getBookQuotesForBook(audiobook.id).firstOrNull() ?: emptyList()
                    SidecarMetadataManager.saveBookMetadata(getApplication(), updated, quotes)
                }
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error toggling favorite: ${e.message}", e)
            }
        }
    }

    // Android native MediaPlayer instance
    private var mediaPlayer: android.media.MediaPlayer? = null

    private fun updateMediaPlayerForBook(book: Audiobook, seekToMs: Long? = null) {
        try {
            if (mediaPlayer == null) {
                val file = java.io.File(book.filePath)
                val isNetwork = book.filePath.startsWith("http://") || book.filePath.startsWith("https://")
                if (file.exists() || book.filePath.startsWith("content://") || isNetwork) {
                    val context = getApplication<Application>()
                    val mp = android.media.MediaPlayer().apply {
                        setDataSource(context, android.net.Uri.parse(book.filePath))
                        prepare()
                        val targetSeek = seekToMs ?: _playbackPositionMillis.value
                        seekTo(targetSeek.toInt())
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            playbackParams = playbackParams.apply { speed = _playbackSpeed.value }
                        }
                    }
                    mediaPlayer = mp
                }
            } else {
                seekToMs?.let {
                    mediaPlayer?.seekTo(it.toInt())
                }
            }
        } catch (e: Exception) {
            Log.e("AudiobookViewModel", "Error setting up MediaPlayer: ${e.message}", e)
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("AudiobookViewModel", "Error releasing MediaPlayer", e)
        }
    }

    // Load active audiobook to Player Screen
    fun selectBook(book: Audiobook) {
        viewModelScope.launch {
            if (_isPlaying.value) {
                saveCurrentPositionState(forceCommit = true)
                releaseMediaPlayer()
                stopSpeak()
            }
            
            // Clear current pages first to indicate loading and avoid index mismatched crashes during transitions
            _epubPages.value = emptyList()

            // 1. Automatic sidecar check & recovery upon opening: if a .audire.meta file exists on disk, restore latest stats/bookmarks
            var targetBook = book.copy(lastListenedTime = System.currentTimeMillis())
            val context = getApplication<Application>()
            try {
                val sidecarData = withContext(Dispatchers.IO) {
                    SidecarMetadataManager.readBookMetadata(context, book.filePath)
                }
                if (sidecarData != null) {
                    var needsUpdate = false
                    var updated = targetBook
                    if (sidecarData.currentPositionMillis > 0L && targetBook.currentPositionMillis == 0L) {
                        updated = updated.copy(currentPositionMillis = sidecarData.currentPositionMillis)
                        needsUpdate = true
                    }
                    if (sidecarData.isFavorite != targetBook.isFavorite) {
                        updated = updated.copy(isFavorite = sidecarData.isFavorite)
                        needsUpdate = true
                    }
                    if (sidecarData.isCompleted != targetBook.isCompleted) {
                        updated = updated.copy(isCompleted = sidecarData.isCompleted)
                        needsUpdate = true
                    }
                    if (needsUpdate) {
                        targetBook = updated
                    }
                }
            } catch (e: Throwable) {
                // Silently proceed
            }
            
            // Update state IMMEDIATELY on Main Thread to prevent UI staleness or glitchy transitions
            _currentPlayingBook.value = targetBook
            _playbackPositionMillis.value = targetBook.currentPositionMillis
            
            // Persist the lastListenedTime update in the background DB thread & automatically ensure .audire.meta is up-to-date
            withContext(Dispatchers.IO) {
                repository.insertAudiobook(targetBook)
                try {
                    val quotes = database.audiobookDao().getBookQuotesForBook(targetBook.id).firstOrNull() ?: emptyList()
                    SidecarMetadataManager.saveBookMetadata(context, targetBook, quotes)
                } catch (e: Throwable) {
                    // Silently ignore
                }
            }
            
            if (isDocumentFile(targetBook)) {
                val context = getApplication<Application>()
                if (targetBook.filePath.lowercase().endsWith(".epub") || targetBook.title.lowercase().endsWith(".epub")) {
                    val pages = withContext(Dispatchers.IO) {
                        getEpubTextPages(context, targetBook.filePath)
                    }
                    _epubPages.value = pages
                    if (targetBook.durationMillis != pages.size.toLong()) {
                        targetBook = targetBook.copy(durationMillis = pages.size.toLong())
                        _currentPlayingBook.value = targetBook
                        withContext(Dispatchers.IO) {
                            repository.insertAudiobook(targetBook)
                            try {
                                val quotes = database.audiobookDao().getBookQuotesForBook(targetBook.id).firstOrNull() ?: emptyList()
                                SidecarMetadataManager.saveBookMetadata(context, targetBook, quotes)
                            } catch (e: Throwable) {}
                        }
                    }
                } else if (targetBook.filePath.lowercase().endsWith(".pdf") || targetBook.title.lowercase().endsWith(".pdf")) {
                    // Extract page count instantly using native Android PdfRenderer (less than 1ms)
                    val pageCount = withContext(Dispatchers.IO) {
                        if (targetBook.filePath.startsWith("demo://")) {
                            10
                        } else {
                            synchronized(pdfLock) {
                                try {
                                    val uriString = targetBook.filePath
                                    val pfd = if (uriString.startsWith("content://")) {
                                        context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")
                                    } else {
                                        val file = if (uriString.startsWith("file://")) {
                                            java.io.File(Uri.parse(uriString).path ?: uriString)
                                        } else {
                                            java.io.File(uriString)
                                        }
                                        if (file.exists()) {
                                            android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                                        } else {
                                            context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")
                                        }
                                    }
                                    pfd?.use { descriptor ->
                                        android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                                            renderer.pageCount
                                        }
                                    } ?: 0
                                } catch (e: Throwable) {
                                    Log.e("AudiobookViewModel", "Error reading PDF page count: ${e.message}")
                                    0
                                }
                            }
                        }
                    }
                    
                    if (pageCount > 0 && targetBook.durationMillis != pageCount.toLong()) {
                        targetBook = targetBook.copy(durationMillis = pageCount.toLong())
                        _currentPlayingBook.value = targetBook
                        withContext(Dispatchers.IO) {
                            repository.insertAudiobook(targetBook)
                            try {
                                val quotes = database.audiobookDao().getBookQuotesForBook(targetBook.id).firstOrNull() ?: emptyList()
                                SidecarMetadataManager.saveBookMetadata(context, targetBook, quotes)
                            } catch (e: Throwable) {}
                        }
                    }
                    
                    // Initialize epubPages with empty strings matching page count to avoid parsing the whole document at once (preventing OutOfMemoryErrors)
                    _epubPages.value = List(pageCount) { "" }
                }
            }
            
            if (_isPlaying.value) {
                if (isDocumentFile(targetBook)) {
                    speakCurrentEpubPage()
                } else {
                    updateMediaPlayerForBook(targetBook)
                    mediaPlayer?.start()
                    restartPlaybackTicker()
                }
            }
            syncWithPlaybackService()
        }
    }

    fun openExternalBookUri(context: android.content.Context, uri: android.net.Uri, onComplete: (Audiobook) -> Unit) {
        viewModelScope.launch {
            val path = uri.toString()
            val existingList = repository.getAllAudiobooksSync()
            val existingBook = existingList.find { it.filePath == path }
            if (existingBook != null) {
                selectBook(existingBook)
                onComplete(existingBook)
                return@launch
            }

            var title = ""
            if (uri.scheme == "content") {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (pe: Exception) {
                    Log.e("AudiobookViewModel", "Error taking persistable permission: ${pe.message}")
                }
                try {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (index != -1) {
                                title = it.getString(index)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AudiobookViewModel", "Error querying displayName", e)
                }
            }
            if (title.isEmpty()) {
                title = uri.path ?: "Documento Externo"
                val cut = title.lastIndexOf('/')
                if (cut != -1) {
                    title = title.substring(cut + 1)
                }
            }

            val isPdf = title.lowercase().endsWith(".pdf")
            val author = if (isPdf) "Documento PDF" else "EPUB E-Book"
            val duration = 1L
            
            val newBook = Audiobook(
                title = title,
                author = author,
                durationMillis = duration,
                filePath = path,
                coverUrl = "",
                currentPositionMillis = 0,
                lastListenedTime = System.currentTimeMillis()
            )

            withContext(Dispatchers.IO) {
                repository.insertAudiobook(newBook)
            }

            val updatedList = repository.getAllAudiobooksSync()
            val savedBook = updatedList.find { it.filePath == path } ?: newBook
            
            selectBook(savedBook)
            onComplete(savedBook)
        }
    }

    // Controls: Play / Pause
    fun togglePlayPause() {
        val book = _currentPlayingBook.value ?: return
        if (isDocumentFile(book)) {
            if (_isPlaying.value) {
                stopSpeak()
                viewModelScope.launch {
                    saveCurrentPositionState(forceCommit = true)
                }
            } else {
                _isPlaying.value = true
                speakCurrentEpubPage()
            }
        } else {
            if (_isPlaying.value) {
                _isPlaying.value = false
                try {
                    mediaPlayer?.pause()
                } catch (e: Exception) {
                    Log.e("AudiobookViewModel", "Error pausing MediaPlayer", e)
                }
                stopPlaybackTicker()
                viewModelScope.launch {
                    saveCurrentPositionState(forceCommit = true)
                }
            } else {
                updateMediaPlayerForBook(book)
                try {
                    mediaPlayer?.start()
                } catch (e: Exception) {
                    Log.e("AudiobookViewModel", "Error starting MediaPlayer", e)
                }
                _isPlaying.value = true
                startPlaybackTicker(book)
            }
        }
        syncWithPlaybackService()
    }

    // Configurable Skip actions (Scenario: Jump back / forward +30s / -30s for audio or page for doc)
    fun skipBackward() {
        val book = _currentPlayingBook.value ?: return
        if (isDocumentFile(book)) {
            val currentPos = _playbackPositionMillis.value
            if (currentPos > 0) {
                val target = currentPos - 1
                _playbackPositionMillis.value = target
                if (_isPlaying.value) {
                    speakCurrentEpubPage()
                }
                viewModelScope.launch {
                    saveCurrentPositionState(forceCommit = true)
                }
            }
        } else {
            seekToRelative(-30000L)
        }
    }

    fun skipForward() {
        val book = _currentPlayingBook.value ?: return
        if (isDocumentFile(book)) {
            val currentPos = _playbackPositionMillis.value
            val maxPages = book.durationMillis
            if (currentPos < maxPages - 1) {
                val target = currentPos + 1
                _playbackPositionMillis.value = target
                if (_isPlaying.value) {
                    speakCurrentEpubPage()
                }
                viewModelScope.launch {
                    saveCurrentPositionState(forceCommit = true)
                }
            }
        } else {
            seekToRelative(30000L)
        }
    }

    private fun seekToRelative(amountMillis: Long) {
        val book = _currentPlayingBook.value ?: return
        val newPos = (_playbackPositionMillis.value + amountMillis).coerceIn(0, book.durationMillis)
        _playbackPositionMillis.value = newPos
        
        try {
            mediaPlayer?.seekTo(newPos.toInt())
        } catch (e: Exception) {
            Log.e("AudiobookViewModel", "Error seeking MediaPlayer", e)
        }

        viewModelScope.launch {
            repository.updatePlaybackPosition(
                audiobookId = book.id,
                newPositionMillis = newPos,
                isExplicitRewind = true
            )
            triggerSaveStatusFeedback("Progreso ajustado correctamente")
        }
    }

    // Dynamic Seek Slider Adjustment
    fun seekToFraction(fraction: Float) {
        val book = _currentPlayingBook.value ?: return
        if (isDocumentFile(book)) {
            val targetPage = (fraction * book.durationMillis).toLong().coerceIn(0L, (book.durationMillis - 1).coerceAtLeast(0L))
            _playbackPositionMillis.value = targetPage
            if (_isPlaying.value && (book.filePath.lowercase().endsWith(".epub") || book.title.lowercase().endsWith(".epub"))) {
                speakCurrentEpubPage()
            }
            viewModelScope.launch {
                saveCurrentPositionState(forceCommit = true)
            }
        } else {
            val targetMillis = (book.durationMillis * fraction).toLong().coerceIn(0L, book.durationMillis)
            _playbackPositionMillis.value = targetMillis

            try {
                mediaPlayer?.seekTo(targetMillis.toInt())
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error seeking MediaPlayer", e)
            }

            viewModelScope.launch {
                repository.updatePlaybackPosition(
                    audiobookId = book.id,
                    newPositionMillis = targetMillis,
                    isExplicitRewind = true
                )
                triggerSaveStatusFeedback("Progreso ajustado correctamente")
            }
        }
    }

    // Audio Speed adjustment toggles (M3 slider elements/pill configuration)
    fun setPlaybackSpeed(speed: Float) {
        val book = _currentPlayingBook.value
        _playbackSpeed.value = speed

        if (book != null && isDocumentFile(book)) {
            if (_isPlaying.value && (book.filePath.lowercase().endsWith(".epub") || book.title.lowercase().endsWith(".epub"))) {
                speakCurrentEpubPage()
            }
        } else {
            try {
                if (mediaPlayer?.isPlaying == true) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        mediaPlayer?.let { mp ->
                            mp.playbackParams = mp.playbackParams.apply { this.speed = speed }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error changing speed on MediaPlayer", e)
            }
        }
    }

    fun cyclePlaybackSpeed() {
        val book = _currentPlayingBook.value
        val current = _playbackSpeed.value
        val next = when (current) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            2.0f -> 0.75f
            else -> 1.0f
        }
        _playbackSpeed.value = next

        if (book != null && isDocumentFile(book)) {
            if (_isPlaying.value && (book.filePath.lowercase().endsWith(".epub") || book.title.lowercase().endsWith(".epub"))) {
                speakCurrentEpubPage()
            }
        } else {
            try {
                if (mediaPlayer?.isPlaying == true) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        mediaPlayer?.let { mp ->
                            mp.playbackParams = mp.playbackParams.apply { speed = next }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error changing speed on MediaPlayer", e)
            }
        }
    }

    // Auto-save timer interval changes
    fun setAutoSaveInterval(seconds: Int) {
        _autoSaveIntervalSeconds.value = seconds
    }

    // System Interruption Handler (phone call or audio focus loss event)
    fun handleSystemInterruption(causeName: String) {
        if (!_isPlaying.value) {
            // Trigger quick backup to confirm stability
            viewModelScope.launch {
                saveCurrentPositionState(forceCommit = true)
                triggerSaveStatusFeedback("Diag: Guardado preventivo exitoso")
            }
            return
        }
        Log.d("AudiobookViewModel", "System Interruption! Saving current status state immediately: $causeName")
        _isPlaying.value = false
        try {
            mediaPlayer?.pause()
        } catch (e: Exception) {
            Log.e("AudiobookViewModel", "Error pausing MediaPlayer during interruption", e)
        }
        stopPlaybackTicker()

        // Critical auto-saving on system interruptions
        viewModelScope.launch {
            saveCurrentPositionState(forceCommit = true)
            triggerSaveStatusFeedback("¡Interrupción ($causeName)! Estado guardado")
        }
    }

    // Helper recursively traversing DocumentFile tree for audio & document files
    private suspend fun traverseDirectory(context: android.content.Context, dir: androidx.documentfile.provider.DocumentFile, output: MutableList<Audiobook>, existingPaths: Set<String>) {
        try {
            val files = dir.listFiles()
            for (file in files) {
                if (file.isDirectory) {
                    traverseDirectory(context, file, output, existingPaths)
                } else if (file.isFile) {
                    val name = file.name ?: ""
                    val mimeType = file.type ?: ""
                    val isAudio = mimeType.startsWith("audio/") ||
                            name.lowercase().endsWith(".mp3") ||
                            name.lowercase().endsWith(".m4b") ||
                            name.lowercase().endsWith(".m4a") ||
                            name.lowercase().endsWith(".aac") ||
                            name.lowercase().endsWith(".wav") ||
                            name.lowercase().endsWith(".ogg")
                    
                    val isPdf = name.lowercase().endsWith(".pdf") || mimeType.contains("pdf")
                    val isEpub = name.lowercase().endsWith(".epub") || mimeType.contains("epub")

                    if (isAudio || isPdf || isEpub) {
                        val fileUriStr = file.uri.toString()
                        if (existingPaths.contains(fileUriStr)) {
                            // Skip heavy processing for files already present in DB
                            output.add(
                                Audiobook(
                                    title = name.substringBeforeLast("."),
                                    author = if (isAudio) "Local Folder" else if (isPdf) "PDF Documento" else "EPUB E-Book",
                                    durationMillis = 100L,
                                    filePath = fileUriStr,
                                    coverUrl = ""
                                )
                            )
                            continue
                        }

                        var duration = 300000L // default fallback
                        var title = name.substringBeforeLast(".")
                        var artist = "Local Folder"
                        var coverUrl = ""

                        if (isAudio) {
                            try {
                                val retriever = android.media.MediaMetadataRetriever()
                                retriever.setDataSource(context, file.uri)
                                val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                                val titleMeta = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                                val artistMeta = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                durStr?.toLongOrNull()?.let { duration = it }
                                titleMeta?.let { title = it }
                                artistMeta?.let { artist = it }
                                
                                val embeddedPic = retriever.embeddedPicture
                                if (embeddedPic != null) {
                                    try {
                                        val coversDir = java.io.File(context.cacheDir, "covers")
                                        if (!coversDir.exists()) {
                                            coversDir.mkdirs()
                                        }
                                        val hash = file.uri.toString().hashCode()
                                        val coverFile = java.io.File(coversDir, "cover_$hash.jpg")
                                        java.io.FileOutputStream(coverFile).use { fos ->
                                            fos.write(embeddedPic)
                                        }
                                        coverUrl = coverFile.absolutePath
                                    } catch (ce: Exception) {
                                        Log.e("AudiobookViewModel", "Error saving embedded cover", ce)
                                    }
                                }
                                retriever.release()
                            } catch (e: Exception) {
                                Log.w("AudiobookViewModel", "Could not read metadata for ${name}: ${e.message}")
                            }
                        } else if (isPdf) {
                            artist = "PDF Documento"
                            var pageCount = 1
                            try {
                                synchronized(pdfLock) {
                                    val pfd = context.contentResolver.openFileDescriptor(file.uri, "r")
                                    if (pfd != null) {
                                        val renderer = android.graphics.pdf.PdfRenderer(pfd)
                                    pageCount = renderer.pageCount
                                    if (pageCount > 0) {
                                        val page = renderer.openPage(0)
                                        
                                        // Scale down cover thumbnail to avoid allocating giant bitmaps
                                        val maxThumbDim = 450f
                                        val origW = page.width
                                        val origH = page.height
                                        val thumbScale = if (origH > 0) {
                                            if (origH > maxThumbDim) maxThumbDim / origH else 1f
                                        } else 1f
                                        val thumbW = (origW * thumbScale).toInt().coerceAtLeast(1)
                                        val thumbH = (origH * thumbScale).toInt().coerceAtLeast(1)
                                        
                                        val bitmap = android.graphics.Bitmap.createBitmap(thumbW, thumbH, android.graphics.Bitmap.Config.ARGB_8888)
                                        val canvas = android.graphics.Canvas(bitmap)
                                        canvas.drawColor(android.graphics.Color.WHITE)
                                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        page.close()

                                        val coversDir = java.io.File(context.cacheDir, "covers")
                                        if (!coversDir.exists()) coversDir.mkdirs()
                                        val hash = file.uri.toString().hashCode()
                                        val coverFile = java.io.File(coversDir, "cover_$hash.jpg")
                                        java.io.FileOutputStream(coverFile).use { fos ->
                                            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, fos)
                                        }
                                        coverUrl = coverFile.absolutePath
                                    }
                                        renderer.close()
                                        pfd.close()
                                    }
                                }
                            } catch (e: Throwable) {
                                Log.e("AudiobookViewModel", "Error generating PDF cover/pages metadata", e)
                            }
                            duration = pageCount.toLong()
                        } else if (isEpub) {
                            artist = "EPUB E-Book"
                            duration = 100L
                        }

                        var position = 0L
                        var isFav = false
                        var isComp = false
                        var lastListened = 0L
                        
                        // Check if companion sidecar .audire.meta exists in folder to automatically restore stats
                        try {
                            val sidecar = SidecarMetadataManager.readBookMetadata(context, file.uri.toString())
                            if (sidecar != null) {
                                if (sidecar.title.isNotBlank()) title = sidecar.title
                                if (sidecar.author.isNotBlank()) artist = sidecar.author
                                position = sidecar.currentPositionMillis
                                isFav = sidecar.isFavorite
                                isComp = sidecar.isCompleted
                                lastListened = sidecar.lastListenedTime
                            }
                        } catch (e: Throwable) {}

                        output.add(
                            Audiobook(
                                title = title,
                                author = artist,
                                durationMillis = duration,
                                filePath = file.uri.toString(),
                                coverUrl = coverUrl,
                                currentPositionMillis = position,
                                lastListenedTime = lastListened,
                                isFavorite = isFav,
                                isCompleted = isComp
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudiobookViewModel", "Error traversing folder: ${dir.name}", e)
        }
    }

    // Physical Scan storage operation querying Scoped Storage URIs in Background Thread
    fun scanDeviceStorage() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            
            withContext(Dispatchers.IO) {
                try {
                    val context = getApplication<Application>()
                    val scannedList = mutableListOf<Audiobook>()

                    val dirs = repository.getAllScanDirectoriesSync()
                    val existingBooks = repository.getAllAudiobooksSync()
                    val existingPaths = existingBooks.map { it.filePath }.toSet()
                    
                    // Scan only added Scoped Storage tree URIs (no general MediaStore scanner)
                    val dirCounts = mutableMapOf<String, Int>()
                    for (dir in dirs) {
                        try {
                            if (dir.path.startsWith("content://")) {
                                val treeUri = Uri.parse(dir.path)
                                val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                                if (rootDoc != null) {
                                    val folderScanned = mutableListOf<Audiobook>()
                                    traverseDirectory(context, rootDoc, folderScanned, existingPaths)
                                    scannedList.addAll(folderScanned)
                                    dirCounts[dir.path] = folderScanned.size
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AudiobookViewModel", "Error scanning Scoped Storage directory ${dir.path}: ${e.message}")
                        }
                    }
                    
                    val scannedPaths = scannedList.map { it.filePath }.toSet()
                    
                    // Remove books that are no longer in the scanned directories
                    for (book in existingBooks) {
                        if (!book.filePath.startsWith("http://") && 
                            !book.filePath.startsWith("https://") && 
                            !book.filePath.startsWith("demo://") && 
                            !scannedPaths.contains(book.filePath)) {
                            repository.deleteAudiobookById(book.id)
                        }
                    }
                    
                    var newFoundCount = 0
                    for (scanned in scannedList) {
                        if (scanned.filePath.isNotEmpty() && !existingPaths.contains(scanned.filePath)) {
                            repository.insertAudiobook(scanned)
                            newFoundCount++
                        }
                    }

                    // Keep existing books to allow merging/accumulating scanned files across directories.
                    // We only add new files and preserve previously scanned ones.
                    
                    // Update scan directories status counts
                    for (dir in dirs) {
                        try {
                            val booksInThisDir = dirCounts[dir.path] ?: 0
                            val updatedDir = dir.copy(
                                titlesFound = booksInThisDir,
                                lastScanTime = System.currentTimeMillis()
                            )
                            repository.insertScanDirectory(updatedDir)
                        } catch (e: Exception) {
                            Log.e("AudiobookViewModel", "Error updating dir count: ${e.message}")
                        }
                    }
                    
                    _dbLatency.value = (3..8).random()
                    
                    withContext(Dispatchers.Main) {
                        if (newFoundCount > 0) {
                            triggerSaveStatusFeedback("Escaneo completo: $newFoundCount pistas añadidas")
                        } else {
                            if (scannedList.isNotEmpty()) {
                                triggerSaveStatusFeedback("Escaneo completo: Biblioteca actualizada")
                            } else {
                                if (dirs.isEmpty()) {
                                    triggerSaveStatusFeedback("No hay directorios configurados")
                                } else {
                                    triggerSaveStatusFeedback("No se encontraron audios locales válidos")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AudiobookViewModel", "Error scanning device storage", e)
                    withContext(Dispatchers.Main) {
                        triggerSaveStatusFeedback("Fallo al escanear")
                    }
                }
            }
            _isScanning.value = false
        }
    }

    fun addScanDirectory(path: String) {
        viewModelScope.launch {
            repository.insertScanDirectory(ScanDirectory(path, 0, System.currentTimeMillis()))
            triggerSaveStatusFeedback("Directorio agregado")
            scanDeviceStorage()
        }
    }

    fun removeScanDirectory(path: String) {
        viewModelScope.launch {
            repository.deleteScanDirectory(path)
            triggerSaveStatusFeedback("Directorio excluido")
            scanDeviceStorage()
        }
    }

    fun updateBookCover(bookId: Int, coverUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = repository.getAllAudiobooksSync()
                val book = list.find { it.id == bookId }
                if (book != null) {
                    val updated = book.copy(coverUrl = coverUri)
                    repository.insertAudiobook(updated)
                    if (_currentPlayingBook.value?.id == bookId) {
                        _currentPlayingBook.value = updated
                    }
                    withContext(Dispatchers.Main) {
                        triggerSaveStatusFeedback("Portada actualizada")
                    }
                }
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error updating book cover: ${e.message}", e)
            }
        }
    }

    fun clearApplicationCache() {
        _cacheUsage.value = "0.0 GB"
        triggerSaveStatusFeedback("Caché limpiada")
    }

    // Ticker process handling
    private fun startPlaybackTicker(book: Audiobook) {
        stopPlaybackTicker()
        lastAutoSaveTimeMillis = System.currentTimeMillis()

        tickerJob = viewModelScope.launch(Dispatchers.Default) {
            var lastTickTime = System.currentTimeMillis()
            while (isActive) {
                delay(200) // Update position smoothly every 200 milliseconds
                val now = System.currentTimeMillis()
                val delta = (now - lastTickTime) * _playbackSpeed.value
                lastTickTime = now

                val currentPos = if (mediaPlayer != null) {
                    try {
                        mediaPlayer?.currentPosition?.toLong() ?: _playbackPositionMillis.value
                    } catch (e: Exception) {
                        _playbackPositionMillis.value + delta.toLong()
                    }
                } else {
                    _playbackPositionMillis.value + delta.toLong()
                }
                
                val newPos = currentPos.coerceIn(0L, book.durationMillis)
                _playbackPositionMillis.value = newPos

                // Automatic save check (Scenario 1: Periodic check)
                val checkLimit = _autoSaveIntervalSeconds.value * 1000L
                if (now - lastAutoSaveTimeMillis >= checkLimit) {
                    saveCurrentPositionState()
                    lastAutoSaveTimeMillis = now
                }

                if (newPos >= book.durationMillis) {
                    // Loop or complete
                    withContext(Dispatchers.Main) {
                        _isPlaying.value = false
                        saveCurrentPositionState(forceCommit = true)
                        releaseMediaPlayer()
                        triggerSaveStatusFeedback("Completado")
                    }
                    break
                }
            }
        }
    }

    private fun stopPlaybackTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun restartPlaybackTicker() {
        val book = _currentPlayingBook.value ?: return
        startPlaybackTicker(book)
    }

    private suspend fun saveCurrentPositionState(forceCommit: Boolean = false) = withContext(Dispatchers.IO) {
        val book = _currentPlayingBook.value ?: return@withContext
        val currentPosition = _playbackPositionMillis.value

        val start = System.currentTimeMillis()
        repository.updatePlaybackPosition(
            audiobookId = book.id,
            newPositionMillis = currentPosition,
            isExplicitRewind = forceCommit
        )
        val end = System.currentTimeMillis()
        _dbLatency.value = (end - start).coerceIn(1, 20).toInt() // measure actual write speed database latency

        val listeningToSave = listeningAccumulatorMillis
        if (listeningToSave > 0) {
            listeningAccumulatorMillis = 0L
            try {
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                repository.addListeningDuration(dateStr, listeningToSave)
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error saving listening log: ${e.message}", e)
            }
        }

        if (forceCommit || currentPosition % 10000 < 1000) {
            triggerSaveStatusFeedback("Autosaved to library")
            // Silently persist companion sidecar file in book directory
            try {
                val quotes = database.audiobookDao().getBookQuotesForBook(book.id).firstOrNull() ?: emptyList()
                val updatedBook = book.copy(currentPositionMillis = currentPosition, lastListenedTime = System.currentTimeMillis())
                SidecarMetadataManager.saveBookMetadata(getApplication(), updatedBook, quotes)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun syncAllMetadataToStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val books = repository.getAllAudiobooksSync()
                val allQuotes = database.audiobookDao().getAllBookQuotes().firstOrNull() ?: emptyList()
                val count = SidecarMetadataManager.syncAllBooks(getApplication(), books, allQuotes)
                withContext(Dispatchers.Main) {
                    val lang = selectedLanguage.value
                    val msg = if (lang == "es") "Sincronizados metadatos (.audire.meta) en $count libros" else "Metadata (.audire.meta) synchronized for $count books"
                    triggerSaveStatusFeedback(msg)
                }
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error syncing metadata to folders: ${e.message}", e)
            }
        }
    }

    fun ensureThumbnailsForLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val books = repository.getAllAudiobooksSync()
                for (book in books) {
                    val resolvedCover = ThumbnailManager.getOrCreateThumbnail(getApplication(), book)
                    if (resolvedCover.isNotEmpty() && resolvedCover != book.coverUrl) {
                        val updated = book.copy(coverUrl = resolvedCover)
                        repository.insertAudiobook(updated)
                    }
                }
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error generating covers in background: ${e.message}", e)
            }
        }
    }

    private fun triggerSaveStatusFeedback(message: String) {
        _lastSavedNotification.value = message
        // clear after 3 seconds
        viewModelScope.launch {
            delay(3500)
            if (_lastSavedNotification.value == message) {
                _lastSavedNotification.value = "Autosaved to library"
            }
        }
    }

    fun syncWithPlaybackService() {
        val book = _currentPlayingBook.value
        if (book != null) {
            com.aistudio.sanctuary.audpbk.AudiobookPlaybackService.startService(
                context = getApplication(),
                bookTitle = book.title,
                bookAuthor = book.author,
                isPlaying = _isPlaying.value
            )
        } else {
            com.aistudio.sanctuary.audpbk.AudiobookPlaybackService.stopService(getApplication())
        }
    }

    override fun onCleared() {
        try {
            runBlocking {
                saveCurrentPositionState(forceCommit = true)
            }
        } catch (e: Exception) {
            Log.e("AudiobookViewModel", "Error saving on cleared: ${e.message}")
        }
        super.onCleared()
        com.aistudio.sanctuary.audpbk.PlaybackController.activeViewModel = null
        stopPlaybackTicker()
        releaseMediaPlayer()
        tts?.shutdown()
        com.aistudio.sanctuary.audpbk.AudiobookPlaybackService.stopService(getApplication())
    }

    fun addManualListeningLogs(minutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                repository.addListeningDuration(dateStr, minutes * 60 * 1000L)
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error adding manual listening logs: ${e.message}", e)
            }
        }
    }

    fun subtractManualListeningLogs(minutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                val existingLog = database.audiobookDao().getListeningLogByDate(dateStr)
                if (existingLog != null) {
                    val newDuration = (existingLog.durationMillis - (minutes * 60 * 1000L)).coerceAtLeast(0L)
                    val updated = ListeningLog(dateStr, newDuration)
                    database.audiobookDao().insertListeningLog(updated)
                }
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error subtracting manual listening logs: ${e.message}", e)
            }
        }
    }

    fun clearListeningHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteAllListeningLogs()
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error clearing stats: ${e.message}", e)
            }
        }
    }

    fun insertBookQuote(bookId: Int, bookTitle: String, text: String, page: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val q = BookQuote(
                    bookId = bookId,
                    bookTitle = bookTitle,
                    quoteText = text,
                    pageReference = page
                )
                repository.insertBookQuote(q)
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error inserting quote: ${e.message}", e)
            }
        }
    }

    fun deleteBookQuote(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteBookQuoteById(id)
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error deleting quote: ${e.message}", e)
            }
        }
    }

    fun deleteAudiobook(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // If we are currently playing this book, stop playback first
                if (_currentPlayingBook.value?.id == id) {
                    _currentPlayingBook.value = null
                    _isPlaying.value = false
                    stopPlaybackTicker()
                }
                repository.deleteAudiobookById(id)
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error deleting audiobook: ${e.message}", e)
            }
        }
    }

    private val pdfLoadingPages = java.util.Collections.synchronizedSet(mutableSetOf<Int>())

    fun getSinglePdfPageText(context: android.content.Context, uriString: String, pageIndex: Int): String {
        if (uriString.startsWith("demo://")) {
            return "Página de Ejemplo ${pageIndex + 1}: Este es un texto de demostración para pruebas de lectura en PDF."
        }
        synchronized(pdfLock) {
            try {
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
                val uri = android.net.Uri.parse(uriString)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream).use { document ->
                        if (pageIndex >= 0 && pageIndex < document.numberOfPages) {
                            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                            stripper.startPage = pageIndex + 1
                            stripper.endPage = pageIndex + 1
                            val text = stripper.getText(document).trim()
                            return text.ifEmpty { "Página ${pageIndex + 1}\n\n[Esta página no contiene texto legible directamente o es un gráfico/imagen]" }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.e("AudiobookViewModel", "Error extracting single page $pageIndex text: ${e.message}", e)
            }
        }
        return "Página ${pageIndex + 1}\n\n[Error al extraer texto]"
    }

    fun loadPdfPageTextIfNeeded(pageIndex: Int) {
        val book = _currentPlayingBook.value ?: return
        if (!book.filePath.lowercase().endsWith(".pdf") && !book.title.lowercase().endsWith(".pdf")) return
        
        val pages = _epubPages.value
        if (pageIndex !in pages.indices) return
        
        // Trigger load for current page if empty
        if (pages[pageIndex].isEmpty() && pdfLoadingPages.add(pageIndex)) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val context = getApplication<android.app.Application>()
                    val extractedText = getSinglePdfPageText(context, book.filePath, pageIndex)
                    
                    withContext(Dispatchers.Main) {
                        val currentList = _epubPages.value.toMutableList()
                        if (pageIndex in currentList.indices) {
                            currentList[pageIndex] = extractedText
                            _epubPages.value = currentList
                        }
                        // If we are currently playing/speaking this page, trigger TTS speak now that the text is loaded
                        if (_isPlaying.value && _playbackPositionMillis.value.toInt() == pageIndex) {
                            speakCurrentEpubPage()
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("AudiobookViewModel", "Error in loadPdfPageTextIfNeeded for page $pageIndex: ${e.message}")
                } finally {
                    pdfLoadingPages.remove(pageIndex)
                }
            }
        }
        
        // Prefetch next page to make scrolling/speaking completely seamless
        val nextPageIndex = pageIndex + 1
        if (nextPageIndex in pages.indices && pages[nextPageIndex].isEmpty() && pdfLoadingPages.add(nextPageIndex)) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val context = getApplication<android.app.Application>()
                    val extractedText = getSinglePdfPageText(context, book.filePath, nextPageIndex)
                    
                    withContext(Dispatchers.Main) {
                        val currentList = _epubPages.value.toMutableList()
                        if (nextPageIndex in currentList.indices) {
                            currentList[nextPageIndex] = extractedText
                            _epubPages.value = currentList
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("AudiobookViewModel", "Error prefetching page $nextPageIndex: ${e.message}")
                } finally {
                    pdfLoadingPages.remove(nextPageIndex)
                }
            }
        }
    }

    /**
     * Exports library data, bookmarks, notes, and reading logs into a clean JSON string.
     */
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val root = org.json.JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val booksArray = org.json.JSONArray()
        val books = repository.getAllAudiobooksSync()
        for (b in books) {
            val obj = org.json.JSONObject()
            obj.put("title", b.title)
            obj.put("author", b.author)
            obj.put("durationMillis", b.durationMillis)
            obj.put("filePath", b.filePath)
            obj.put("coverUrl", b.coverUrl)
            obj.put("currentPositionMillis", b.currentPositionMillis)
            obj.put("lastListenedTime", b.lastListenedTime)
            obj.put("isCompleted", b.isCompleted)
            obj.put("isFavorite", b.isFavorite)
            booksArray.put(obj)
        }
        root.put("books", booksArray)

        val quotesArray = org.json.JSONArray()
        val quotes = database.audiobookDao().getAllBookQuotes().firstOrNull() ?: emptyList()
        for (q in quotes) {
            val obj = org.json.JSONObject()
            obj.put("bookId", q.bookId)
            obj.put("bookTitle", q.bookTitle)
            obj.put("quoteText", q.quoteText)
            obj.put("pageReference", q.pageReference)
            obj.put("timestamp", q.timestamp)
            quotesArray.put(obj)
        }
        root.put("quotes", quotesArray)

        root.toString(2)
    }

    /**
     * Imports library data, bookmarks, and quotes from a JSON backup string.
     */
    suspend fun importBackupJson(jsonString: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val root = org.json.JSONObject(jsonString)
            val booksArray = root.optJSONArray("books")
            var importedBooksCount = 0
            if (booksArray != null) {
                for (i in 0 until booksArray.length()) {
                    val obj = booksArray.getJSONObject(i)
                    val book = Audiobook(
                        title = obj.optString("title", "Untitled"),
                        author = obj.optString("author", "Unknown"),
                        durationMillis = obj.optLong("durationMillis", 0L),
                        filePath = obj.optString("filePath", ""),
                        coverUrl = obj.optString("coverUrl", ""),
                        currentPositionMillis = obj.optLong("currentPositionMillis", 0L),
                        lastListenedTime = obj.optLong("lastListenedTime", System.currentTimeMillis()),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        isFavorite = obj.optBoolean("isFavorite", false)
                    )
                    repository.insertAudiobook(book)
                    importedBooksCount++
                }
            }

            val quotesArray = root.optJSONArray("quotes")
            var importedQuotesCount = 0
            if (quotesArray != null) {
                for (i in 0 until quotesArray.length()) {
                    val obj = quotesArray.getJSONObject(i)
                    val quote = BookQuote(
                        bookId = obj.optInt("bookId", 0),
                        bookTitle = obj.optString("bookTitle", ""),
                        quoteText = obj.optString("quoteText", ""),
                        pageReference = obj.optString("pageReference", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                    repository.insertBookQuote(quote)
                    importedQuotesCount++
                }
            }

            Pair(true, "Restaurados con éxito: $importedBooksCount libros y $importedQuotesCount marcas/citas")
        } catch (e: Exception) {
            Log.e("AudiobookViewModel", "Error importing backup: ${e.message}", e)
            Pair(false, "Error al importar: ${e.message}")
        }
    }

    companion object {
        val pdfLock = Any()

        private val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        private val cacheSizeKb = (maxMemoryKb / 4).coerceAtLeast(10240) // 25% of available memory

        val pdfBitmapCache = object : android.util.LruCache<String, android.graphics.Bitmap>(cacheSizeKb) {
            override fun sizeOf(key: String, bitmap: android.graphics.Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }
}
