package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.Audiobook
import com.example.data.ScanDirectory
import com.example.data.ListeningLog
import com.example.data.BookQuote
import com.example.data.Achievement
import com.example.data.AchievementCategory
import com.example.data.AchievementManager
import com.example.data.ThumbnailManager
import com.example.data.FolderHierarchyBuilder
import com.example.data.FolderNode
import com.example.ui.viewmodel.LibraryViewMode
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Primary
import com.example.ui.viewmodel.AudiobookViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AudiobookViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val postNotificationPermission = android.Manifest.permission.POST_NOTIFICATIONS
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    postNotificationPermission
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(postNotificationPermission),
                    101
                )
            }
        }

        setContent {
            val themeMode by viewModel.selectedThemeMode.collectAsStateWithLifecycle()
            val customPrimaryHex by viewModel.customPrimaryHex.collectAsStateWithLifecycle()
            val customBgHex by viewModel.customBgHex.collectAsStateWithLifecycle()
            val customSecondaryHex by viewModel.customSecondaryHex.collectAsStateWithLifecycle()

            MyApplicationTheme(
                themeMode = themeMode,
                customPrimaryHex = customPrimaryHex,
                customBgHex = customBgHex,
                customSecondaryHex = customSecondaryHex
            ) {
                MainScreen(viewModel = viewModel)
            }
        }

        // Handle incoming external files when launched
        handleIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent != null && intent.action == android.content.Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                viewModel.openExternalBookUri(this, uri) {
                    viewModel.setCurrentTab("Player")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkStoragePermission()
        viewModel.onActivityResumed()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onActivityPaused()
    }

    private fun checkStoragePermission() {
        val permissionToRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            permissionToRequest
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        viewModel.storagePermissionGranted.value = isGranted
    }
}

