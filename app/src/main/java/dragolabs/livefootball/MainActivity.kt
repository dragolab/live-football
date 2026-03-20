package dragolabs.livefootball

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import dragolabs.livefootball.model.Category
import dragolabs.livefootball.model.Channel
import dragolabs.livefootball.ui.theme.LiveFootballTheme
import dragolabs.livefootball.ui.VideoPlayer
import dragolabs.livefootball.viewmodel.SportViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveFootballTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: SportViewModel = viewModel()

    NavHost(navController = navController, startDestination = "categories") {
        composable("categories") {
            CategoryScreen(viewModel, navController)
        }
        composable(
            "channels/{categoryId}",
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getString("categoryId") ?: ""
            ChannelScreen(categoryId, viewModel, navController)
        }
        composable(
            "videoPlayer/{channelId}/{categoryId}",
            arguments = listOf(
                navArgument("channelId") { type = NavType.StringType },
                navArgument("categoryId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
            val channels by viewModel.channels.collectAsState()
            val channel = channels.find { it.channelId == channelId }
            if (channel != null) {
                VideoPlayer(
                    url = channel.channelUrl,
                    userAgent = channel.agent,
                    referer = channel.eh1,
                    origin = channel.origin
                )
            }
        }
        composable(
            "webView/{channelUrl}/{agent}",
            arguments = listOf(
                navArgument("channelUrl") { type = NavType.StringType },
                navArgument("agent") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("channelUrl") ?: ""
            val url = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
            val encodedAgent = backStackEntry.arguments?.getString("agent") ?: ""
            val agent = URLDecoder.decode(encodedAgent, StandardCharsets.UTF_8.toString())
            WebViewContent(url, agent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(viewModel: SportViewModel, navController: NavController) {
    val categories by viewModel.categories.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        if (categories.isEmpty()) {
            viewModel.loadCategories()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Sports Categories") }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (categories.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Nessun dato trovato", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadCategories() }) {
                        Text("Riprova")
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(categories) { category ->
                        CategoryItem(category) {
                            navController.navigate("channels/${category.cid ?: ""}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryItem(category: Category, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = category.categoryImage ?: "",
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                text = category.categoryName ?: "Unknown",
                modifier = Modifier.padding(8.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(categoryId: String, viewModel: SportViewModel, navController: NavController) {
    val channels by viewModel.channels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(categoryId) {
        viewModel.loadChannels(categoryId)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Channels") }) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (channels.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Nessun canale trovato")
                    Button(onClick = { viewModel.loadChannels(categoryId) }) {
                        Text("Riprova")
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(channels) { channel ->
                        ChannelItem(channel) {
                            val isNative = channel.channelType.lowercase().let { it == "exo" || it == "url" }
                            if (isNative) {
                                navController.navigate("videoPlayer/${channel.channelId}/$categoryId")
                            } else {
                                val encodedUrl = URLEncoder.encode(channel.channelUrl ?: "", StandardCharsets.UTF_8.toString())
                                val agent = URLEncoder.encode(channel.agent ?: "", StandardCharsets.UTF_8.toString())
                                navController.navigate("webView/$encodedUrl/$agent")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelItem(channel: Channel, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(channel.channelName ?: "Unknown", fontWeight = FontWeight.Bold) },
        supportingContent = { Text((channel.channelType ?: "N/A").uppercase(), color = Color.Gray) },
        modifier = Modifier.clickable { onClick() }
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.LightGray)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContent(url: String, agent: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = agent
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript(
                            """
                            (function() {
                                var ads = document.querySelectorAll('.ads, .advertisement, #header, #footer');
                                for(var i=0; i<ads.length; i++) ads[i].style.display='none';
                                var video = document.querySelector('video');
                                if(video) {
                                    video.style.position = 'fixed';
                                    video.style.top = '0';
                                    video.style.left = '0';
                                    video.style.width = '100%';
                                    video.style.height = '100%';
                                    video.style.zIndex = '9999';
                                }
                            })();
                            """.trimIndent(), null
                        )
                    }
                }
                webChromeClient = WebChromeClient()
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
