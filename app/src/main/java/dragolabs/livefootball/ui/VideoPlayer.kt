package dragolabs.livefootball.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(url: String, userAgent: String? = null, referer: String? = null, origin: String? = null) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    // Configura la sorgente dati con lo User-Agent e headers
    val dataSourceFactory = DefaultHttpDataSource.Factory()
    userAgent?.let { dataSourceFactory.setUserAgent(it) }
    val headers = mutableMapOf<String, String>()
    referer?.let { headers["Referer"] = it.replace("Referer: ", "") }
    origin?.let { headers["Origin"] = it }
    if (headers.isNotEmpty()) {
        dataSourceFactory.setDefaultRequestProperties(headers)
    }
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
                val mediaItem = MediaItem.fromUri(url)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(true) }

    // Listener per aggiornare lo stato di riproduzione UI
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                isPlaying = isPlayingChanged
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Vista Video Nativa
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Usiamo i nostri controlli MD3
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Controlli MD3
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Sinistra: Play/Pausa e Cast
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { 
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        
                        // Bottone Google Cast (Chromecast)
                        AndroidView(
                            factory = { ctx ->
                                MediaRouteButton(ctx).apply {
                                    CastButtonFactory.setUpMediaRouteButton(ctx, this)
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    // Destra: Live, Rotazione e Impostazioni
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Tasto GO LIVE
                        TextButton(onClick = { 
                            exoPlayer.seekToDefaultPosition() 
                        }) {
                            Text("LIVE", color = Color.Red, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }

                        // Tasto Rotazione Schermo
                        IconButton(onClick = {
                            activity?.let {
                                it.requestedOrientation = if (it.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                } else {
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }
                            }
                        }) {
                            Icon(Icons.Default.ScreenRotation, contentDescription = null, tint = Color.White)
                        }

                        // Rotella Impostazioni
                        IconButton(onClick = { /* Implementazione futura: menu qualità */ }) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}
