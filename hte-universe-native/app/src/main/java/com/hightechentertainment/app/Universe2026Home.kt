package com.hightechentertainment.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.hightechentertainment.app.data.TrackDto
import com.hightechentertainment.app.data.ArtistDto
import com.hightechentertainment.app.data.ReleaseDto
import com.hightechentertainment.app.data.VideoDto
import com.hightechentertainment.app.data.PlaylistDto
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hightechentertainment.app.media.spotifyAppUri

private val U26Black = com.hightechentertainment.app.ui.design.HteColors.Obsidian
private val U26Panel = com.hightechentertainment.app.ui.design.HteColors.Panel
private val U26Raised = com.hightechentertainment.app.ui.design.HteColors.PanelRaised
private val U26Gold = com.hightechentertainment.app.ui.design.HteColors.Gold
private val U26GoldLight = Color(0xFFF0DCA7)
private val U26Silver = com.hightechentertainment.app.ui.design.HteColors.Silver
private val U26Muted = com.hightechentertainment.app.ui.design.HteColors.Muted
private val U26Line = com.hightechentertainment.app.ui.design.HteColors.Hairline

/** Stable, case-insensitive ordering used by Home's opt-in favourite-genre recommendations. */
internal fun rankGenreIndexesByPreference(genres: List<String?>, preferredGenreCounts: Map<String, Int>): List<Int> =
    genres.indices.sortedWith(
        compareByDescending<Int> { index -> preferredGenreCounts[genres[index]?.trim()?.lowercase().orEmpty()] ?: 0 }
            .thenBy { index -> index }
    )

