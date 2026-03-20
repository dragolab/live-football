package dragolabs.livefootball

import android.os.Bundle
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dragolabs.livefootball.model.Category
import dragolabs.livefootball.model.Channel
import dragolabs.livefootball.ui.VideoPlayer
import dragolabs.livefootball.ui.theme.LiveFootballTheme
import dragolabs.livefootball.viewmodel.SportViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.cast.framework.CastContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inizializzazione sicura del CastContext nel thread principale
        try {
            CastContext.getSharedInstance(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        enableEdgeToEdge()
        setContent {
            LiveFootballTheme {
                val viewModel: SportViewModel = viewModel()
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: SportViewModel) {
    val categories by viewModel.categories.collectAsState(initial = emptyList())
    val channels by viewModel.channels.collectAsState(initial = emptyList())
    val selectedCategory by viewModel.selectedCategory.collectAsState(initial = null)
    val selectedChannel by viewModel.selectedChannel.collectAsState(initial = null)

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (selectedChannel != null) {
                // IL PLAYER VIDEO CON TUTTI GLI HEADERS
                VideoPlayer(
                    url = selectedChannel!!.channelUrl,
                    userAgent = selectedChannel!!.agent,
                    referer = selectedChannel!!.eh1, // eh1 è il Referer nell'app originale
                    origin = selectedChannel!!.origin
                )

                // Tasto per chiudere il player
                Button(
                    onClick = { viewModel.selectChannel(null) },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                ) {
                    Text("Indietro")
                }
            } else if (selectedCategory == null) {
                // Lista Categorie
                if (categories.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(categories) { category ->
                            CategoryItem(category) { viewModel.selectCategory(category) }
                        }
                    }
                }
            } else {
                // Lista Canali
                Column {
                    Text(
                        text = selectedCategory!!.categoryName ?: "Canali",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    if (channels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nessun canale trovato")
                        }
                    } else {
                        LazyColumn {
                            items(channels) { channel ->
                                ChannelItem(channel) { viewModel.selectChannel(channel) }
                            }
                            item {
                                Button(
                                    onClick = { viewModel.selectCategory(null) },
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                ) {
                                    Text("Torna alle Categorie")
                                }
                            }
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
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onClick() },
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = category.categoryImage,
                contentDescription = null,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
            Text(
                text = category.categoryName ?: "",
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun ChannelItem(channel: Channel, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(channel.channelName) },
        supportingContent = { if (channel.liveTime?.isNotEmpty() == true) Text("LIVE: ${channel.liveTime}") },
        leadingContent = {
            AsyncImage(
                model = channel.channelImage,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Crop
            )
        },
        modifier = Modifier.clickable { onClick() }
    )
}
