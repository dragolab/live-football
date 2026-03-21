package dragolabs.calciolive.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay
import java.util.*

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(url: String, userAgent: String?, referer: String?, origin: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val view = LocalView.current

    // Identificazione stream (HLS, DASH, SS o Embed)
    val isVideoLink = remember(url) {
        val l = url.lowercase()
        l.contains(".m3u8") || l.contains(".mpd") || l.contains(".mp4") || l.contains("mpegts") || l.contains(".ism")
    }
    val isEmbed = remember(url) { !isVideoLink && (url.contains("embed") || url.contains("html")) }

    var visible by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    // 3. LOGICA USER-AGENT ORIGINALE (Metodo B)
    val finalUserAgent = remember(userAgent) {
        if (userAgent.isNullOrBlank()) {
            "${System.getProperty("java.vm.name")}/${System.getProperty("java.vm.version")} (Linux; U; Android ${Build.VERSION.RELEASE}; ${Build.MODEL} Build/${Build.ID})"
        } else userAgent
    }

    // Gestione Insets MD3
    DisposableEffect(Unit) {
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    if (isEmbed) {
        WebViewPlayer(url, finalUserAgent, onBack)
    } else {
        val exoPlayer = remember(url) {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(finalUserAgent)
                .setConnectTimeoutMs(15000) // Timeout come p1.r
                .setReadTimeoutMs(15000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(buildMap {
                    if (!referer.isNullOrBlank()) put("Referer", referer)
                    if (!origin.isNullOrBlank()) put("Origin", origin)
                })

            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory))
                .build().apply {
                    setMediaItem(buildMediaItemFromOriginalLogic(url))
                    prepare()
                    playWhenReady = true
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            isLoading = (state == Player.STATE_BUFFERING)
                            if (state == Player.STATE_READY) isError = false
                        }
                        override fun onPlayerError(error: PlaybackException) {
                            isError = true
                            isLoading = false
                        }
                    })
                }
        }

        DisposableEffect(exoPlayer) { onDispose { exoPlayer.stop(); exoPlayer.release() } }
        LaunchedEffect(visible) { if (visible && !isError) { delay(5000); visible = false } }

        Box(Modifier.fillMaxSize().background(Color.Black).clickable(remember { MutableInteractionSource() }, null) { visible = !visible }) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading && !isError) CircularProgressIndicator(Modifier.align(Alignment.Center))

            if (isError) {
                ErrorOverlay(onRetry = { exoPlayer.prepare(); exoPlayer.play() }, onBack = onBack)
            }

            AnimatedVisibility(visible = visible && !isError, enter = fadeIn(), exit = fadeOut()) {
                PlayerControls(onBack, exoPlayer, activity)
            }
        }
    }
}

// 4. LOGICA ORIGINALE: DECODIFICA DRM E FORMAT DETECTION
@OptIn(UnstableApi::class)
private fun buildMediaItemFromOriginalLogic(url: String): MediaItem {
    val uri = Uri.parse(url)
    val lowerUrl = url.lowercase()

    val mimeType = when {
        lowerUrl.contains(".mpd") -> MimeTypes.APPLICATION_MPD
        lowerUrl.contains(".ism") -> MimeTypes.APPLICATION_SS
        lowerUrl.contains(".mp4") -> MimeTypes.VIDEO_MP4
        lowerUrl.contains("mpegts") -> "video/mpeg2"
        else -> MimeTypes.APPLICATION_M3U8
    }

    val builder = MediaItem.Builder()
        .setUri(uri)
        .setMimeType(mimeType)

    // Gestione DRM Widevine (Metodo G() dell'app originale)
    if (lowerUrl.contains(".mpd") && lowerUrl.contains("drmlicense")) {
        val licenseParam = uri.getQueryParameter("drmLicense")
        if (licenseParam != null && licenseParam.contains(":")) {
            val drmData = licenseParam.split(":")
            // Invertiamo o applichiamo le chiavi in base alla licenza trovata
            builder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(drmData[0]) // Spesso il primo è l'URL di licenza
                    .build()
            )
        }
    }

    return builder.build()
}

@Composable
fun WebViewPlayer(url: String, userAgent: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = WebViewClient()
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        userAgentString = userAgent
                    }
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(16.dp).statusBarsPadding().background(Color.Black.copy(0.5f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
        }
    }
}

@Composable
fun PlayerControls(onBack: () -> Unit, player: Player, activity: Activity?) {
    val controlColor = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(0.7f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

    Box(Modifier.fillMaxSize().padding(24.dp)) {
        FilledIconButton(onClick = onBack, Modifier.align(Alignment.TopStart), colors = controlColor) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
        }
        Row(Modifier.align(Alignment.BottomCenter), Arrangement.spacedBy(16.dp), Alignment.CenterVertically) {
            var isPlaying by remember { mutableStateOf(true) }
            FilledIconButton(onClick = { if(isPlaying) player.pause() else player.play(); isPlaying = !isPlaying }, Modifier.size(56.dp), colors = controlColor) {
                Icon(if(isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(32.dp))
            }
            Button(onClick = { player.seekToDefaultPosition() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), shape = CircleShape) {
                Text("LIVE", fontWeight = FontWeight.Bold, color = Color.White)
            }
            FilledIconButton(onClick = {
                activity?.requestedOrientation = if (activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }, Modifier.size(56.dp), colors = controlColor) {
                Icon(Icons.Default.ScreenRotation, null)
            }
        }
    }
}

@Composable
fun ErrorOverlay(onRetry: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.Black).padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Icon(Icons.Default.ErrorOutline, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text("Errore Stream", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text("Il server richiede l'app ufficiale per questa qualità", color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, Modifier.fillMaxWidth().height(52.dp)) { Text("Riprova") }
        OutlinedButton(onClick = onBack, Modifier.fillMaxWidth().padding(top = 12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White), border = BorderStroke(1.dp, Color.White.copy(0.5f))) { Text("Indietro") }
    }
}