@Composable
fun ForegroundServiceStatusBar(dbLatency: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 12.dp, start = 24.dp, end = 24.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E6B12).copy(alpha = alpha))
            )
            Text(
                text = "Foreground Service Active",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
        
        Text(
            text = String.format("0.00%dms L/W Sync", dbLatency.coerceAtLeast(1)),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun MainScreen(viewModel: AudiobookViewModel) {
    val selectedTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val lastNotification by viewModel.lastSavedNotification.collectAsStateWithLifecycle()
    val dbLatency by viewModel.dbLatency.collectAsStateWithLifecycle()
    val scanDirectories by viewModel.scanDirectories.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val sharedPrefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    val isOnboardingCompleted = remember { sharedPrefs.getBoolean("onboarding_completed", false) }
    var showOnboardingDialog by remember { mutableStateOf(!isOnboardingCompleted) }

    val onboardingDirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error taking persistable permission: ${e.message}")
            }
            viewModel.addScanDirectory(uri.toString())
            sharedPrefs.edit().putBoolean("onboarding_completed", true).apply()
            showOnboardingDialog = false
        }
    }

    if (showOnboardingDialog) {
        AlertDialog(
            onDismissRequest = { /* No dismiss on tap outside */ },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Configuración Inicial",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "¡Bienvenido a Audire!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Para comenzar, por favor selecciona la carpeta donde guardas tus audiolibros. Audire solo escaneará y reproducirá los archivos de audio contenidos en esta carpeta seleccionada para proteger tu privacidad.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onboardingDirPickerLauncher.launch(null)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_select_dir_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Seleccionar carpeta")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        sharedPrefs.edit().putBoolean("onboarding_completed", true).apply()
                        showOnboardingDialog = false
                    }
                ) {
                    Text("Omitir por ahora")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp)
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (selectedTab != "Reader") {
                Column {
                    // Pulse foreground service log info has been removed per user request

                    // Scanning and index status bar
                    AnimatedVisibility(
                        visible = isScanning,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        ScanningStatusBar()
                    }
                    
                    TopAppBarView(
                        selectedTab = selectedTab,
                        onSearchClick = {
                            // Triggers automated storage index scan
                            viewModel.scanDeviceStorage()
                        }
                    )
                }
            }
        },
        bottomBar = {
            if (selectedTab != "Reader") {
                BottomNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = { viewModel.setCurrentTab(it) },
                    lang = selectedLanguage
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        BackHandler(enabled = selectedTab != "Library") {
            viewModel.setCurrentTab("Library")
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (selectedTab == "Reader") PaddingValues(0.dp) else innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                "Library" -> LibraryScreen(
                    viewModel = viewModel,
                    onNavigateToPlayer = { viewModel.setCurrentTab("Player") },
                    onNavigateToReader = { viewModel.setCurrentTab("Reader") }
                )
                "Reader" -> ReaderScreen(
                    viewModel = viewModel,
                    onNavigateBack = { viewModel.setCurrentTab("Library") }
                )
                "Player" -> PlayerScreen(
                    viewModel = viewModel,
                    onNavigateToReader = { viewModel.setCurrentTab("Reader") }
                )
                "Stats" -> StatsScreen(
                    viewModel = viewModel
                )
                "Quotes" -> QuotesScreen(
                    viewModel = viewModel
                )
                "Settings" -> SettingsScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun ScanningStatusBar() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Escaneando almacenamiento...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(2.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun TopAppBarView(
    selectedTab: String,
    onSearchClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Audire",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

data class TabInfo(
    val id: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val testTag: String
)

@Composable
fun BottomNavigationBar(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    lang: String
) {
    // List of tabs, with Library in the center (index 2)
    val tabs = remember {
        listOf(
            TabInfo("Player", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle, "tab_player"),
            TabInfo("Stats", Icons.Filled.TrendingUp, Icons.Outlined.TrendingUp, "tab_stats"),
            TabInfo("Library", Icons.Filled.AutoStories, Icons.Outlined.AutoStories, "tab_library"),
            TabInfo("Quotes", Icons.Filled.FormatQuote, Icons.Outlined.FormatQuote, "tab_quotes"),
            TabInfo("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "tab_settings")
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = selectedTab == tab.id
                
                // Spring animations for size and transition
                val iconSize by animateDpAsState(
                    targetValue = if (isSelected) 28.dp else 23.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "iconSize"
                )
                
                val pillAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "pillAlpha"
                )

                val pillScaleX by animateFloatAsState(
                    targetValue = if (isSelected) 1.1f else 0.8f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "pillScaleX"
                )

                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 56.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onTabSelected(tab.id) }
                        .testTag(tab.testTag),
                    contentAlignment = Alignment.Center
                ) {
                    // Pill background for selected item
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(width = 54.dp, height = 38.dp)
                            .graphicsLayer(
                                alpha = pillAlpha,
                                scaleX = pillScaleX
                            )
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(18.dp)
                            )
                    )
                    
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.id,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// VISTA 1: biblioteca (LIBRARY SCREEN)
// -------------------------------------------------------------
@Composable
fun BookCoverImage(
    book: Audiobook,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    var resolvedCoverPath by remember(book.id, book.coverUrl) { mutableStateOf(book.coverUrl) }

    LaunchedEffect(book.id, book.coverUrl, book.filePath) {
        if (resolvedCoverPath.isEmpty() || (!resolvedCoverPath.startsWith("http") && !resolvedCoverPath.startsWith("content://") && !java.io.File(resolvedCoverPath).exists())) {
            withContext(Dispatchers.IO) {
                val path = ThumbnailManager.getOrCreateThumbnail(context, book)
                if (path.isNotEmpty()) {
                    resolvedCoverPath = path
                }
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (resolvedCoverPath.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(if (resolvedCoverPath.startsWith("http") || resolvedCoverPath.startsWith("content://")) resolvedCoverPath else java.io.File(resolvedCoverPath))
                    .crossfade(true)
                    .build(),
                contentDescription = "Portada de ${book.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            // Procedural fallback cover
            val initial = book.title.take(1).uppercase()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = book.title.take(20),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun BookstoreGridCard(
    book: Audiobook,
    onClick: () -> Unit,
    onChooseImage: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val progressFraction = if (book.durationMillis > 0) {
        (book.currentPositionMillis.toFloat() / book.durationMillis).coerceIn(0f, 1f)
    } else 0f
    val percent = (progressFraction * 100).toInt()

    val formatLabel = when {
        book.filePath.endsWith(".pdf", true) || book.author.contains("PDF", true) -> "PDF"
        book.filePath.endsWith(".epub", true) || book.author.contains("EPUB", true) -> "EPUB"
        book.filePath.endsWith(".cbz", true) || book.filePath.endsWith(".cbr", true) -> "CÓMIC"
        else -> "AUDIO"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("bookstore_card_${book.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 3D-styled Book Cover with spine shadow & badges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
            ) {
                BookCoverImage(
                    book = book,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Realistic Book Spine 3D shadow gradient overlay on the left
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(16.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Black.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Top Format Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                // Favorite Ribbon/Star at top right
                IconButton(
                    onClick = { onToggleFavorite() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = if (book.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorito",
                        tint = if (book.isFavorite) Color(0xFFFFD700) else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Progress Bar and Pill at the bottom of the cover
                if (book.currentPositionMillis > 0 || book.isCompleted) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (book.isCompleted) "✓ Leído" else "$percent%",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = if (book.isCompleted) MaterialTheme.colorScheme.primaryContainer else Color.White
                            )
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp)
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            // Info section below cover
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onChooseImage() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Cambiar Portada",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = { onDelete() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PhysicalFolderExplorerView(
    books: List<Audiobook>,
    currentPath: String,
    selectedLanguage: String,
    onNavigateFolder: (String) -> Unit,
    onNavigateFolderUp: () -> Unit,
    onBookClick: (Audiobook) -> Unit,
    onChooseImage: (Audiobook) -> Unit,
    onToggleFavorite: (Audiobook) -> Unit,
    onDeleteBook: (Audiobook) -> Unit
) {
    val context = LocalContext.current
    val rootNode = remember(books, selectedLanguage) {
        FolderHierarchyBuilder.buildHierarchy(books, context, selectedLanguage)
    }
    val currentNode = remember(rootNode, currentPath) {
        FolderHierarchyBuilder.findNode(rootNode, currentPath)
    }
    val breadcrumbs = remember(rootNode, currentPath) {
        FolderHierarchyBuilder.getBreadcrumbs(rootNode, currentPath)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. Breadcrumbs Bar & Sync Action Banner
        item(key = "breadcrumbs_header") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Breadcrumbs scrollable row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        breadcrumbs.forEachIndexed { index, node ->
                            Text(
                                text = if (index == 0) "📁 ${node.name}" else "> ${node.name}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (index == breadcrumbs.size - 1) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (index == breadcrumbs.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onNavigateFolder(node.path) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentPath != "root") {
                            FilledTonalButton(
                                onClick = onNavigateFolderUp,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (selectedLanguage == "es") "Subir nivel" else "Go up", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Text(
                                text = if (selectedLanguage == "es") "Explorador de almacenamiento" else "Storage explorer",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = if (selectedLanguage == "es") "${currentNode.totalBooksCount} elementos" else "${currentNode.totalBooksCount} items",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        // 2. Subfolders inside current directory
        if (currentNode.subFolders.isNotEmpty()) {
            item(key = "subfolders_title") {
                Text(
                    text = if (selectedLanguage == "es") "CARPETAS FÍSICAS (${currentNode.subFolders.size})" else "PHYSICAL FOLDERS (${currentNode.subFolders.size})",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(currentNode.subFolders, key = { "sub_${it.path}" }) { subNode ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateFolder(subNode.path) }
                        .testTag("folder_item_${subNode.name}"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = subNode.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (selectedLanguage == "es") {
                                        "${subNode.subFolders.size} carpetas • ${subNode.totalBooksCount} libros"
                                    } else {
                                        "${subNode.subFolders.size} folders • ${subNode.totalBooksCount} books"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 3. Books inside current directory
        if (currentNode.books.isNotEmpty()) {
            item(key = "books_title") {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (selectedLanguage == "es") "ARCHIVOS EN ESTA CARPETA (${currentNode.books.size})" else "FILES IN THIS FOLDER (${currentNode.books.size})",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(currentNode.books, key = { "folder_book_${it.id}" }) { book ->
                AudiobookCard(
                    book = book,
                    onClick = { onBookClick(book) },
                    onChooseImage = { onChooseImage(book) },
                    onToggleFavorite = { onToggleFavorite(book) },
                    onDelete = { onDeleteBook(book) }
                )
            }
        }

        // Empty directory state
        if (currentNode.subFolders.isEmpty() && currentNode.books.isEmpty()) {
            item(key = "empty_folder") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedLanguage == "es") "No hay elementos en esta carpeta" else "No items in this folder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    viewModel: AudiobookViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToReader: () -> Unit
) {
    val books by viewModel.filteredAudiobooksFlow.collectAsStateWithLifecycle()
    val filter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val typeFilter by viewModel.selectedTypeFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val libraryViewMode by viewModel.libraryViewMode.collectAsStateWithLifecycle()
    val folderCurrentPath by viewModel.folderCurrentPath.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val storagePerm by viewModel.storagePermissionGranted.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val isQuoteDismissed by viewModel.isQuoteDismissed.collectAsStateWithLifecycle()

    val permissionToRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val localDirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error taking persistable permission: ${e.message}")
            }
            viewModel.addScanDirectory(uri.toString())
            viewModel.scanDeviceStorage()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        viewModel.storagePermissionGranted.value = isGranted
        if (isGranted) {
            Toast.makeText(context, "Permiso concedido. Selecciona tu carpeta.", Toast.LENGTH_SHORT).show()
            localDirPickerLauncher.launch(null)
        } else {
            Toast.makeText(context, "Permiso denegado.", Toast.LENGTH_SHORT).show()
        }
    }

    var selectedBookForCoverUpdate by remember { mutableStateOf<Int?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        val bookId = selectedBookForCoverUpdate
        if (uri != null && bookId != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // ignore
            }
            viewModel.updateBookCover(bookId, uri.toString())
        }
    }

    var bookToDelete by remember { mutableStateOf<Audiobook?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (selectedLanguage == "es") "Biblioteca" else "Library",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            // Segmented 3-Way View Switcher: Bookstore / List / Folder
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(3.dp)
            ) {
                // 1. Estantería (Bookstore Grid)
                IconButton(
                    onClick = { viewModel.setLibraryViewMode(LibraryViewMode.BOOKSTORE) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (libraryViewMode == LibraryViewMode.BOOKSTORE) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .testTag("view_mode_bookstore")
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = "Estantería",
                        tint = if (libraryViewMode == LibraryViewMode.BOOKSTORE) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 2. Lista (Detailed List)
                IconButton(
                    onClick = { viewModel.setLibraryViewMode(LibraryViewMode.LIST) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (libraryViewMode == LibraryViewMode.LIST) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .testTag("view_mode_list")
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewList,
                        contentDescription = "Lista",
                        tint = if (libraryViewMode == LibraryViewMode.LIST) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 3. Carpetas Físicas (Folder Tree)
                IconButton(
                    onClick = { viewModel.setLibraryViewMode(LibraryViewMode.FOLDER) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (libraryViewMode == LibraryViewMode.FOLDER) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .testTag("view_mode_folder")
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Carpetas",
                        tint = if (libraryViewMode == LibraryViewMode.FOLDER) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        
        // Show the motivational Quote of the Day if not dismissed
        if (!isQuoteDismissed) {
            DailyQuoteCard(
                selectedLanguage = selectedLanguage,
                onDismiss = { viewModel.dismissQuoteToday() }
            )
        }
        
        Spacer(modifier = Modifier.height(6.dp))

        // Fully functional search input
        val labelSearchPlaceHolder = if (selectedLanguage == "es") "Buscar libros o títulos..." else "Search books or titles..."
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text(labelSearchPlaceHolder) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search icon",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        val statusFavorites = if (selectedLanguage == "es") "Favoritos" else "Favorites"
        val statusAll = if (selectedLanguage == "es") "Todos" else "All"
        val statusInProgress = if (selectedLanguage == "es") "En progreso" else "In Progress"
        val statusCompleted = if (selectedLanguage == "es") "Completados" else "Completed"

        val typeAll = if (selectedLanguage == "es") "Todos los formatos" else "All formats"
        val typeAudio = if (selectedLanguage == "es") "Audiolibros" else "Audiobooks"
        val typePdf = if (selectedLanguage == "es") "Libros (PDF)" else "Books (PDF)"
        val typeEpub = if (selectedLanguage == "es") "E-books (EPUB)" else "E-books (EPUB)"
        val typeComics = if (selectedLanguage == "es") "Cómics / Manhwa" else "Comics / Manhwa"

        // Status filters title and row
        Text(
            text = if (selectedLanguage == "es") "POR ESTADO de lectura" else "BY STATE of reading",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterPill(title = statusFavorites, selected = filter == "Favorites", onClick = { viewModel.setFilter("Favorites") })
            FilterPill(title = statusAll, selected = filter == "All", onClick = { viewModel.setFilter("All") })
            FilterPill(title = statusInProgress, selected = filter == "In Progress", onClick = { viewModel.setFilter("In Progress") })
            FilterPill(title = statusCompleted, selected = filter == "Completed", onClick = { viewModel.setFilter("Completed") })
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Format filters title and row
        Text(
            text = if (selectedLanguage == "es") "POR FORMATO o tipo" else "BY FORMAT or type",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterPill(title = typeAll, selected = typeFilter == "All", onClick = { viewModel.setTypeFilter("All") })
            FilterPill(title = typeAudio, selected = typeFilter == "Audiobooks", onClick = { viewModel.setTypeFilter("Audiobooks") })
            FilterPill(title = typePdf, selected = typeFilter == "PDFs", onClick = { viewModel.setTypeFilter("PDFs") })
            FilterPill(title = typeEpub, selected = typeFilter == "EPUBs", onClick = { viewModel.setTypeFilter("EPUBs") })
            FilterPill(title = typeComics, selected = typeFilter == "Comics", onClick = { viewModel.setTypeFilter("Comics") })
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        if (books.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (selectedLanguage == "es") "Tu biblioteca está vacía" else "Your library is empty",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (selectedLanguage == "es") {
                        "No se encontraron audiolibros o libros en tu dispositivo. Selecciona una carpeta para escanearlos o sincronizar metadatos."
                    } else {
                        "No audiobooks or books found on your device. Select a folder to scan them or sync metadata."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (libraryViewMode) {
                    LibraryViewMode.BOOKSTORE -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 145.dp),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 28.dp)
                        ) {
                            items(books, key = { "grid_${it.id}" }) { book ->
                                BookstoreGridCard(
                                    book = book,
                                    onClick = {
                                        viewModel.selectBook(book)
                                        if (viewModel.isDocumentFile(book)) {
                                            if (viewModel.isPlaying.value) {
                                                viewModel.stopSpeak()
                                            }
                                            onNavigateToReader()
                                        } else {
                                            onNavigateToPlayer()
                                        }
                                    },
                                    onChooseImage = {
                                        selectedBookForCoverUpdate = book.id
                                        imagePickerLauncher.launch("image/*")
                                    },
                                    onToggleFavorite = {
                                        viewModel.toggleFavorite(book)
                                    },
                                    onDelete = {
                                        bookToDelete = book
                                    }
                                )
                            }
                        }
                    }

                    LibraryViewMode.LIST -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 28.dp)
                        ) {
                            items(books, key = { "list_${it.id}" }) { book ->
                                AudiobookCard(
                                    book = book,
                                    onClick = {
                                        viewModel.selectBook(book)
                                        if (viewModel.isDocumentFile(book)) {
                                            if (viewModel.isPlaying.value) {
                                                viewModel.stopSpeak()
                                            }
                                            onNavigateToReader()
                                        } else {
                                            onNavigateToPlayer()
                                        }
                                    },
                                    onChooseImage = {
                                        selectedBookForCoverUpdate = book.id
                                        imagePickerLauncher.launch("image/*")
                                    },
                                    onToggleFavorite = {
                                        viewModel.toggleFavorite(book)
                                    },
                                    onDelete = {
                                        bookToDelete = book
                                    }
                                )
                            }
                        }
                    }

                    LibraryViewMode.FOLDER -> {
                        PhysicalFolderExplorerView(
                            books = books,
                            currentPath = folderCurrentPath,
                            selectedLanguage = selectedLanguage,
                            onNavigateFolder = { viewModel.navigateFolder(it) },
                            onNavigateFolderUp = { viewModel.navigateFolderUp() },
                            onBookClick = { book ->
                                viewModel.selectBook(book)
                                if (viewModel.isDocumentFile(book)) {
                                    if (viewModel.isPlaying.value) {
                                        viewModel.stopSpeak()
                                    }
                                    onNavigateToReader()
                                } else {
                                    onNavigateToPlayer()
                                }
                            },
                            onChooseImage = { book ->
                                selectedBookForCoverUpdate = book.id
                                imagePickerLauncher.launch("image/*")
                            },
                            onToggleFavorite = { book ->
                                viewModel.toggleFavorite(book)
                            },
                            onDeleteBook = { book ->
                                bookToDelete = book
                            }
                        )
                    }
                }
            }
        }
    }

    if (bookToDelete != null) {
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = {
                Text(
                    text = LanguageManager.getString("delete_book", selectedLanguage),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = LanguageManager.getString("delete_book_confirm", selectedLanguage) + "\n\n" + (bookToDelete?.title ?: "")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        bookToDelete?.let { viewModel.deleteAudiobook(it.id) }
                        bookToDelete = null
                    }
                ) {
                    Text(
                        text = if (selectedLanguage == "es") "Eliminar" else "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text(text = if (selectedLanguage == "es") "Cancelar" else "Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun FilterPill(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            )
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudiobookCard(
    book: Audiobook,
    onClick: () -> Unit,
    onChooseImage: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val progressFraction = if (book.durationMillis > 0) {
        book.currentPositionMillis.toFloat() / book.durationMillis
    } else 0f
    
    val percentCompleted = (progressFraction * 100).toInt()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("audiobook_card_${book.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Book cover icon frame (64.dp) using BookCoverImage
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                BookCoverImage(
                    book = book,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatMillisToHMS(book.durationMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    
                    LinearProgressIndicator(
                        progress = { progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(4.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    Text(
                        text = if (book.isCompleted) "Done" else if (book.currentPositionMillis == 0L) "New" else "$percentCompleted%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (book.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Favorito Heart / Star Toggle Action
            IconButton(
                onClick = { onToggleFavorite() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (book.isFavorite) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
            ) {
                Icon(
                    imageVector = if (book.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = if (book.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Choose Image from Gallery button directly on Card for granular cover updates
            IconButton(
                onClick = { onChooseImage() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Cambiar Portada",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Delete Book Button
            IconButton(
                onClick = { onDelete() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f))
                    .testTag("delete_book_button_${book.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Borrar libro",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}


// -------------------------------------------------------------
// VISTA 2: reproductor (PLAYER SCREEN)
// -------------------------------------------------------------
@Composable
fun PlayerScreen(
    viewModel: AudiobookViewModel,
    onNavigateToReader: () -> Unit
) {
    val activeBook by viewModel.currentPlayingBook.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val positionMillis by viewModel.playbackPositionMillis.collectAsStateWithLifecycle()
    val selectedSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val autosavedText by viewModel.lastSavedNotification.collectAsStateWithLifecycle()
    val epubPages by viewModel.epubPages.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showQuickQuoteDialog by remember { mutableStateOf(false) }
    var quickQuoteText by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val book = activeBook
            if (book != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // ignore
                }
                viewModel.updateBookCover(book.id, uri.toString())
            }
        }
    }

    if (activeBook == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Selecciona un audiolibro de tu biblioteca para comenzar la escucha.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
    } else {
        val book = activeBook!!
        val isDoc = viewModel.isDocumentFile(book)
        
        val progressFraction = if (book.durationMillis > 0) {
            if (isDoc) {
                positionMillis.toFloat() / (book.durationMillis - 1).coerceAtLeast(1L).toFloat()
            } else {
                positionMillis.toFloat() / book.durationMillis
            }
        } else 0f

        val elapsedFormatted = if (isDoc) "Página ${positionMillis + 1}" else formatMillisToHMSP(positionMillis)
        val remainingFormatted = if (isDoc) "${book.durationMillis} páginas" else "-" + formatMillisToHMSP((book.durationMillis - positionMillis).coerceAtLeast(0L))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            if (isDoc) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .testTag("epub_pdf_redirect_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "E-book",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (viewModel.selectedLanguage.value == "es") "Modo Lectura Disponible" else "Reading Mode Available",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (viewModel.selectedLanguage.value == "es") 
                                "Este archivo (.epub o .pdf) es un documento de texto. Disfruta de una experiencia de lectura fluida con opciones de narrador de voz avanzado."
                            else 
                                "This file (.epub or .pdf) is a text document. Enjoy a distraction-free reading experience with optional high-fidelity voice narration.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onNavigateToReader,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AutoStories, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (viewModel.selectedLanguage.value == "es") "Entrar al Modo Lectura" else "Enter Reading Mode",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // Modern, Clean Minimalist Cover Artwork Concept
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFE1E4FF),
                                    Color(0xFFD1E4FF)
                                )
                            )
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .testTag("cover_artwork_frame"),
                    contentAlignment = Alignment.Center
                ) {
                    // Background subtle gradient depth matching template
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(book.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = { imagePickerLauncher.launch("image/*") },
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Elegir de la galería", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Book Titles
            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Narrated by ${book.author}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Clean Minimalist Progress auto-saved notification
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .background(Color(0xFFE1E2EC).copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = null,
                    tint = Color(0xFF2E6B12),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = autosavedText ?: "Progress auto-saved just now",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Line Timeline seek slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = elapsedFormatted,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = remainingFormatted,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Slider(
                    value = progressFraction.coerceIn(0f, 1f),
                    onValueChange = { viewModel.seekToFraction(it) },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("timeline_slider")
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Tactile control panel: custom shapes and color matches
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Speed selection button
                IconButton(
                    onClick = { viewModel.cyclePlaybackSpeed() },
                    modifier = Modifier.testTag("speed_button")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ShutterSpeed,
                            contentDescription = "Velocidad de reproducción",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${selectedSpeed}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Skip backward container (rounded-full bg-[#F0F0F7])
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF0F0F7))
                        .clickable { viewModel.skipBackward() }
                        .testTag("skip_backward_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay30,
                        contentDescription = "Retroceder 30 segundos",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Central Play/Pause button with 28dp corners (bg-[#D1E4FF] text-[#001D36])
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { viewModel.togglePlayPause() }
                        .testTag("play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                // Skip forward container (rounded-full bg-[#F0F0F7])
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF0F0F7))
                        .clickable { viewModel.skipForward() }
                        .testTag("skip_forward_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward30,
                        contentDescription = "Adelantar 30 segundos",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Sleep Timer icon
                IconButton(onClick = {}) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Temporizador de apagado",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Off",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Secondary buttons (Chapters, Bookmarks)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatListBulleted,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Chapters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Bookmarks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            showQuickQuoteDialog = true
                            quickQuoteText = ""
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("quick_quote_trigger_player"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = "Anotar frase",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (viewModel.selectedLanguage.value == "es") "Frase" else "Quote",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            if (showQuickQuoteDialog) {
                val pageReference = if (isDoc) {
                    if (viewModel.selectedLanguage.value == "es") "Página ${positionMillis + 1}" else "Page ${positionMillis + 1}"
                } else {
                    formatMillisToHMSP(positionMillis)
                }
                AlertDialog(
                    onDismissRequest = { showQuickQuoteDialog = false },
                    title = {
                        Text(
                            text = if (viewModel.selectedLanguage.value == "es") "Anotar Frase de este Libro" else "Save Quote from this Book",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${if (viewModel.selectedLanguage.value == "es") "Referencia:" else "Reference:"} $pageReference",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = quickQuoteText,
                                onValueChange = { quickQuoteText = it },
                                placeholder = { Text(LanguageManager.getString("quote_hint", viewModel.selectedLanguage.value)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (quickQuoteText.isNotBlank()) {
                                    viewModel.insertBookQuote(
                                        bookId = book.id,
                                        bookTitle = book.title,
                                        text = quickQuoteText,
                                        page = pageReference
                                    )
                                    showQuickQuoteDialog = false
                                }
                            },
                            enabled = quickQuoteText.isNotBlank()
                        ) {
                            Text(
                                text = if (viewModel.selectedLanguage.value == "es") "Guardar" else "Save",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showQuickQuoteDialog = false }) {
                            Text(text = if (viewModel.selectedLanguage.value == "es") "Cancelar" else "Cancel")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(24.dp)
                )
            }
        }
    }
}


// -------------------------------------------------------------
// VISTA 5: FRASES Y ANOTACIONES (QUOTES SCREEN)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuotesScreen(
    viewModel: AudiobookViewModel
) {
    val quotes by viewModel.bookQuotes.collectAsStateWithLifecycle()
    val books by viewModel.audiobooks.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()

    var selectedSubTab by remember { mutableStateOf(0) } // 0 = Mis Citas, 1 = Inspiración Diaria

    var showAddDialog by remember { mutableStateOf(false) }
    var quoteText by remember { mutableStateOf("") }
    var pageRef by remember { mutableStateOf("") }
    var selectedBookIndex by remember { mutableStateOf(0) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val calendar = remember { java.util.Calendar.getInstance() }
    val dayOfYear = remember { calendar.get(java.util.Calendar.DAY_OF_YEAR) }

    fun getFormattedDateForDay(day: Int, lang: String): String {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_YEAR, day)
        val format = if (lang == "es") {
            java.text.SimpleDateFormat("dd 'de' MMMM", java.util.Locale("es"))
        } else {
            java.text.SimpleDateFormat("MMMM dd", java.util.Locale.US)
        }
        return format.format(cal.time)
    }

    val pastQuotes = remember(dayOfYear) {
        (dayOfYear downTo 1).map { day ->
            val index = day % com.example.data.dailyQuotesList300.size
            val quote = com.example.data.dailyQuotesList300[index]
            day to quote
        }
    }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = LanguageManager.getString("quotes_title", selectedLanguage),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            
            // Add quote button (only visible when on "Mis Citas" tab)
            if (selectedSubTab == 0) {
                IconButton(
                    onClick = {
                        showAddDialog = true
                        quoteText = ""
                        pageRef = ""
                        if (books.isNotEmpty()) selectedBookIndex = 0
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .testTag("add_quote_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Añadir frase",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        // Material 3 Tabs to switch between User-Added Quotes and Daily Suggested Quotes
        val tabTitle1 = if (selectedLanguage == "es") "Mis Citas" else "My Quotes"
        val tabTitle2 = if (selectedLanguage == "es") "Inspiración Diaria" else "Daily Inspiration"

        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text(tabTitle1, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text(tabTitle2, fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedSubTab == 0) {
            // TAB 0: MIS CITAS GUARDADAS
            if (quotes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = LanguageManager.getString("quotes_empty", selectedLanguage),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(quotes, key = { it.id }) { quote ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quote_card_${quote.id}"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = quote.bookTitle,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (quote.pageReference.isNotEmpty()) {
                                            Text(
                                                text = quote.pageReference,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Share button for user quote
                                        IconButton(
                                            onClick = {
                                                val textToShare = "\"${quote.quoteText}\" — ${quote.bookTitle} ${quote.pageReference}\n\nCompartido desde Audire 📚"
                                                val sendIntent = android.content.Intent().apply {
                                                    action = android.content.Intent.ACTION_SEND
                                                    putExtra(android.content.Intent.EXTRA_TEXT, textToShare)
                                                    type = "text/plain"
                                                }
                                                val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                                                context.startActivity(shareIntent)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Compartir",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { viewModel.deleteBookQuote(quote.id) },
                                            modifier = Modifier.size(32.dp).testTag("delete_quote_${quote.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Borrar frase",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(
                                            imageVector = Icons.Default.FormatQuote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                            modifier = Modifier.size(24.dp).rotate(180f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = quote.quoteText,
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontStyle = FontStyle.Italic,
                                                lineHeight = 22.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // TAB 1: INSPIRACIÓN DIARIA & PREVIOUS DAYS HISTORY
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Text(
                        text = if (selectedLanguage == "es") "Frase de Hoy" else "Today's Quote",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    // This card is always accessible here, without a close button, so they can read/share today's quote!
                    DailyQuoteCard(selectedLanguage = selectedLanguage, onDismiss = null)
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = if (selectedLanguage == "es") "Historial de Sugerencias" else "Suggestions History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(pastQuotes) { (day, quote) ->
                    val dateStr = getFormattedDateForDay(day, selectedLanguage)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                IconButton(
                                    onClick = {
                                        val textToShare = if (selectedLanguage == "es") {
                                            "«${quote.textEs}» — ${quote.authorEs}\n\nCompartido desde Audire 📚"
                                        } else {
                                            "\"${quote.textEn}\" — ${quote.authorEn}\n\nShared from Audire 📚"
                                        }
                                        val sendIntent = android.content.Intent().apply {
                                            action = android.content.Intent.ACTION_SEND
                                            putExtra(android.content.Intent.EXTRA_TEXT, textToShare)
                                            type = "text/plain"
                                        }
                                        val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                                        context.startActivity(shareIntent)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (selectedLanguage == "es") quote.textEs else quote.textEn,
                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (selectedLanguage == "es") "— ${quote.authorEs}" else "— ${quote.authorEn}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.End),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = if (selectedLanguage == "es") "Añadir Frase o Anotación" else "Add Quote or Annotation",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (books.isNotEmpty()) {
                        Text(
                            text = if (selectedLanguage == "es") "Selecciona el Libro:" else "Select Book:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Box {
                            OutlinedButton(
                                onClick = { dropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = books.getOrNull(selectedBookIndex)?.title ?: "Selecciona un libro",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                books.forEachIndexed { index, b ->
                                    DropdownMenuItem(
                                        text = { Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        onClick = {
                                            selectedBookIndex = index
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = if (selectedLanguage == "es") "No tienes libros en tu biblioteca." else "No books in your library.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    OutlinedTextField(
                        value = quoteText,
                        onValueChange = { quoteText = it },
                        label = { Text(LanguageManager.getString("quote_hint", selectedLanguage)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = pageRef,
                        onValueChange = { pageRef = it },
                        label = { Text(LanguageManager.getString("page_hint", selectedLanguage)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (quoteText.isNotBlank()) {
                            val selectedBook = books.getOrNull(selectedBookIndex)
                            viewModel.insertBookQuote(
                                bookId = selectedBook?.id ?: 0,
                                bookTitle = selectedBook?.title ?: "Desconocido",
                                text = quoteText,
                                page = pageRef
                            )
                            showAddDialog = false
                        }
                    },
                    enabled = quoteText.isNotBlank() && books.isNotEmpty()
                ) {
                    Text(
                        text = if (selectedLanguage == "es") "Guardar" else "Save",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(text = if (selectedLanguage == "es") "Cancelar" else "Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}


// -------------------------------------------------------------
// VISTA 3: AJUSTES Y DIAGNÓSTICO (SETTINGS SCREEN MODERNIZADA)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AudiobookViewModel
) {
    val context = LocalContext.current
    var inputDirPath by remember { mutableStateOf("") }
    
    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error taking persistable permission: ${e.message}")
            }
            viewModel.addScanDirectory(uri.toString())
        }
    }
    
    val directories by viewModel.scanDirectories.collectAsStateWithLifecycle()
    val storagePerm by viewModel.storagePermissionGranted.collectAsStateWithLifecycle()
    val bgPlay by viewModel.backgroundPlaybackEnabled.collectAsStateWithLifecycle()
    val syncInterval by viewModel.autoSaveIntervalSeconds.collectAsStateWithLifecycle()
    
    // Customization states
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val themeMode by viewModel.selectedThemeMode.collectAsStateWithLifecycle()
    val customPrimaryColor by viewModel.customPrimaryHex.collectAsStateWithLifecycle()
    val customBgColor by viewModel.customBgHex.collectAsStateWithLifecycle()
    val customSecondaryColor by viewModel.customSecondaryHex.collectAsStateWithLifecycle()

    // Diagnostics flows
    val dbLatency by viewModel.dbLatency.collectAsStateWithLifecycle()
    val bufferHealth by viewModel.bufferHealth.collectAsStateWithLifecycle()
    val cacheUsage by viewModel.cacheUsage.collectAsStateWithLifecycle()

    // Voice calibration flows
    val naturalness by viewModel.piperNaturalness.collectAsStateWithLifecycle()
    val expressiveness by viewModel.piperExpressiveness.collectAsStateWithLifecycle()

    val permissionToRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        viewModel.storagePermissionGranted.value = isGranted
        if (isGranted) {
            Toast.makeText(context, if (selectedLanguage == "es") "Permiso concedido. Escaneando almacenamiento..." else "Permission granted. Scanning storage...", Toast.LENGTH_SHORT).show()
            viewModel.scanDeviceStorage()
        } else {
            Toast.makeText(context, if (selectedLanguage == "es") "Permiso denegado" else "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var exportedJsonString by remember { mutableStateOf("") }
    var importJsonInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    var activeCategoryFilter by rememberSaveable { mutableStateOf(0) } // 0: Todas, 1: Biblioteca, 2: Tema, 3: Voz/Audio, 4: Sistema

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.45f
    val sectionCardBg = if (isLight) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // --- HEADER PRINCIPAL MODERNO ---
        Column {
            Text(
                text = LanguageManager.getString("settings_title", selectedLanguage),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = LanguageManager.getString("settings_subtitle", selectedLanguage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --- CATEGORY FILTER CHIPS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                0 to (if (selectedLanguage == "es") "Todo" else "All"),
                1 to (if (selectedLanguage == "es") "Biblioteca" else "Library"),
                2 to (if (selectedLanguage == "es") "Apariencia" else "Appearance"),
                3 to (if (selectedLanguage == "es") "Voz y Audio" else "Voice & Audio"),
                4 to (if (selectedLanguage == "es") "Sistema" else "System")
            ).forEach { (idx, label) ->
                val isSelected = activeCategoryFilter == idx
                FilterChip(
                    selected = isSelected,
                    onClick = { activeCategoryFilter = idx },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // ==================== SECCIÓN 1: ALMACENAMIENTO Y BIBLIOTECA ====================
        if (activeCategoryFilter == 0 || activeCategoryFilter == 1) {
            Card(
                colors = CardDefaults.cardColors(containerColor = sectionCardBg),
                border = borderStroke(),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().testTag("settings_storage_section")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Section title row with icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = LanguageManager.getString("settings_cat_storage", selectedLanguage),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (selectedLanguage == "es") "Indexación y carpetas de medios locales" else "Indexing and local media directories",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Storage Permission Switch Card
                    PermissionToggleCard(
                        title = LanguageManager.getString("local_storage", selectedLanguage),
                        subtitle = LanguageManager.getString("local_storage_desc", selectedLanguage),
                        checked = storagePerm,
                        onCheckedChange = { checked ->
                            if (checked) {
                                val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    permissionToRequest
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (isGranted) {
                                    viewModel.storagePermissionGranted.value = true
                                    viewModel.scanDeviceStorage()
                                } else {
                                    permissionLauncher.launch(permissionToRequest)
                                }
                            } else {
                                viewModel.storagePermissionGranted.value = false
                            }
                        }
                    )

                    // Botón de Escanear Archivos Ahora
                    Button(
                        onClick = {
                            viewModel.scanDeviceStorage()
                            Toast.makeText(
                                context,
                                if (selectedLanguage == "es") "Buscando audiolibros, epubs y pdfs..." else "Scanning for audiobooks, epubs and pdfs...",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_rescan_button")
                    ) {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = LanguageManager.getString("settings_rescan_btn", selectedLanguage),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Scan Directories List Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = LanguageManager.getString("scan_dirs", selectedLanguage),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        TextButton(
                            onClick = { dirPickerLauncher.launch(null) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = LanguageManager.getString("add_dir", selectedLanguage),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Directory Items Container
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    ) {
                        if (directories.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = LanguageManager.getString("no_dirs", selectedLanguage),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            directories.forEachIndexed { index, dir ->
                                if (index > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                }
                                DirectoryItemRow(
                                    directory = dir,
                                    onDeleteClick = { viewModel.removeScanDirectory(dir.path) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==================== SECCIÓN 2: APARIENCIA Y TEMAS ====================
        if (activeCategoryFilter == 0 || activeCategoryFilter == 2) {
            Card(
                colors = CardDefaults.cardColors(containerColor = sectionCardBg),
                border = borderStroke(),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().testTag("settings_appearance_section")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Section title row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF9C27B0).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = Color(0xFF9C27B0),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = LanguageManager.getString("settings_cat_appearance", selectedLanguage),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (selectedLanguage == "es") "Idioma, paletas dinámicas y contrastes" else "Language, dynamic palettes, and contrasts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // 1. Selector de Idioma
                    Text(
                        text = LanguageManager.getString("lang_label", selectedLanguage).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            "es" to "Español (ES)",
                            "en" to "English (EN)"
                        ).forEach { (code, label) ->
                            val isSelected = selectedLanguage == code
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest)
                                    .border(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.selectedLanguage.value = code }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 2. Selector de Tema Visual
                    Text(
                        text = LanguageManager.getString("theme_mode_label", selectedLanguage).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )

                    val themes = listOf(
                        Triple("dark", "theme_dark", Triple("#0061A4", "#111318", "#43474E")),
                        Triple("light", "theme_light", Triple("#0061A4", "#FDFBFF", "#43474E")),
                        Triple("preset_peach", "theme_peach", Triple("#FA5B4A", "#261D1C", "#8B4F30")),
                        Triple("preset_ocean", "theme_ocean", Triple("#00ADB5", "#0D1B2A", "#1B4965")),
                        Triple("preset_emerald", "theme_emerald", Triple("#2ECC71", "#0F1E15", "#27AE60")),
                        Triple("preset_cosmic", "theme_cosmic", Triple("#9B5DE5", "#0F0C1B", "#5A189A")),
                        Triple("custom", "theme_custom", Triple("#E91E63", "#121212", "#43474E"))
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        themes.forEach { (modeId, labelKey, colHexes) ->
                            val isSelected = themeMode == modeId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerLowest)
                                    .clickable { viewModel.selectedThemeMode.value = modeId }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.selectedThemeMode.value = modeId }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = LanguageManager.getString(labelKey, selectedLanguage),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(colHexes.first))))
                                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(colHexes.second))))
                                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(colHexes.third))))
                                }
                            }
                        }
                    }

                    // Si se escoge Tema Personalizado
                    if (themeMode == "custom") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = LanguageManager.getString("theme_custom_title", selectedLanguage),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // 1) Primary color picker
                            Text(
                                text = LanguageManager.getString("theme_primary_color", selectedLanguage),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val primarySwatches = listOf(
                                "#0061A4", "#E91E63", "#9C27B0", "#673AB7", 
                                "#3F51B5", "#03A9F4", "#009688", "#4CAF50", 
                                "#FF9800", "#F44336"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                primarySwatches.forEach { hex ->
                                    val isSelected = customPrimaryColor == hex
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(hex)))
                                            .border(
                                                width = if (isSelected) 3.dp else 0.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { viewModel.customPrimaryHex.value = hex },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (Color(android.graphics.Color.parseColor(hex)).luminance() > 0.5f) Color.Black else Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // 2) Background color picker
                            Text(
                                text = LanguageManager.getString("theme_bg_color", selectedLanguage),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val bgSwatches = listOf(
                                "#000000", "#111318", "#0F0C1B", "#1A1C22",
                                "#0E1A11", "#F3F4F9", "#FAF9FF", "#FFFFFF"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                bgSwatches.forEach { hex ->
                                    val isSelected = customBgColor == hex
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(hex)))
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            )
                                            .clickable { viewModel.customBgHex.value = hex },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (Color(android.graphics.Color.parseColor(hex)).luminance() > 0.5f) Color.Black else Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // 3) Secondary color picker
                            Text(
                                text = LanguageManager.getString("theme_secondary_color", selectedLanguage),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val secondarySwatches = listOf(
                                "#43474E", "#8B7E74", "#D27D2D", "#606C38",
                                "#3B4CC4", "#E0B0FF", "#BDB2FF", "#9E9E9E"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                secondarySwatches.forEach { hex ->
                                    val isSelected = customSecondaryColor == hex
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(android.graphics.Color.parseColor(hex)))
                                            .border(
                                                width = if (isSelected) 3.dp else 0.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { viewModel.customSecondaryHex.value = hex },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (Color(android.graphics.Color.parseColor(hex)).luminance() > 0.5f) Color.Black else Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = LanguageManager.getString("theme_unlocked", selectedLanguage),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontStyle = FontStyle.Italic
                                )
                                
                                TextButton(
                                    onClick = {
                                        viewModel.customPrimaryHex.value = "#0061A4"
                                        viewModel.customBgHex.value = "#111318"
                                        viewModel.customSecondaryHex.value = "#43474E"
                                    }
                                ) {
                                    Text(
                                        text = LanguageManager.getString("theme_reset", selectedLanguage),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== SECCIÓN 3: REPRODUCCIÓN Y VOZ TTS ====================
        if (activeCategoryFilter == 0 || activeCategoryFilter == 3) {
            Card(
                colors = CardDefaults.cardColors(containerColor = sectionCardBg),
                border = borderStroke(),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().testTag("settings_playback_section")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Section title row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2ECC71).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = Color(0xFF2ECC71),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = LanguageManager.getString("settings_cat_playback", selectedLanguage),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (selectedLanguage == "es") "Audio de fondo, síntesis neuronal y sincronización" else "Background audio, neural synthesis, and sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Background playback switch
                    PermissionToggleCard(
                        title = LanguageManager.getString("bg_playback", selectedLanguage),
                        subtitle = LanguageManager.getString("bg_playback_desc", selectedLanguage),
                        checked = bgPlay,
                        onCheckedChange = { viewModel.backgroundPlaybackEnabled.value = it }
                    )

                    // Auto-save Interval Selector
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = LanguageManager.getString("sync_frequency", selectedLanguage),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = LanguageManager.getString("sync_freq_desc", selectedLanguage),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(5, 10, 30).forEach { sec ->
                                val isSelected = syncInterval == sec
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                        .clickable { viewModel.setAutoSaveInterval(sec) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${sec}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable {
                                        Toast.makeText(context, if (selectedLanguage == "es") "Sincronización manual programada" else "Manual sync scheduled", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = LanguageManager.getString("sync_manual", selectedLanguage),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Neural Voice Engine Calibration Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (selectedLanguage == "es") "Calibración de Voz Narrativa (TTS)" else "Narrative Voice Calibration (TTS)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedLanguage == "es") 
                                "Ajusta la entonación y expresividad para la narración fluida de libros (.epub y .pdf)."
                            else 
                                "Calibrate tone and expressiveness parameters for reading your e-books (.epub and .pdf).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 1) Naturalness slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (selectedLanguage == "es") "Naturalidad de Ondas" else "Wave Naturalness (noise_scale)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format("%.3f", naturalness),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = naturalness,
                                onValueChange = { viewModel.piperNaturalness.value = it },
                                valueRange = 0.4f..0.9f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            )
                        }

                        // 2) Expressiveness slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (selectedLanguage == "es") "Modulación Narrativa" else "Lyric Expressiveness (noise_w)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format("%.2f", expressiveness),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = expressiveness,
                                onValueChange = { viewModel.piperExpressiveness.value = it },
                                valueRange = 0.3f..1.2f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            )
                        }

                        // Buttons row (Test voice + Android TTS settings)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val text = if (selectedLanguage == "es") {
                                        "Hola. Has configurado correctamente los parámetros de entonación para reproducir tus libros locales sin conexión a internet."
                                    } else {
                                        "Hello. You have successfully configured your local voice wave calibration parameters to narrate offline."
                                    }
                                    viewModel.speakTest(text)
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedLanguage == "es") "Probar Voz" else "Test Voice",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = android.content.Intent("com.android.settings.TTS_SETTINGS")
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, if (selectedLanguage == "es") "Ajustes de TTS no disponibles" else "TTS settings unavailable", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedLanguage == "es") "Motor del SO" else "OS Engine",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==================== SECCIÓN 4: RESPALDO Y DIAGNÓSTICO ====================
        if (activeCategoryFilter == 0 || activeCategoryFilter == 4) {
            Card(
                colors = CardDefaults.cardColors(containerColor = sectionCardBg),
                border = borderStroke(),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().testTag("settings_diagnostics_section")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Section title row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00ADB5).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = Color(0xFF00ADB5),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = LanguageManager.getString("settings_cat_data", selectedLanguage),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (selectedLanguage == "es") "Copias de seguridad JSON y salud del motor" else "JSON backups and engine health",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Backup export & restore buttons
                    Text(
                        text = if (selectedLanguage == "es") 
                            "Exporta o restaura tu biblioteca, marcas de lectura, historial y citas en formato JSON para que nunca pierdas tu progreso." 
                        else 
                            "Export or restore your library, bookmarks, history, and quotes in JSON format so you never lose your progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    exportedJsonString = viewModel.exportBackupJson()
                                    showExportDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedLanguage == "es") "Exportar" else "Export")
                        }

                        Button(
                            onClick = {
                                importJsonInput = ""
                                showImportDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedLanguage == "es") "Restaurar" else "Restore")
                        }
                    }

                    // Diagnostic Cards Grid
                    Text(
                        text = LanguageManager.getString("diagnostics", selectedLanguage).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DiagnosticCard(
                            title = LanguageManager.getString("latency", selectedLanguage).uppercase(),
                            value = "${dbLatency}ms",
                            badgeText = "Optimized",
                            badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        DiagnosticCard(
                            title = LanguageManager.getString("buffer", selectedLanguage).uppercase(),
                            value = String.format("%.1f%%", bufferHealth),
                            badgeText = "Excellent",
                            badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DiagnosticCard(
                            title = LanguageManager.getString("cache", selectedLanguage).uppercase(),
                            value = cacheUsage,
                            badgeText = if (selectedLanguage == "es") "Limpiar" else "Clear",
                            badgeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            onBadgeClick = { 
                                viewModel.clearApplicationCache()
                                Toast.makeText(context, if (selectedLanguage == "es") "Caché liberada" else "Cache cleared", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        DiagnosticCard(
                            title = "ESTADO MOTOR",
                            value = "Audire Core",
                            badgeText = "v2.5.0-ok",
                            badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ==================== SECCIÓN 5: ZONA CRÍTICA Y PURGA ====================
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().testTag("settings_danger_section")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = LanguageManager.getString("settings_cat_danger", selectedLanguage),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = if (selectedLanguage == "es") 
                            "Elimina de forma permanente todo el historial de reproducción, marcas de tiempo y libros indexados para reiniciar completamente la aplicación." 
                        else 
                            "Permanently delete all listening history, timestamp logs, and cached libraries to start fresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = { showResetConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("reset_db_button")
                    ) {
                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedLanguage == "es") "Restablecer Base de Datos y Purga" else "Reset Database and Purge Library",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- FOOTER INFORMATIVO ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (selectedLanguage == "es") "Audire Engine: Listo" else "Audire Engine: Ready", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (selectedLanguage == "es") "Sincronización: Activa" else "Sync: Active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                text = "Audire Pro 2026.1",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // --- MODALES DE SEGURIDAD Y DIÁLOGOS ---
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            icon = { Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Text(
                    text = if (selectedLanguage == "es") "¿Restablecer Base de Datos?" else "Reset Database?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (selectedLanguage == "es") 
                        "¿Estás seguro de que deseas purgar la biblioteca y restablecer todos los registros? Esta acción es irreversible." 
                    else 
                        "Are you sure you want to purge your library and reset all records? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetDatabase()
                        showResetConfirmDialog = false
                        Toast.makeText(
                            context,
                            if (selectedLanguage == "es") "Base de datos restablecida con éxito" else "Database reset successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (selectedLanguage == "es") "Sí, restablecer" else "Yes, reset", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(if (selectedLanguage == "es") "Cancelar" else "Cancel")
                }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Audire Backup", exportedJsonString)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, if (selectedLanguage == "es") "¡Copia de seguridad copiada al portapapeles!" else "Backup copied to clipboard!", Toast.LENGTH_SHORT).show()
                        showExportDialog = false
                    }
                ) {
                    Text(if (selectedLanguage == "es") "Copiar JSON" else "Copy JSON", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(if (selectedLanguage == "es") "Cerrar" else "Close")
                }
            },
            title = {
                Text(
                    text = if (selectedLanguage == "es") "Exportar Copia de Seguridad" else "Export Backup",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (selectedLanguage == "es") "Copia este JSON o guárdalo como archivo seguro para restaurar en cualquier momento:" else "Copy this JSON or save it as a secure file to restore at any time:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = exportedJsonString,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (importJsonInput.isNotBlank()) {
                            scope.launch {
                                val (success, msg) = viewModel.importBackupJson(importJsonInput.trim())
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                if (success) {
                                    showImportDialog = false
                                }
                            }
                        }
                    },
                    enabled = importJsonInput.isNotBlank()
                ) {
                    Text(if (selectedLanguage == "es") "Restaurar Datos" else "Restore Data", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(if (selectedLanguage == "es") "Cancelar" else "Cancel")
                }
            },
            title = {
                Text(
                    text = if (selectedLanguage == "es") "Restaurar Copia de Seguridad" else "Restore Backup",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (selectedLanguage == "es") "Pega aquí el contenido JSON de una copia de seguridad previa:" else "Paste the JSON content of a previous backup below:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = { importJsonInput = it },
                        placeholder = { Text("{\"version\": 1, \"books\": [...]}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                }
            }
        )
    }
}

@Composable
fun PermissionToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = borderStroke()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun DirectoryItemRow(
    directory: ScanDirectory,
    onDeleteClick: () -> Unit
) {
    val displayName = remember(directory.path) {
        if (directory.path.startsWith("content://")) {
            try {
                val decoded = android.net.Uri.decode(directory.path)
                val lastPart = decoded.substringAfterLast("/")
                val segment = if (lastPart.contains(":")) {
                    lastPart.substringAfterLast(":")
                } else {
                    lastPart
                }
                segment.trim().ifEmpty { "Carpeta seleccionada" }
            } catch (e: Exception) {
                directory.path
            }
        } else {
            directory.path
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${directory.titlesFound} Títulos Encontrados",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        IconButton(onClick = onDeleteClick) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Remover directorio",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun DiagnosticCard(
    title: String,
    value: String,
    badgeText: String,
    badgeColor: Color,
    onBadgeClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = borderStroke()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 18.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(badgeColor, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = onBadgeClick != null) { onBadgeClick?.invoke() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun borderStroke(): androidx.compose.foundation.BorderStroke {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.45f
    val strokeColor = if (isLight) {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    return androidx.compose.foundation.BorderStroke(1.dp, strokeColor)
}

// Helper formatting functions
fun formatMillisToHMS(millis: Long): String {
    val totalSecs = millis / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

fun formatMillisToHMSP(millis: Long): String {
    val ms = millis % 1000
    val totalSecs = millis / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    if (hours > 0) {
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, ms)
    } else {
        return String.format("%02d:%02d.%03d", minutes, seconds, ms)
    }
}

// =============================================================
// SECCION DE ESTADISTICAS (STATS SCREEN)
// =============================================================

data class StreakStats(
    val currentStreak: Int,
    val maxStreak: Int,
    val totalHours: Float
)

fun calculateStreakStats(logs: List<ListeningLog>): StreakStats {
    val activeDates = logs.filter { it.durationMillis >= 10_000 } // min 10 seconds to count as listened day
        .map { it.date }
        .toSet()

    if (activeDates.isEmpty()) {
        return StreakStats(0, 0, 0f)
    }

    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    val todayCalendar = java.util.Calendar.getInstance()
    val todayStr = sdf.format(todayCalendar.time)

    val yesterdayCalendar = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    val yesterdayStr = sdf.format(yesterdayCalendar.time)

    var currentStreak = 0
    val checkCalendar = java.util.Calendar.getInstance()

    val startFromToday = activeDates.contains(todayStr)
    val startFromYesterday = activeDates.contains(yesterdayStr)

    if (startFromToday) {
        currentStreak = 1
        checkCalendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
        while (true) {
            val dateToCheck = sdf.format(checkCalendar.time)
            if (activeDates.contains(dateToCheck)) {
                currentStreak++
                checkCalendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
    } else if (startFromYesterday) {
        currentStreak = 1
        checkCalendar.apply {
            time = yesterdayCalendar.time
            add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        while (true) {
            val dateToCheck = sdf.format(checkCalendar.time)
            if (activeDates.contains(dateToCheck)) {
                currentStreak++
                checkCalendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
    }

    // Max Streak Calculation across history
    val sortedDates = activeDates.toList().sorted()
    var maxStreak = 0
    var tempStreak = 0
    var lastDate: java.util.Date? = null

    for (dateStr in sortedDates) {
        val currentDate = sdf.parse(dateStr) ?: continue
        if (lastDate == null) {
            tempStreak = 1
        } else {
            val diff = currentDate.time - lastDate.time
            val diffDays = diff / (24 * 60 * 60 * 1000)
            if (diffDays <= 1) {
                tempStreak++
            } else {
                if (tempStreak > maxStreak) {
                    maxStreak = tempStreak
                }
                tempStreak = 1
            }
        }
        lastDate = currentDate
    }
    if (tempStreak > maxStreak) {
        maxStreak = tempStreak
    }

    if (currentStreak > maxStreak) {
        maxStreak = currentStreak
    }

    val totalHours = logs.sumOf { it.durationMillis } / (1000.0 * 60 * 60)

    return StreakStats(
        currentStreak = currentStreak,
        maxStreak = maxStreak,
        totalHours = totalHours.toFloat()
    )
}

@Composable
fun ListeningBarChart(logs: List<ListeningLog>) {
    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }
    val dayFormat = remember { java.text.SimpleDateFormat("E", java.util.Locale.getDefault()) } // e.g. "lun", "mar"
    
    val daysList = remember(logs) {
        val list = mutableListOf<Triple<String, Long, Boolean>>()
        val calendar = java.util.Calendar.getInstance()
        for (i in 6 downTo 0) {
            val cloneCal = calendar.clone() as java.util.Calendar
            cloneCal.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val dateStr = sdf.format(cloneCal.time)
            val label = if (i == 0) "Hoy" else dayFormat.format(cloneCal.time).uppercase()
            val duration = logs.find { it.date == dateStr }?.durationMillis ?: 0L
            list.add(Triple(label, duration, i == 0))
        }
        list
    }

    val maxDuration = remember(daysList) {
        daysList.maxOfOrNull { it.second }?.coerceAtLeast(1000L * 60) ?: (3600000L)
    }

    val bestDay = remember(daysList) {
        daysList.maxByOrNull { it.second }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            daysList.forEach { (dayLabel, duration, isToday) ->
                val hours = duration / (1000f * 60 * 60)
                val minutes = (duration % (1000 * 60 * 60)) / (1000 * 60)
                val heightFraction = (duration.toFloat() / maxDuration).coerceIn(0.04f, 1f)
                val isPeak = bestDay != null && duration == bestDay.second && duration > 0

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    // Peak indicator
                    if (isPeak) {
                        Text(
                            text = "⭐",
                            fontSize = 10.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Duration top label
                    Text(
                        text = if (duration == 0L) "-" else if (hours >= 1f) "${String.format("%.1f", hours)}h" else "${minutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Bar column
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Background track
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        )
                        
                        // Active bar with gradient
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(heightFraction)
                                .width(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (isToday) {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                                            )
                                        )
                                    }
                                )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Day label pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = dayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FormatDonutChart(
    audioCount: Int,
    epubCount: Int,
    pdfCount: Int,
    selectedLanguage: String
) {
    val total = (audioCount + epubCount + pdfCount).coerceAtLeast(1)
    val audioAngle = (audioCount.toFloat() / total) * 360f
    val epubAngle = (epubCount.toFloat() / total) * 360f
    val pdfAngle = (pdfCount.toFloat() / total) * 360f

    val audioColor = MaterialTheme.colorScheme.primary
    val epubColor = Color(0xFF2ECC71) // Emerald Green
    val pdfColor = Color(0xFFE91E63) // Vibrant Rose

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        // Donut Canvas
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(130.dp)
        ) {
            Canvas(modifier = Modifier.size(110.dp)) {
                val strokeWidth = 18.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                val canvasSize = Size(radius * 2, radius * 2)

                if (audioCount == 0 && epubCount == 0 && pdfCount == 0) {
                    drawArc(
                        color = Color.Gray.copy(alpha = 0.2f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = canvasSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                } else {
                    var currentStart = -90f

                    if (audioAngle > 0f) {
                        drawArc(
                            color = audioColor,
                            startAngle = currentStart,
                            sweepAngle = (audioAngle - 4f).coerceAtLeast(2f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = canvasSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        currentStart += audioAngle
                    }

                    if (epubAngle > 0f) {
                        drawArc(
                            color = epubColor,
                            startAngle = currentStart,
                            sweepAngle = (epubAngle - 4f).coerceAtLeast(2f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = canvasSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        currentStart += epubAngle
                    }

                    if (pdfAngle > 0f) {
                        drawArc(
                            color = pdfColor,
                            startAngle = currentStart,
                            sweepAngle = (pdfAngle - 4f).coerceAtLeast(2f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = canvasSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${audioCount + epubCount + pdfCount}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (selectedLanguage == "es") "Libros" else "Books",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Legend Column
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(start = 12.dp)
        ) {
            DonutLegendItem(
                color = audioColor,
                icon = "🎧",
                label = LanguageManager.getString("stats_audio_format", selectedLanguage),
                count = audioCount,
                percent = if (total > 0) (audioCount * 100 / total) else 0
            )
            DonutLegendItem(
                color = epubColor,
                icon = "📖",
                label = LanguageManager.getString("stats_epub_format", selectedLanguage),
                count = epubCount,
                percent = if (total > 0) (epubCount * 100 / total) else 0
            )
            DonutLegendItem(
                color = pdfColor,
                icon = "📄",
                label = LanguageManager.getString("stats_pdf_format", selectedLanguage),
                count = pdfCount,
                percent = if (total > 0) (pdfCount * 100 / total) else 0
            )
        }
    }
}

@Composable
fun DonutLegendItem(
    color: Color,
    icon: String,
    label: String,
    count: Int,
    percent: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "$icon $label",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f, fill = false))
        Text(
            text = "$count ($percent%)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ConsistencyHeatmap(logs: List<ListeningLog>, selectedLanguage: String) {
    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }
    
    // Matrix of last 28 days (4 weeks x 7 days)
    val matrix = remember(logs) {
        val days = mutableListOf<Pair<String, Long>>()
        val calendar = java.util.Calendar.getInstance()
        for (i in 27 downTo 0) {
            val cloneCal = calendar.clone() as java.util.Calendar
            cloneCal.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val dateStr = sdf.format(cloneCal.time)
            val duration = logs.find { it.date == dateStr }?.durationMillis ?: 0L
            days.add(Pair(dateStr, duration))
        }
        days
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = LanguageManager.getString("stats_heatmap_title", selectedLanguage),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // Legend
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = if (selectedLanguage == "es") "Menos" else "Less",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)))
                Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                Text(
                    text = if (selectedLanguage == "es") "Más" else "More",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4 rows of 7 days
        val chunks = matrix.chunked(7)
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            chunks.forEach { week ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    week.forEach { (_, duration) ->
                        val alpha = when {
                            duration == 0L -> 0.08f
                            duration < 15 * 60 * 1000L -> 0.35f
                            duration < 45 * 60 * 1000L -> 0.70f
                            else -> 1.0f
                        }
                        val cellColor = if (duration == 0L) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(5.dp))
                                .background(cellColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MilestoneBadgeCard(
    icon: String,
    title: String,
    description: String,
    isUnlocked: Boolean,
    progressFraction: Float,
    selectedLanguage: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Badge visual icon with glow
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isUnlocked) {
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    )
                    .border(
                        1.5.dp,
                        if (isUnlocked) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )

                    if (isUnlocked) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (selectedLanguage == "es") "DESBLOQUEADO" else "UNLOCKED",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else {
                        Text(
                            text = "${(progressFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isUnlocked) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}

@Composable
fun AchievementItemCard(
    achievement: Achievement,
    selectedLanguage: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (achievement.isUnlocked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        ),
        border = BorderStroke(
            width = if (achievement.isUnlocked) 1.5.dp else 1.dp,
            color = if (achievement.isUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (achievement.isUnlocked) {
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    )
                    .border(
                        1.5.dp,
                        if (achievement.isUnlocked) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = achievement.icon,
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = achievement.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (achievement.isUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )

                    if (achievement.isUnlocked) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (selectedLanguage == "es") "DESBLOQUEADO" else "UNLOCKED",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else {
                        Text(
                            text = achievement.progressLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!achievement.isUnlocked) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { achievement.progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                }
            }
        }
    }
}

@Composable
fun StatsScreen(viewModel: AudiobookViewModel) {
    val logs by viewModel.listeningLogs.collectAsStateWithLifecycle()
    val allBooks: List<Audiobook> by viewModel.audiobooks.collectAsStateWithLifecycle()
    val quotesList by viewModel.bookQuotes.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    
    val streakStats = remember(logs) {
        calculateStreakStats(logs)
    }

    val todayDateStr = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    }
    
    val todayLog = logs.find { it.date == todayDateStr }
    val todayMillis = todayLog?.durationMillis ?: 0L
    
    val todayHours = todayMillis / (1000 * 60 * 60)
    val todayMinutes = (todayMillis % (1000 * 60 * 60)) / (1000 * 60)
    
    // Daily goal is 60 minutes
    val dailyGoalMillis = 60 * 60 * 1000L
    val dailyProgressFraction = (todayMillis.toFloat() / dailyGoalMillis).coerceIn(0f, 1f)

    val totalHours = streakStats.totalHours
    val currentStreak = streakStats.currentStreak
    val maxStreak = streakStats.maxStreak

    // Library breakdown counts
    val audioCount = remember(allBooks) { allBooks.count { it.filePath.lowercase().contains("mp3") || it.filePath.lowercase().contains("m4a") || it.filePath.lowercase().contains("m4b") || it.filePath.lowercase().contains("aac") } }
    val epubCount = remember(allBooks) { allBooks.count { it.filePath.lowercase().contains("epub") } }
    val pdfCount = remember(allBooks) { allBooks.count { it.filePath.lowercase().contains("pdf") } }
    val inProgressCount = remember(allBooks) { allBooks.count { it.currentPositionMillis > 1000L } }

    val (rankName, nextLevelLabel, currentLvlProgress) = when {
        totalHours < 1f -> Triple("Novato", "Lector (1 h)", (totalHours / 1f).coerceIn(0f, 1f))
        totalHours < 5f -> Triple("Lector", "Maestro (5 h)", ((totalHours - 1f) / 4f).coerceIn(0f, 1f))
        totalHours < 15f -> Triple("Maestro", "Sabio (15 h)", ((totalHours - 5f) / 10f).coerceIn(0f, 1f))
        else -> Triple("Sabio de Audire", "Máximo Nivel ✨", 1.0f)
    }

    val rankText = when (rankName) {
        "Novato" -> if (selectedLanguage == "es") "Novato" else "Rookie"
        "Lector" -> if (selectedLanguage == "es") "Lector Apasionado" else "Avid Listener"
        "Maestro" -> if (selectedLanguage == "es") "Maestro de Lectura" else "Reading Master"
        else -> if (selectedLanguage == "es") "Sabio de Audire" else "Audire Grandmaster"
    }

    val nextLevelText = when {
        totalHours < 1f -> if (selectedLanguage == "es") "Lector (1 h)" else "Listener (1 h)"
        totalHours < 5f -> if (selectedLanguage == "es") "Maestro (5 h)" else "Grandmaster (5 h)"
        totalHours < 15f -> if (selectedLanguage == "es") "Sabio (15 h)" else "Sage (15 h)"
        else -> if (selectedLanguage == "es") "Máximo Rango ✨" else "Maximum Rank ✨"
    }

    var selectedStatsTab by rememberSaveable { mutableStateOf(0) } // 0: Resumen, 1: Hábitos, 2: Logros

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.45f
    val statsCardBg = if (isLight) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- HEADER CON TÍTULO Y DESCRIPCIÓN ---
        Column {
            Text(
                text = LanguageManager.getString("stats_title", selectedLanguage),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 28.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = LanguageManager.getString("stats_subtitle", selectedLanguage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --- HERO LEVEL BANNER CARD ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().testTag("stats_hero_level_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (rankName) {
                                    "Novato" -> Icons.Filled.Star
                                    "Lector" -> Icons.Filled.AutoStories
                                    "Maestro" -> Icons.Filled.EmojiEvents
                                    else -> Icons.Filled.WorkspacePremium
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = rankText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${String.format("%.1f", totalHours)} h " + (if (selectedLanguage == "es") "acumuladas" else "accumulated"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Motivational WhatsApp share button
                    IconButton(
                        onClick = {
                            val motivationalText = if (selectedLanguage == "es") {
                                """
                                📚 ¡Sigo superándome con Audire! Hoy he avanzado con disciplina:

                                🔥 Racha de Lectura: $currentStreak ${if (currentStreak == 1) "día" else "días"} consecutivos!
                                🏆 Rango: $rankText
                                ⏱️ Escuchado hoy: ${if (todayHours > 0) "${todayHours}h ${todayMinutes}m" else "${todayMinutes}m"}
                                ⚡ Tiempo total: ${String.format("%.1f", totalHours)} horas de enriquecimiento

                                "La disciplina vence al talento. Sigue cultivando tu mente día a día." 🌱✨

                                Compartido desde Audire App
                                """.trimIndent()
                            } else {
                                """
                                📚 I'm leveling up with Audire! Today I read with discipline:

                                🔥 Reading Streak: $currentStreak ${if (currentStreak == 1) "day" else "days"} in a row!
                                🏆 Rank: $rankText
                                ⏱️ Listened today: ${if (todayHours > 0) "${todayHours}h ${todayMinutes}m" else "${todayMinutes}m"}
                                ⚡ Total listening: ${String.format("%.1f", totalHours)} hours of learning

                                "Discipline beats talent. Keep cultivating your mind every day." 🌱✨

                                Shared from Audire App
                                """.trimIndent()
                            }

                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, motivationalText)
                                type = "text/plain"
                                setPackage("com.whatsapp")
                            }
                            
                            try {
                                context.startActivity(sendIntent)
                            } catch (e: Exception) {
                                val fallbackIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, motivationalText)
                                    type = "text/plain"
                                }
                                context.startActivity(android.content.Intent.createChooser(fallbackIntent, if (selectedLanguage == "es") "Compartir logros" else "Share Achievements"))
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // XP Progress Bar to next rank
                LinearProgressIndicator(
                    progress = { currentLvlProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = LanguageManager.getString("stats_next_rank", selectedLanguage),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = nextLevelText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // --- MODERN SEGMENTED TABS (Resumen / Hábitos / Logros) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                LanguageManager.getString("stats_tab_overview", selectedLanguage) to Icons.Filled.Dashboard,
                LanguageManager.getString("stats_tab_habits", selectedLanguage) to Icons.Filled.Timeline,
                LanguageManager.getString("stats_tab_achievements", selectedLanguage) to Icons.Filled.EmojiEvents
            ).forEachIndexed { index, (title, icon) ->
                val isSelected = selectedStatsTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { selectedStatsTab = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // --- TAB CONTENT ---
        when (selectedStatsTab) {
            0 -> {
                // ==================== TAB 0: RESUMEN ====================
                // 1. 4-METRIC BENTO GRID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Racha actual card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = statsCardBg),
                        border = borderStroke(),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f).testTag("stats_current_streak_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF9800).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Whatshot,
                                    contentDescription = null,
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "$currentStreak ${if (currentStreak == 1) (if (selectedLanguage == "es") "Día" else "Day") else (if (selectedLanguage == "es") "Días" else "Days")}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = LanguageManager.getString("stats_curr_streak", selectedLanguage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Tiempo total card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = statsCardBg),
                        border = borderStroke(),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f).testTag("stats_total_time_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${String.format("%.1f", totalHours)} h",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = LanguageManager.getString("stats_total", selectedLanguage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Libros activos card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = statsCardBg),
                        border = borderStroke(),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2ECC71).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = Color(0xFF2ECC71),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${allBooks.size}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (selectedLanguage == "es") "Biblioteca Total" else "Total Library",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Citas anotadas card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = statsCardBg),
                        border = borderStroke(),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF9C27B0).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FormatQuote,
                                    contentDescription = null,
                                    tint = Color(0xFF9C27B0),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${quotesList.size}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF9C27B0)
                            )
                            Text(
                                text = LanguageManager.getString("stats_quotes_saved", selectedLanguage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 2. PROGRESO DE HOY (RING GAUGE)
                Card(
                    colors = CardDefaults.cardColors(containerColor = statsCardBg),
                    border = borderStroke(),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().testTag("stats_progress_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = LanguageManager.getString("stats_today_prog", selectedLanguage),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = LanguageManager.getString("stats_meta_desc", selectedLanguage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(150.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { dailyProgressFraction },
                                modifier = Modifier.size(140.dp),
                                strokeWidth = 14.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                strokeCap = StrokeCap.Round
                            )
                            
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (todayHours > 0) "${todayHours}h ${todayMinutes}m" else "${todayMinutes}m",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${(dailyProgressFraction * 100).toInt()}% " + (if (selectedLanguage == "es") "de la meta" else "of goal"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = when {
                                dailyProgressFraction >= 1f -> LanguageManager.getString("stats_completed", selectedLanguage)
                                dailyProgressFraction >= 0.5f -> LanguageManager.getString("stats_half", selectedLanguage)
                                todayMillis > 0 -> LanguageManager.getString("stats_momentum", selectedLanguage)
                                else -> LanguageManager.getString("stats_start", selectedLanguage)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // 3. DISTRIBUCIÓN POR FORMATO (DONUT CHART)
                Card(
                    colors = CardDefaults.cardColors(containerColor = statsCardBg),
                    border = borderStroke(),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().testTag("stats_donut_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = LanguageManager.getString("stats_library_dist", selectedLanguage),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        FormatDonutChart(
                            audioCount = audioCount,
                            epubCount = epubCount,
                            pdfCount = pdfCount,
                            selectedLanguage = selectedLanguage
                        )
                    }
                }
            }

            1 -> {
                // ==================== TAB 1: HÁBITOS Y GRÁFICOS ====================
                // 1. GRÁFICO DE BARRAS DE 7 DÍAS
                Card(
                    colors = CardDefaults.cardColors(containerColor = statsCardBg),
                    border = borderStroke(),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().testTag("stats_history_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = LanguageManager.getString("stats_weekly", selectedLanguage),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (selectedLanguage == "es") "Actividad diaria registrada" else "Daily registered activity",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${String.format("%.1f", totalHours)} h",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        ListeningBarChart(logs = logs)
                    }
                }

                // 2. MATRIZ DE CONSTANCIA (HEATMAP 28 DÍAS)
                Card(
                    colors = CardDefaults.cardColors(containerColor = statsCardBg),
                    border = borderStroke(),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().testTag("stats_heatmap_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        ConsistencyHeatmap(logs = logs, selectedLanguage = selectedLanguage)
                    }
                }

                // 3. INSIGHTS Y RACHA MÁXIMA
                Card(
                    colors = CardDefaults.cardColors(containerColor = statsCardBg),
                    border = borderStroke(),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.EmojiEvents,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = LanguageManager.getString("stats_max_streak", selectedLanguage),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "$maxStreak ${if (maxStreak == 1) (if (selectedLanguage == "es") "día" else "day") else (if (selectedLanguage == "es") "días" else "days")}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (selectedLanguage == "es") 
                                "Mantén una lectura diaria continua de al menos 10 minutos para proteger tu racha y no perder el fuego."
                            else 
                                "Keep a daily reading habit of at least 10 minutes to protect your streak and keep the flame burning.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            2 -> {
                // ==================== TAB 2: LOGROS E INSIGNIAS (100 LOGROS) ====================
                var selectedAchievementCategory by rememberSaveable { mutableStateOf(AchievementCategory.ALL) }
                
                val allAchievements = remember(allBooks, totalHours, currentStreak, maxStreak, quotesList, logs, selectedLanguage) {
                    AchievementManager.generateAchievements(
                        allBooks = allBooks,
                        totalHours = totalHours,
                        currentStreak = currentStreak,
                        maxStreak = maxStreak,
                        quotesList = quotesList,
                        logs = logs,
                        lang = selectedLanguage
                    )
                }

                val filteredAchievements = remember(allAchievements, selectedAchievementCategory) {
                    if (selectedAchievementCategory == AchievementCategory.ALL) {
                        allAchievements
                    } else {
                        allAchievements.filter { it.category == selectedAchievementCategory }
                    }
                }

                val unlockedCount = remember(allAchievements) { allAchievements.count { it.isUnlocked } }
                val totalCount = allAchievements.size
                val completionPercent = if (totalCount > 0) (unlockedCount * 100) / totalCount else 0

                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("stats_badges_container")
                ) {
                    // Summary Banner
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (selectedLanguage == "es") "Galería de Logros e Insignias" else "Achievements & Badges Gallery",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (selectedLanguage == "es") 
                                        "$unlockedCount de $totalCount desbloqueados ($completionPercent%)" 
                                    else 
                                        "$unlockedCount of $totalCount unlocked ($completionPercent%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { (unlockedCount.toFloat() / totalCount.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🏆",
                                    fontSize = 28.sp
                                )
                            }
                        }
                    }

                    // Category Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AchievementCategory.values().forEach { cat ->
                            val isSelected = selectedAchievementCategory == cat
                            val catLabel = when (cat) {
                                AchievementCategory.ALL -> LanguageManager.getString("cat_all", selectedLanguage)
                                AchievementCategory.STREAKS -> LanguageManager.getString("cat_streaks", selectedLanguage)
                                AchievementCategory.TIME -> LanguageManager.getString("cat_hours", selectedLanguage)
                                AchievementCategory.LIBRARY -> LanguageManager.getString("cat_library", selectedLanguage)
                                AchievementCategory.QUOTES -> LanguageManager.getString("cat_quotes", selectedLanguage)
                                AchievementCategory.HABITS -> LanguageManager.getString("cat_habits", selectedLanguage)
                                AchievementCategory.MASTERY -> LanguageManager.getString("cat_mastery", selectedLanguage)
                            }
                            
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedAchievementCategory = cat },
                                label = { Text(catLabel, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    // Achievement items list
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        filteredAchievements.forEach { achievement ->
                            AchievementItemCard(
                                achievement = achievement,
                                selectedLanguage = selectedLanguage
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: AudiobookViewModel,
    onNavigateBack: () -> Unit
) {
    // Intercept back button/gesture to ALWAYS return to Library/Player safely
    BackHandler(enabled = true) {
        onNavigateBack()
    }

    val activeBook by viewModel.currentPlayingBook.collectAsStateWithLifecycle()
    val positionMillis by viewModel.playbackPositionMillis.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val epubPages by viewModel.epubPages.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val readerTheme by viewModel.readerThemeMode.collectAsStateWithLifecycle()
    val isPdfNightInverted by viewModel.isPdfNightInverted.collectAsStateWithLifecycle()
    val quotesList by viewModel.bookQuotes.collectAsStateWithLifecycle()
    val currentBookQuotes: List<com.example.data.BookQuote> = remember(quotesList, activeBook?.id) { 
        quotesList.filter { it.bookId == (activeBook?.id ?: -1) } 
    }

    var isPdfTextMode by rememberSaveable { mutableStateOf(false) }
    var readerFontSize by rememberSaveable { mutableStateOf(18) }
    var isImmersiveMode by rememberSaveable { mutableStateOf(false) }
    var showImmersiveHint by remember { mutableStateOf(true) }

    // Auto-scroll states
    var isAutoScrolling by rememberSaveable { mutableStateOf(false) }
    var autoScrollSpeedFactor by rememberSaveable { mutableStateOf(1.0f) } // 0.25x to 6.0x
    var showAutoScrollSpeedDialog by remember { mutableStateOf(false) }

    var showJumpToPageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var pageInputText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    var showAddQuoteDialog by remember { mutableStateOf(false) }
    var quoteInputText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(4000)
        showImmersiveHint = false
    }

    // Dynamic reader theme color schemes
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val (readerBgColor, readerTextColor) = when (readerTheme) {
        "sepia" -> Color(0xFFF4ECD8) to Color(0xFF3E2723)
        "eink" -> Color(0xFFF0F0F0) to Color(0xFF111111)
        "night" -> Color(0xFF0D0D0D) to Color(0xFFE2E2E2)
        "white" -> Color(0xFFFFFFFF) to Color(0xFF1A1A1A)
        else -> if (isSystemDark) Color(0xFF151515) to Color(0xFFE3E3E3) else Color(0xFFFBF0D9) to Color(0xFF2C2518) // parchment
    }

    if (activeBook == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = onNavigateBack) {
                Text(if (selectedLanguage == "es") "Volver a la Biblioteca" else "Back to Library")
            }
        }
        return
    }

    val book = activeBook!!
    val isPdf = book.filePath.lowercase().endsWith(".pdf") || book.title.lowercase().endsWith(".pdf")
    val totalPages = if (isPdf && !isPdfTextMode) book.durationMillis.toInt().coerceAtLeast(1) else epubPages.size.coerceAtLeast(1)
    val currentPageIndex = positionMillis.toInt().coerceIn(0, totalPages - 1)

    var activeDockSheet by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            if (!isImmersiveMode) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = book.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = readerTextColor
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (selectedLanguage == "es") "Pág. ${currentPageIndex + 1}/$totalPages" else "Pg. ${currentPageIndex + 1}/$totalPages",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = readerTextColor.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = if (isPdf) {
                                        if (isPdfTextMode) (if (selectedLanguage == "es") "PDF Texto" else "PDF Text")
                                        else (if (selectedLanguage == "es") "PDF Visual" else "PDF Visual")
                                    } else "EPUB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = readerTextColor.copy(alpha = 0.6f)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = if (selectedLanguage == "es") "Volver a Biblioteca" else "Back to Library",
                                tint = readerTextColor
                            )
                        }
                    },
                    actions = {
                        // Quick Voice Play / Pause Indicator in Top Bar
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.testTag("narrator_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.RecordVoiceOver,
                                contentDescription = "Narrador de voz",
                                tint = if (isPlaying) MaterialTheme.colorScheme.primary else readerTextColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Full Screen / Immersive Toggle Action
                        IconButton(onClick = { isImmersiveMode = true }) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = if (selectedLanguage == "es") "Pantalla Completa" else "Full Screen",
                                tint = readerTextColor
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = readerBgColor
                    )
                )
            }
        },
        bottomBar = {
            if (!isImmersiveMode) {
                Surface(
                    color = readerBgColor,
                    tonalElevation = 6.dp,
                    border = BorderStroke(1.dp, readerTextColor.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        // Mini Scrubber Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.skipBackward() },
                                enabled = currentPageIndex > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ChevronLeft,
                                    contentDescription = "Página anterior",
                                    tint = if (currentPageIndex > 0) readerTextColor else readerTextColor.copy(alpha = 0.3f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Slider(
                                value = if (totalPages > 1) currentPageIndex.toFloat() / (totalPages - 1).toFloat() else 0f,
                                onValueChange = { scale ->
                                    val targetPage = (scale * (totalPages - 1)).toInt()
                                    viewModel.setPage(targetPage)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = readerTextColor.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reader_page_slider")
                            )

                            IconButton(
                                onClick = { viewModel.skipForward() },
                                enabled = currentPageIndex < totalPages - 1,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = "Siguiente página",
                                    tint = if (currentPageIndex < totalPages - 1) readerTextColor else readerTextColor.copy(alpha = 0.3f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // ==================== THE UNIFIED BOTTOM DOCK ====================
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = readerTextColor.copy(alpha = 0.06f),
                            border = BorderStroke(1.dp, readerTextColor.copy(alpha = 0.1f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reader_bottom_dock")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Páginas y Marcadores
                                ReaderDockItem(
                                    icon = Icons.Default.MenuBook,
                                    label = LanguageManager.getString("dock_nav", selectedLanguage),
                                    isActive = activeDockSheet == "pages",
                                    textColor = readerTextColor,
                                    onClick = { activeDockSheet = if (activeDockSheet == "pages") null else "pages" }
                                )

                                // 2. Apariencia y Temas
                                ReaderDockItem(
                                    icon = Icons.Default.Palette,
                                    label = LanguageManager.getString("dock_appearance", selectedLanguage),
                                    isActive = activeDockSheet == "appearance",
                                    textColor = readerTextColor,
                                    onClick = { activeDockSheet = if (activeDockSheet == "appearance") null else "appearance" }
                                )

                                // 3. Auto-Scroll
                                ReaderDockItem(
                                    icon = Icons.Default.Speed,
                                    label = LanguageManager.getString("dock_scroll", selectedLanguage),
                                    isActive = activeDockSheet == "scroll" || isAutoScrolling,
                                    badgeText = if (isAutoScrolling) "${autoScrollSpeedFactor}x" else null,
                                    textColor = readerTextColor,
                                    onClick = { activeDockSheet = if (activeDockSheet == "scroll") null else "scroll" }
                                )

                                // 4. Frases y Notas
                                ReaderDockItem(
                                    icon = Icons.Default.FormatQuote,
                                    label = LanguageManager.getString("dock_quotes", selectedLanguage),
                                    isActive = activeDockSheet == "quotes",
                                    badgeText = if (currentBookQuotes.isNotEmpty()) "${currentBookQuotes.size}" else null,
                                    textColor = readerTextColor,
                                    onClick = { activeDockSheet = if (activeDockSheet == "quotes") null else "quotes" }
                                )

                                // 5. Narrador TTS
                                ReaderDockItem(
                                    icon = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.RecordVoiceOver,
                                    label = LanguageManager.getString("dock_voice", selectedLanguage),
                                    isActive = activeDockSheet == "voice" || isPlaying,
                                    textColor = readerTextColor,
                                    onClick = { activeDockSheet = if (activeDockSheet == "voice") null else "voice" }
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = readerBgColor
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(if (isImmersiveMode) 0.dp else 8.dp)
        ) {
            if (isPdf && !isPdfTextMode) {
                PdfMangaReader(
                    viewModel = viewModel,
                    filePath = book.filePath,
                    initialPageIndex = currentPageIndex,
                    totalPages = totalPages.toInt(),
                    modifier = Modifier.fillMaxSize(),
                    isImmersiveMode = isImmersiveMode,
                    isAutoScrolling = isAutoScrolling,
                    autoScrollSpeedFactor = autoScrollSpeedFactor,
                    onToggleAutoScroll = { isAutoScrolling = !isAutoScrolling },
                    onSingleTap = { isImmersiveMode = !isImmersiveMode },
                    onDoubleTap = { isImmersiveMode = !isImmersiveMode }
                )
            } else {
                if (epubPages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = readerTextColor,
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = if (selectedLanguage == "es") "Preparando páginas de lectura..." else "Preparing reading pages...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = readerTextColor.copy(alpha = 0.8f)
                            )
                        }
                    }
                } else {
                    val textReaderState = rememberLazyListState(initialFirstVisibleItemIndex = currentPageIndex)
                    
                    // Continuous Smooth Auto-scroll Engine for Text / Novel / EPUB mode
                    LaunchedEffect(isAutoScrolling, autoScrollSpeedFactor) {
                        if (isAutoScrolling) {
                            val baseSpeed = 30f // dp per second base for text reading
                            val pxPerSec = baseSpeed * autoScrollSpeedFactor * context.resources.displayMetrics.density
                            val frameDurationMs = 16L
                            while (isActive && isAutoScrolling) {
                                val step = (pxPerSec * (frameDurationMs / 1000f)).coerceAtLeast(0.5f)
                                textReaderState.scrollBy(step)
                                kotlinx.coroutines.delay(frameDurationMs)
                            }
                        }
                    }

                    // Sync scroll position from ViewModel (slider / narrator / navigation)
                    LaunchedEffect(currentPageIndex) {
                        if (Math.abs(textReaderState.firstVisibleItemIndex - currentPageIndex) > 1 && !isAutoScrolling) {
                            textReaderState.animateScrollToItem(currentPageIndex)
                        }
                    }
                    
                    // Sync scroll position to ViewModel (status bar & sliders update)
                    LaunchedEffect(textReaderState) {
                        snapshotFlow { textReaderState.firstVisibleItemIndex }
                            .collect { index ->
                                if (index != currentPageIndex && index in epubPages.indices) {
                                    viewModel.setPage(index)
                                }
                            }
                    }

                    LazyColumn(
                        state = textReaderState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        isImmersiveMode = !isImmersiveMode
                                    },
                                    onDoubleTap = {
                                        isImmersiveMode = !isImmersiveMode
                                    }
                                )
                            },
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        contentPadding = PaddingValues(bottom = if (isImmersiveMode) 90.dp else 140.dp)
                    ) {
                        items(epubPages.size) { page ->
                            val pageText = epubPages[page]
                            if (isPdf && pageText.isEmpty()) {
                                LaunchedEffect(page) {
                                    viewModel.loadPdfPageTextIfNeeded(page)
                                }
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Light separator or page indicator above the page text
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(1.dp)
                                            .background(readerTextColor.copy(alpha = 0.15f))
                                    )
                                    Text(
                                        text = if (selectedLanguage == "es") "PÁGINA ${page + 1}" else "PAGE ${page + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            letterSpacing = 1.5.sp
                                        ),
                                        color = readerTextColor.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(1.dp)
                                            .background(readerTextColor.copy(alpha = 0.15f))
                                    )
                                }

                                if (pageText.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = readerTextColor.copy(alpha = 0.5f),
                                                strokeWidth = 2.dp
                                            )
                                            Text(
                                                text = if (selectedLanguage == "es") "Extrayendo texto de la página ${page + 1}..." else "Extracting text for page ${page + 1}...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = readerTextColor.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = pageText,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                            lineHeight = (readerFontSize * 1.55).sp,
                                            fontSize = readerFontSize.sp
                                        ),
                                        color = readerTextColor,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Floating Navigation & Exit Overlays in Immersive Full Screen Mode
            if (isImmersiveMode) {
                // Top-Left Always-Accessible Floating Back Button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 16.dp, top = 16.dp)
                ) {
                    Surface(
                        onClick = onNavigateBack,
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.72f),
                        contentColor = Color.White,
                        tonalElevation = 6.dp,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = if (selectedLanguage == "es") "Volver a Biblioteca" else "Back to Library",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Top-Right Floating Controls Restore Button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(end = 16.dp, top = 16.dp)
                ) {
                    Surface(
                        onClick = { isImmersiveMode = false },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.72f),
                        contentColor = Color.White,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Mostrar menú",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (selectedLanguage == "es") "Menú / Controles" else "Controls",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Floating Auto-Scroll Control Pill (appears whenever Auto-scroll is active)
            AnimatedVisibility(
                visible = isAutoScrolling,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (isImmersiveMode) 20.dp else 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xF0181818),
                    contentColor = Color.White,
                    tonalElevation = 8.dp,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        // Play / Pause Toggle Button
                        IconButton(
                            onClick = { isAutoScrolling = !isAutoScrolling },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isAutoScrolling) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                                contentDescription = if (isAutoScrolling) "Pausar" else "Continuar",
                                tint = if (isAutoScrolling) Color(0xFF4CAF50) else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Slower speed button
                        IconButton(
                            onClick = { 
                                autoScrollSpeedFactor = ((autoScrollSpeedFactor - 0.25f) * 100).toInt() / 100f
                                if (autoScrollSpeedFactor < 0.25f) autoScrollSpeedFactor = 0.25f
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text(
                                text = "-",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }

                        // Speed Badge (tap to open auto-scroll sheet)
                        Surface(
                            onClick = { activeDockSheet = "scroll" },
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${autoScrollSpeedFactor}x",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }

                        // Faster speed button
                        IconButton(
                            onClick = { 
                                autoScrollSpeedFactor = ((autoScrollSpeedFactor + 0.25f) * 100).toInt() / 100f
                                if (autoScrollSpeedFactor > 6.0f) autoScrollSpeedFactor = 6.0f
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(20.dp)
                                .background(Color.White.copy(alpha = 0.2f))
                        )

                        // Dismiss/Stop Auto-Scroll
                        IconButton(
                            onClick = { isAutoScrolling = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Detener",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Quick Info banner on entry
            if (showImmersiveHint) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (isImmersiveMode) 40.dp else 16.dp)
                        .align(Alignment.TopCenter),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedLanguage == "es") 
                                    "Toca la pantalla para modo inmersivo • Usa el Dock inferior para ajustes" 
                                else 
                                    "Tap screen for full screen • Use the Bottom Dock for quick settings",
                                style = MaterialTheme.typography.labelMedium.copy(color = Color.White)
                            )
                        }
                    }
                }
            }
        }
    }

    // ==================== EXPANDING CONFIGURATION BOTTOM SHEETS ====================

    // 1. Pages & Bookmarks Sheet
    if (activeDockSheet == "pages") {
        ModalBottomSheet(
            onDismissRequest = { activeDockSheet = null },
            containerColor = readerBgColor,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = LanguageManager.getString("dock_pages_title", selectedLanguage),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = readerTextColor
                        )
                    }
                    IconButton(onClick = { activeDockSheet = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = readerTextColor)
                    }
                }

                // Page navigation jump box
                Surface(
                    color = readerTextColor.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (selectedLanguage == "es") "Página actual: ${currentPageIndex + 1} de $totalPages" else "Current page: ${currentPageIndex + 1} of $totalPages",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = readerTextColor
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = pageInputText,
                                onValueChange = { if (it.all { char -> char.isDigit() }) pageInputText = it },
                                placeholder = { Text(if (selectedLanguage == "es") "Ir a pág..." else "Jump to...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = readerTextColor,
                                    unfocusedTextColor = readerTextColor
                                )
                            )

                            Button(
                                onClick = {
                                    val entered = pageInputText.toIntOrNull()
                                    if (entered != null && entered in 1..totalPages) {
                                        viewModel.setPage(entered - 1)
                                        pageInputText = ""
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (selectedLanguage == "es") "Ir" else "Go")
                            }
                        }

                        // Quick step buttons (-10, -1, +1, +10)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(-10, -1, 1, 10).forEach { step ->
                                val target = (currentPageIndex + step).coerceIn(0, totalPages - 1)
                                OutlinedButton(
                                    onClick = { viewModel.setPage(target) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(if (step > 0) "+$step" else "$step")
                                }
                            }
                        }
                    }
                }

                // Add Bookmark from current page button
                Button(
                    onClick = {
                        viewModel.insertBookQuote(
                            bookId = book.id,
                            bookTitle = book.title,
                            text = if (selectedLanguage == "es") "Marcador en página ${currentPageIndex + 1}" else "Bookmark at page ${currentPageIndex + 1}",
                            page = if (selectedLanguage == "es") "Página ${currentPageIndex + 1}" else "Page ${currentPageIndex + 1}"
                        )
                        Toast.makeText(context, if (selectedLanguage == "es") "Marcador añadido" else "Bookmark added", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (selectedLanguage == "es") "Guardar Marcador en Página ${currentPageIndex + 1}" else "Save Bookmark at Page ${currentPageIndex + 1}")
                }
            }
        }
    }

    // 2. Reading Appearance Sheet
    if (activeDockSheet == "appearance") {
        ModalBottomSheet(
            onDismissRequest = { activeDockSheet = null },
            containerColor = readerBgColor,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = LanguageManager.getString("dock_appearance_title", selectedLanguage),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = readerTextColor
                        )
                    }
                    IconButton(onClick = { activeDockSheet = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = readerTextColor)
                    }
                }

                // Theme color selector
                Text(
                    text = if (selectedLanguage == "es") "Paleta y Tema de Lectura:" else "Theme & Color Palette:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = readerTextColor
                )

                val themes = listOf(
                    "parchment" to ("Pergamino" to Color(0xFFFBF0D9)),
                    "sepia" to ("Sepia Ámbar" to Color(0xFFF4ECD8)),
                    "eink" to ("Papel E-Ink" to Color(0xFFF0F0F0)),
                    "night" to ("Noche OLED" to Color(0xFF0D0D0D)),
                    "white" to ("Blanco Puro" to Color(0xFFFFFFFF))
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    themes.forEach { (mode, pair) ->
                        val (label, bg) = pair
                        val isSelected = readerTheme == mode
                        Surface(
                            onClick = { viewModel.setReaderThemeMode(mode) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else readerTextColor.copy(alpha = 0.05f),
                            border = BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else readerTextColor.copy(alpha = 0.15f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(bg)
                                        .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = readerTextColor
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = readerTextColor.copy(alpha = 0.1f))

                // Font size controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (selectedLanguage == "es") "Tamaño de Fuente" else "Font Size",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = readerTextColor
                        )
                        Text(
                            text = "${readerFontSize}sp",
                            style = MaterialTheme.typography.labelSmall,
                            color = readerTextColor.copy(alpha = 0.6f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (readerFontSize > 12) readerFontSize -= 2 },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("A-", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { if (readerFontSize < 36) readerFontSize += 2 },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("A+", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // PDF specific mode switches
                if (isPdf) {
                    HorizontalDivider(color = readerTextColor.copy(alpha = 0.1f))
                    
                    // Toggle Visual Pages vs Extracted Text
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (selectedLanguage == "es") "Modo Texto Extraído" else "Extracted Text Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = readerTextColor
                            )
                            Text(
                                text = if (selectedLanguage == "es") "Flujo continuo como novela en vez de páginas PDF fijas" else "Flowing novel reader instead of visual comic sheets",
                                style = MaterialTheme.typography.labelSmall,
                                color = readerTextColor.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = isPdfTextMode,
                            onCheckedChange = { isPdfTextMode = it }
                        )
                    }

                    // Night inverted mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (selectedLanguage == "es") "Modo Nocturno Invertido" else "Inverted Night Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = readerTextColor
                            )
                            Text(
                                text = if (selectedLanguage == "es") "Invierte colores en páginas de PDF para leer a oscuras" else "Inverts PDF page colors for reading in dark environments",
                                style = MaterialTheme.typography.labelSmall,
                                color = readerTextColor.copy(alpha = 0.6f)
                            )
                        }
                        Switch(
                            checked = isPdfNightInverted,
                            onCheckedChange = { viewModel.togglePdfNightInverted() }
                        )
                    }
                }
            }
        }
    }

    // 3. Auto-Scroll Sheet
    if (activeDockSheet == "scroll") {
        ModalBottomSheet(
            onDismissRequest = { activeDockSheet = null },
            containerColor = readerBgColor,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = LanguageManager.getString("dock_scroll_title", selectedLanguage),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = readerTextColor
                        )
                    }
                    IconButton(onClick = { activeDockSheet = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = readerTextColor)
                    }
                }

                // Main Play / Pause Banner Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAutoScrolling) MaterialTheme.colorScheme.primaryContainer else readerTextColor.copy(alpha = 0.08f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isAutoScrolling) {
                                    if (selectedLanguage == "es") "Auto-Scroll En Curso" else "Auto-Scroll Active"
                                } else {
                                    if (selectedLanguage == "es") "Auto-Scroll Pausado" else "Auto-Scroll Paused"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isAutoScrolling) MaterialTheme.colorScheme.primary else readerTextColor
                            )
                            Text(
                                text = "${autoScrollSpeedFactor}x velocidad",
                                style = MaterialTheme.typography.bodySmall,
                                color = readerTextColor.copy(alpha = 0.7f)
                            )
                        }

                        Button(
                            onClick = { isAutoScrolling = !isAutoScrolling },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isAutoScrolling) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isAutoScrolling) (if (selectedLanguage == "es") "Pausar" else "Pause") else (if (selectedLanguage == "es") "Iniciar" else "Start"))
                        }
                    }
                }

                // Preset Buttons
                Text(
                    text = if (selectedLanguage == "es") "Perfiles Recomendados:" else "Recommended Profiles:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = readerTextColor
                )

                val presets = listOf(
                    0.5f to (if (selectedLanguage == "es") "📖 Novela (0.5x)" else "📖 Novel (0.5x)"),
                    1.0f to (if (selectedLanguage == "es") "📄 Normal (1.0x)" else "📄 Normal (1.0x)"),
                    1.8f to (if (selectedLanguage == "es") "⚡ Manga (1.8x)" else "⚡ Manga (1.8x)"),
                    3.0f to (if (selectedLanguage == "es") "🚀 Rápido (3.0x)" else "🚀 Fast (3.0x)")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (spd, lbl) ->
                        val isSel = Math.abs(autoScrollSpeedFactor - spd) < 0.05f
                        OutlinedButton(
                            onClick = { autoScrollSpeedFactor = spd },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                            ),
                            border = BorderStroke(
                                if (isSel) 2.dp else 1.dp,
                                if (isSel) MaterialTheme.colorScheme.primary else readerTextColor.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = lbl,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSel) MaterialTheme.colorScheme.primary else readerTextColor,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Fine slider
                Text(
                    text = if (selectedLanguage == "es") "Ajuste continuo (0.25x a 6.0x):" else "Fine adjustment (0.25x to 6.0x):",
                    style = MaterialTheme.typography.labelSmall,
                    color = readerTextColor.copy(alpha = 0.7f)
                )

                Slider(
                    value = autoScrollSpeedFactor,
                    onValueChange = { autoScrollSpeedFactor = ((it * 100).toInt()) / 100f },
                    valueRange = 0.25f..6.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }

    // 4. Quotes & Notes Sheet
    if (activeDockSheet == "quotes") {
        ModalBottomSheet(
            onDismissRequest = { activeDockSheet = null },
            containerColor = readerBgColor,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatQuote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = LanguageManager.getString("dock_quotes_title", selectedLanguage),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = readerTextColor
                        )
                    }
                    IconButton(onClick = { activeDockSheet = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = readerTextColor)
                    }
                }

                // Add quote field
                Surface(
                    color = readerTextColor.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (selectedLanguage == "es") "Anotar frase de la Página ${currentPageIndex + 1}:" else "Save quote from Page ${currentPageIndex + 1}:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = readerTextColor
                        )
                        OutlinedTextField(
                            value = quoteInputText,
                            onValueChange = { quoteInputText = it },
                            placeholder = { Text(LanguageManager.getString("quote_hint", selectedLanguage)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = readerTextColor,
                                unfocusedTextColor = readerTextColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (quoteInputText.isNotBlank()) {
                                    viewModel.insertBookQuote(
                                        bookId = book.id,
                                        bookTitle = book.title,
                                        text = quoteInputText,
                                        page = if (selectedLanguage == "es") "Página ${currentPageIndex + 1}" else "Page ${currentPageIndex + 1}"
                                    )
                                    quoteInputText = ""
                                    Toast.makeText(context, if (selectedLanguage == "es") "Frase guardada" else "Quote saved", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = quoteInputText.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(if (selectedLanguage == "es") "Guardar Frase" else "Save Quote")
                        }
                    }
                }

                // List of saved quotes in this book
                Text(
                    text = if (selectedLanguage == "es") "Frases guardadas en este libro (${currentBookQuotes.size}):" else "Saved quotes in this book (${currentBookQuotes.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = readerTextColor
                )

                if (currentBookQuotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedLanguage == "es") "Aún no tienes frases guardadas en este libro." else "No quotes saved in this book yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = readerTextColor.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = currentBookQuotes, key = { it.id }) { quote ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val digits = quote.pageReference.filter { it.isDigit() }
                                        val pageNum = digits.toIntOrNull()
                                        if (pageNum != null && pageNum in 1..totalPages) {
                                            viewModel.setPage(pageNum - 1)
                                            activeDockSheet = null
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = readerTextColor.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = quote.pageReference,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = quote.quoteText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = readerTextColor,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                val sendIntent = android.content.Intent().apply {
                                                    action = android.content.Intent.ACTION_SEND
                                                    putExtra(android.content.Intent.EXTRA_TEXT, "«${quote.quoteText}» — ${book.title} (${quote.pageReference})")
                                                    type = "text/plain"
                                                }
                                                context.startActivity(android.content.Intent.createChooser(sendIntent, null))
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = "Compartir", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteBookQuote(quote.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 5. Voice Narrator (TTS) Sheet
    if (activeDockSheet == "voice") {
        ModalBottomSheet(
            onDismissRequest = { activeDockSheet = null },
            containerColor = readerBgColor,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = LanguageManager.getString("dock_voice_title", selectedLanguage),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = readerTextColor
                        )
                    }
                    IconButton(onClick = { activeDockSheet = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = readerTextColor)
                    }
                }

                // Voice Play / Pause Big Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer else readerTextColor.copy(alpha = 0.08f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isPlaying) {
                                    if (selectedLanguage == "es") "Narrador Activo" else "Narrator Active"
                                } else {
                                    if (selectedLanguage == "es") "Narrador Pausado" else "Narrator Paused"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isPlaying) MaterialTheme.colorScheme.primary else readerTextColor
                            )
                            Text(
                                text = if (selectedLanguage == "es") "Lee el contenido de la página actual" else "Reads current page contents",
                                style = MaterialTheme.typography.bodySmall,
                                color = readerTextColor.copy(alpha = 0.7f)
                            )
                        }

                        Button(
                            onClick = { viewModel.togglePlayPause() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPlaying) (if (selectedLanguage == "es") "Pausar" else "Pause") else (if (selectedLanguage == "es") "Reproducir" else "Play"))
                        }
                    }
                }

                // Speed Selector
                Text(
                    text = if (selectedLanguage == "es") "Velocidad de Voz:" else "Speech Speed:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = readerTextColor
                )

                val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    speeds.forEach { spd ->
                        val isSelected = Math.abs(playbackSpeed - spd) < 0.05f
                        OutlinedButton(
                            onClick = { viewModel.setPlaybackSpeed(spd) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent
                            ),
                            border = BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else readerTextColor.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text(
                                text = "${spd}x",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else readerTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReaderDockItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    textColor: Color,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.75f),
                    modifier = Modifier.size(22.dp)
                )
                if (badgeText != null) {
                    Box(
                        modifier = Modifier
                            .offset(x = 6.dp, y = (-4).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isActive) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.75f),
                maxLines = 1
            )
        }
    }
}

@Composable
fun AutoScrollSpeedDialog(
    currentSpeed: Float,
    isAutoScrolling: Boolean,
    isPdfVisualMode: Boolean,
    lang: String,
    onSpeedSelected: (Float) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onDismiss: () -> Unit
) {
    var tempSpeed by remember { mutableStateOf(currentSpeed) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onSpeedSelected(tempSpeed)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(if (lang == "es") "Aplicar" else "Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (lang == "es") "Cerrar" else "Close")
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (lang == "es") "Velocidad de Auto-Scroll" else "Auto-Scroll Speed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = if (isPdfVisualMode) {
                        if (lang == "es") "Ajusta la velocidad para mangas, cómics o documentos gráficos." 
                        else "Adjust scroll rate for comics, manga or graphical pages."
                    } else {
                        if (lang == "es") "Ajusta la velocidad para libros normales y texto extraído." 
                        else "Adjust scroll rate for text documents and e-books."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Current Speed Gauge Card
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (lang == "es") "Velocidad Actual" else "Current Speed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${((tempSpeed * 100).toInt()) / 100f}x",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Live Play/Pause toggle inside dialog
                        IconButton(
                            onClick = onToggleAutoScroll,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isAutoScrolling) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = if (isAutoScrolling) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isAutoScrolling) "Pausar" else "Iniciar",
                                tint = if (isAutoScrolling) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Presets Title
                Text(
                    text = if (lang == "es") "Perfiles recomendados:" else "Recommended presets:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                // Presets Chips Grid
                val presets = listOf(
                    0.5f to (if (lang == "es") "📖 Novela / Texto Lento (0.5x)" else "📖 Novel / Slow (0.5x)"),
                    1.0f to (if (lang == "es") "📄 Lectura Normal (1.0x)" else "📄 Normal Reading (1.0x)"),
                    1.8f to (if (lang == "es") "⚡ Manga / Cómic (1.8x)" else "⚡ Manga / Comic (1.8x)"),
                    3.0f to (if (lang == "es") "🚀 Manga Rápido (3.0x)" else "🚀 Fast Manga (3.0x)"),
                    4.5f to (if (lang == "es") "⏩ Exploración Rápida (4.5x)" else "⏩ Quick Skim (4.5x)")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { (presetSpeed, label) ->
                        val isSelected = Math.abs(tempSpeed - presetSpeed) < 0.05f
                        Surface(
                            onClick = { tempSpeed = presetSpeed },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Fine slider
                Text(
                    text = if (lang == "es") "Ajuste preciso:" else "Fine adjustment:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = tempSpeed,
                    onValueChange = { tempSpeed = it },
                    valueRange = 0.25f..6.0f,
                    steps = 22, // 0.25 steps
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun PdfMangaReader(
    viewModel: com.example.ui.viewmodel.AudiobookViewModel,
    filePath: String,
    initialPageIndex: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
    isImmersiveMode: Boolean = false,
    isAutoScrolling: Boolean = false,
    autoScrollSpeedFactor: Float = 1.0f,
    onToggleAutoScroll: () -> Unit = {},
    onSingleTap: () -> Unit = {},
    onDoubleTap: () -> Unit = {}
) {
    val context = LocalContext.current
    val isPdfNightInverted by viewModel.isPdfNightInverted.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialPageIndex)
    
    // Zoom & pan states
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Continuous Smooth Auto-scroll Engine for Manga / PDF Page mode
    LaunchedEffect(isAutoScrolling, autoScrollSpeedFactor) {
        if (isAutoScrolling) {
            val baseSpeed = 70f // Base pixels per second for manga / visual pages
            val pxPerSec = baseSpeed * autoScrollSpeedFactor * context.resources.displayMetrics.density
            val frameDurationMs = 16L
            while (isActive && isAutoScrolling) {
                val step = (pxPerSec * (frameDurationMs / 1000f)).coerceAtLeast(0.5f)
                lazyListState.scrollBy(step)
                kotlinx.coroutines.delay(frameDurationMs)
            }
        }
    }

    // Synchronize current visible index with ViewModel page and pre-fetch adjacent pages
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .collect { index ->
                if (index != viewModel.playbackPositionMillis.value.toInt()) {
                    viewModel.setPage(index)
                }
                viewModel.prefetchPdfPages(context, filePath, index, totalPages)
            }
    }

    // Synchronize slider drag / button clicks from ViewModel to list scroll position
    val currentPosition by viewModel.playbackPositionMillis.collectAsStateWithLifecycle()
    LaunchedEffect(currentPosition) {
        val targetPage = currentPosition.toInt()
        if (targetPage in 0 until totalPages && Math.abs(lazyListState.firstVisibleItemIndex - targetPage) > 1 && !isAutoScrolling) {
            lazyListState.scrollToItem(targetPage)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onSingleTap()
                    },
                    onDoubleTap = { centroid ->
                        onDoubleTap()
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset.Zero
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, rotation ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offset += pan
                    } else {
                        offset = Offset.Zero
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = if (isImmersiveMode) 90.dp else 140.dp),
            userScrollEnabled = (scale == 1f) // Disable list scroll when zoomed in so user can pan around easily!
        ) {
            items(totalPages) { pageIndex ->
                PdfPageItem(
                    viewModel = viewModel,
                    filePath = filePath,
                    pageIndex = pageIndex,
                    isNightInverted = isPdfNightInverted
                )
            }
        }
    }
}

@Composable
fun PdfPageItem(
    viewModel: com.example.ui.viewmodel.AudiobookViewModel,
    filePath: String,
    pageIndex: Int,
    isNightInverted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(filePath, pageIndex) { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    LaunchedEffect(filePath, pageIndex) {
        withContext(Dispatchers.IO) {
            bitmap = viewModel.getPdfPageBitmap(context, filePath, pageIndex)
        }
    }
    
    val colorFilter = if (isNightInverted) {
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    } else null

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Página PDF ${pageIndex + 1}",
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(if (isNightInverted) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White),
            contentScale = ContentScale.FillWidth,
            colorFilter = colorFilter,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(400.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
    }
}

fun getBookFolderName(filePath: String, context: android.content.Context, lang: String): String {
    if (filePath.startsWith("demo://")) {
        return if (lang == "es") "Libros de Ejemplo" else "Demo Books"
    }
    if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
        return if (lang == "es") "Transmisión / Internet" else "Streaming / Internet"
    }
    if (filePath.startsWith("content://")) {
        try {
            val uri = android.net.Uri.parse(filePath)
            val path = uri.path
            if (path != null) {
                val decodedPath = java.net.URLDecoder.decode(path, "UTF-8")
                if (decodedPath.contains(":")) {
                    val parts = decodedPath.split(":")
                    if (parts.size > 1) {
                        val subPath = parts[1]
                        val segments = subPath.split("/")
                        if (segments.size > 1) {
                            return segments[segments.size - 2]
                        }
                    }
                }
                val segments = decodedPath.split("/")
                if (segments.size > 2) {
                    return segments[segments.size - 2]
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return if (lang == "es") "Carpeta del Dispositivo" else "Device Folder"
    }
    
    try {
        val file = java.io.File(filePath)
        val parent = file.parentFile?.name
        if (!parent.isNullOrEmpty()) {
            return parent
        }
    } catch (e: Exception) {
        // ignore
    }
    
    return if (lang == "es") "Descargas / Raíz" else "Downloads / Root"
}

data class DailyQuote(val textEs: String, val authorEs: String, val textEn: String, val authorEn: String)

val dailyQuotesList = listOf(
    DailyQuote(
        "Un libro es un regalo que puedes abrir una y otra vez.", "Garrison Keillor",
        "A book is a gift you can open again and again.", "Garrison Keillor"
    ),
    DailyQuote(
        "La lectura es para la mente lo que el ejercicio es para el cuerpo.", "Joseph Addison",
        "Reading is to the mind what exercise is to the body.", "Joseph Addison"
    ),
    DailyQuote(
        "No hay amigos tan fieles como un buen libro.", "Ernest Hemingway",
        "There is no friend as loyal as a book.", "Ernest Hemingway"
    ),
    DailyQuote(
        "La voz de un buen libro puede guiarte en tus noches más oscuras.", "Anónimo",
        "The voice of a good book can guide you through your darkest nights.", "Anonymous"
    ),
    DailyQuote(
        "Leer nos da un lugar a donde ir cuando tenemos que quedarnos donde estamos.", "Mason Cooley",
        "Reading gives us someplace to go when we have to stay where we are.", "Mason Cooley"
    ),
    DailyQuote(
        "La voz humana es el instrumento más bello, especialmente al narrar una gran historia.", "Anónimo",
        "The human voice is the most beautiful instrument, especially when telling a great story.", "Anonymous"
    ),
    DailyQuote(
        "La lectura de todos los buenos libros es como una conversación con los mejores hombres de los siglos pasados.", "René Descartes",
        "The reading of all good books is like a conversation with the finest minds of past centuries.", "René Descartes"
    )
)

@Composable
fun DailyQuoteCard(selectedLanguage: String, onDismiss: (() -> Unit)? = null) {
    val dayOfYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) }
    val quote = remember(dayOfYear) { com.example.data.dailyQuotesList300[dayOfYear % com.example.data.dailyQuotesList300.size] }
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FormatQuote,
                        contentDescription = "Quote",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedLanguage == "es") "Frase del Día" else "Quote of the Day",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val textToShare = if (selectedLanguage == "es") {
                                "«${quote.textEs}» — ${quote.authorEs}\n\nCompartido desde Audire 📚"
                            } else {
                                "\"${quote.textEn}\" — ${quote.authorEn}\n\nShared from Audire 📚"
                            }
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, textToShare)
                                type = "text/plain"
                            }
                            val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Quote",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (onDismiss != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp).testTag("dismiss_quote_card")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss Quote",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (selectedLanguage == "es") quote.textEs else quote.textEn,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = FontStyle.Italic,
                    lineHeight = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (selectedLanguage == "es") "— ${quote.authorEs}" else "— ${quote.authorEn}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
