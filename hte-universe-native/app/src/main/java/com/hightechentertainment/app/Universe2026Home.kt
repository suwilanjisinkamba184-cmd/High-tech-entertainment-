package com.hightechentertainment.app

import com.hightechentertainment.app.data.TrackDto
import com.hightechentertainment.app.data.VideoDto

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val U26Black = Color(0xFF08090B)
private val U26Panel = Color(0xFF121418)
private val U26Raised = Color(0xFF1A1E25)
private val U26Gold = Color(0xFFD6B15E)
private val U26Silver = Color(0xFFC7CCD6)
private val U26Muted = Color(0xFF89919D)
private val U26Cyan = Color(0xFF70D7FF)

@Composable
fun Universe2026Home(
    tracks: List<TrackDto>, videos: List<VideoDto>, isLoading: Boolean,
    onExplore: () -> Unit, onVideos: () -> Unit, onVoice: () -> Unit,
    onPlayTrack: (TrackDto) -> Unit, onPlayVideo: (VideoDto) -> Unit,
    onDownloadTrack: (TrackDto) -> Unit, onDownloadVideo: (VideoDto) -> Unit,
    favouriteTrackIds: Set<String>, onToggleFavourite: (TrackDto) -> Unit, onRefresh: () -> Unit
) {
    val featured = tracks.firstOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(U26Black),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(U26Cyan))
                    Spacer(Modifier.width(8.dp))
                    Text("TRANSMISSION LIVE  /  HTE SYS.2026", color = U26Cyan, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("01 — 09", color = U26Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                Text("Your universe.\nYour sound.", color = Color.White, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                Text("Music, culture and discovery — connected.", color = U26Silver, fontSize = 13.sp)
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, U26Gold.copy(alpha = .28f), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = U26Panel)
            ) {
                Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF302719), U26Panel, Color(0xFF101820))))) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("FEATURED FREQUENCY", color = U26Gold, fontSize = 9.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Outlined.GraphicEq, null, tint = U26Gold, modifier = Modifier.size(20.dp))
                        }
                        Box(Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Color(0xFF59452A), Color(0xFF20242B), Color(0xFF101820)))), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Headphones, null, tint = U26Gold.copy(alpha = .82f), modifier = Modifier.size(62.dp))
                            Text("HTE  /  AUDIO UNIVERSE", color = U26Silver.copy(alpha = .65f), fontSize = 8.sp, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
                        }
                        Text(featured?.title ?: "The next sound starts here.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(featured?.artist ?: "Discover the latest from High-Tech Entertainment", color = U26Silver, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { if (featured != null) onPlayTrack(featured) else onExplore() }, colors = ButtonDefaults.buttonColors(containerColor = U26Gold, contentColor = U26Black), shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
                                Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(if (featured != null) "Listen now" else "Explore music", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = onExplore, border = androidx.compose.foundation.BorderStroke(1.dp, U26Silver.copy(alpha = .25f)), shape = RoundedCornerShape(13.dp)) {
                                Text("Discover", color = U26Silver)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(Color(0xFF101A20)).border(1.dp, U26Cyan.copy(alpha = .22f), RoundedCornerShape(19.dp)).clickable(onClick = onVoice).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(U26Cyan.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.GraphicEq, null, tint = U26Cyan, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("HTE CO-PILOT  /  SYNAPSE AI", color = U26Cyan, fontSize = 9.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
                    Text("Your voice. Your universe.", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Tap to explore by voice", color = U26Muted, fontSize = 11.sp)
                }
                Icon(Icons.Outlined.Mic, null, tint = U26Cyan, modifier = Modifier.size(23.dp))
            }
        }
        item {
            U26SectionHeading("CONTINUE LISTENING", "VIEW ALL", onExplore)
            if (tracks.isEmpty()) {
                U26EmptyState(if (isLoading) "Syncing your universe…" else "Your next favourite is waiting", isLoading, onRefresh)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(11.dp), contentPadding = PaddingValues(top = 10.dp)) {
                    items(tracks.take(6), key = { it.id }) { track ->
                        Card(modifier = Modifier.width(164.dp).clickable { onPlayTrack(track) }, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = U26Panel)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFF4B3923), U26Raised, Color(0xFF202B34)))), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.MusicNote, null, tint = U26Gold, modifier = Modifier.size(34.dp))
                                    IconButton(onClick = { onPlayTrack(track) }, modifier = Modifier.align(Alignment.BottomEnd).size(34.dp)) { Icon(Icons.Outlined.PlayCircle, "Play ${track.title}", tint = U26Gold) }
                                }
                                Text(track.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, color = U26Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(2.dp)).background(U26Gold.copy(alpha = .65f)))
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(onClick = { onToggleFavourite(track) }, modifier = Modifier.size(28.dp)) { Icon(if (track.id in favouriteTrackIds) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "Toggle favourite", tint = if (track.id in favouriteTrackIds) U26Gold else U26Muted, modifier = Modifier.size(16.dp)) }
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
                    tracks.take(4).forEachIndexed { index, track ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(U26Panel).clickable { onPlayTrack(track) }.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("%02d".format(index + 1), color = U26Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                            Box(Modifier.size(43.dp).clip(RoundedCornerShape(10.dp)).background(U26Raised), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Album, null, tint = U26Gold) }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, color = U26Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onDownloadTrack(track) }) { Icon(Icons.Outlined.Download, "Download ${track.title}", tint = U26Silver) }
                            Icon(Icons.Outlined.PlayArrow, null, tint = U26Gold)
                        }
                    }
                }
            } else if (!isLoading) Text("New releases will appear here when published.", color = U26Muted, fontSize = 12.sp)
        }
        item {
            U26SectionHeading("CINEMA / VISUALS", "OPEN VIDEOS", onVideos)
            if (videos.isEmpty()) {
                Text(if (isLoading) "Loading visual catalogue…" else "Music videos, performances and stories will appear here.", color = U26Muted, fontSize = 12.sp)
            } else {
                val video = videos.first()
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(U26Panel).clickable { onPlayVideo(video) }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(82.dp, 66.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFF4B3923), U26Raised))), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PlayCircle, null, tint = U26Gold, modifier = Modifier.size(34.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(video.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(video.artist ?: "High-Tech Entertainment", color = U26Muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { onDownloadVideo(video) }) { Icon(Icons.Outlined.Download, "Download ${video.title}", tint = U26Silver) }
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
            Box(Modifier.width(34.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(U26Gold))
        }
        TextButton(onClick = onClick) { Text(action, color = U26Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
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
