package com.hightechentertainment.app

import com.hightechentertainment.app.data.TrackDto
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val U26Black = Color(0xFF0B0A0F)
private val U26Panel = Color(0xFF17161E)
private val U26Raised = Color(0xFF211F29)
private val U26Gold = Color(0xFFE5C158)
private val U26GoldLight = Color(0xFFF0DCA7)
private val U26Silver = Color(0xFFC7CCD6)
private val U26Muted = Color(0xFF89919D)
private val U26Line = Color(0xFF302D38)

@Composable
fun Universe2026Home(
    tracks: List<TrackDto>, videos: List<VideoDto>, playlists: List<PlaylistDto>,
    playlistTracks: Map<String, List<TrackDto>>, isLoading: Boolean,
    onExplore: () -> Unit, onVideos: () -> Unit, onVoice: () -> Unit, onProfile: () -> Unit,
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
    val featuredTracks = tracks.take(5)
    val favouriteTracks = tracks.filter { it.id in favouriteTrackIds }

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
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("WELCOME BACK", color = U26Muted, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(U26Gold))
                            Spacer(Modifier.width(8.dp))
                            Text("HIGH-TECH ENTERTAINMENT", color = U26Gold, fontSize = 9.sp, letterSpacing = 1.45.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Sound beyond limits.",
                            color = Color.White,
                            fontSize = if (wide) 32.sp else 27.sp,
                            lineHeight = if (wide) 38.sp else 33.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-.7).sp
                        )
                        Text("Music. Beats. Production. Beyond.", color = U26Silver, fontSize = 12.sp)
                    }
                    Surface(
                        onClick = onVoice,
                        shape = RoundedCornerShape(17.dp),
                        color = U26Gold.copy(alpha = .12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, U26Gold.copy(alpha = .38f))
                    ) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.GraphicEq, "Open HTE voice assistant", tint = U26Gold, modifier = Modifier.size(24.dp))
                        }
                    }
                    IconButton(onClick = onProfile, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Outlined.AccountCircle, "Open your HTE account", tint = U26Silver, modifier = Modifier.size(27.dp))
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                        .background(U26Panel).border(1.dp, U26Line, RoundedCornerShape(15.dp))
                        .clickable(onClick = onExplore).padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Search, null, tint = U26Gold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(11.dp))
                    Text("Search music, artists and genres", Modifier.weight(1f), color = U26Muted, fontSize = 12.sp)
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = U26Silver, modifier = Modifier.size(17.dp))
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
                        U26SectionHeading("FEATURED ON HTE", "EXPLORE ALL", onExplore)
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

            if (favouriteTracks.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    U26SectionHeading("YOUR FAVOURITES", "OPEN LIBRARY", onExplore)
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

            if (playlists.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    U26SectionHeading("YOUR PLAYLISTS", "OPEN LIBRARY", onExplore)
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
                    U26QuickStat("VIDEOS", videos.size.toString(), Icons.Outlined.VideoLibrary, Modifier.weight(1f), onVideos)
                    U26QuickStat("YOUR LIBRARY", "OPEN", Icons.Outlined.MusicNote, Modifier.weight(1f), onExplore)
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
                            U26VideoCard(video, wide, onPlay = { onPlayVideo(video) }, onDownload = { onDownloadVideo(video) })
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

            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF292116), U26Panel)))
                        .border(1.dp, U26Gold.copy(alpha = .24f), RoundedCornerShape(18.dp))
                        .clickable(onClick = onVoice).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(U26Gold.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Mic, null, tint = U26Gold, modifier = Modifier.size(23.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("SYNAPSE AI", color = U26Gold, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                        Text("Control your universe by voice", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Search, navigate and manage playback", color = U26Muted, fontSize = 10.sp)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = U26Gold)
                }
            }
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
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF594321), Color(0xFF1B1B1C), U26Black))))
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
            .background(Brush.linearGradient(listOf(Color(0xFF302516), U26Panel, U26Black)))
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
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF49391F), U26Raised))), contentAlignment = Alignment.Center) {
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
private fun U26VideoCard(video: VideoDto, wide: Boolean, onPlay: () -> Unit, onDownload: () -> Unit) {
    Column(Modifier.width(if (wide) 238.dp else 194.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            Modifier.fillMaxWidth().height(if (wide) 140.dp else 112.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3A3020), U26Raised, Color(0xFF101216))))
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            if (!video.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = video.thumbnailUrl,
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
            TextButton(onClick = onDownload, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Icon(Icons.Outlined.Download, null, tint = U26Silver, modifier = Modifier.size(15.dp))
                Text("SAVE", color = U26Silver, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
                    Icon(Icons.Outlined.QueueMusic, null, tint = U26Gold, modifier = Modifier.size(27.dp))
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
