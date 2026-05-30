package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.data.SurahMetadata
import com.example.data.SurahRepository
import com.example.data.api.AyahData
import com.example.data.local.Bookmark
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val factory = remember { QuranViewModelFactory(context) }
                val viewModel: QuranViewModel = viewModel(factory = factory)

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    QuranAppContainer(viewModel = viewModel)
                }
            }
        }
    }
}

// --- App Navigation State Container ---

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun QuranAppContainer(viewModel: QuranViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    AppScreen.Dashboard -> DashboardScreen(viewModel = viewModel)
                    AppScreen.Reader -> ReaderScreen(viewModel = viewModel)
                    AppScreen.TafseerDetail -> TafseerDetailScreen(viewModel = viewModel)
                }
            }
        }
    }
}

// --- Dashboard Screen ---

@Composable
fun DashboardScreen(viewModel: QuranViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val surahList by viewModel.surahList.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Full Quran, 1 = Bookmarks & Notes

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // High-fidelity Royal Geometric Dashboard Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
                .padding(vertical = 24.dp, horizontal = 20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Arabic Insignia Centerpiece
                Text(
                    text = "نُورِ قُرْآن",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        letterSpacing = 2.sp
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "کلامِ الہی مع ترجمہ کنز الایمان و تفسیری رہنمائی",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Beautiful filled search bar for instantly matching Quran Surahs
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = {
                        Text(
                            text = "سورت کا نام، نمبر یا مفہوم تلاش کریں...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                            )
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .testTag("surah_search_input"),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        }

        // Custom Navigation Tabs (Full Quran vs Bookmarks & Reflected Notes)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier.testTag("tab_full_quran")
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Quran icon",
                        tint = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "تفصیلِ قرآن (114)",
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                modifier = Modifier.testTag("tab_bookmarks")
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Bookmarks icon",
                        tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "زخیرہ آیات و مطالعہ (${bookmarks.size})",
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        // Display Active List based on selected Tab
        if (selectedTab == 0) {
            if (surahList.isEmpty()) {
                EmptyStateView(
                    imageVector = Icons.Default.Warning,
                    title = "کوئی نتیجہ نہیں ملا",
                    description = "تلاش کردہ لفظ کے نام کے ساتھ کوئی سورت دستیاب نہیں ہے۔ براۓ مہربانی دوبارہ چیک کریں۔"
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(surahList, key = { it.number }) { surah ->
                        SurahRowItem(
                            surah = surah,
                            onTapped = { viewModel.selectSurah(surah) }
                        )
                    }
                }
            }
        } else {
            if (bookmarks.isEmpty()) {
                EmptyStateView(
                    imageVector = Icons.Default.Star,
                    title = "کوئی آیت محفوظ نہیں ہے",
                    description = "تلاوت قران کے دوران خوبصورت 'ستارے' والے آئیکن کو تفسیری و معلوماتی تبصرہ کے ليے محفوظ کریں تاکہ وہ یہاں آپ کے مطالعہ کے لیے جمع ہو سکیں۔"
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        BookmarkCardItem(
                            bookmark = bookmark,
                            onNavigateToSurah = {
                                // Find Surah metadata and load
                                val surahMeta = SurahRepository.surahsRef.firstOrNull { it.number == bookmark.surahNumber }
                                if (surahMeta != null) {
                                    viewModel.selectSurah(surahMeta)
                                }
                            },
                            onOpenTafseer = {
                                val ayahPair = AyahPair(
                                    index = bookmark.ayahNumber,
                                    arabic = AyahData(bookmark.ayahNumber, bookmark.arabicText, bookmark.ayahNumber, 1, 1, 1, 1, 1),
                                    urdu = AyahData(bookmark.ayahNumber, bookmark.urduTranslation, bookmark.ayahNumber, 1, 1, 1, 1, 1),
                                    isBookmarked = true
                                )
                                // Configure active surah in VM
                                val surahMeta = SurahRepository.surahsRef.firstOrNull { it.number == bookmark.surahNumber }
                                if (surahMeta != null) {
                                    viewModel.selectSurah(surahMeta)
                                    viewModel.selectAyahForTafseer(ayahPair)
                                }
                            },
                            onDelete = { viewModel.removeBookmarkDirect(bookmark.id) },
                            onSavePersonalNote = { updatedNote ->
                                viewModel.updateBookmarkPersonalNote(bookmark, updatedNote)
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- Empty Placeholder UX ---

@Composable
fun EmptyStateView(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = "Empty location placeholder",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// --- Surah Item Card Row ---

@Composable
fun SurahRowItem(
    surah: SurahMetadata,
    onTapped: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onTapped() }
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .testTag("surah_item_${surah.number}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Surah Number in Gold Shape Badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = CircleShape
                    )
                    .border(1.dp, MaterialTheme.colorScheme.tertiary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = surah.number.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Primary metadata info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = surah.nameEnglish,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Makki/Madani Custom Chips
                    Text(
                        text = if (surah.revelationType.lowercase() == "meccan") "مکّی" else "مدنی",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${surah.translationEnglish} • ${surah.numberOfAyahs} آیات",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                )
            }

            // Arabic title stack
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = surah.nameArabic,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textDirection = TextDirection.Rtl
                    )
                )
                Text(
                    text = surah.nameUrdu,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.tertiary
                    )
                )
            }
        }
    }
}

// --- Active Surah Reader Screen ---

@Composable
fun ReaderScreen(viewModel: QuranViewModel) {
    val surah by viewModel.selectedSurah.collectAsStateWithLifecycle()
    val contentState by viewModel.surahContentState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Font Size settings flow
    var arabicFontSize by remember { mutableFloatStateOf(26f) }
    var urduFontSize by remember { mutableFloatStateOf(16f) }
    var showFontSizeControls by remember { mutableStateOf(false) }

    // Search/filtering verses
    var verseSearchQuery by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Reader Tool Bar
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = 12.dp, horizontal = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { viewModel.navigateTo(AppScreen.Dashboard) },
                        modifier = Modifier.testTag("btn_back_dashboard")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = surah?.nameEnglish ?: "تلاوتِ قرآن",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${surah?.nameUrdu} (تعارف: ${surah?.translationEnglish})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.tertiary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Font adjustment toggle button
                    IconButton(onClick = { showFontSizeControls = !showFontSizeControls }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Adjust font size selector",
                            tint = if (showFontSizeControls) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )
        }

        // Expanded Font Sizing Controls Panel
        if (showFontSizeControls) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "عربی رسم الخط کا سائز: ${arabicFontSize.toInt()}sp",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Slider(
                            value = arabicFontSize,
                            onValueChange = { arabicFontSize = it },
                            valueRange = 20f..46f,
                            modifier = Modifier.width(180.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "اردو ترجمہ سائز: ${urduFontSize.toInt()}sp",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Slider(
                            value = urduFontSize,
                            onValueChange = { urduFontSize = it },
                            valueRange = 14f..26f,
                            modifier = Modifier.width(180.dp)
                        )
                    }
                }
            }
        }

        // Inline Verse Search Field (Helps study inside long chapters)
        TextField(
            value = verseSearchQuery,
            onValueChange = { verseSearchQuery = it },
            placeholder = {
                Text(
                    text = "اس سورت میں ترجمہ کا لفظ تلاش کریں...",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "search verse",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (verseSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { verseSearchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "clear search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .heightIn(max = 44.dp)
                .clip(RoundedCornerShape(8.dp)),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        // Rendering screen statuses
        when (val state = contentState) {
            is SurahContentState.Idle -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is SurahContentState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "سرور سے ترجمہ کنز الایمان حاصل کیا جا رہا ہے...",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
            is SurahContentState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier.padding(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error cloud connection offline",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = state.message,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { surah?.let { viewModel.loadSurahVerses(it.number) } },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("دوبارہ کوشش کریں")
                            }
                        }
                    }
                }
            }
            is SurahContentState.Success -> {
                val filteredVerses = remember(state.versesCombined, verseSearchQuery) {
                    if (verseSearchQuery.isBlank()) {
                        state.versesCombined
                    } else {
                        state.versesCombined.filter {
                            it.urdu.text.contains(verseSearchQuery, ignoreCase = true) ||
                            it.arabic.text.contains(verseSearchQuery) ||
                            it.index.toString() == verseSearchQuery
                        }
                    }
                }

                if (filteredVerses.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("کوئی آیت لفظِ تلاش سے میل نہیں کھاتی۔", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Bismillah banner header for all Surahs except At-Tawbah (No. 9)
                        if (surah?.number != 9 && verseSearchQuery.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontFamily = FontFamily.Serif,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                    )
                                }
                            }
                        }

                        items(filteredVerses, key = { it.index }) { pair ->
                            VerseReaderItem(
                                rawAyah = pair,
                                arabicSize = arabicFontSize,
                                urduSize = urduFontSize,
                                onBookmarkToggle = { viewModel.toggleBookmark(pair) },
                                onShowTafseer = { viewModel.selectAyahForTafseer(pair) },
                                onCopy = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val formattedClip = "(${pair.arabic.text})\n\nترجمہ کنز الایمان: (${pair.urdu.text})\n[Surah: ${surah?.nameEnglish}, Ayah #${pair.index}]"
                                    val clip = ClipData.newPlainText("Quran Verse", formattedClip)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "آیت پاک کی تحریر کلپ بورڈ میں نقل کی گئی!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Individual Verse Row Item Renderer ---

@Composable
fun VerseReaderItem(
    rawAyah: AyahPair,
    arabicSize: Float,
    urduSize: Float,
    onBookmarkToggle: () -> Unit,
    onShowTafseer: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .testTag("verse_item_${rawAyah.index}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Verse Header bar: Index & Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Ayah Index Badge
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "آیت #${rawAyah.index}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                // Interactive action icons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Copy
                    Button(
                        onClick = onCopy,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = null
                    ) {
                        Text(
                            text = "کاپی",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        )
                    }

                    // Bookmark
                    IconButton(onClick = onBookmarkToggle, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Save favorite",
                            tint = if (rawAyah.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Sparkle AI Tafseer Trigger
                    Button(
                        onClick = onShowTafseer,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("btn_ayah_tafseer_${rawAyah.index}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = null
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Sparkle brain AI icon",
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تفسیرِ کنز الایمان",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Classical Stunning Arab Layout (Right to left, Arabic typography)
            Text(
                text = rawAyah.arabic.text,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = arabicSize.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = (arabicSize * 1.6f).sp,
                    textDirection = TextDirection.Rtl,
                    textAlign = TextAlign.Right
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            // Horizontal Golden geometric line divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Urdu translation Kanzul Iman (Right to left, distinctive styling)
            Text(
                text = rawAyah.urdu.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = urduSize.sp,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = (urduSize * 1.5f).sp,
                    textDirection = TextDirection.Rtl,
                    textAlign = TextAlign.Right
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// --- Bookmark Card Item (Displays in dashboard saved tab) ---

@Composable
fun BookmarkCardItem(
    bookmark: Bookmark,
    onNavigateToSurah: () -> Unit,
    onOpenTafseer: () -> Unit,
    onDelete: () -> Unit,
    onSavePersonalNote: (String) -> Unit
) {
    var personalNoteText by remember { mutableStateOf(bookmark.personalNote) }
    var isEditingNote by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .testTag("bookmark_card_${bookmark.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "saved item bookmark tag",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${bookmark.surahName} (آیت #${bookmark.ayahNumber})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Row {
                    // Navigate context info
                    IconButton(onClick = onNavigateToSurah, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "read full context",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    // Delete
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "delete bookmark",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Arabic text excerpt
            Text(
                text = bookmark.arabicText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    textDirection = TextDirection.Rtl,
                    textAlign = TextAlign.Right,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )

            // Urdu Translation
            Text(
                text = bookmark.urduTranslation,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Serif,
                    textDirection = TextDirection.Rtl,
                    textAlign = TextAlign.Right,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // User Personal Reflection Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.03f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "میرا ذاتی ذہنی خاکہ (سیکھا ہوا سبق/رائے):",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    IconButton(
                        onClick = {
                            if (isEditingNote) {
                                onSavePersonalNote(personalNoteText)
                            }
                            isEditingNote = !isEditingNote
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isEditingNote) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = "edit reflection note",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (isEditingNote) {
                    OutlinedTextField(
                        value = personalNoteText,
                        onValueChange = { personalNoteText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp),
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = {
                            Text(
                                "یہاں اس آیت سے اپنی سیکھی ہوئی بات، دعا، یا عزم درج کریں... جیسے 'حاجت برآری کے لیے وظیفہ' یا 'روزمرہ اعمال کا محاسبہ’",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                    )
                } else {
                    Text(
                        text = if (personalNoteText.isBlank()) "ابھی کوئی تفسیری نوٹ درج نہیں ہے (تبدیل کرنے کے ليے 'پنسل' والے بٹن پر کلک کیجیے)۔" else personalNoteText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (personalNoteText.isBlank()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action button to expand deep Tafseer panel
            Button(
                onClick = onOpenTafseer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Depper scholarly AI assistant",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "جیمنائی تفصیلی روحانی تفسیر کھولیے",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

// --- Tafseer Detail Screen (Provides exegesis and Follow up Q&A chatbot) ---

@Composable
fun TafseerDetailScreen(viewModel: QuranViewModel) {
    val surah by viewModel.selectedSurah.collectAsStateWithLifecycle()
    val activeAyahPair by viewModel.selectedAyahPair.collectAsStateWithLifecycle()
    val tafseerState by viewModel.tafseerState.collectAsStateWithLifecycle()
    val chatMessages by viewModel.tafseerChat.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var userQuestion by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Bar
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.navigateTo(AppScreen.Reader) },
                    modifier = Modifier.testTag("btn_back_reader")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "back reader screen",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "تفسیر کنز الایمان",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "${surah?.nameEnglish} (${surah?.nameUrdu}) • آیت #${activeAyahPair?.index}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    )
                }

                // Share exegesis content action
                IconButton(
                    onClick = {
                        val exegesis = (tafseerState as? TafseerState.Success)?.explanation ?: ""
                        val formattedShare = "📖 [تفسیر کنز الایمان و مفصل جائزہ]\n\nآیت مبارکہ: (${activeAyahPair?.arabic?.text})\nترجمہ: (${activeAyahPair?.urdu?.text})\n\n${exegesis.take(800)}...\n\n(Noor-ul-Quran ایپ کے ذریعے روحانی مطالعہ)"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Share exegesis", formattedShare))
                        Toast.makeText(context, "تفسیر اور تبصرہ شیئر کرنے کے ليے کاپی کر لیا گیا ہے!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )
        }

        // Main content vertical scroller
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Verse Header Card (Arabic at top)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = activeAyahPair?.arabic?.text ?: "",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Serif,
                                color = MaterialTheme.colorScheme.onSurface,
                                textDirection = TextDirection.Rtl,
                                textAlign = TextAlign.Right
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = activeAyahPair?.urdu?.text ?: "",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Serif,
                                color = MaterialTheme.colorScheme.primary,
                                textDirection = TextDirection.Rtl,
                                textAlign = TextAlign.Right
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Exegesis State display section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    // Left vertical indicator bar
                    Box(
                        modifier = Modifier
                            .align(Alignment.Top)
                            .padding(top = 2.dp)
                            .height(40.dp)
                            .width(4.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Gemini exegesis content analyzer",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "علمی مطالعہ و خلاصہ عقلانیت (AI Tafseer)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            when (val state = tafseerState) {
                                is TafseerState.Idle -> {
                                    Text("سروس کو کال کرنے کا عمل شروع ہو رہا ہے...")
                                }
                                is TafseerState.Loading -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "جیمنائی سرور کلاسیکی کتبِ تفاسیر سے مستند حقائق ترتیب دے رہا ہے...",
                                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                                is TafseerState.Error -> {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("تفصیل پیش کرنے میں خامی ہوئی: ${state.message}", color = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(onClick = { activeAyahPair?.let { viewModel.selectAyahForTafseer(it) } }) {
                                            Text("دوبارہ کو شش کریں")
                                        }
                                    }
                                }
                                is TafseerState.Success -> {
                                    MarkdownText(text = state.explanation)
                                }
                            }
                        }
                    }
                }
            }

            // Interactive Spiritual follow-up chat segment
            item {
                Text(
                    text = "تعمیری مکالمہ و روحانی سوالات (Q&A)",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Direct Chat Thread render loop
            if (chatMessages.isEmpty()) {
                item {
                    Text(
                        text = "اس آیت کے عملی پہلوؤں، مفہوم و معنی کے متعلق اپنے اندیشے، دعائیں یا تاریخی شبہات پوچھنے کے لیے بلا تکلف نیچے لکھیں۔ جیمنائی رہنمائی کرے گا۔",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }
            } else {
                items(chatMessages) { message ->
                    ChatMessageItem(msg = message)
                }
            }

            // Spinner loader for chat response
            if (isChatLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Sticky chat field input at the bottom of the details window
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = userQuestion,
                    onValueChange = { userQuestion = it },
                    placeholder = {
                        Text(
                            text = "آیت سے متعلق کوئی سوال پوچھیں... (جیسے: 'اس سے کیا دعا مانگیں؟')",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .testTag("chat_input_field"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (userQuestion.isNotBlank()) {
                                viewModel.sendChatMessage(userQuestion)
                                userQuestion = ""
                                keyboardController?.hide()
                            }
                        }
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (userQuestion.isNotBlank()) {
                            viewModel.sendChatMessage(userQuestion)
                            userQuestion = ""
                            keyboardController?.hide()
                        }
                    },
                    modifier = Modifier
                        .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape)
                        .size(46.dp)
                        .testTag("btn_send_chat"),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message btn",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// --- Chat Message Bubble Renderer ---

@Composable
fun ChatMessageItem(msg: ChatMessage) {
    val isUser = msg.sender == "user"
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentTextColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 2.dp, bottomEnd = 12.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .background(bubbleColor, shape)
                .padding(10.dp)
        ) {
            MarkdownText(text = msg.text, colorOverride = contentTextColor)
        }
    }
}

// --- High-Fidelity Markdown Parser Composable ---

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    colorOverride: Color? = null
) {
    val lines = text.split("\n")
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("###") -> {
                    val content = trimmed.substring(3).trim()
                    Text(
                        text = content,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = colorOverride ?: MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        textAlign = TextAlign.Right
                    )
                }
                trimmed.startsWith("##") -> {
                    val content = trimmed.substring(2).trim()
                    Text(
                        text = content,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = colorOverride ?: MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        ),
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                        textAlign = TextAlign.Right
                    )
                }
                trimmed.startsWith("*") -> {
                    val content = trimmed.substring(1).trim()
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "• ",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colorOverride ?: MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = parseBoldText(content),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colorOverride ?: MaterialTheme.colorScheme.onSurface
                            ),
                            textAlign = TextAlign.Right
                        )
                    }
                }
                trimmed.startsWith("-") -> {
                    val content = trimmed.substring(1).trim()
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "• ",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colorOverride ?: MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = parseBoldText(content),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colorOverride ?: MaterialTheme.colorScheme.onSurface
                            ),
                            textAlign = TextAlign.Right
                        )
                    }
                }
                else -> {
                    if (trimmed.isNotEmpty()) {
                        Text(
                            text = parseBoldText(trimmed),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = colorOverride ?: MaterialTheme.colorScheme.onSurface
                            ),
                            textAlign = TextAlign.Right
                        )
                    }
                }
            }
        }
    }
}

// Bold markdown formatter helper
fun parseBoldText(text: String): AnnotatedString {
    return buildAnnotatedString {
        val parts = text.split("**")
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                append(part)
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), this.length - part.length, this.length)
            } else {
                append(part)
            }
        }
    }
}
