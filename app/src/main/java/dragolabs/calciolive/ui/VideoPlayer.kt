package dragolabs.calciolive.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.ui.text.style.TextAlign
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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import dragolabs.calciolive.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(channel: Channel, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    val view = LocalView.current

    val originalUrl = channel.channelUrl ?: ""
    val userAgent = channel.agent
    val referer = channel.eh1
    val origin = channel.origin
    val channelId = channel.channelId

    val channelType = channel.channelType?.lowercase() ?: ""
    val isProtectedEmbed = channelType == "embed" || channelType == "geturl"

    var visible by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var isInPip by remember { mutableStateOf(false) }

    var realUrl by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var playerState by remember { mutableIntStateOf(Player.STATE_IDLE) }

    LaunchedEffect(visible, isError) {
        if (visible && !isError) {
            delay(4000)
            visible = false
        }
    }

    DisposableEffect(activity) {
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPip = info.isInPictureInPictureMode
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.addOnPictureInPictureModeChangedListener(listener)
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity?.removeOnPictureInPictureModeChangedListener(listener)
            }
        }
    }

    val finalUserAgent = remember(userAgent) {
        if (userAgent.isNullOrBlank()) {
            "Dalvik/2.1.0 (Linux; U; Android 13; Pixel 6 Build/TQ3A.230805.001)"
        } else userAgent
    }

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

    // SNIFFER BACKGROUND
    LaunchedEffect(originalUrl, retryCount) {
        if (isProtectedEmbed && !channelId.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    Log.d("ExoSniffer", "Contattando API per ID: $channelId")
                    val apiUrl = "http://api.techlabapi.com/droid/api/v1/static/js/config/18_app2025/get_ads_posts.js?id=$channelId"
                    val connection = URL(apiUrl).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", finalUserAgent)
                    connection.setRequestProperty("Accept", "application/javascript")
                    if (!referer.isNullOrBlank()) connection.setRequestProperty("Referer", referer)
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d("ExoSniffer", "Risposta API ottenuta (Lunghezza: ${response.length})")
                    val json = JSONObject(response)

                    var encryptedHex = ""
                    if (json.has("post")) encryptedHex = json.getString("post")
                    else if (json.has("post_v2")) encryptedHex = json.getString("post_v2")

                    if (encryptedHex.isNotEmpty()) {
                        val decryptedStr = ExoDecoder.decryptPostField(encryptedHex)
                        val decryptedJson = JSONObject(decryptedStr)
                        realUrl = decryptedJson.getString("channel_url")
                        Log.d("ExoSniffer", "URL decriptato con successo!")
                    } else if (json.has("channel_url")) {
                        realUrl = json.getString("channel_url")
                    } else {
                        realUrl = originalUrl
                    }
                } catch (e: Exception) {
                    Log.e("ExoSniffer", "Errore API: ${e.message}")
                    realUrl = originalUrl
                }
            }
        } else {
            realUrl = originalUrl
        }
    }

    LaunchedEffect(playerState, isProtectedEmbed) {
        if (isProtectedEmbed && playerState == Player.STATE_BUFFERING) {
            delay(8000) // Se resta in buffering per 8 secondi, forza la ricarica del token
            retryCount++
        }
    }

    if (realUrl != null) {
        // EXOPLAYER NATIVO CON INIEZIONE HEADER DINAMICI
        val exoPlayer = remember(realUrl) {
            val headersMap = mutableMapOf<String, String>()
            headersMap["User-Agent"] = finalUserAgent
            if (!referer.isNullOrBlank()) headersMap["Referer"] = referer
            if (!origin.isNullOrBlank()) headersMap["Origin"] = origin

            // Decodifica Header Dinamici (Cookie, Token)
            val urlToParse = if (realUrl!!.contains("?headers=")) realUrl else originalUrl
            try {
                if (urlToParse!!.contains("?headers=")) {
                    val base64Headers = urlToParse.substringAfter("?headers=").substringBefore("&")
                    val decodedJsonStr = String(Base64.decode(base64Headers, Base64.DEFAULT), Charsets.UTF_8)
                    val jsonObject = JSONObject(decodedJsonStr)
                    val keys = jsonObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        headersMap[key] = jsonObject.optString(key)
                    }
                }
            } catch (e: Exception) { Log.e("ExoHeader", "Errore parsing headers: ${e.message}") }

            // Configurazione esatta dell'app originale
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(finalUserAgent)
                .setConnectTimeoutMs(30000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(true) // FONDAMENTALE
                .setDefaultRequestProperties(headersMap) // FONDAMENTALE PER I COOKIE SUI .TS

            val hlsExtractorFactory = DefaultHlsExtractorFactory(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES, true)
            val errorPolicy = DefaultLoadErrorHandlingPolicy(20)

            val hlsMediaSourceFactory = HlsMediaSource.Factory(httpFactory)
                .setExtractorFactory(hlsExtractorFactory)
                .setLoadErrorHandlingPolicy(errorPolicy)
                .setPlaylistParserFactory(object : androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory {
                    override fun createPlaylistParser() = SanitizingPlaylistParser(androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser())
                    override fun createPlaylistParser(m: androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist, p: androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist?) =
                        SanitizingPlaylistParser(androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser(m, p))
                })
                .setAllowChunklessPreparation(true)

            val player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory).setLoadErrorHandlingPolicy(errorPolicy))
                .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(32000, 131072, 2500, 5000).build())
                .setTrackSelector(DefaultTrackSelector(context).apply { setParameters(buildUponParameters().setForceHighestSupportedBitrate(true)) })
                .build()

            val mediaItem = buildMediaItemFromOriginalLogic(realUrl!!)
            if (mediaItem.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8) player.setMediaSource(hlsMediaSourceFactory.createMediaSource(mediaItem))
            else player.setMediaItem(mediaItem)

            player.prepare()
            player.playWhenReady = true

            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    playerState = state
                    isLoading = (state == Player.STATE_BUFFERING)
                    if (state == Player.STATE_READY) isError = false
                }
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("ExoPlayer", "Errore: ${error.message}")
                    if (isProtectedEmbed && retryCount < 5) retryCount++
                    else {
                        isError = true
                        isLoading = false
                    }
                }
            })
            player
        }

        DisposableEffect(exoPlayer) { onDispose { exoPlayer.stop(); exoPlayer.release() } }

        val onLiveRefresh: () -> Unit = {
            exoPlayer.stop()
            retryCount++
        }

        Box(Modifier.fillMaxSize().background(Color.Black).clickable(remember { MutableInteractionSource() }, null) { visible = !visible }) {
            AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; keepScreenOn = true; setShutterBackgroundColor(android.graphics.Color.BLACK) } }, modifier = Modifier.fillMaxSize())
            if (isLoading && !isError) CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (isError && !isInPip) ErrorOverlay(onRetry = { isError = false; retryCount = 0; exoPlayer.prepare(); exoPlayer.play() }, onBack = onBack)
            AnimatedVisibility(visible = visible && !isError && !isInPip, enter = fadeIn(), exit = fadeOut()) { PlayerControls(onBack, exoPlayer, activity, onLiveRefresh) }
        }
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (isError) ErrorOverlay(onRetry = { isError = false; retryCount++ }, onBack = onBack)
            else CircularProgressIndicator()
        }
    }
}