@Composable
fun MusicHomeScreen(
    tracks: List<TrackDto>, artists: List<ArtistDto>, releases: List<ReleaseDto>, videos: List<VideoDto>, playlists: List<PlaylistDto>,
    playlistTracks: Map<String, List<TrackDto>>, profileName: String?, profileAvatarUrl: String?, isLoading: Boolean,
    onExplore: () -> Unit, onLibrary: () -> Unit, onVideos: () -> Unit, onVoice: () -> Unit, onProfile: () -> Unit, onSettings: () -> Unit, onArtistClick: (ArtistDto) -> Unit,
    onPlayTrack: (TrackDto) -> Unit, onPlayPlaylist: (PlaylistDto) -> Unit, onPlayVideo: (VideoDto) -> Unit,
    onDownloadTrack: (TrackDto) -> Unit, onDownloadVideo: (VideoDto) -> Unit,
    favouriteTrackIds: Set<String>, onToggleFavourite: (TrackDto) -> Unit, onRefresh: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }
    val genres = tracks.mapNotNull { it.genre?.takeIf(String::isNotBlank) }.distinct().sorted()
    val categories = listOf("All", "Favourites") + genres
    val visibleTracks = when (selectedCategory) {
        "Favourites" -> tracks.filter { it.id in favouriteTrackIds }
        "All" -> tracks
        else -> tracks.filter { it.genre.equals(selectedCategory, ignoreCase = true) }
    }
    val favouriteTracks = tracks.filter { it.id in favouriteTrackIds }
    val preferredGenreCounts = favouriteTracks.mapNotNull { it.genre?.trim()?.takeIf(String::isNotBlank) }
        .groupingBy { it.lowercase() }.eachCount()
    val recommendationsEnabled = AppSettingsStore(LocalContext.current).personalisedRecommendations
    val usePersonalisedRecommendations = recommendationsEnabled && preferredGenreCounts.isNotEmpty()
    val recommendationCandidates = if (usePersonalisedRecommendations) tracks.filterNot { it.id in favouriteTrackIds } else tracks
    val recommendationOrder = rankGenreIndexesByPreference(recommendationCandidates.map { it.genre }, preferredGenreCounts)
    val featuredTracks = if (usePersonalisedRecommendations) {
        recommendationOrder.map(recommendationCandidates::get)
            .filter { (preferredGenreCounts[it.genre?.trim()?.lowercase().orEmpty()] ?: 0) > 0 }
            .take(5)
    } else tracks.take(5)

    BoxWithConstraints(Modifier.fillMaxSize().background(U26Black)) {
        val wide = maxWidth >= 720.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = if (wide) 28.dp else 18.dp,
                end = if (wide) 28.dp else 18.dp,
                top = 10.dp,
                bottom = 118.dp
            ),
            verticalArrangement = Arrangement.spacedBy(23.dp)
        ) {
            item(key = "home_header_actions") {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("HIGH-TECH ENTERTAINMENT", color = U26Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                        Text(
                            profileName?.takeIf(String::isNotBlank)?.let { "Welcome, $it" } ?: "Your sound. Your universe.",
                            color = U26Silver, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onVoice) {
                        Icon(Icons.Outlined.GraphicEq, contentDescription = "Open HTE AI Voice Assistant", tint = U26Gold)
                    }
                    IconButton(onClick = onProfile) {
                        Icon(Icons.Outlined.Person, contentDescription = "Open account", tint = U26Silver)
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Open settings", tint = U26Silver)
                    }
                }
            }

            if (artists.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    U26SectionHeading("MEET THE ARTISTS", "DISCOVER MUSIC", onExplore)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(artists, key = { it.id }) { artist -> U26ArtistCard(artist, wide) { onArtistClick(artist) } }
                    }
                }
            }

            if (releases.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    U26SectionHeading("OFFICIAL PROJECTS", "EXPLORE ALL", onExplore)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(releases, key = { it.id }) { release -> U26ProjectCard(release, wide) }
                    }
                }
            }

            if (favouriteTracks.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    U26SectionHeading("YOUR FAVOURITES", "OPEN LIBRARY", onLibrary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(favouriteTracks, key = { it.id }) { track ->
                            U26ReleaseCard(
                                track = track,
                                wide = wide,
                                isFavourite = true,
                                onPlay = { onPlayTrack(track) },
                                onFavourite = { onToggleFavourite(track) },
                                onDownload = { onDownloadTrack(track) }
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("BROWSE YOUR SOUND", color = U26Muted, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(categories, key = { it }) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category, fontSize = 10.sp, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = U26Gold.copy(alpha = .18f),
                                    selectedLabelColor = U26Gold,
                                    labelColor = U26Silver
                                )
                            )
                        }
                    }
                }
            }

            item {
                if (featuredTracks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        U26SectionHeading(if (usePersonalisedRecommendations) "PICKED FOR YOU" else "FEATURED ON HTE", "EXPLORE ALL", onExplore)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(featuredTracks, key = { it.id }) { track ->
                                U26FeaturedRelease(
                                    track,
                                    onPlay = { onPlayTrack(track) },
                                    wide = wide,
                                    modifier = Modifier.width(if (wide) 360.dp else 300.dp)
                                )
                            }
                        }
                    }
                } else {
                    U26EmptyFeature(isLoading, onRefresh)
                }
            }

            if (playlists.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    U26SectionHeading("YOUR PLAYLISTS", "OPEN LIBRARY", onLibrary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            U26PlaylistCard(
                                playlist = playlist,
                                tracks = playlistTracks[playlist.id].orEmpty(),
                                wide = wide,
                                onPlay = { onPlayPlaylist(playlist) }
                            )
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    U26QuickStat("MUSIC", tracks.size.toString(), Icons.Outlined.MusicNote, Modifier.weight(1f), onExplore)
                    U26QuickStat("ARTISTS", artists.size.toString(), Icons.Outlined.Groups, Modifier.weight(1f), onExplore)
                    U26QuickStat("VIDEOS", videos.size.toString(), Icons.Outlined.VideoLibrary, Modifier.weight(1f), onVideos)
                    U26QuickStat("LIBRARY", "OPEN", Icons.Outlined.MusicNote, Modifier.weight(1f), onLibrary)
                }
            }

            item {
                U26SectionHeading("FRESH FROM HTE", "EXPLORE ALL", onExplore)
                if (visibleTracks.isEmpty()) {
                    U26EmptyState(if (isLoading) "Loading new releases…" else "New releases will appear here.", isLoading, onRefresh)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 10.dp)) {
                        items(visibleTracks.take(10), key = { it.id }) { track ->
                            U26ReleaseCard(
                                track = track,
                                wide = wide,
                                isFavourite = track.id in favouriteTrackIds,
                                onPlay = { onPlayTrack(track) },
                                onFavourite = { onToggleFavourite(track) },
                                onDownload = { onDownloadTrack(track) }
                            )
                        }
                    }
                }
            }

            item {
                U26SectionHeading("TRENDING IN THE UNIVERSE", "FULL CATALOGUE", onExplore)
                if (visibleTracks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        visibleTracks.take(5).forEachIndexed { index, track ->
                            U26TrendingRow(
                                index = index + 1,
                                track = track,
                                onPlay = { onPlayTrack(track) },
                                onDownload = { onDownloadTrack(track) }
                            )
                        }
                    }
                } else if (!isLoading) {
                    U26EmptyState("No tracks in this selection yet.", false, onRefresh)
                }
            }

            item {
                U26SectionHeading("HTE VISUALS", "OPEN VIDEO HUB", onVideos)
                if (videos.isEmpty()) {
                    Text(
                        if (isLoading) "Loading music videos…" else "Music videos, performances and stories will appear here.",
                        color = U26Muted, fontSize = 12.sp
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 10.dp)) {
                        items(videos.take(8), key = { it.id }) { video ->
                            U26VideoCard(video, wide, onPlay = { onPlayVideo(video) }, onDownload = if (!video.downloadUrl.isNullOrBlank()) ({ onDownloadVideo(video) }) else null)
                        }
                    }
                }
            }

            if (genres.isNotEmpty()) item {
                U26SectionHeading("FIND YOUR FREQUENCY", "BROWSE GENRES", onExplore)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(genres) { genre ->
                        AssistChip(
                            onClick = { selectedCategory = genre },
                            label = { Text(genre, color = U26Silver) },
                            leadingIcon = { Icon(Icons.Outlined.GraphicEq, null, tint = U26Gold, modifier = Modifier.size(16.dp)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = U26Panel),
                            border = androidx.compose.foundation.BorderStroke(1.dp, U26Line)
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun U26ArtistCard(artist: ArtistDto, wide: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(if (wide) 190.dp else 156.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = U26Panel)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier.fillMaxWidth().height(if (wide) 142.dp else 118.dp)
                    .clip(RoundedCornerShape(13.dp)).background(U26Raised),
                contentAlignment = Alignment.Center
            ) {
                if (!artist.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artist.imageUrl,
                        contentDescription = "${artist.name} artist artwork",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF302817), U26Raised))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Person, null, tint = U26Gold, modifier = Modifier.size(42.dp))
                    }
                }
            }
            Text(artist.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                artist.bio?.takeIf(String::isNotBlank) ?: "Artist · High-Tech Entertainment",
                color = U26Muted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun U26ProjectCard(release: ReleaseDto, wide: Boolean) {
    var selectedSpotifyUrl by remember(release.id) { mutableStateOf<String?>(null) }
    Card(
        modifier = Modifier.width(if (wide) 230.dp else 192.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = U26Panel)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier.fillMaxWidth().height(if (wide) 132.dp else 108.dp)
                    .clip(RoundedCornerShape(13.dp)).background(U26Raised),
                contentAlignment = Alignment.Center
            ) {
                if (!release.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = release.coverUrl,
                        contentDescription = "${release.title} cover artwork",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Outlined.Album, null, tint = U26Gold, modifier = Modifier.size(42.dp))
                }
            }
            Text(release.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(release.releaseDate ?: "Official HTE release", color = U26Gold, fontSize = 9.sp)
            if (!release.description.isNullOrBlank()) {
                Text(release.description, color = U26Muted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            release.spotifyUrl?.takeIf(String::isNotBlank)?.let { spotifyUrl ->
                TextButton(
                    onClick = { selectedSpotifyUrl = spotifyUrl },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = U26Gold, modifier = Modifier.size(16.dp))
                    Text("PLAY IN HTE", color = U26Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            selectedSpotifyUrl?.let { url -> SpotifyEmbedDialog(url = url, onDismiss = { selectedSpotifyUrl = null }) }
        }
    }
}

@Composable
private fun U26FeaturedRelease(track: TrackDto, onPlay: () -> Unit, wide: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(if (wide) 270.dp else 242.dp).clickable(onClick = onPlay),
        shape = RoundedCornerShape(25.dp),
        colors = CardDefaults.cardColors(containerColor = U26Panel)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!track.coverUrl.isNullOrBlank()) {
                AsyncImage(model = track.coverUrl, contentDescription = "Featured artwork for ${track.title}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF3A2E18), Color(0xFF1B1B1C), U26Black))))
                Icon(Icons.Outlined.GraphicEq, null, tint = U26Gold.copy(alpha = .18f), modifier = Modifier.align(Alignment.Center).size(190.dp))
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .08f), Color.Black.copy(alpha = .38f), Color.Black.copy(alpha = .94f)))))
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Surface(color = U26Gold, shape = RoundedCornerShape(50)) {
                    Text("FEATURED RELEASE", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = U26Black, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                }
                Text(track.title, color = Color.White, fontSize = if (wide) 25.sp else 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = U26Silver, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(onClick = onPlay, color = U26Gold, shape = RoundedCornerShape(50)) {
                        Row(Modifier.padding(horizontal = 15.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PlayArrow, null, tint = U26Black, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("LISTEN NOW", color = U26Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("HIGH-TECH ORIGINAL", color = U26GoldLight, fontSize = 8.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun U26EmptyFeature(isLoading: Boolean, onRefresh: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(218.dp).clip(RoundedCornerShape(25.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF211D14), U26Panel, U26Black)))
            .border(1.dp, U26Gold.copy(alpha = .22f), RoundedCornerShape(25.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Outlined.GraphicEq, null, tint = U26Gold, modifier = Modifier.size(40.dp))
            Text("THE HTE SOUND STARTS HERE", color = U26Gold, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            Text(if (isLoading) "Preparing your catalogue…" else "Your next favourite is on its way.", color = Color.White, fontSize = 13.sp)
            if (!isLoading) TextButton(onClick = onRefresh) { Text("Refresh catalogue", color = U26Gold) }
        }
    }
}

@Composable
private fun U26QuickStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = U26Panel,
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, U26Line)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = U26Gold, modifier = Modifier.size(18.dp))
            Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(label, color = U26Muted, fontSize = 8.sp, letterSpacing = .6.sp, maxLines = 1)
        }
    }
}

