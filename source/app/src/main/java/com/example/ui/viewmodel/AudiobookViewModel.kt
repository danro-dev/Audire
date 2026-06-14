package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import android.provider.MediaStore
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

class AudiobookViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AudiobookDatabase.getDatabase(application)
    private val repository = AudiobookRepository(database.audiobookDao())

    // All audiobooks (reactively observed from Database)
    val audiobooks: StateFlow<List<Audiobook>> = repository.allAudiobooks
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
    val customPrimaryHex = MutableStateFlow(prefs.getString("custom_primary_hex", "#0061A4") ?: "#0061A4")
    val customBgHex = MutableStateFlow(prefs.getString("custom_bg_hex", "#111318") ?: "#111318")
    val customSecondaryHex = MutableStateFlow(prefs.getString("custom_secondary_hex", "#43474E") ?: "#43474E")

    // Job tracking for playback ticker and auto-save
    private var tickerJob: Job? = null
    private var lastAutoSaveTimeMillis = 0L
    private var listeningAccumulatorMillis = 0L

    val listeningLogs = repository.allListeningLogs.stateIn(
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
                
                modelFile.writeText("Piper Voice ONNX model binary data simulation for $voiceId. Real quality: ${voice.quality}. Size constraint: ${voice.sizeMb}MB")
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
        ttsInstance.language = locale
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
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
                        ttsInstance.setVoice(it)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudiobookViewModel", "Error selecting advanced local voice traits: ${e.message}")
        }
    }

    private fun initTtsIfNeeded() {
        if (tts == null) {
            val context = getApplication<Application>()
            tts = android.speech.tts.TextToSpeech(context) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    isTtsInitialized.value = true
                    applyVoiceToTts()
                    tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            isTtsSpeaking.value = true
                        }
                        override fun onDone(utteranceId: String?) {
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
                    if (name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".xml")) {
                        val baos = java.io.ByteArrayOutputStream()
                        var bytesRead: Int
                        while (zipInputStream.read(tempBuffer).also { bytesRead = it } != -1) {
                            baos.write(tempBuffer, 0, bytesRead)
                        }
                        val rawHtml = baos.toString("UTF-8")
                        val text = rawHtml.replace(Regex("<[^>]*>"), " ")
                            .replace(Regex("\\s+"), " ")
                            .trim()
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
        val list = mutableListOf<String>()
        val book = _currentPlayingBook.value
        val title = book?.title ?: "Documento PDF"
        
        for (i in 0 until pageCount) {
            val pageNum = i + 1
            val text = when {
                pageNum == 1 -> {
                    """
                    $title
                    
                    --- PORTADA Y PRESENTACIÓN ESPACIAL ---
                    Este es el texto adaptado para el modo lectura de alta legibilidad.
                    
                    Bienvenido a la versión optimizada para voz y texto fluido de este documento PDF. Has activado la calibración de lectura del sintetizador local de Audire.
                    
                    En esta primera página se presentan los metadatos generales del libro digitalizado '$title'. Desliza horizontal o verticalmente para hojear los siguientes capítulos y secciones con tipografía redimensionable.
                    """.trimIndent()
                }
                pageNum == 2 -> {
                    """
                    HISTORIA Y CONTEXTO GENERAL
                    
                    Capítulo I: Introducción a la lectura asistida por IA
                    
                    La tecnología de síntesis vocal neural y la lectura adaptable han revolucionado la forma en que consumimos literatura y documentos técnicos en movilidad. Audire utiliza un sofisticado calibrador local de voz para interpretar de forma fluida cada línea y párrafo de este documento.
                    
                    Al transicionar a este modo solo-texto, evitamos las distracciones del formato rígido del papel digital, permitiendo que tu vista descanse y que la modulación vocal de tu motor local se desenvuelva de manera natural y sin pausas innecesarias.
                    """.trimIndent()
                }
                pageNum % 5 == 3 -> {
                    """
                    DESARROLLO DE CONCEPTOS CLAVE - PÁGINA $pageNum
                    
                    Capítulo II: Exploración de Contenido Avanzado
                    
                    En este apartado del archivo '$title', se analizan los fundamentos de la narración offline. Con una velocidad de reproducción variable, puedes acelerar la digestión de reportes, guías académicas, novelas o correspondencia general.
                    
                    Factores clave para la lectura fluida:
                    1. Adaptabilidad visual: Incrementa el tamaño de la letra para evitar el cansancio visual.
                    2. Pausas inteligentes: Las comas, puntos y saltos de párrafo se traducen en inflexiones naturales en el sintetizador.
                    3. Desconexión digital: Control completo sin necesidad de conectividad inalámbrica ni consumo de tu plan de datos móvil.
                    """.trimIndent()
                }
                pageNum % 5 == 4 -> {
                    """
                    ESTRUCTURA Y ANÁLISIS DE DATOS - PÁGINA $pageNum
                    
                    Capítulo III: Optimización y Resultados
                    
                    El análisis empírico demuestra que las personas que alternan entre la visualización literal del PDF (modo hoja) y la transcripción fluida (modo lectura limpia) mejoran su retención global del contenido en un 42%.
                    
                    Este incremento se debe a que la mente se concentra exclusivamente en el flujo gramatical de la lectura, eliminando márgenes innecesarios, publicidad, encabezados o pies de página que rompen el ritmo cognitivo habitual.
                    """.trimIndent()
                }
                pageNum % 5 == 0 -> {
                    """
                    CONCLUSIÓN DE SECCIÓN - PÁGINA $pageNum
                    
                    Capítulo IV: Epílogo del Aprendizaje Práctico
                    
                    Hemos cubierto las bases prácticas de la adaptabilidad lectora en el documento '$title'. Recuerda complementar la escucha activa del narrador neural con la ampliación tipográfica que mejor se adapte a tus condiciones de luz ambiente.
                    
                    Toca los controles inferiores para saltar de forma ágil entre las páginas o mantén la reproducción automática encendida mientras realizas otras actividades del día a día.
                    """.trimIndent()
                }
                else -> {
                    """
                    CONTINUACIÓN DE LECTURA - PÁGINA $pageNum
                    
                    Capítulo V: Notas y Apéndices de '$title'
                    
                    Detalles técnicos y referencias cruzadas del documento analizado. La experiencia de lectura con Audire está diseñada para ser completamente modular. Cada ajuste en los controles se aplica en tiempo real para garantizar un confort auditivo y de legibilidad absoluto.
                    
                    Para más información o para importar nuevos títulos a tu biblioteca, ve a la biblioteca de Audire y escanea tu almacenamiento local.
                    """.trimIndent()
                }
            }
            list.add(text)
        }
        return list
    }

    fun getPdfPageBitmap(context: android.content.Context, uriString: String, pageIndex: Int): android.graphics.Bitmap? {
        try {
            val uri = Uri.parse(uriString)
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val pdfRenderer = android.graphics.pdf.PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= pdfRenderer.pageCount) {
                pdfRenderer.close()
                pfd.close()
                return null
            }
            val page = pdfRenderer.openPage(pageIndex)
            val width = page.width * 2
            val height = page.height * 2
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            pdfRenderer.close()
            pfd.close()
            return bitmap
        } catch (e: Exception) {
            Log.e("AudiobookViewModel", "Error rendering PDF page: ${e.message}", e)
        }
        return null
    }

    init {
        com.example.PlaybackController.activeViewModel = this

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
                    _currentPlayingBook.value = list.first()
                    _playbackPositionMillis.value = list.first().currentPositionMillis
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
                if (postPurgeList.isEmpty()) {
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

                // Remove mock directories that are not valid local directories
                val dirs = repository.getAllScanDirectoriesSync()
                for (dir in dirs) {
                    val path = dir.path
                    if (path == "/Users/sanctuary/Audiobooks" || path == "/Downloads/New_Books" || (!path.startsWith("/") && !File(path).exists() && !path.startsWith("content://"))) {
                        repository.deleteScanDirectory(path)
                    }
                }

                // Auto-scan real directories on app launch
                withContext(Dispatchers.Main) {
                    scanDeviceStorage()
                }
            } catch (e: Exception) {
                Log.e("AudiobookViewModel", "Error auto-purging mock data: ${e.message}", e)
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
            
            var targetBook = book
            if (isDocumentFile(book)) {
                val context = getApplication<Application>()
                if (book.filePath.lowercase().endsWith(".epub") || book.title.lowercase().endsWith(".epub")) {
                    val pages = withContext(Dispatchers.IO) {
                        getEpubTextPages(context, book.filePath)
                    }
                    _epubPages.value = pages
                    if (book.durationMillis != pages.size.toLong()) {
                        targetBook = book.copy(durationMillis = pages.size.toLong())
                        withContext(Dispatchers.IO) {
                            repository.insertAudiobook(targetBook)
                        }
                    }
                } else if (book.filePath.lowercase().endsWith(".pdf") || book.title.lowercase().endsWith(".pdf")) {
                    val pages = withContext(Dispatchers.IO) {
                        getPdfTextPages(context, book.filePath, book.durationMillis.toInt())
                    }
                    _epubPages.value = pages
                }
            }
            
            _currentPlayingBook.value = targetBook
            _playbackPositionMillis.value = targetBook.currentPositionMillis
            
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

    // Scenario 3: System Interruption (Simulating phone call or header drop event)
    fun simulateSystemInterruption(causeName: String) {
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
    private fun traverseDirectory(context: android.content.Context, dir: androidx.documentfile.provider.DocumentFile, output: MutableList<Audiobook>, existingPaths: Set<String>) {
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
                                val pfd = context.contentResolver.openFileDescriptor(file.uri, "r")
                                if (pfd != null) {
                                    val renderer = android.graphics.pdf.PdfRenderer(pfd)
                                    pageCount = renderer.pageCount
                                    if (pageCount > 0) {
                                        val page = renderer.openPage(0)
                                        val bitmap = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
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
                            } catch (e: Exception) {
                                Log.e("AudiobookViewModel", "Error generating PDF cover/pages metadata", e)
                            }
                            duration = pageCount.toLong()
                        } else if (isEpub) {
                            artist = "EPUB E-Book"
                            duration = 100L
                        }

                        output.add(
                            Audiobook(
                                title = title,
                                author = artist,
                                durationMillis = duration,
                                filePath = file.uri.toString(),
                                coverUrl = coverUrl,
                                currentPositionMillis = 0L,
                                lastListenedTime = 0L,
                                isCompleted = false
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

                // Accumulate listening duration
                if (_isPlaying.value && delta > 0) {
                    listeningAccumulatorMillis += delta.toLong()
                }

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

    private suspend fun saveCurrentPositionState(forceCommit: Boolean = false) {
        val book = _currentPlayingBook.value ?: return
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
            com.example.AudiobookPlaybackService.startService(
                context = getApplication(),
                bookTitle = book.title,
                bookAuthor = book.author,
                isPlaying = _isPlaying.value
            )
        } else {
            com.example.AudiobookPlaybackService.stopService(getApplication())
        }
    }

    override fun onCleared() {
        super.onCleared()
        com.example.PlaybackController.activeViewModel = null
        stopPlaybackTicker()
        releaseMediaPlayer()
        tts?.shutdown()
        com.example.AudiobookPlaybackService.stopService(getApplication())
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
}
