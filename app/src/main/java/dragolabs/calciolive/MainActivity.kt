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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import dragolabs.calciolive.ui.VideoPlayer
import dragolabs.calciolive.ui.theme.LiveFootballTheme
import dragolabs.calciolive.viewmodel.SportViewModel
import dragolabs.calciolive.model.Channel
import dragolabs.calciolive.model.Category
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
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
    var isNavbarVisible by remember { mutableStateOf(true) }

    val themeSurface = MaterialTheme.colorScheme.surfaceContainer
    val themeItem = MaterialTheme.colorScheme.secondaryContainer

    val homeGridState = rememberLazyGridState()
    val channelListState = rememberLazyListState()
    val infoScrollState = rememberScrollState()

    BackHandler(enabled = selectedChannel != null) { viewModel.selectChannel(null) }
    BackHandler(enabled = selectedCategory != null && selectedChannel == null) { viewModel.selectCategory(null) }

    val nestedScrollConnection = remember(currentTab, selectedCategory, isNavbarVisible) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (isLandscape) return Offset.Zero
                val canScroll = when {
                    currentTab == 1 -> infoScrollState.canScrollForward || infoScrollState.canScrollBackward
                    selectedCategory == null -> homeGridState.canScrollForward || homeGridState.canScrollBackward
                    else -> channelListState.canScrollForward || channelListState.canScrollBackward
                }
                if (canScroll) {
                    if (available.y < -25f && isNavbarVisible) isNavbarVisible = false
                    else if (available.y > 25f && !isNavbarVisible) isNavbarVisible = true
                } else { isNavbarVisible = true }
                return Offset.Zero
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(themeSurface)) {
        if (isLandscape && selectedChannel == null) {
            NavigationRail(
                containerColor = themeSurface,
                header = { Image(painterResource(id = R.drawable.app_logo), null, Modifier.size(48.dp).padding(8.dp).clip(CircleShape)) },
                modifier = Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Spacer(Modifier.weight(1f))
                NavigationRailItem(selected = currentTab == 0, onClick = { currentTab = 0; viewModel.selectCategory(null) }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                NavigationRailItem(selected = currentTab == 1, onClick = { currentTab = 1 }, icon = { Icon(Icons.Default.Info, null) }, label = { Text("Info") })
                Spacer(Modifier.weight(1f))
            }
        }

        Scaffold(
            modifier = Modifier.weight(1f).nestedScroll(nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (selectedChannel == null) {
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
                                        selectedCategory != null -> selectedCategory!!.categoryName ?: "Canali"
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
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = themeSurface)
                    )
                }
            },
            bottomBar = {
                if (!isLandscape && selectedChannel == null) {
                    AnimatedVisibility(
                        visible = isNavbarVisible,
                        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(400)),
                        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(400))
                    ) {
                        Surface(color = themeSurface, tonalElevation = 3.dp) {
                            NavigationBar(modifier = Modifier.height(100.dp).navigationBarsPadding(), containerColor = Color.Transparent) {
                                NavigationBarItem(selected = currentTab == 0, onClick = { currentTab = 0; viewModel.selectCategory(null); isNavbarVisible = true }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                                NavigationBarItem(selected = currentTab == 1, onClick = { currentTab = 1; isNavbarVisible = true }, icon = { Icon(Icons.Default.Info, null) }, label = { Text("Info") })
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()).background(themeSurface).padding(horizontal = 4.dp).background(Color.Black)) {
                if (currentTab == 1) InfoScreen(infoScrollState)
                else HomeScreen(viewModel, isLandscape, homeGridState, channelListState, themeItem)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: SportViewModel, isLandscape: Boolean, gridState: LazyGridState, listState: LazyListState, itemColor: Color) {
    val categories by viewModel.categories.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(refreshing) { if (refreshing) { viewModel.loadCategories(); delay(1500); refreshing = false } }

    AnimatedContent(targetState = Pair(selectedCategory, selectedChannel), label = "") { (cat, ch) ->
        // Contenitore per AnimatedContent che garantisce la misurazione a tutto schermo del contenuto destro
        Box(Modifier.fillMaxSize()) {
            when {
                ch != null -> VideoPlayer(ch.channelUrl, ch.agent, ch.eh1, ch.origin) { viewModel.selectChannel(null) }
                cat == null -> {
                    // FIX CENTRAGGIO PULL-TO-REFRESH SU TABLET
                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = { refreshing = true },
                        modifier = Modifier.fillMaxSize() // Cruciale: occupa tutto il Box padre
                    ) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(if (isLandscape) 4 else 2),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) { items(categories) { CategoryCard(it) { viewModel.selectCategory(it) } } }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp, start = 8.dp, end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) { items(channels) { ChannelCard(it, itemColor) { viewModel.selectChannel(it) } } }
                }
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
        Text("Contattaci", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dragolabs.vercel.app/"))) },
            modifier = Modifier.fillMaxWidth().height(56.dp).focusable(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = monetColor)
        ) { Text("Sito Web", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:info.dragolabs@gmail.com") }) },
            modifier = Modifier.fillMaxWidth().height(56.dp).focusable(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = monetColor)
        ) { Text("E-mail", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
fun InfoSection(subtitle: String, body: String, color: Color) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(subtitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = Color.LightGray, fontSize = 11.sp)
    }
}

@Composable
fun ChannelCard(channel: Channel, bgColor: Color, onClick: () -> Unit) {
    val localTimeInfo = remember(channel.liveTime) { convertGmtToLocal(channel.liveTime) }
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = bgColor, modifier = Modifier.fillMaxWidth().focusable()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = channel.channelImage,
                contentDescription = null,
                modifier = Modifier.size(105.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Nome Partita: Bold 17sp
                Text("⚽ ${channel.channelName}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

                Spacer(Modifier.height(4.dp))

                // Data e Ora: UNICA RIGA (dd-mm-yyyy hh:mm), font 14sp Bold
                Text("📅 $localTimeInfo", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                // Rimosso Spacer e la riga "📡 Server 1 - HD"
            }
        }
    }
}

fun convertGmtToLocal(gmtString: String?): String {
    if (gmtString.isNullOrBlank()) return "Live"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT+6") }
        val date = inputFormat.parse(gmtString) ?: return gmtString
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { time = date }
        val timePart = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        val datePart = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(date)
        if (now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR) && now.get(Calendar.YEAR) == target.get(Calendar.YEAR)) "Today • $timePart" else "$datePart $timePart"
    } catch (e: Exception) { gmtString }
}

@Composable
fun CategoryCard(category: Category, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().aspectRatio(1f).clickable { onClick() }.focusable(), shape = RoundedCornerShape(16.dp)) {
        Box {
            AsyncImage(
                model = category.categoryImage,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f)), startY = 120f)))
            Text(category.categoryName ?: "", Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color.White, fontWeight = FontWeight.ExtraBold)
        }
    }
}