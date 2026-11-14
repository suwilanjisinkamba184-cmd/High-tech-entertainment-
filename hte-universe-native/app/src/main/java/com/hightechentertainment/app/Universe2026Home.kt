package com.hightechentertainment.app

import com.hightechentertainment.app.data.TrackDto
import com.hightechentertainment.app.data.VideoDto
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

private val U26Black = Color(0xFF08090B)
private val U26Panel = Color(0xFF121418)
private val U26Raised = Color(0xFF1A1E25)
private val U26Gold = Color(0xFFD6B15E)
private val U26Silver = Color(0xFFC7CCD6)
private val U26Muted = Color(0xFF89919D)
private val U26Violet = Color(0xFFB78BFF)
private val U26Lime = Color(0xFFD4F542)

@Composable
fun Universe2026Home(
    tracks: List<TrackDto>, videos: List<VideoDto>, isLoading: Boolean,
    onExplore: () -> Unit, onVideos: () -> Unit, onVoice: () -> Unit,
    onPlayTrack: (TrackDto) -> Unit, onPlayVideo: (VideoDto) -> Unit,
    onDownloadTrack: (TrackDto) -> Unit, onDownloadVideo: (VideoDto) -> Unit,
    favouriteTrackIds: Set<String>, onToggleFavourite: (TrackDto) -> Unit, onRefresh: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }
    val featured = tracks.firstOrNull()
    val wideGenres = tracks.mapNotNull { it.genre?.takeIf(String::isNotBlank) }.distinct().take(8)
    BoxWithConstraints(Modifier.fillMaxSize().background(U26Black)) {
        val wide = maxWidth >= 720.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = if (wide) 26.dp else 18.dp, end = if (wide) 26.dp else 18.dp, top = 14.dp, bottom = 118.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(U26Lime))
                        Spacer(Modifier.width(8.dp))
                        Text("HIGH-TECH ENTERTAINMENT  /  UNIVERSE 4.0", color = U26Lime, fontSize = 9.sp, letterSpacing = 1.35.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("HI-RES • 4K", color = U26Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                    }
                    Text("Your universe.\nYour sound.", color = Color.White, fontSize = if (wide) 38.sp else 33.sp, lineHeight = if (wide) 42.sp else 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                    Text("Music. Beats. Production. Beyond.", color = U26Silver, fontSize = 13.sp)
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Music", "4K Videos", "Trending", "Podcasts").forEach { category ->
                        val selected = selectedCategory == category
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                                selectedCategory = category
                                if (category == "4K Videos") onVideos()
                                else if (category == "Music" || category == "Trending" || category == "Podcasts") onExplore()
                            },
                            color = if (selected) U26Lime else U26Raised,
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) U26Lime else Color.White.copy(alpha = .08f))
                        ) {
                            Text(category, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp), color = if (selected) U26Black else U26Silver, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                U26SectionHeading("QUICK ACCESS", "OPEN LIBRARY", onExplore)
                val shortcuts = tracks.take(4)
                if (shortcuts.isEmpty()) {
                    U26EmptyState(if (isLoading) "Syncing your universe…" else "Your library is ready for its first release", isLoading, onRefresh)
                } else {
                    val rows = if (wide) 4 else 2
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        shortcuts.chunked(rows).forEach { group ->
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                group.forEach { track ->
                                    Row(
                                        Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).background(U26Raised).clickable { onPlayTrack(track) },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (!track.coverUrl.isNullOrBlank()) {
                                            AsyncImage(model = track.coverUrl, contentDescription = "Artwork for ${track.title}", modifier = Modifier.size(48.dp), contentScale = ContentScale.Crop)
                                        } else {
                                            Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(U26Violet.copy(alpha = .55f), U26Raised))), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Outlined.MusicNote, null, tint = U26Lime)
                                            }
                                        }
                                        Text(track.title, Modifier.weight(1f).padding(horizontal = 9.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                repeat((rows - group.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            item {
                U26SectionHeading("CURATED & TRENDING", "DISCOVER MORE", onExplore)
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        U26FeatureCard(
                            title = featured?.title ?: "Discover HTE",
                            subtitle = featured?.artist ?: "Fresh releases from High-Tech Entertainment",
                            tag = "FEATURED AUDIO",
                            colors = listOf(Color(0xFFB78BFF), Color(0xFF7C3AED)),
                            modifier = Modifier.weight(1f),
                            actionLabel = if (featured == null) "Explore" else "Listen now",
                            onClick = { if (featured != null) onPlayTrack(featured) else onExplore() }
                        )
                        U26FeatureCard(
                            title = videos.firstOrNull()?.title ?: "HTE Visual Network",
                            subtitle = videos.firstOrNull()?.artist ?: "Music videos and performances",
                            tag = "VIDEO THEATER",
                            colors = listOf(Color(0xFFD4F542), Color(0xFF84CC16)),
                            modifier = Modifier.weight(1f),
                            actionLabel = "Open videos",
                            onClick = { if (videos.isNotEmpty()) onPlayVideo(videos.first()) else onVideos() }
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        U26FeatureCard(
                            title = featured?.title ?: "Discover HTE",
                            subtitle = featured?.artist ?: "Fresh releases from High-Tech Entertainment",
                            tag = "FEATURED AUDIO",
                            colors = listOf(Color(0xFFB78BFF), Color(0xFF7C3AED)),
                            modifier = Modifier.fillMaxWidth(),
                            actionLabel = if (featured == null) "Explore music" else "Listen now",
                            onClick = { if (featured != null) onPlayTrack(featured) else onExplore() }
                        )
                        U26FeatureCard(
                            title = videos.firstOrNull()?.title ?: "HTE Visual Network",
                            subtitle = videos.firstOrNull()?.artist ?: "Music videos and performances",
                            tag = "VIDEO THEATER",
                            colors = listOf(Color(0xFFD4F542), Color(0xFF84CC16)),
                            modifier = Modifier.fillMaxWidth(),
                            actionLabel = "Open videos",
                            onClick = { if (videos.isNotEmpty()) onPlayVideo(videos.first()) else onVideos() }
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(Color(0xFF101A20)).border(1.dp, U26Violet.copy(alpha = .25f), RoundedCornerShape(19.dp)).clickable(onClick = onVoice).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(U26Violet.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.GraphicEq, null, tint = U26Violet, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("SYNAPSE AI  /  VOICE COMPANION", color = U26Violet, fontSize = 9.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                        Text("Your voice. Your universe.", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Search, navigate and control playback by voice", color = U26Muted, fontSize = 11.sp)
                    }
                    Icon(Icons.Outlined.Mic, null, tint = U26Violet, modifier = Modifier.size(23.dp))
                }
            }
            item {
                U26SectionHeading("CONTINUE LISTENING", "VIEW ALL", onExplore)
                if (tracks.isEmpty()) U26EmptyState(if (isLoading) "Syncing your universe…" else "Your next favourite is waiting", isLoading, onRefresh)
                else LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp), contentPadding = PaddingValues(top = 10.dp)) {
                    items(tracks.take(8), key = { it.id }) { track ->
                        Card(modifier = Modifier.width(if (wide) 190.dp else 164.dp).clickable { onPlayTrack(track) }, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = U26Panel)) {
                            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.fillMaxWidth().height(if (wide) 112.dp else 96.dp).clip(RoundedCornerShape(12.dp)).background(U26Raised), contentAlignment = Alignment.Center) {
                                    if (!track.coverUrl.isNullOrBlank()) AsyncImage(model = track.coverUrl, contentDescription = "Artwork for ${track.title}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                    else Icon(Icons.Outlined.Album, null, tint = U26Gold, modifier = Modifier.size(34.dp))
                                    IconButton(onClick = { onPlayTrack(track) }, modifier = Modifier.align(Alignment.BottomEnd).size(36.dp)) { Icon(Icons.Outlined.PlayCircle, "Play ${track.title}", tint = U26Lime) }
                                }
                                Text(track.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, color = U26Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(track.genre ?: "HTE release", Modifier.weight(1f), color = U26Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    IconButton(onClick = { onToggleFavourite(track) }, modifier = Modifier.size(28.dp)) {
                                        Icon(if (track.id in favouriteTrackIds) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Toggle favourite", tint = if (track.id in favouriteTrackIds) U26Lime else U26Muted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                U26SectionHeading("TRENDING IN UNIVERSE", "ALL TRACKS", onExplore)
                if (tracks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tracks.take(5).forEachIndexed { index, track ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(U26Panel).clickable { onPlayTrack(track) }.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("%02d".format(index + 1), color = U26Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                                if (!track.coverUrl.isNullOrBlank()) AsyncImage(model = track.coverUrl, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                                else Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(U26Raised), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Album, null, tint = U26Gold) }
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, color = U26Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { onDownloadTrack(track) }) { Icon(Icons.Outlined.Download, "Download ${track.title}", tint = U26Silver) }
                                Icon(Icons.Outlined.PlayArrow, null, tint = U26Lime)
                            }
                        }
                    }
                } else if (!isLoading) Text("New releases will appear here when published.", color = U26Muted, fontSize = 12.sp)
            }
            item {
                U26SectionHeading("TRENDING MUSIC VIDEOS", "OPEN VIDEO HUB", onVideos)
                if (videos.isEmpty()) Text(if (isLoading) "Loading visual catalogue…" else "Published music videos and performances will appear here.", color = U26Muted, fontSize = 12.sp)
                else LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(videos.take(8), key = { it.id }) { video ->
                        Column(Modifier.width(if (wide) 230.dp else 190.dp).clickable { onPlayVideo(video) }, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.fillMaxWidth().height(if (wide) 132.dp else 108.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(U26Violet.copy(alpha = .5f), U26Raised, Color(0xFF101820)))), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.PlayCircle, null, tint = U26Lime, modifier = Modifier.size(42.dp))
                                Text("HTE VIDEO", Modifier.align(Alignment.TopStart).padding(9.dp), color = U26Lime, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                            }
                            Text(video.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(video.artist ?: "High-Tech Entertainment", color = U26Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row {
                                TextButton(onClick = { onPlayVideo(video) }, contentPadding = PaddingValues(horizontal = 4.dp)) { Icon(Icons.Outlined.PlayArrow, null, tint = U26Lime, modifier = Modifier.size(16.dp)); Text("Watch", color = U26Lime, fontSize = 10.sp) }
                                TextButton(onClick = { onDownloadVideo(video) }, contentPadding = PaddingValues(horizontal = 4.dp)) { Icon(Icons.Outlined.Download, null, tint = U26Silver, modifier = Modifier.size(15.dp)); Text("Save", color = U26Silver, fontSize = 10.sp) }
                            }
                        }
                    }
                }
            }
            if (wideGenres.isNotEmpty()) item {
                U26SectionHeading("EXPLORE GENRES", "BROWSE CATALOGUE", onExplore)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(wideGenres) { genre ->
                        AssistChip(onClick = onExplore, label = { Text(genre, color = U26Silver) }, leadingIcon = { Icon(Icons.Outlined.MusicNote, null, tint = U26Gold, modifier = Modifier.size(15.dp)) }, colors = AssistChipDefaults.assistChipColors(containerColor = U26Panel))
                    }
                }
            }
        }
    }
}

@Composable
private fun U26FeatureCard(title: String, subtitle: String, tag: String, colors: List<Color>, modifier: Modifier, actionLabel: String, onClick: () -> Unit) {
    Card(modifier = modifier.height(178.dp).clickable(onClick = onClick), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = colors.first())) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors))) {
            Icon(Icons.Outlined.GraphicEq, null, tint = Color.Black.copy(alpha = .10f), modifier = Modifier.align(Alignment.CenterEnd).size(130.dp))
            Column(Modifier.fillMaxSize().padding(17.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.fillMaxWidth(.76f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(tag, color = Color.Black.copy(alpha = .72f), fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.ExtraBold)
                    Text(title, color = Color(0xFF101014), fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, color = Color.Black.copy(alpha = .72f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color(0xFF101014), shape = RoundedCornerShape(50)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PlayArrow, null, tint = colors.first(), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(actionLabel, color = colors.first(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun U26SectionHeading(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = U26Silver, fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.width(34.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(U26Lime))
        }
        TextButton(onClick = onClick) { Text(action, color = U26Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
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
