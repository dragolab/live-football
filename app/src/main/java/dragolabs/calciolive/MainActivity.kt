package dragolabs.calciolive

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import dragolabs.calciolive.ui.VideoPlayer
import dragolabs.calciolive.ui.theme.LiveFootballTheme
import dragolabs.calciolive.viewmodel.SportViewModel
import dragolabs.calciolive.model.Channel
import dragolabs.calciolive.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveFootballTheme {
                val viewModel: SportViewModel = viewModel()
                MainScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: SportViewModel) {
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    var currentTab by remember { mutableIntStateOf(0) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val themeSurface = MaterialTheme.colorScheme.surfaceContainer
    val appBackground = MaterialTheme.colorScheme.background

    val homeGridState = rememberLazyGridState()
    val channelListState = rememberLazyListState()
    val infoScrollState = rememberScrollState()

    BackHandler(enabled = selectedChannel != null) { viewModel.selectChannel(null) }
    BackHandler(enabled = selectedCategory != null && selectedChannel == null) { viewModel.selectCategory(null) }

    // FIX: Creiamo un riferimento "fantasma" per mantenere in vita il player durante l'animazione di uscita
    var activeChannel by remember { mutableStateOf<Channel?>(null) }
    LaunchedEffect(selectedChannel) {
        if (selectedChannel != null) {
            activeChannel = selectedChannel
        }
    }

    Box(Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize().background(appBackground)) {
            if (isLandscape && selectedChannel == null) {
                NavigationRail(
                    containerColor = themeSurface,
                    modifier = Modifier.width(80.dp).fillMaxHeight()
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Image(
                            painterResource(id = R.drawable.app_logo), null,
                            Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 16.dp).size(48.dp).clip(CircleShape)
                        )
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            NavigationRailItem(selected = currentTab == 0, onClick = { currentTab = 0; viewModel.selectCategory(null) }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                            NavigationRailItem(selected = currentTab == 1, onClick = { currentTab = 1 }, icon = { Icon(Icons.Default.Info, null) }, label = { Text("Info") })
                        }
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    Surface(color = themeSurface) {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!isLandscape && currentTab == 0 && selectedCategory == null) {
                                        Image(painterResource(id = R.drawable.app_logo), null, Modifier.size(32.dp).clip(CircleShape))
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Text(buildAnnotatedString {
                                        val titleText = when {
                                            currentTab == 1 -> "Informazioni"
                                            selectedCategory != null -> translateCategory(selectedCategory!!.categoryName ?: "")
                                            else -> "Calcio Live "
                                        }
                                        withStyle(SpanStyle(fontWeight = FontWeight.Black, fontSize = 20.sp)) { append(titleText) }
                                        if (currentTab == 0 && selectedCategory == null) {
                                            withStyle(SpanStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)) { append("by Drago Labs") }
                                        }
                                    })
                                }
                            },
                            navigationIcon = {
                                if (selectedCategory != null) {
                                    IconButton(onClick = { viewModel.selectCategory(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                                }
                            },
                            modifier = Modifier.statusBarsPadding(),
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    }
                },
                bottomBar = {
                    if (!isLandscape) {
                        Surface(color = themeSurface, tonalElevation = 3.dp) {
                            NavigationBar(modifier = Modifier.height(100.dp).navigationBarsPadding(), containerColor = Color.Transparent) {
                                NavigationBarItem(selected = currentTab == 0, onClick = { currentTab = 0; viewModel.selectCategory(null) }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                                NavigationBarItem(selected = currentTab == 1, onClick = { currentTab = 1 }, icon = { Icon(Icons.Default.Info, null) }, label = { Text("Info") })
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(Modifier.fillMaxSize().padding(innerPadding)) {
                    // FIX: Animazione di transizione tra le Tab
                    AnimatedContent(
                        targetState = currentTab,
                        label = "TabTransition",
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> width } + fadeIn(tween(300))).togetherWith(
                                    slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> -width } + fadeOut(tween(300)))
                            } else {
                                (slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> -width } + fadeIn(tween(300))).togetherWith(
                                    slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> width } + fadeOut(tween(300)))
                            }
                        }
                    ) { tab ->
                        if (tab == 1) InfoScreen(infoScrollState)
                        else HomeScreen(viewModel, isLandscape, homeGridState, channelListState)
                    }
                }
            }
        }

        // Overlay Player: Ora aspetta l'uscita grazie al "activeChannel"
        AnimatedVisibility(
            visible = selectedChannel != null,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            ) + fadeOut(animationSpec = tween(200)),
            modifier = Modifier.fillMaxSize().zIndex(10f)
        ) {
            activeChannel?.let { ch ->
                VideoPlayer(ch) { viewModel.selectChannel(null) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: SportViewModel, isLandscape: Boolean, gridState: LazyGridState, listState: LazyListState) {
    val categories by viewModel.categories.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState()

    LaunchedEffect(refreshing) { if (refreshing) { viewModel.loadCategories(); delay(1500); refreshing = false } }

    AnimatedContent(
        targetState = selectedCategory,
        label = "ScreenTransition",
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> width } + fadeIn(tween(300))).togetherWith(
                    slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> -width } + fadeOut(tween(300)))
            } else {
                (slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> -width } + fadeIn(tween(300))).togetherWith(
                    slideOutHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> width } + fadeOut(tween(300)))
            }
        }
    ) { cat ->
        Box(Modifier.fillMaxSize()) {
            if (cat == null) {
                PullToRefreshBox(
                    isRefreshing = refreshing, onRefresh = { refreshing = true }, state = refreshState, modifier = Modifier.fillMaxSize(),
                    indicator = { Indicator(modifier = Modifier.align(Alignment.TopCenter).offset(x = if (isLandscape) (-40).dp else 0.dp), isRefreshing = refreshing, state = refreshState) }
                ) {
                    LazyVerticalGrid(
                        state = gridState, columns = GridCells.Fixed(if (isLandscape) 4 else 2), contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(categories, key = { it.categoryName ?: it.hashCode() }) {
                            CategoryCardMD3(it, isLandscape) { viewModel.selectCategory(it) }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(channels, key = { it.channelId ?: it.hashCode() }) {
                        ChannelCard(it) { viewModel.selectChannel(it) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryCardMD3(category: Category, isLandscape: Boolean, onClick: () -> Unit) {
    val leagueName = translateCategory(category.categoryName ?: "")
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    var circleColor by remember { mutableStateOf(Color.White) }
    var hasExtractedColor by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Card(
        onClick = onClick, interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth().aspectRatio(if (isLandscape) 1.15f else 0.85f).border(width = if (isFocused) 3.dp else 0.dp, color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent, shape = RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.size(90.dp), contentAlignment = Alignment.Center) {
                Surface(modifier = Modifier.size(85.dp), shape = CircleShape, color = circleColor, tonalElevation = 2.dp) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current).data(category.categoryImage).allowHardware(false).build(),
                        contentDescription = null, modifier = Modifier.padding(14.dp).fillMaxSize(), contentScale = ContentScale.Fit,
                        onSuccess = { state ->
                            // FIX: Lettura pixel spostata in un thread secondario. ZERO stuttering.
                            if (!hasExtractedColor) {
                                hasExtractedColor = true
                                coroutineScope.launch(Dispatchers.Default) {
                                    try {
                                        val bitmap = (state.result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                        if (bitmap != null && bitmap.width > 5) {
                                            val pixel = bitmap.getPixel(2, 2)
                                            if (android.graphics.Color.alpha(pixel) == 255) {
                                                val c = Color(pixel)
                                                withContext(Dispatchers.Main) { circleColor = c }
                                            }
                                        }
                                    } catch (e: Exception) {}
                                }
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(text = leagueName, style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp), fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Card(
        onClick = onClick, interactionSource = interactionSource, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().border(width = if (isFocused) 3.dp else 0.dp, color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent, shape = RoundedCornerShape(16.dp))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(110.dp).aspectRatio(1.5f).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                AsyncImage(model = channel.channelImage, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(channel.channelName ?: "", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 16.sp)
                Text("Live", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun InfoScreen(scrollState: ScrollState) {
    val context = LocalContext.current
    val monetColor = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp)) {
        InfoSection("Attenzione!", "Questa applicazione è stata sviluppata esclusivamente a scopo di test, studio e dimostrazione tecnica. Non è un servizio commerciale e non è destinata alla distribuzione pubblica o all'uso continuativo.", monetColor)
        InfoSection("Diritti d'Autore e Contenuti", "Drago Labs non trasmette, non ospita e non memorizza alcun contenuto multimediale sui propri server. Tutti i flussi video sono forniti da terze parti e sono accessibili sul web. L'app funge esclusivamente da player tecnico per testare la stabilità dello streaming. I loghi, i nomi delle squadre e i marchi delle competizioni appartengono ai rispettivi proprietari e sono utilizzati qui solo a fini illustrativi", monetColor)
        InfoSection("Responsabilità dell'Utente", "L'utente è l'unico responsabile dell'uso dell'applicazione e della verifica della legalità dei contenuti visualizzati nel proprio paese di residenza. Drago Labs declina ogni responsabilità per eventuali usi impropri o violazioni del copyright.", monetColor)
        Spacer(Modifier.height(32.dp))
        Text("Contattaci", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dragolabs.vercel.app/"))) }, modifier = Modifier.fillMaxWidth().height(56.dp).focusable(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = monetColor)) { Text("Sito Web", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:info.dragolabs@gmail.com") }) }, modifier = Modifier.fillMaxWidth().height(56.dp).focusable(), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = monetColor)) { Text("E-mail", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
fun InfoSection(subtitle: String, body: String, color: Color) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(subtitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
}

fun translateCategory(name: String): String {
    val clean = name.lowercase(Locale.ROOT).trim()
    return when {
        clean.contains("premier league") -> "Premier League"
        clean.contains("serie a") -> "Serie A"
        clean.contains("la liga") -> "LaLiga"
        clean.contains("ligue 1") -> "Ligue 1"
        clean.contains("bundesliga") -> "Bundesliga"
        clean.contains("primeira liga") -> "Primeira Liga Portoghese"
        clean.contains("super league") -> "Super League Greca"
        clean.contains("serie b") -> "Serie B"
        clean.contains("efl championship") -> "EFL Championship"
        clean.contains("super lig") -> "Süper Lig Turca"
        clean.contains("scottish premiership") -> "Scottish Premiership"
        clean.contains("mls") -> "MLS"
        clean.contains("saudi pro league") -> "Saudi Pro League"
        clean.contains("champions league") -> "UEFA Champions League"
        clean.contains("europa league") -> "UEFA Europa League"
        clean.contains("conference league") -> "UEFA Conference League"
        else -> name
    }
}