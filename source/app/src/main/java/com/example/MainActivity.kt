package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
    }

    override fun onResume() {
        super.onResume()
        checkStoragePermission()
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
    var selectedTab by rememberSaveable { mutableStateOf("Library") }
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
                    onTabSelected = { selectedTab = it },
                    lang = selectedLanguage
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (selectedTab == "Reader") PaddingValues(0.dp) else innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                "Library" -> LibraryScreen(
                    viewModel = viewModel,
                    onNavigateToPlayer = { selectedTab = "Player" },
                    onNavigateToReader = { selectedTab = "Reader" }
                )
                "Reader" -> ReaderScreen(
                    viewModel = viewModel,
                    onNavigateBack = { selectedTab = "Library" }
                )
                "Player" -> PlayerScreen(
                    viewModel = viewModel,
                    onNavigateToReader = { selectedTab = "Reader" }
                )
                "Stats" -> StatsScreen(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarView(
    selectedTab: String,
    onSearchClick: () -> Unit
) {
    CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Audire",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        actions = {
            if (selectedTab == "Library") {
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.testTag("scan_trigger_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Escanear almacenamiento",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@Composable
fun BottomNavigationBar(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    lang: String
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets.navigationBars
    ) {
        NavigationBarItem(
            selected = selectedTab == "Library",
            onClick = { onTabSelected("Library") },
            label = { Text(LanguageManager.getString("nav_library", lang), style = MaterialTheme.typography.labelSmall) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == "Library") Icons.Filled.AutoStories else Icons.Outlined.AutoStories,
                    contentDescription = "Library Tab"
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier.testTag("tab_library")
        )
        
        NavigationBarItem(
            selected = selectedTab == "Player",
            onClick = { onTabSelected("Player") },
            label = { Text(LanguageManager.getString("nav_player", lang), style = MaterialTheme.typography.labelSmall) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == "Player") Icons.Filled.PlayCircle else Icons.Outlined.PlayCircle,
                    contentDescription = "Player Tab"
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier.testTag("tab_player")
        )
        
        NavigationBarItem(
            selected = selectedTab == "Stats",
            onClick = { onTabSelected("Stats") },
            label = { Text(LanguageManager.getString("nav_stats", lang), style = MaterialTheme.typography.labelSmall) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == "Stats") Icons.Filled.TrendingUp else Icons.Outlined.TrendingUp,
                    contentDescription = "Stats Tab"
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier.testTag("tab_stats")
        )
        
        NavigationBarItem(
            selected = selectedTab == "Settings",
            onClick = { onTabSelected("Settings") },
            label = { Text(LanguageManager.getString("nav_settings", lang), style = MaterialTheme.typography.labelSmall) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == "Settings") Icons.Filled.Settings else Icons.Outlined.Settings,
                    contentDescription = "Settings Tab"
                )
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier.testTag("tab_settings")
        )
    }
}

// -------------------------------------------------------------
// VISTA 1: biblioteca (LIBRARY SCREEN)
// -------------------------------------------------------------
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
    val context = LocalContext.current
    val storagePerm by viewModel.storagePermissionGranted.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()

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
                text = if (selectedLanguage == "es") "Biblioteca" else "Library",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

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
                        "No se encontraron audiolibros reales en tu dispositivo. Selecciona la carpeta donde guardas tus archivos para poder escanearlos y reproducirlos."
                    } else {
                        "No real audiobooks found on your device. Select the folder where you store your files to scan and play them."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))
                
                Button(
                    onClick = {
                        val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            permissionToRequest
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (isGranted) {
                            viewModel.storagePermissionGranted.value = true
                            localDirPickerLauncher.launch(null)
                        } else {
                            permissionLauncher.launch(permissionToRequest)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (selectedLanguage == "es") "Permitir y Seleccionar Carpeta" else "Allow & Select Folder")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(books, key = { it.id }) { book ->
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
                        }
                    )
                }
            }
        }
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
    onToggleFavorite: () -> Unit
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
            // Smaller book cover icon frame (64.dp)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFE1E4FF),
                                Color(0xFFD1E4FF)
                            )
                        )
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (book.coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(book.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Portada de ${book.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp)
                    )
                }
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
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}


