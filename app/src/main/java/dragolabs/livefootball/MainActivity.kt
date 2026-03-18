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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
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
import dragolabs.livefootball.viewmodel.SportViewModel
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveFootballTheme {
                AppNavigation()
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
            "exoPlayer/{channelId}/{categoryId}",
            arguments = listOf(
                navArgument("channelId") { type = NavType.StringType },
                navArgument("categoryId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
            val categoryId = backStackEntry.arguments?.getString("categoryId") ?: ""
            val channel = viewModel.channels.collectAsState().value.find { it.channelId == channelId }
            if (channel != null) {
                ExoPlayerScreen(channel, viewModel)
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
            WebViewScreen(url, agent)
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
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = padding,
                modifier = Modifier.fillMaxSize()
            ) {
                items(categories) { category ->
                    CategoryItem(category) {
                        navController.navigate("channels/${category.cid}")
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
            .clickable { onClick() }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = category.categoryImage,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                text = category.categoryName,
                modifier = Modifier.padding(8.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
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
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(contentPadding = padding) {
                items(channels) { channel ->
                    ChannelItem(channel) {
                        if (channel.channelType == "exo") {
                            navController.navigate("exoPlayer/${channel.channelId}/$categoryId")
                        } else {
                            val encodedUrl = URLEncoder.encode(channel.channelUrl, StandardCharsets.UTF_8.toString())
                            val agent = URLEncoder.encode(channel.agent ?: "", StandardCharsets.UTF_8.toString())
                            navController.navigate("webView/$encodedUrl/$agent")
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
        headlineContent = { Text(channel.channelName) },
        supportingContent = { Text(channel.channelType.uppercase()) },
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
fun ExoPlayerScreen(initialChannel: Channel, viewModel: SportViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentUrl by remember { mutableStateOf(initialChannel.channelUrl) }
    var isRetrying by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (!isRetrying && initialChannel.cUrl != null) {
                        isRetrying = true
                        scope.launch {
                            val newUrl = viewModel.refreshChannelUrl(initialChannel)
                            if (newUrl != null) {
                                currentUrl = newUrl
                                val dataSourceFactory = DefaultHttpDataSource.Factory()
                                    .setUserAgent(initialChannel.agent ?: "Mozilla/5.0")
                                    .setDefaultRequestProperties(mapOf(
                                        "Referer" to (initialChannel.eh1?.replace("Referer: ", "") ?: ""),
                                        "Origin" to (initialChannel.origin ?: "")
                                    ))
                                val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
                                    .createMediaSource(MediaItem.fromUri(newUrl))
                                setMediaSource(mediaSource)
                                prepare()
                                play()
                            }
                        }
                    }
                }
            })
        }
    }

    LaunchedEffect(currentUrl) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(initialChannel.agent ?: "Mozilla/5.0")
            .setDefaultRequestProperties(mapOf(
                "Referer" to (initialChannel.eh1?.replace("Referer: ", "") ?: ""),
                "Origin" to (initialChannel.origin ?: "")
            ))
        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(currentUrl))
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(url: String, agent: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = agent
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Inject JS to hide ads/unwanted elements and try to force fullscreen
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
