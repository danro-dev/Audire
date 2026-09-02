package com.aistudio.sanctuary.audpbk.data

data class Achievement(
    val id: String,
    val category: AchievementCategory,
    val icon: String,
    val titleEs: String,
    val titleEn: String,
    val descEs: String,
    val descEn: String,
    val currentProgress: Float, // 0.0f to 1.0f
    val progressLabel: String,
    val isUnlocked: Boolean,
    val lang: String = "es"
) {
    val title: String
        get() = if (lang == "es") titleEs else titleEn

    val description: String
        get() = if (lang == "es") descEs else descEn

    val progressFraction: Float
        get() = currentProgress.coerceIn(0f, 1f)
}

enum class AchievementCategory(val labelEs: String, val labelEn: String) {
    ALL("Todos", "All"),
    STREAKS("Rachas", "Streaks"),
    TIME("Tiempo", "Time"),
    LIBRARY("Biblioteca", "Library"),
    QUOTES("Frases", "Quotes"),
    HABITS("Hábitos", "Habits"),
    MASTERY("Maestría", "Mastery")
}

data class AchDef(
    val target: Int,
    val titleEs: String,
    val titleEn: String,
    val descEs: String,
    val descEn: String
)

object AchievementManager {

    fun generateAchievements(
        allBooks: List<Audiobook>,
        totalHours: Float,
        currentStreak: Int,
        maxStreak: Int,
        quotesList: List<BookQuote>,
        logs: List<ListeningLog>,
        lang: String
    ): List<Achievement> {
        val totalMinutes = (totalHours * 60).toInt()
        val completedBooks = allBooks.count { it.durationMillis > 0 && it.currentPositionMillis >= (it.durationMillis * 0.9f) }
        val audioCount = allBooks.count { it.filePath.lowercase().run { endsWith(".mp3") || endsWith(".m4a") || endsWith(".m4b") || endsWith(".aac") } }
        val epubCount = allBooks.count { it.filePath.lowercase().endsWith(".epub") }
        val pdfCount = allBooks.count { it.filePath.lowercase().endsWith(".pdf") }
        val effectiveStreak = maxOf(currentStreak, maxStreak)
        val quotesCount = quotesList.size
        val distinctQuotedBooks = quotesList.map { it.bookId }.distinct().size

        val achievements = mutableListOf<Achievement>()

        // ----------------------------------------------------
        // 1. RACHAS Y CONSTANCIA (18 Logros)
        // ----------------------------------------------------
        val streakTargets = listOf(
            AchDef(1, "🌱 Chispa Inicial", "First Spark", "Completa tu 1er día de lectura", "Complete your 1st reading day"),
            AchDef(2, "🔥 Segundo Paso", "Second Step", "Mantén el hábito 2 días", "Maintain the habit for 2 days"),
            AchDef(3, "⚡ Hábito Formado", "Habit Formed", "Alcanza 3 días consecutivos", "Reach 3 consecutive days"),
            AchDef(5, "✨ En Llamas", "On Fire", "5 días seguidos de lectura", "5 days in a row"),
            AchDef(7, "🏆 Fuego Semanal", "Weekly Flame", "1 semana entera sin fallar", "1 full week unbroken"),
            AchDef(10, "🌟 Doble Dígito", "Double Digits", "10 días de constancia pura", "10 days of pure dedication"),
            AchDef(14, "💎 Quincena Dorada", "Golden Fortnight", "2 semanas continuas de estudio", "2 continuous weeks"),
            AchDef(21, "🧠 Mente de Acero", "Steel Mind", "21 días: el hábito es inquebrantable", "21 days: unbroken habit"),
            AchDef(30, "🌙 Mes Perfecto", "Perfect Month", "30 días consecutivos de lectura", "30 consecutive reading days"),
            AchDef(45, "🛡️ Lector Incansable", "Tireless Reader", "45 días de superación diaria", "45 days of daily growth"),
            AchDef(60, "⚔️ Gladiador Literario", "Literary Gladiator", "60 días de lectura ininterrumpida", "60 days of uninterrupted reading"),
            AchDef(75, "💫 Devoción Total", "Total Devotion", "75 días enriqueciendo tu mente", "75 days enriching your mind"),
            AchDef(90, "🏅 Trimestre de Sabiduría", "Quarter of Wisdom", "90 días: 3 meses de gloria", "90 days: 3 months of glory"),
            AchDef(120, "👑 Disciplina Legendaria", "Legendary Discipline", "120 días de hábito indestructible", "120 days of indestructible habit"),
            AchDef(150, "🌌 Viajero Constante", "Constant Traveler", "150 días sumergido en historias", "150 days immersed in stories"),
            AchDef(180, "🪐 Medio Año de Oro", "Half-Year Golden", "180 días de constancia absoluta", "180 days of absolute consistency"),
            AchDef(270, "☀️ Tres Estaciones", "Three Seasons", "270 días de disciplina férrea", "270 days of iron discipline"),
            AchDef(365, "🎆 Héroe Anual de Audire", "Audire Annual Hero", "365 días seguidos: ¡Un año perfecto!", "365 consecutive days: A perfect year!")
        )

        streakTargets.forEach { item ->
            val progress = (effectiveStreak.toFloat() / item.target.toFloat()).coerceIn(0f, 1f)
            val unlocked = effectiveStreak >= item.target
            achievements.add(
                Achievement(
                    id = "streak_${item.target}",
                    category = AchievementCategory.STREAKS,
                    icon = if (unlocked) "🔥" else "🔒",
                    titleEs = item.titleEs,
                    titleEn = item.titleEn,
                    descEs = item.descEs,
                    descEn = item.descEn,
                    currentProgress = progress,
                    progressLabel = "$effectiveStreak / ${item.target} ${if (lang == "es") "días" else "days"}",
                    isUnlocked = unlocked,
                    lang = lang
                )
            )
        }

        // ----------------------------------------------------
        // 2. TIEMPO Y DEDICACIÓN (20 Logros)
        // ----------------------------------------------------
        val timeTargets = listOf(
            AchDef(15, "⏱️ Primer Cuarto", "First Quarter", "Acumula 15 minutos de lectura", "Accumulate 15 reading minutes"),
            AchDef(30, "⏳ Media Hora", "Half Hour", "Alcanza 30 minutos totales", "Reach 30 total minutes"),
            AchDef(60, "🕰️ Primera Hora", "First Hour", "1 hora completa de inmersión", "1 full hour of immersion"),
            AchDef(120, "🎧 Sesión Doble", "Double Session", "2 horas de lectura o audio", "2 hours of reading or audio"),
            AchDef(180, "☕ Tres Horas", "Three Hours", "3 horas dedicadas al aprendizaje", "3 hours dedicated to learning"),
            AchDef(300, "📚 Maratón Inicial", "Initial Marathon", "5 horas de conocimiento puro", "5 hours of pure knowledge"),
            AchDef(480, "🚀 Lector Veloz", "Fast Reader", "8 horas acumuladas", "8 accumulated hours"),
            AchDef(600, "🎯 Decena de Horas", "Ten Hours", "10 horas de crecimiento personal", "10 hours of personal growth"),
            AchDef(900, "🔮 Sabio en Ciernes", "Budding Sage", "15 horas en tu historial", "15 hours in your history"),
            AchDef(1200, "🏛️ Biblioteca Viviente", "Living Library", "20 horas de lectura enriquecedora", "20 hours of rich reading"),
            AchDef(1800, "🪐 Explorador Cósmico", "Cosmic Explorer", "30 horas explorando universos", "30 hours exploring worlds"),
            AchDef(2400, "💎 Cuarenta Horas", "Forty Hours", "40 horas de concentración total", "40 hours of total focus"),
            AchDef(3000, "⚡ Cincuenta Horas", "Fifty Hours", "50 horas: ¡Medio centenar!", "50 hours: Half a hundred!"),
            AchDef(4500, "🛡️ Gran Académico", "Grand Scholar", "75 horas acumuladas en Audire", "75 hours accumulated in Audire"),
            AchDef(6000, "👑 Centenario de Horas", "Hour Centurion", "100 horas de lectura maestra", "100 hours of master reading"),
            AchDef(9000, "🌠 Maestro Iluminado", "Illuminated Master", "150 horas de pura devoción", "150 hours of pure devotion"),
            AchDef(12000, "🔱 Bicentenario Épico", "Epic Bicentennial", "200 horas dedicadas a libros", "200 hours dedicated to books"),
            AchDef(18000, "🌌 Gran Sabio Eterno", "Eternal Grand Sage", "300 horas de sabiduría profunda", "300 hours of deep wisdom"),
            AchDef(30000, "🌞 Titán de la Literatura", "Titan of Literature", "500 horas de conocimiento", "500 hours of knowledge"),
            AchDef(60000, "⭐ Leyenda Absoluta", "Absolute Legend", "1000 horas: ¡Cima del conocimiento!", "1000 hours: Peak of wisdom!")
        )

        timeTargets.forEach { item ->
            val progress = (totalMinutes.toFloat() / item.target.toFloat()).coerceIn(0f, 1f)
            val unlocked = totalMinutes >= item.target
            val targetHours = item.target / 60f
            achievements.add(
                Achievement(
                    id = "time_${item.target}",
                    category = AchievementCategory.TIME,
                    icon = if (unlocked) "⏱️" else "🔒",
                    titleEs = item.titleEs,
                    titleEn = item.titleEn,
                    descEs = item.descEs,
                    descEn = item.descEn,
                    currentProgress = progress,
                    progressLabel = "${String.format("%.1f", totalHours)} / ${if (item.target < 60) "${item.target}m" else "${targetHours.toInt()}h"}",
                    isUnlocked = unlocked,
                    lang = lang
                )
            )
        }

        // ----------------------------------------------------
        // 3. BIBLIOTECA Y FORMATOS (18 Logros)
        // ----------------------------------------------------
        val libTargets = listOf(
            AchDef(1, "📥 Primera Adquisición", "First Acquisition", "Añade tu 1er libro a la biblioteca", "Add your 1st book to library"),
            AchDef(3, "📚 Trilogía Personal", "Personal Trilogy", "Ten al menos 3 libros en tu colección", "Have at least 3 books in collection"),
            AchDef(5, "🗄️ Estantería Inicial", "Initial Bookshelf", "Colecciona 5 libros o audios", "Collect 5 books or audios"),
            AchDef(10, "📂 Pequeña Librería", "Small Bookshop", "10 libros importados en total", "10 imported books in total"),
            AchDef(20, "🏛️ Sala de Lectura", "Reading Hall", "20 títulos en tu colección", "20 titles in collection"),
            AchDef(35, "📦 Coleccionista Nato", "Born Collector", "35 libros disponibles para leer", "35 books available to read"),
            AchDef(50, "🏰 Gran Archivo", "Great Archive", "50 libros y audiolibros listos", "50 books and audiobooks ready"),
            AchDef(100, "🪐 Biblioteca de Alejandría", "Library of Alexandria", "¡100 libros en tu biblioteca personal!", "100 books in your personal library!")
        )

        libTargets.forEach { item ->
            val count = allBooks.size
            val progress = (count.toFloat() / item.target.toFloat()).coerceIn(0f, 1f)
            val unlocked = count >= item.target
            achievements.add(
                Achievement(
                    id = "library_${item.target}",
                    category = AchievementCategory.LIBRARY,
                    icon = if (unlocked) "📚" else "🔒",
                    titleEs = item.titleEs,
                    titleEn = item.titleEn,
                    descEs = item.descEs,
                    descEn = item.descEn,
                    currentProgress = progress,
                    progressLabel = "$count / ${item.target} ${if (lang == "es") "libros" else "books"}",
                    isUnlocked = unlocked,
                    lang = lang
                )
            )
        }

        // Formatos específicos
        achievements.add(
            Achievement(
                id = "format_audio_1",
                category = AchievementCategory.LIBRARY,
                icon = if (audioCount >= 1) "🎧" else "🔒",
                titleEs = "🎧 Oído Atento",
                titleEn = "Attentive Ear",
                descEs = "Ten al menos 1 audiolibro en tu biblioteca",
                descEn = "Have at least 1 audiobook in your library",
                currentProgress = if (audioCount >= 1) 1f else 0f,
                progressLabel = "$audioCount / 1",
                isUnlocked = audioCount >= 1,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "format_audio_5",
                category = AchievementCategory.LIBRARY,
                icon = if (audioCount >= 5) "🎙️" else "🔒",
                titleEs = "🎙️ Podcastero Literario",
                titleEn = "Literary Podcaster",
                descEs = "Reúne 5 audiolibros",
                descEn = "Gather 5 audiobooks",
                currentProgress = (audioCount.toFloat() / 5f).coerceIn(0f, 1f),
                progressLabel = "$audioCount / 5",
                isUnlocked = audioCount >= 5,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "format_epub_1",
                category = AchievementCategory.LIBRARY,
                icon = if (epubCount >= 1) "📖" else "🔒",
                titleEs = "📖 Devorador de EPUBs",
                titleEn = "EPUB Devourer",
                descEs = "Importa tu primer e-book EPUB",
                descEn = "Import your first EPUB e-book",
                currentProgress = if (epubCount >= 1) 1f else 0f,
                progressLabel = "$epubCount / 1",
                isUnlocked = epubCount >= 1,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "format_epub_5",
                category = AchievementCategory.LIBRARY,
                icon = if (epubCount >= 5) "📑" else "🔒",
                titleEs = "📑 Maestro del EPUB",
                titleEn = "EPUB Master",
                descEs = "Ten 5 libros digitales EPUB",
                descEn = "Have 5 digital EPUB books",
                currentProgress = (epubCount.toFloat() / 5f).coerceIn(0f, 1f),
                progressLabel = "$epubCount / 5",
                isUnlocked = epubCount >= 5,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "format_pdf_1",
                category = AchievementCategory.LIBRARY,
                icon = if (pdfCount >= 1) "📄" else "🔒",
                titleEs = "📄 Lector de Documentos",
                titleEn = "Document Reader",
                descEs = "Importa tu 1er PDF o Manga",
                descEn = "Import your 1st PDF or Manga",
                currentProgress = if (pdfCount >= 1) 1f else 0f,
                progressLabel = "$pdfCount / 1",
                isUnlocked = pdfCount >= 1,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "format_pdf_5",
                category = AchievementCategory.LIBRARY,
                icon = if (pdfCount >= 5) "🗞️" else "🔒",
                titleEs = "🗞️ Archivero de PDFs",
                titleEn = "PDF Archivist",
                descEs = "Colecciona 5 documentos o cómics PDF",
                descEn = "Collect 5 PDF documents or comics",
                currentProgress = (pdfCount.toFloat() / 5f).coerceIn(0f, 1f),
                progressLabel = "$pdfCount / 5",
                isUnlocked = pdfCount >= 5,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "format_versatile",
                category = AchievementCategory.LIBRARY,
                icon = if (audioCount >= 1 && epubCount >= 1 && pdfCount >= 1) "🌈" else "🔒",
                titleEs = "🌈 Lector Polifacético",
                titleEn = "Multifaceted Reader",
                descEs = "Ten en tu biblioteca Audio, EPUB y PDF",
                descEn = "Have Audio, EPUB, and PDF in your library",
                currentProgress = listOf(audioCount >= 1, epubCount >= 1, pdfCount >= 1).count { it } / 3f,
                progressLabel = "${listOf(audioCount >= 1, epubCount >= 1, pdfCount >= 1).count { it }} / 3",
                isUnlocked = audioCount >= 1 && epubCount >= 1 && pdfCount >= 1,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "completed_1",
                category = AchievementCategory.LIBRARY,
                icon = if (completedBooks >= 1) "🏁" else "🔒",
                titleEs = "🏁 Primer Libro Terminado",
                titleEn = "First Book Finished",
                descEs = "Completa el 90% o más de tu 1er libro",
                descEn = "Complete 90% or more of your 1st book",
                currentProgress = if (completedBooks >= 1) 1f else 0f,
                progressLabel = "$completedBooks / 1",
                isUnlocked = completedBooks >= 1,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "completed_3",
                category = AchievementCategory.LIBRARY,
                icon = if (completedBooks >= 3) "🎖️" else "🔒",
                titleEs = "🎖️ Trío Conquistado",
                titleEn = "Conquered Trio",
                descEs = "Termina 3 libros completos",
                descEn = "Finish 3 complete books",
                currentProgress = (completedBooks.toFloat() / 3f).coerceIn(0f, 1f),
                progressLabel = "$completedBooks / 3",
                isUnlocked = completedBooks >= 3,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "completed_10",
                category = AchievementCategory.LIBRARY,
                icon = if (completedBooks >= 10) "🏆" else "🔒",
                titleEs = "🏆 Decálogo del Triunfo",
                titleEn = "Decalogue of Triumph",
                descEs = "Termina 10 libros completos",
                descEn = "Finish 10 complete books",
                currentProgress = (completedBooks.toFloat() / 10f).coerceIn(0f, 1f),
                progressLabel = "$completedBooks / 10",
                isUnlocked = completedBooks >= 10,
                lang = lang
            )
        )

        // ----------------------------------------------------
        // 4. CITAS Y SABIDURÍA (15 Logros)
        // ----------------------------------------------------
        val quoteTargets = listOf(
            AchDef(1, "🔖 Primera Iluminación", "First Illumination", "Guarda tu 1ra cita o marcador", "Save your 1st quote or bookmark"),
            AchDef(2, "📝 Pensamiento Doble", "Double Thought", "Anota 2 frases memorables", "Note down 2 memorable quotes"),
            AchDef(3, "📑 Coleccionista Inicial", "Initial Collector", "Reúne 3 frases inspiradoras", "Gather 3 inspiring quotes"),
            AchDef(5, "✨ Quinteto de Oro", "Golden Quintet", "5 citas guardadas en tu libreta", "5 saved quotes in your notebook"),
            AchDef(8, "📜 Ocho Sabidurías", "Eightfold Wisdom", "8 citas anotadas de tus lecturas", "8 quotes noted from your reads"),
            AchDef(12, "💎 Joyero de Frases", "Jewelry of Phrases", "12 citas de grandes autores", "12 quotes from great authors"),
            AchDef(20, "🏛️ Compendio Filosófico", "Philosophical Compendium", "20 citas registradas", "20 registered quotes"),
            AchDef(30, "🧠 Maestro Pensador", "Master Thinker", "30 citas que transforman vidas", "30 life-transforming quotes"),
            AchDef(50, "🪐 Gran Antólogo", "Grand Anthologist", "50 citas inmortales coleccionadas", "50 immortal quotes collected"),
            AchDef(75, "🌌 Sabiduría Universal", "Universal Wisdom", "75 citas guardadas en Audire", "75 quotes saved in Audire"),
            AchDef(100, "👑 Biblioteca de Citas", "Library of Quotes", "¡100 citas históricas anotadas!", "100 historical quotes noted!")
        )

        quoteTargets.forEach { item ->
            val progress = (quotesCount.toFloat() / item.target.toFloat()).coerceIn(0f, 1f)
            val unlocked = quotesCount >= item.target
            achievements.add(
                Achievement(
                    id = "quote_${item.target}",
                    category = AchievementCategory.QUOTES,
                    icon = if (unlocked) "🔖" else "🔒",
                    titleEs = item.titleEs,
                    titleEn = item.titleEn,
                    descEs = item.descEs,
                    descEn = item.descEn,
                    currentProgress = progress,
                    progressLabel = "$quotesCount / ${item.target} ${if (lang == "es") "frases" else "quotes"}",
                    isUnlocked = unlocked,
                    lang = lang
                )
            )
        }

        achievements.add(
            Achievement(
                id = "quotes_multi_books_2",
                category = AchievementCategory.QUOTES,
                icon = if (distinctQuotedBooks >= 2) "🎯" else "🔒",
                titleEs = "🎯 Lector Crítico",
                titleEn = "Critical Reader",
                descEs = "Extrae citas de al menos 2 libros distintos",
                descEn = "Extract quotes from at least 2 distinct books",
                currentProgress = (distinctQuotedBooks.toFloat() / 2f).coerceIn(0f, 1f),
                progressLabel = "$distinctQuotedBooks / 2",
                isUnlocked = distinctQuotedBooks >= 2,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "quotes_multi_books_5",
                category = AchievementCategory.QUOTES,
                icon = if (distinctQuotedBooks >= 5) "🌟" else "🔒",
                titleEs = "🌟 Explorador Conceptual",
                titleEn = "Conceptual Explorer",
                descEs = "Guarda citas de 5 libros diferentes",
                descEn = "Save quotes from 5 different books",
                currentProgress = (distinctQuotedBooks.toFloat() / 5f).coerceIn(0f, 1f),
                progressLabel = "$distinctQuotedBooks / 5",
                isUnlocked = distinctQuotedBooks >= 5,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "quotes_multi_books_10",
                category = AchievementCategory.QUOTES,
                icon = if (distinctQuotedBooks >= 10) "👑" else "🔒",
                titleEs = "👑 Sabio Comparativo",
                titleEn = "Comparative Sage",
                descEs = "Citas extraídas de 10 libros diferentes",
                descEn = "Quotes extracted from 10 different books",
                currentProgress = (distinctQuotedBooks.toFloat() / 10f).coerceIn(0f, 1f),
                progressLabel = "$distinctQuotedBooks / 10",
                isUnlocked = distinctQuotedBooks >= 10,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "quotes_first_day",
                category = AchievementCategory.QUOTES,
                icon = if (quotesCount >= 1 && effectiveStreak >= 1) "🌻" else "🔒",
                titleEs = "🌻 Mente Despierta",
                titleEn = "Awakened Mind",
                descEs = "Anota una cita en tu primer día de racha",
                descEn = "Note a quote on your first streak day",
                currentProgress = if (quotesCount >= 1 && effectiveStreak >= 1) 1f else 0.5f,
                progressLabel = if (quotesCount >= 1 && effectiveStreak >= 1) "1 / 1" else "0 / 1",
                isUnlocked = quotesCount >= 1 && effectiveStreak >= 1,
                lang = lang
            )
        )

        // ----------------------------------------------------
        // 5. HÁBITOS Y RITMOS DE LECTURA (15 Logros)
        // ----------------------------------------------------
        val activeDaysCount = logs.map { it.date }.distinct().size

        achievements.add(
            Achievement(
                id = "habit_days_3",
                category = AchievementCategory.HABITS,
                icon = if (activeDaysCount >= 3) "🌱" else "🔒",
                titleEs = "🌱 Semilla Semanal",
                titleEn = "Weekly Seed",
                descEs = "Lee en al menos 3 días registrados en tu historial",
                descEn = "Read on at least 3 logged days in your history",
                currentProgress = (activeDaysCount.toFloat() / 3f).coerceIn(0f, 1f),
                progressLabel = "$activeDaysCount / 3",
                isUnlocked = activeDaysCount >= 3,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "habit_days_7",
                category = AchievementCategory.HABITS,
                icon = if (activeDaysCount >= 7) "🌿" else "🔒",
                titleEs = "🌿 Hábito Arraigado",
                titleEn = "Rooted Habit",
                descEs = "7 días de actividad registrados",
                descEn = "7 days of logged activity",
                currentProgress = (activeDaysCount.toFloat() / 7f).coerceIn(0f, 1f),
                progressLabel = "$activeDaysCount / 7",
                isUnlocked = activeDaysCount >= 7,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "habit_days_14",
                category = AchievementCategory.HABITS,
                icon = if (activeDaysCount >= 14) "🌳" else "🔒",
                titleEs = "🌳 Roble Literario",
                titleEn = "Literary Oak",
                descEs = "14 días activos de lectura en el historial",
                descEn = "14 active reading days logged",
                currentProgress = (activeDaysCount.toFloat() / 14f).coerceIn(0f, 1f),
                progressLabel = "$activeDaysCount / 14",
                isUnlocked = activeDaysCount >= 14,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "habit_days_30",
                category = AchievementCategory.HABITS,
                icon = if (activeDaysCount >= 30) "🏔️" else "🔒",
                titleEs = "🏔️ Constancia de Montaña",
                titleEn = "Mountain Constancy",
                descEs = "30 días diferentes con sesiones de lectura",
                descEn = "30 distinct days with reading sessions",
                currentProgress = (activeDaysCount.toFloat() / 30f).coerceIn(0f, 1f),
                progressLabel = "$activeDaysCount / 30",
                isUnlocked = activeDaysCount >= 30,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "habit_days_60",
                category = AchievementCategory.HABITS,
                icon = if (activeDaysCount >= 60) "🪐" else "🔒",
                titleEs = "🪐 Órbita de Hábitos",
                titleEn = "Habit Orbit",
                descEs = "60 días registrados en Audire",
                descEn = "60 logged reading days in Audire",
                currentProgress = (activeDaysCount.toFloat() / 60f).coerceIn(0f, 1f),
                progressLabel = "$activeDaysCount / 60",
                isUnlocked = activeDaysCount >= 60,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "habit_days_100",
                category = AchievementCategory.HABITS,
                icon = if (activeDaysCount >= 100) "👑" else "🔒",
                titleEs = "👑 Centenario de Días",
                titleEn = "Days Centurion",
                descEs = "100 días únicos aprendiendo y leyendo",
                descEn = "100 unique days learning and reading",
                currentProgress = (activeDaysCount.toFloat() / 100f).coerceIn(0f, 1f),
                progressLabel = "$activeDaysCount / 100",
                isUnlocked = activeDaysCount >= 100,
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "habit_marathon_30m",
                category = AchievementCategory.HABITS,
                icon = if (logs.any { it.durationMillis >= 30 * 60 * 1000L }) "⚡" else "🔒",
                titleEs = "⚡ Sesión Profunda 30m",
                titleEn = "Deep Session 30m",
                descEs = "Logra una sesión diaria de 30 minutos continuos",
                descEn = "Achieve a single 30-minute daily session",
                currentProgress = if (logs.any { it.durationMillis >= 30 * 60 * 1000L }) 1f else 0f,
                progressLabel = if (logs.any { it.durationMillis >= 30 * 60 * 1000L }) "1 / 1" else "0 / 1",
                isUnlocked = logs.any { it.durationMillis >= 30 * 60 * 1000L },
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "habit_marathon_60m",
                category = AchievementCategory.HABITS,
                icon = if (logs.any { it.durationMillis >= 60 * 60 * 1000L }) "🔥" else "🔒",
                titleEs = "🔥 Hiperenfoque 1 Hora",
                titleEn = "Hyperfocus 1 Hour",
                descEs = "Logra una sesión diaria de al menos 1 hora",
                descEn = "Achieve a single 1-hour daily session",
                currentProgress = if (logs.any { it.durationMillis >= 60 * 60 * 1000L }) 1f else 0f,
                progressLabel = if (logs.any { it.durationMillis >= 60 * 60 * 1000L }) "1 / 1" else "0 / 1",
                isUnlocked = logs.any { it.durationMillis >= 60 * 60 * 1000L },
                lang = lang
            )
        )
        achievements.add(
            Achievement(
                id = "habit_marathon_120m",
                category = AchievementCategory.HABITS,
                icon = if (logs.any { it.durationMillis >= 120 * 60 * 1000L }) "🏆" else "🔒",
                titleEs = "🏆 Maratón Maestro 2 Horas",
                titleEn = "Master Marathon 2 Hours",
                descEs = "Sesión diaria récord de 2 horas o más",
                descEn = "Record daily session of 2 hours or more",
                currentProgress = if (logs.any { it.durationMillis >= 120 * 60 * 1000L }) 1f else 0f,
                progressLabel = if (logs.any { it.durationMillis >= 120 * 60 * 1000L }) "1 / 1" else "0 / 1",
                isUnlocked = logs.any { it.durationMillis >= 120 * 60 * 1000L },
                lang = lang
            )
        )

        // ----------------------------------------------------
        // 6. MAESTRÍA Y EXPLORACIÓN (15 Logros)
        // ----------------------------------------------------
        val masterDefs = listOf(
            AchDef(1, "🔍 Primer Descubrimiento", "First Discovery", "Inicia tu viaje de exploración en Audire", "Start your journey in Audire"),
            AchDef(2, "⚙️ Lector Personalizado", "Customized Reader", "Usa temas y configuraciones de lectura", "Use themes and custom reader styles"),
            AchDef(3, "💫 Coleccionista Experto", "Expert Collector", "Combina 3 o más libros en biblioteca", "Combine 3 or more books in library"),
            AchDef(5, "🚀 Sabio del Conocimiento", "Sage of Knowledge", "Supera 5 horas de lectura total", "Surpass 5 total reading hours"),
            AchDef(7, "👑 Gran Maestro Audire", "Audire Grandmaster", "Desbloquea una racha de 7 días y 5 libros", "Unlock a 7-day streak and 5 books")
        )

        masterDefs.forEach { item ->
            val isUnlocked = when (item.target) {
                1 -> allBooks.isNotEmpty() || totalMinutes > 0
                2 -> allBooks.isNotEmpty()
                3 -> allBooks.size >= 3
                5 -> totalHours >= 5f
                7 -> effectiveStreak >= 7 && allBooks.size >= 5
                else -> false
            }
            achievements.add(
                Achievement(
                    id = "mastery_${item.target}",
                    category = AchievementCategory.MASTERY,
                    icon = if (isUnlocked) "👑" else "🔒",
                    titleEs = item.titleEs,
                    titleEn = item.titleEn,
                    descEs = item.descEs,
                    descEn = item.descEn,
                    currentProgress = if (isUnlocked) 1f else 0.5f,
                    progressLabel = if (isUnlocked) "1 / 1" else "0 / 1",
                    isUnlocked = isUnlocked,
                    lang = lang
                )
            )
        }

        return achievements
    }
}