@Composable
private fun U26ReleaseCard(
    track: TrackDto, wide: Boolean, isFavourite: Boolean,
    onPlay: () -> Unit, onFavourite: () -> Unit, onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.width(if (wide) 198.dp else 158.dp).clickable(onClick = onPlay),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = U26Panel)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(if (wide) 142.dp else 116.dp).clip(RoundedCornerShape(13.dp)).background(U26Raised)) {
                if (!track.coverUrl.isNullOrBlank()) {
                    AsyncImage(model = track.coverUrl, contentDescription = "Artwork for ${track.title}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF302817), U26Raised))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Album, null, tint = U26Gold, modifier = Modifier.size(38.dp))
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp),
                    shape = RoundedCornerShape(50),
                    color = U26Gold
                ) {
                    Icon(Icons.Outlined.PlayArrow, "Play ${track.title}", tint = U26Black, modifier = Modifier.padding(7.dp).size(19.dp))
                }
            }
            Text(track.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = U26Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(track.genre ?: "HTE release", Modifier.weight(1f), color = U26Gold, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onFavourite, modifier = Modifier.size(32.dp)) {
                    Icon(if (isFavourite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, if (isFavourite) "Remove favourite" else "Add favourite", tint = if (isFavourite) U26Gold else U26Muted, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Download, "Download ${track.title}", tint = U26Silver, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun U26TrendingRow(index: Int, track: TrackDto, onPlay: () -> Unit, onDownload: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(U26Panel)
            .border(1.dp, U26Line.copy(alpha = .7f), RoundedCornerShape(15.dp))
            .clickable(onClick = onPlay).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("%02d".format(index), color = U26Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
        if (!track.coverUrl.isNullOrBlank()) {
            AsyncImage(model = track.coverUrl, contentDescription = null, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)).background(U26Raised), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Album, null, tint = U26Gold)
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = U26Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDownload) { Icon(Icons.Outlined.Download, "Download ${track.title}", tint = U26Silver) }
        Icon(Icons.Outlined.PlayArrow, null, tint = U26Gold)
    }
}

@Composable
private fun U26VideoCard(video: VideoDto, wide: Boolean, onPlay: () -> Unit, onDownload: (() -> Unit)?) {
    Column(Modifier.width(if (wide) 238.dp else 194.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            Modifier.fillMaxWidth().height(if (wide) 140.dp else 112.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF252117), U26Raised, Color(0xFF101216))))
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = "Thumbnail for ${video.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Outlined.VideoLibrary, null, tint = U26Gold.copy(alpha = .45f), modifier = Modifier.size(62.dp))
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .52f)))))
            Surface(Modifier.align(Alignment.Center), color = Color.Black.copy(alpha = .68f), shape = RoundedCornerShape(50)) {
                Icon(Icons.Outlined.PlayArrow, "Watch ${video.title}", tint = Color.White, modifier = Modifier.padding(8.dp).size(24.dp))
            }
            Text("HTE VISUALS", Modifier.align(Alignment.BottomStart).padding(10.dp), color = U26GoldLight, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
        }
        Text(video.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(video.artist ?: "High-Tech Entertainment", color = U26Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPlay, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(Icons.Outlined.PlayArrow, null, tint = U26Gold, modifier = Modifier.size(16.dp))
                Text("WATCH", color = U26Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            if (onDownload != null) TextButton(onClick = onDownload, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(Icons.Outlined.Download, null, tint = U26Silver, modifier = Modifier.size(15.dp))
                Text("SAVE ORIGINAL", color = U26Silver, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun U26PlaylistCard(
    playlist: PlaylistDto,
    tracks: List<TrackDto>,
    wide: Boolean,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier.width(if (wide) 250.dp else 218.dp).clickable(onClick = onPlay),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = U26Panel)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(U26Raised),
                contentAlignment = Alignment.Center
            ) {
                val artwork = tracks.firstOrNull()?.coverUrl
                if (!artwork.isNullOrBlank()) {
                    AsyncImage(model = artwork, contentDescription = "Artwork for ${playlist.name}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Outlined.MusicNote, null, tint = U26Gold, modifier = Modifier.size(27.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(playlist.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(playlist.description?.takeIf(String::isNotBlank) ?: "Your HTE collection", color = U26Muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${tracks.size} TRACKS", color = U26Gold, fontSize = 8.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Outlined.PlayCircle, "Play playlist ${playlist.name}", tint = U26Gold, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun U26SectionHeading(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = U26Silver, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.width(34.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(U26Gold))
        }
        TextButton(onClick = onClick) { Text(action, color = U26Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun U26EmptyState(message: String, loading: Boolean, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = U26Muted, fontSize = 12.sp)
        if (loading) CircularProgressIndicator(color = U26Gold, modifier = Modifier.padding(top = 10.dp).size(22.dp), strokeWidth = 2.dp)
        else TextButton(onClick = onRefresh) { Text("Retry", color = U26Gold) }
    }
}

@Composable
fun Universe2026Home(
    tracks: List<TrackDto>, artists: List<ArtistDto>, releases: List<ReleaseDto>, videos: List<VideoDto>, playlists: List<PlaylistDto>,
    playlistTracks: Map<String, List<TrackDto>>, profileName: String?, profileAvatarUrl: String?, isLoading: Boolean,
    onExplore: () -> Unit, onLibrary: () -> Unit, onVideos: () -> Unit, onVoice: () -> Unit, onProfile: () -> Unit, onSettings: () -> Unit, onArtistClick: (ArtistDto) -> Unit,
    onPlayTrack: (TrackDto) -> Unit, onPlayPlaylist: (PlaylistDto) -> Unit, onPlayVideo: (VideoDto) -> Unit,
    onDownloadTrack: (TrackDto) -> Unit, onDownloadVideo: (VideoDto) -> Unit,
    favouriteTrackIds: Set<String>, onToggleFavourite: (TrackDto) -> Unit, onRefresh: () -> Unit
) = MusicHomeScreen(
    tracks, artists, releases, videos, playlists, playlistTracks, profileName, profileAvatarUrl, isLoading,
    onExplore, onLibrary, onVideos, onVoice, onProfile, onSettings, onArtistClick,
    onPlayTrack, onPlayPlaylist, onPlayVideo, onDownloadTrack, onDownloadVideo, favouriteTrackIds, onToggleFavourite, onRefresh
)