// COMPONENTI PULSANTI TV-FRIENDLY (Focus D-Pad)
@Composable
fun FocusableIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(0.7f),
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    icon: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .size(56.dp)
            .border(width = if (isFocused) 3.dp else 0.dp, color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent, shape = CircleShape)
            .focusable()
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            icon()
        }
    }
}

@Composable
fun FocusableTextButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Red,
    contentColor: Color = Color.White
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .height(56.dp)
            .border(width = if (isFocused) 3.dp else 0.dp, color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent, shape = CircleShape)
            .focusable()
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(text, fontWeight = FontWeight.Bold, color = contentColor)
        }
    }
}

@Composable
fun PlayerControls(onBack: () -> Unit, player: Player, activity: Activity?, onLiveRefresh: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp)) {
        FocusableIconButton(onClick = onBack, Modifier.align(Alignment.TopStart)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(32.dp)) }
        FocusableIconButton(onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { activity?.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build()) } }, Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.PictureInPictureAlt, null, Modifier.size(32.dp)) }
        Row(Modifier.align(Alignment.BottomCenter), Arrangement.spacedBy(16.dp), Alignment.CenterVertically) {
            var isPlaying by remember { mutableStateOf(true) }
            FocusableIconButton(onClick = { if(isPlaying) player.pause() else player.play(); isPlaying = !isPlaying }) { Icon(if(isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(32.dp)) }
            FocusableTextButton(onClick = onLiveRefresh, text = "LIVE")
            FocusableIconButton(onClick = { activity?.requestedOrientation = if (activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }) { Icon(Icons.Default.ScreenRotation, null, Modifier.size(32.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorOverlay(onRetry: () -> Unit, onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.ErrorOutline, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
            Text("Errore nello streaming", color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(top = 16.dp))
            Spacer(Modifier.height(24.dp))

            val rI = remember { MutableInteractionSource() }
            val rF by rI.collectIsFocusedAsState()
            Surface(
                onClick = onRetry, interactionSource = rI, shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(52.dp).border(if (rF) 3.dp else 0.dp, Color.White, RoundedCornerShape(26.dp)).focusable()
            ) { Box(contentAlignment = Alignment.Center) { Text("Riprova", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) } }

            Spacer(Modifier.height(12.dp))

            val bI = remember { MutableInteractionSource() }
            val bF by bI.collectIsFocusedAsState()
            Surface(
                onClick = onBack, interactionSource = bI, shape = RoundedCornerShape(26.dp), color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().height(52.dp).border(if (bF) 3.dp else 1.dp, if (bF) MaterialTheme.colorScheme.primary else Color.White.copy(0.5f), RoundedCornerShape(26.dp)).focusable()
            ) { Box(contentAlignment = Alignment.Center) { Text("Indietro", color = Color.White, fontWeight = FontWeight.Bold) } }
        }
    }
}

object ExoDecoder {
    private const val key = "1g2j4d5rb56s39wc"
    private const val iv = "g4fst5gpd5f5r7j4"
    fun decryptPostField(hexString: String): String {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(), "AES"), IvParameterSpec(iv.toByteArray()))
            String(cipher.doFinal(hexToBytes(hexString))).trim { it <= '\u0000' }.trim()
        } catch (e: Exception) { "" }
    }
    private fun hexToBytes(hexString: String): ByteArray {
        val data = ByteArray(hexString.length / 2)
        for (i in hexString.indices step 2) {
            data[i / 2] = ((Character.digit(hexString[i], 16) shl 4) + Character.digit(hexString[i + 1], 16)).toByte()
        }
        return data
    }
}

@OptIn(UnstableApi::class)
class SanitizingPlaylistParser(private val defaultParser: androidx.media3.exoplayer.upstream.ParsingLoadable.Parser<androidx.media3.exoplayer.hls.playlist.HlsPlaylist>) : androidx.media3.exoplayer.upstream.ParsingLoadable.Parser<androidx.media3.exoplayer.hls.playlist.HlsPlaylist> {
    override fun parse(uri: Uri, inputStream: java.io.InputStream): androidx.media3.exoplayer.hls.playlist.HlsPlaylist {
        val sanitized = inputStream.bufferedReader().use { it.readText() }.replace(Regex("(?i)IV\\s*=\\s*\"?(0x)?null\"?"), "IV=0x00000000000000000000000000000000")
        return defaultParser.parse(uri, sanitized.byteInputStream())
    }
}

@OptIn(UnstableApi::class)
private fun buildMediaItemFromOriginalLogic(url: String): MediaItem {
    val mimeType = when {
        url.lowercase().contains(".mpd") -> MimeTypes.APPLICATION_MPD
        url.lowercase().contains(".ism") -> MimeTypes.APPLICATION_SS
        url.lowercase().contains(".mp4") -> MimeTypes.VIDEO_MP4
        url.lowercase().contains("mpegts") -> "video/mpeg2"
        else -> MimeTypes.APPLICATION_M3U8
    }
    val builder = MediaItem.Builder().setUri(Uri.parse(url)).setMimeType(mimeType)
    if (url.lowercase().contains("drmlicense")) {
        val uri = Uri.parse(url.replace("?headers=", "&headers="))
        uri.getQueryParameter("drmLicense")?.let { license ->
            if (license.contains(":")) {
                val data = license.split(":")
                try {
                    val kid = Base64.encodeToString(hexToByteArray(data[0]), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                    val key = Base64.encodeToString(hexToByteArray(data[1]), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                    val json = "{\"keys\":[{\"kty\":\"oct\",\"k\":\"$key\",\"kid\":\"$kid\"}],\"type\":\"temporary\"}"
                    builder.setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID).setLicenseUri("data:application/json;base64," + Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP)).build())
                } catch (e: Exception) { builder.setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setLicenseUri(data[0]).build()) }
            }
        }
    }
    return builder.build()
}

private fun hexToByteArray(s: String) = ByteArray(s.length / 2) { i -> ((Character.digit(s[i * 2], 16) shl 4) + Character.digit(s[i * 2 + 1], 16)).toByte() }