// -------------------------------------------------------------
// VISTA 3: AJUSTES Y DIAGNÓSTICO (SETTINGS SCREEN)
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

    // Piper TTS State Flows
    val downloadedVoices by viewModel.downloadedVoices.collectAsStateWithLifecycle()
    val voiceProgress by viewModel.voiceDownloadProgress.collectAsStateWithLifecycle()
    val piperVoiceId by viewModel.piperSelectedVoiceId.collectAsStateWithLifecycle()
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
            Toast.makeText(context, "Permiso de almacenamiento concedido", Toast.LENGTH_SHORT).show()
            viewModel.scanDeviceStorage()
        } else {
            Toast.makeText(context, "Permiso denegado", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = LanguageManager.getString("sys_config", selectedLanguage),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            text = LanguageManager.getString("settings_title", selectedLanguage),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        Spacer(modifier = Modifier
            .width(48.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary))
        
        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 0: Language Selector
        Text(
            text = LanguageManager.getString("lang_section", selectedLanguage),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            border = borderStroke()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = LanguageManager.getString("lang_label", selectedLanguage).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val languages = listOf(
                        "es" to "Español (ES)",
                        "en" to "English (EN)"
                    )
                    languages.forEach { (code, label) ->
                        val isSelected = selectedLanguage == code
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
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
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION Theme Selector & Palette Customizer
        Text(
            text = LanguageManager.getString("theme_section", selectedLanguage),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            border = borderStroke()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = LanguageManager.getString("theme_mode_label", selectedLanguage).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

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
                                .clip(RoundedCornerShape(16.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable { viewModel.selectedThemeMode.value = modeId }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.selectedThemeMode.value = modeId }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
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
            }
        }

        if (themeMode == "custom") {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = borderStroke()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = LanguageManager.getString("theme_custom_title", selectedLanguage),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // 1) Primary color picker
                    Text(
                        text = LanguageManager.getString("theme_primary_color", selectedLanguage),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                                    .size(32.dp)
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 2) Background color picker
                    Text(
                        text = LanguageManager.getString("theme_bg_color", selectedLanguage),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                                    .size(32.dp)
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 3) Secondary color picker
                    Text(
                        text = LanguageManager.getString("theme_secondary_color", selectedLanguage),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                                    .size(32.dp)
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 1: Permissions
        Text(
            text = LanguageManager.getString("permissions", selectedLanguage),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
            PermissionToggleCard(
                title = LanguageManager.getString("bg_playback", selectedLanguage),
                subtitle = LanguageManager.getString("bg_playback_desc", selectedLanguage),
                checked = bgPlay,
                onCheckedChange = { viewModel.backgroundPlaybackEnabled.value = it }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 2: Auto-save Interval Selector
        Text(
            text = LanguageManager.getString("sync_frequency", selectedLanguage),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            border = borderStroke()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = LanguageManager.getString("sync_freq_desc", selectedLanguage),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
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
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // SECCIÓN PIPER TTS - SOLO CONFIGURACIÓN DE VOZ DE SISTEMA
        Text(
            text = if (selectedLanguage == "es") "Personalización de Voz" else "Voice Personalization",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            border = borderStroke()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Explanation Header
                Text(
                    text = if (selectedLanguage == "es") 
                        "Calibra los parámetros de entonación y expresividad para la narración fluida de tus libros electrónicos (.epub y .pdf) utilizando el sintetizador local."
                    else 
                        "Calibrate tone and expressiveness parameters for fluid narrative reading of your e-books (.epub and .pdf) using the local system synthesizer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(12.dp))
                val context = LocalContext.current
                Button(
                    onClick = {
                        val intent = android.content.Intent("com.android.settings.TTS_SETTINGS")
                        context.startActivity(intent)
                    }
                ) {
                    Text(if (selectedLanguage == "es") "Configurar Motor TTS" else "Configure TTS Engine")
                }

                // List of catalog voices
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (false) viewModel.piperCatalog.forEach { voice ->
                        val isDownloaded = downloadedVoices.contains(voice.id)
                        val progress = voiceProgress[voice.id]
                        val isSelected = piperVoiceId == voice.id

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    else Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (isDownloaded) {
                                        viewModel.piperSelectedVoiceId.value = voice.id
                                    }
                                }
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = voice.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            // Quality badge
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = voice.quality,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${voice.language} • ${voice.sizeMb} MB",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Action buttons
                                    Box {
                                        if (progress != null) {
                                            // Downloading State
                                            Column(horizontalAlignment = Alignment.End) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${(progress * 100).toInt()}%",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else if (isDownloaded) {
                                            // Downloaded State
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (selectedLanguage == "es") "Activa" else "Active",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                } else {
                                                    TextButton(
                                                        onClick = { viewModel.piperSelectedVoiceId.value = voice.id },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) {
                                                        Text(
                                                            text = if (selectedLanguage == "es") "Usar" else "Use",
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                IconButton(
                                                    onClick = { viewModel.deletePiperVoice(voice.id) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete voice",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            // Not Downloaded State
                                            Button(
                                                onClick = { viewModel.downloadPiperVoice(voice.id) },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                ),
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDownward,
                                                    contentDescription = "Download",
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (selectedLanguage == "es") "Descargar" else "Download",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = voice.description,
                                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Adjustments (noise calibration, speed, pitch, naturalness)
                Text(
                    text = (if (selectedLanguage == "es") "AJUSTES ELECTRÓNICOS DE MOTOR" else "NEURAL VOICE CALIBRATION").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

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
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (selectedLanguage == "es") 
                            "noise_scale: Controla la consistencia de respiración y timbre del narrador."
                        else 
                            "noise_scale: Calibrates vocal timber consistency and simulated breath patterns.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

                Spacer(modifier = Modifier.height(16.dp))

                // 2) Expressiveness slider (noise_w)
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
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (selectedLanguage == "es") 
                            "noise_w: Calibra la velocidad fonológica poética y expresividad del acento."
                        else 
                            "noise_w: Adjusts phonological speed variation and accent expressiveness.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

                Spacer(modifier = Modifier.height(20.dp))

                // Test utterance demo button
                Button(
                    onClick = {
                        val text = if (selectedLanguage == "es") {
                            "Hola. Has configurado correctamente los parámetros de entonación para reproducir tus libros locales sin conexión a internet."
                        } else {
                            "Hello. You have successfully configured your local voice wave calibration parameters to narrate offline."
                        }
                        viewModel.speakTest(text)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = true,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Test voice calibration"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedLanguage == "es") "Probar configuración de voz" else "Test current voice configuration",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 3: Scan Directories
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = LanguageManager.getString("scan_dirs", selectedLanguage),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = LanguageManager.getString("add_dir", selectedLanguage),
                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.primary),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        dirPickerLauncher.launch(null)
                    }
                    .padding(4.dp)
            )
        }
        
        Column(
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            if (directories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                    DirectoryItemRow(
                        directory = dir,
                        onDeleteClick = { viewModel.removeScanDirectory(dir.path) }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 4: Diagnostics
        Text(
            text = LanguageManager.getString("diagnostics", selectedLanguage),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                    badgeText = if (selectedLanguage == "es") "Limpiar caché" else "Clear Cache",
                    badgeColor = Color.Transparent,
                    onBadgeClick = { viewModel.clearApplicationCache() },
                    modifier = Modifier.weight(1f)
                )
                DiagnosticCard(
                    title = "API VERSION",
                    value = "v2.4.1-stable",
                    badgeText = if (selectedLanguage == "es") "Actualizar" else "Check updates",
                    badgeColor = Color.Transparent,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // SECTION 5: Sims of system interruptions
        Text(
            text = if (selectedLanguage == "es") "Pruebas y Simulaciones" else "System Testing & Simulator",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = if (selectedLanguage == "es") "Simula situaciones del sistema para comprobar que el progreso se guarda de manera inmediata y transaccional." else "Trigger mock OS state changes to confirm instant transacting & audio-restore routines.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.simulateSystemInterruption("Llamada entrante") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).testTag("sim_call_button")
            ) {
                Text(if (selectedLanguage == "es") "Llamada" else "Call Interrupt", style = MaterialTheme.typography.labelSmall)
            }
            Button(
                onClick = { viewModel.simulateSystemInterruption("Headset desconectado") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1.2f).testTag("sim_headset_button")
            ) {
                Text(if (selectedLanguage == "es") "Desconectar audíf." else "Unplug Headset", style = MaterialTheme.typography.labelSmall)
            }
            Button(
                onClick = { viewModel.simulateSystemInterruption("Cierre forzado") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).testTag("sim_close_button")
            ) {
                Text(if (selectedLanguage == "es") "Cierre" else "Crash App", style = MaterialTheme.typography.labelSmall)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = if (selectedLanguage == "es") "Reiniciar y Purgar Datos" else "Reset & Purge Data",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = if (selectedLanguage == "es") "Elimina de forma permanente todo el historial de reproducción, directorios locales y archivos cargados en el dispositivo para empezar desde cero." else "Delete all listening history, statistics logs, cached libraries, and configuration settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Button(
            onClick = {
                viewModel.resetDatabase()
                Toast.makeText(context, if (selectedLanguage == "es") "Base de datos restablecida con éxito" else "Database reset successfully", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag("reset_db_button")
        ) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (selectedLanguage == "es") "Restablecer Base de Datos y Limpiar Biblioteca" else "Reset Database and Purge Library")
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))

        // Diagnostic Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (selectedLanguage == "es") "Conexión: En línea" else "Server Connection: Online", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (selectedLanguage == "es") "Almacenamiento: Saludable" else "Storage: Healthy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (selectedLanguage == "es") "Sincronización: Activa" else "Sync: Active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                text = "Audire Build 2026.06.10",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
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
        val list = mutableListOf<Pair<String, Long>>()
        val calendar = java.util.Calendar.getInstance()
        for (i in 6 downTo 0) {
            val cloneCal = calendar.clone() as java.util.Calendar
            cloneCal.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val dateStr = sdf.format(cloneCal.time)
            val label = if (i == 0) "Hoy" else dayFormat.format(cloneCal.time).uppercase()
            
            val duration = logs.find { it.date == dateStr }?.durationMillis ?: 0L
            list.add(Pair(label, duration))
        }
        list
    }

    val maxDuration = remember(daysList) {
        daysList.maxOfOrNull { it.second }?.coerceAtLeast(1000L * 60) ?: (3600000L) // scale dynamically, minimal 1 minute
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        daysList.forEach { (dayLabel, duration) ->
            val hours = duration / (1000f * 60 * 60)
            val minutes = (duration % (1000 * 60 * 60)) / (1000 * 60)
            val heightFraction = (duration.toFloat() / maxDuration).coerceIn(0f, 1f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Gray background track
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    )
                    
                    // Value filled bar track
                    if (heightFraction > 0.02f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(heightFraction)
                                .width(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = if (dayLabel == "Hoy") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (dayLabel == "Hoy") FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = if (hours >= 1f) "${String.format("%.1f", hours)}h" else "${minutes}m",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 9.sp,
                    color = if (dayLabel == "Hoy") MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (dayLabel == "Hoy") FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun StatsScreen(viewModel: AudiobookViewModel) {
    val logs by viewModel.listeningLogs.collectAsStateWithLifecycle()
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

    val (rankName, nextLevelLabel, currentLvlProgress) = when {
        totalHours < 1f -> Triple("Novato", "Lector (1 h)", (totalHours / 1f).coerceIn(0f, 1f))
        totalHours < 5f -> Triple("Lector", "Maestro (5 h)", ((totalHours - 1f) / 4f).coerceIn(0f, 1f))
        totalHours < 15f -> Triple("Maestro", "Sabio (15 h)", ((totalHours - 5f) / 10f).coerceIn(0f, 1f))
        else -> Triple("Sabio de Audire", "Máximo Nivel ✨", 1.0f)
    }

    val rankText = when (rankName) {
        "Novato" -> if (selectedLanguage == "es") "Novato" else "Rookie"
        "Lector" -> if (selectedLanguage == "es") "Lector" else "Avid Listener"
        "Maestro" -> if (selectedLanguage == "es") "Maestro" else "Grandmaster"
        else -> if (selectedLanguage == "es") "Sabio de Audire" else "Audire Sage"
    }

    val nextLevelText = when {
        totalHours < 1f -> if (selectedLanguage == "es") "Lector (1 h)" else "Listener (1 h)"
        totalHours < 5f -> if (selectedLanguage == "es") "Maestro (5 h)" else "Grandmaster (5 h)"
        totalHours < 15f -> if (selectedLanguage == "es") "Sabio (15 h)" else "Sage (15 h)"
        else -> if (selectedLanguage == "es") "Máximo Nivel ✨" else "Maximum Rank ✨"
    }

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.45f
    val statsCardBg = if (isLight) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val currentStreakCardBg = if (isLight) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. PROGRESO DIARIO ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = statsCardBg
            ),
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
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(160.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { dailyProgressFraction },
                        modifier = Modifier.size(150.dp),
                        strokeWidth = 14.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (todayHours > 0) "${todayHours}h ${todayMinutes}m" else "${todayMinutes}m",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${(dailyProgressFraction * 100).toInt()}% " + (if (selectedLanguage == "es") "de la meta" else "of daily goal"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
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

        // --- 2. MULTI-RACHAS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Racha actual
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = currentStreakCardBg
                ),
                border = borderStroke(),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f).testTag("stats_current_streak_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Whatshot,
                        contentDescription = "Racha actual",
                        tint = Color(0xFFFF9800), // Streak Orange Flame
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = LanguageManager.getString("stats_curr_streak", selectedLanguage),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "$currentStreak ${if (currentStreak == 1) (if (selectedLanguage == "es") "día" else "day") else (if (selectedLanguage == "es") "días" else "days")}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = if (currentStreak > 0) LanguageManager.getString("stats_fuego", selectedLanguage) else LanguageManager.getString("stats_inicia", selectedLanguage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // Racha Máxima
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = statsCardBg
                ),
                border = borderStroke(),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f).testTag("stats_max_streak_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = "Racha máxima",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = LanguageManager.getString("stats_max_streak", selectedLanguage),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$maxStreak ${if (maxStreak == 1) (if (selectedLanguage == "es") "día" else "day") else (if (selectedLanguage == "es") "días" else "days")}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = LanguageManager.getString("stats_rec_hours", selectedLanguage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // --- 3. BAR CHART HISTORIAL ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = statsCardBg
            ),
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
                    Text(
                        text = LanguageManager.getString("stats_weekly", selectedLanguage),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = LanguageManager.getString("stats_total", selectedLanguage) + ": ${String.format("%.1f", totalHours)} h",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                ListeningBarChart(logs = logs)
            }
        }

        // --- 4. RANGO Y LOGROS ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = statsCardBg
            ),
            border = borderStroke(),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().testTag("stats_achievements_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (rankName) {
                                    "Novato" -> Icons.Filled.Star
                                    "Lector" -> Icons.Filled.AutoStories
                                    "Maestro" -> Icons.Filled.EmojiEvents
                                    else -> Icons.Filled.WorkspacePremium
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = LanguageManager.getString("stats_rank", selectedLanguage) + ": $rankText",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedLanguage == "es") "Tiempo total: ${String.format("%.1f", totalHours)} horas" else "Total time: ${String.format("%.1f", totalHours)} hours",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LinearProgressIndicator(
                    progress = { currentLvlProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = LanguageManager.getString("stats_next_rank", selectedLanguage) + ":",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = nextLevelText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // --- 5. HERRAMIENTAS DE PRUEBA DE ESTADISTICAS (SIMULADOR) ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
            ),
            border = borderStroke(),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().testTag("stats_simulator_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = LanguageManager.getString("stats_tester", selectedLanguage),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = LanguageManager.getString("stats_tester_desc", selectedLanguage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.addManualListeningLogs(15) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.weight(1f).testTag("simulate_add_15")
                    ) {
                        Text(LanguageManager.getString("stats_minutes_add", selectedLanguage))
                    }
                    
                    Button(
                        onClick = { viewModel.subtractManualListeningLogs(15) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f).testTag("simulate_sub_15")
                    ) {
                        Text(LanguageManager.getString("stats_minutes_sub", selectedLanguage))
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { viewModel.clearListeningHistory() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("simulate_clear_logs")
                ) {
                    Text(LanguageManager.getString("stats_clear", selectedLanguage))
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
    val activeBook by viewModel.currentPlayingBook.collectAsStateWithLifecycle()
    val positionMillis by viewModel.playbackPositionMillis.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val epubPages by viewModel.epubPages.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var isPdfTextMode by rememberSaveable { mutableStateOf(false) }
    var readerFontSize by rememberSaveable { mutableStateOf(18) }
    var isImmersiveMode by rememberSaveable { mutableStateOf(false) }
    var showImmersiveHint by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(4000)
        showImmersiveHint = false
    }

    // Book theme background colors
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val readerBgColor = if (isSystemDark) {
        Color(0xFF151515)
    } else {
        Color(0xFFFBF0D9) // Warm book parchment!
    }
    val readerTextColor = if (isSystemDark) {
        Color(0xFFE3E3E3)
    } else {
        Color(0xFF2C2518) // Rich dark-sepia text
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
    val totalPages = book.durationMillis
    val currentPageIndex = positionMillis.toInt()

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
                        Text(
                            text = if (isPdf) "PDF Document" else "EPUB E-book",
                            style = MaterialTheme.typography.labelSmall,
                            color = readerTextColor.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = readerTextColor
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // PDF Reading mode toggle
                        if (isPdf) {
                            IconButton(
                                onClick = { 
                                    isPdfTextMode = !isPdfTextMode 
                                    // Make sure text pages are populated if switched on
                                    if (isPdfTextMode && epubPages.isEmpty()) {
                                        viewModel.selectBook(book)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isPdfTextMode) Icons.Filled.MenuBook else Icons.Filled.Article,
                                    contentDescription = if (isPdfTextMode) "Ver Hoja Original" else "Modo de Lectura Fluida",
                                    tint = readerTextColor
                                )
                            }
                        }

                        // Elegant Font Size control row (for EPUB or PDF text mode)
                        if (!isPdf || isPdfTextMode) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(readerTextColor.copy(alpha = 0.05f))
                                    .border(1.dp, readerTextColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 2.dp)
                            ) {
                                IconButton(
                                    onClick = { if (readerFontSize > 12) readerFontSize -= 2 },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text(
                                        text = "A-",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = readerTextColor
                                    )
                                }
                                Text(
                                    text = "$readerFontSize",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = readerTextColor,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                IconButton(
                                    onClick = { if (readerFontSize < 36) readerFontSize += 2 },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text(
                                        text = "A+",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = readerTextColor
                                    )
                                }
                            }
                        }

                        // Unified Voice Playback Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = readerTextColor
                            )
                            IconButton(onClick = { viewModel.cyclePlaybackSpeed() }) {
                                Icon(
                                    imageVector = Icons.Default.ShutterSpeed,
                                    contentDescription = "Velocidad de narración",
                                    tint = readerTextColor
                                )
                            }
                            IconButton(
                                onClick = { 
                                    // If PDF text is empty, prepare it on click
                                    if (isPdf && epubPages.isEmpty()) {
                                        viewModel.selectBook(book)
                                    }
                                    viewModel.togglePlayPause() 
                                },
                                modifier = Modifier.testTag("narrator_toggle_btn")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                                    contentDescription = "Narrador de voz",
                                    tint = if (isPlaying) MaterialTheme.colorScheme.primary else readerTextColor,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
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
                tonalElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, readerTextColor.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Page Progress Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedLanguage == "es") "Página ${currentPageIndex + 1} de $totalPages" else "Page ${currentPageIndex + 1} of $totalPages",
                            style = MaterialTheme.typography.labelMedium,
                            color = readerTextColor,
                            fontWeight = FontWeight.Bold
                        )
                        // Reading state label
                        if (isPlaying) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedLanguage == "es") "Narrador Activo..." else "Narrating Aloud...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = if (totalPages > 1) currentPageIndex.toFloat() / (totalPages - 1).toFloat() else 0f,
                        onValueChange = { scale ->
                            val targetPage = (scale * (totalPages - 1)).toInt()
                            viewModel.setPage(targetPage)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = readerTextColor,
                            activeTrackColor = readerTextColor,
                            inactiveTrackColor = readerTextColor.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("reader_page_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Next/Prev Page Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.skipBackward() },
                            enabled = currentPageIndex > 0,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Página anterior",
                                tint = if (currentPageIndex > 0) readerTextColor else readerTextColor.copy(alpha = 0.3f)
                            )
                        }

                        // Text instructions to tap/swipe
                        Text(
                            text = if (selectedLanguage == "es") "Desliza para hojear libro" else "Slide to flip pages",
                            style = MaterialTheme.typography.labelSmall,
                            color = readerTextColor.copy(alpha = 0.5f),
                            fontStyle = FontStyle.Italic
                        )

                        IconButton(
                            onClick = { viewModel.skipForward() },
                            enabled = currentPageIndex < totalPages - 1,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowForward,
                                contentDescription = "Siguiente página",
                                tint = if (currentPageIndex < totalPages - 1) readerTextColor else readerTextColor.copy(alpha = 0.3f)
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
                .padding(if (isImmersiveMode) 0.dp else 16.dp)
        ) {
            if (isPdf && !isPdfTextMode) {
                PdfMangaReader(
                    viewModel = viewModel,
                    filePath = book.filePath,
                    initialPageIndex = currentPageIndex,
                    totalPages = totalPages.toInt(),
                    modifier = Modifier.fillMaxSize(),
                    isImmersiveMode = isImmersiveMode,
                    onDoubleTap = { isImmersiveMode = !isImmersiveMode }
                )
            } else {
                val pagerState = rememberPagerState(initialPage = currentPageIndex) { epubPages.size }
                
                LaunchedEffect(currentPageIndex) {
                    if (pagerState.currentPage != currentPageIndex) {
                        pagerState.animateScrollToPage(currentPageIndex)
                    }
                }
                
                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage != currentPageIndex) {
                        viewModel.setPage(pagerState.currentPage)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val pageText = if (page in epubPages.indices) epubPages[page] else "Cargando página..."
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        isImmersiveMode = !isImmersiveMode
                                    }
                                )
                            },
                        verticalArrangement = Arrangement.Top
                    ) {
                        item {
                            Text(
                                text = pageText,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                    lineHeight = (readerFontSize * 1.5).sp,
                                    fontSize = readerFontSize.sp
                                ),
                                color = readerTextColor,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

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
                                    "Doble toque para pantalla completa" 
                                else 
                                    "Double tap to toggle full screen",
                                style = MaterialTheme.typography.labelMedium.copy(color = Color.White)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPageView(
    viewModel: com.example.ui.viewmodel.AudiobookViewModel,
    filePath: String,
    pageIndex: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(filePath, pageIndex) { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    LaunchedEffect(filePath, pageIndex) {
        withContext(Dispatchers.IO) {
            bitmap = viewModel.getPdfPageBitmap(context, filePath, pageIndex)
        }
    }
    
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Página PDF ${pageIndex + 1}",
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
fun PdfMangaReader(
    viewModel: com.example.ui.viewmodel.AudiobookViewModel,
    filePath: String,
    initialPageIndex: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
    isImmersiveMode: Boolean = false,
    onDoubleTap: () -> Unit = {}
) {
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialPageIndex)
    
    // Zoom & pan states
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Synchronize current visible index with ViewModel page so that status bar and slider update
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .collect { index ->
                if (index != viewModel.playbackPositionMillis.value.toInt()) {
                    viewModel.setPage(index)
                }
            }
    }

    // Synchronize slider drag / button clicks from ViewModel to list scroll position
    val currentPosition by viewModel.playbackPositionMillis.collectAsStateWithLifecycle()
    LaunchedEffect(currentPosition) {
        val targetPage = currentPosition.toInt()
        if (targetPage in 0 until totalPages && Math.abs(lazyListState.firstVisibleItemIndex - targetPage) > 1) {
            lazyListState.scrollToItem(targetPage)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
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
            contentPadding = PaddingValues(bottom = if (isImmersiveMode) 16.dp else 120.dp),
            userScrollEnabled = (scale == 1f) // Disable list scroll when zoomed in so user can pan around easily!
        ) {
            items(totalPages) { pageIndex ->
                PdfPageItem(
                    viewModel = viewModel,
                    filePath = filePath,
                    pageIndex = pageIndex
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(filePath, pageIndex) { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    LaunchedEffect(filePath, pageIndex) {
        withContext(Dispatchers.IO) {
            bitmap = viewModel.getPdfPageBitmap(context, filePath, pageIndex)
        }
    }
    
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Página PDF ${pageIndex + 1}",
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            contentScale = ContentScale.FillWidth
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
