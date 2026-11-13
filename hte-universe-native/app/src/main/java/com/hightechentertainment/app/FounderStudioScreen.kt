package com.hightechentertainment.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hightechentertainment.app.data.FounderStudioRepository
import com.hightechentertainment.app.data.FounderTrackDto
import com.hightechentertainment.app.data.FounderVideoDto
import kotlinx.coroutines.launch

private val FounderGold = Color(0xFFD6B15E)
private val FounderPanel = Color(0xFF121418)
private val FounderMuted = Color(0xFF8B929D)

@Composable
fun FounderStudioScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { FounderStudioRepository() }
    var selectedTab by remember { mutableStateOf(0) }
    var tracks by remember { mutableStateOf<List<FounderTrackDto>>(emptyList()) }
    var videos by remember { mutableStateOf<List<FounderVideoDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    var trackTitle by remember { mutableStateOf("") }
    var trackArtist by remember { mutableStateOf("L Junior ZM") }
    var trackGenre by remember { mutableStateOf("") }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var coverUri by remember { mutableStateOf<Uri?>(null) }

    var videoTitle by remember { mutableStateOf("") }
    var videoArtist by remember { mutableStateOf("High-Tech Entertainment") }
    var videoDescription by remember { mutableStateOf("") }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var thumbnailUri by remember { mutableStateOf<Uri?>(null) }

    fun refresh() {
        scope.launch {
            isLoading = true
            runCatching {
                tracks = repository.getTracks()
                videos = repository.getVideos()
            }.onFailure { status = it.message?.take(180) ?: "Founder catalogue could not be loaded." }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { audioUri = it; status = null }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { coverUri = it; status = null }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { videoUri = it; status = null }
    val thumbnailPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { thumbnailUri = it; status = null }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FOUNDER STUDIO", color = FounderGold, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text("Content control centre", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close Founder Studio", tint = Color.White) }
        }
        Text("Owner-only uploads. New content is saved as a draft until you publish it.", color = FounderMuted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        SecondaryTabRow(selectedTabIndex = selectedTab, containerColor = FounderPanel, contentColor = FounderGold) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Music") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Videos") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Manage") })
        }
        Spacer(Modifier.height(12.dp))
        when (selectedTab) {
            0 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { Text("UPLOAD MUSIC", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold) }
                item { OutlinedTextField(trackTitle, { trackTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Track title") }, singleLine = true) }
                item { OutlinedTextField(trackArtist, { trackArtist = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Artist") }, singleLine = true) }
                item { OutlinedTextField(trackGenre, { trackGenre = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Genre") }, singleLine = true) }
                item {
                    OutlinedButton(onClick = { audioPicker.launch("audio/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.AudioFile, null); Spacer(Modifier.width(8.dp)); Text(audioUri?.let { displayName(context, it) } ?: "Choose audio file")
                    }
                }
                item {
                    OutlinedButton(onClick = { coverPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(8.dp)); Text(coverUri?.let { displayName(context, it) } ?: "Choose cover artwork (optional)")
                    }
                }
                item {
                    Button(onClick = {
                        val uri = audioUri
                        if (trackTitle.isBlank() || uri == null) { status = "Enter a title and choose an audio file."; return@Button }
                        scope.launch {
                            isUploading = true; status = null
                            runCatching {
                                val coverUrl = coverUri?.let { selected ->
                                    val size = fileSize(context, selected)
                                    val mime = context.contentResolver.getType(selected) ?: "image/jpeg"
                                    repository.uploadArtwork(context.contentResolver.openInputStream(selected) ?: error("Could not open cover artwork."), size, mime, displayName(context, selected))
                                }
                                val size = fileSize(context, uri)
                                val mime = context.contentResolver.getType(uri) ?: "audio/mpeg"
                                repository.uploadTrack(trackTitle, trackArtist, trackGenre,
                                    context.contentResolver.openInputStream(uri) ?: error("Could not open audio file."), size, mime, coverUrl)
                            }.onSuccess {
                                status = "Track uploaded as a draft. Publish it from Manage when ready."
                                trackTitle = ""; trackGenre = ""; audioUri = null; coverUri = null
                                refresh()
                            }.onFailure { status = it.message?.take(180) ?: "Track upload failed." }
                            isUploading = false
                        }
                    }, enabled = !isUploading, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = FounderGold, contentColor = Color.Black)) {
                        if (isUploading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else { Icon(Icons.Outlined.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text("Upload track as draft") }
                    }
                }
                item { Text("Audio bucket limit: 100 MB. Supported formats depend on the HTE storage bucket.", color = FounderMuted, fontSize = 10.sp) }
            }
            1 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { Text("UPLOAD VIDEO", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold) }
                item { OutlinedTextField(videoTitle, { videoTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Video title") }, singleLine = true) }
                item { OutlinedTextField(videoArtist, { videoArtist = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Artist / channel") }, singleLine = true) }
                item { OutlinedTextField(videoDescription, { videoDescription = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 3) }
                item {
                    OutlinedButton(onClick = { videoPicker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.VideoFile, null); Spacer(Modifier.width(8.dp)); Text(videoUri?.let { displayName(context, it) } ?: "Choose video file")
                    }
                }
                item {
                    OutlinedButton(onClick = { thumbnailPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(8.dp)); Text(thumbnailUri?.let { displayName(context, it) } ?: "Choose thumbnail (optional)")
                    }
                }
                item {
                    Button(onClick = {
                        val uri = videoUri
                        if (videoTitle.isBlank() || uri == null) { status = "Enter a title and choose a video file."; return@Button }
                        scope.launch {
                            isUploading = true; status = null
                            runCatching {
                                val thumbnailUrl = thumbnailUri?.let { selected ->
                                    val size = fileSize(context, selected)
                                    val mime = context.contentResolver.getType(selected) ?: "image/jpeg"
                                    repository.uploadArtwork(context.contentResolver.openInputStream(selected) ?: error("Could not open thumbnail."), size, mime, displayName(context, selected))
                                }
                                val size = fileSize(context, uri)
                                val mime = context.contentResolver.getType(uri) ?: "video/mp4"
                                repository.uploadVideo(videoTitle, videoArtist, videoDescription,
                                    context.contentResolver.openInputStream(uri) ?: error("Could not open video file."), size, mime, thumbnailUrl)
                            }.onSuccess {
                                status = "Video uploaded as a draft. Publish it from Manage when ready."
                                videoTitle = ""; videoDescription = ""; videoUri = null; thumbnailUri = null
                                refresh()
                            }.onFailure { status = it.message?.take(180) ?: "Video upload failed." }
                            isUploading = false
                        }
                    }, enabled = !isUploading, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = FounderGold, contentColor = Color.Black)) {
                        if (isUploading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else { Icon(Icons.Outlined.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text("Upload video as draft") }
                    }
                }
                item { Text("Video bucket limit: 500 MB. Upload progress and retry handling are managed by the storage transfer.", color = FounderMuted, fontSize = 10.sp) }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("YOUR CONTENT", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { refresh() }) { Icon(Icons.Outlined.Refresh, "Refresh", tint = FounderGold) }
                    }
                }
                item { Text("MUSIC · ${tracks.size}", color = Color.White, fontWeight = FontWeight.Bold) }
                items(tracks, key = { "track-${it.id}" }) { track ->
                    FounderContentRow(
                        title = track.title, subtitle = "${track.artist} · ${track.genre ?: "No genre"}", published = track.isPublished,
                        onTogglePublish = { publish -> scope.launch { runCatching { repository.setTrackPublished(track.id, publish); tracks = repository.getTracks() }.onFailure { status = it.message } } },
                        onDelete = { scope.launch { runCatching { repository.deleteTrack(track); tracks = repository.getTracks() }.onFailure { status = it.message } } }
                    )
                }
                item { Text("VIDEOS · ${videos.size}", color = Color.White, fontWeight = FontWeight.Bold) }
                items(videos, key = { "video-${it.id}" }) { video ->
                    FounderContentRow(
                        title = video.title, subtitle = video.artist ?: "High-Tech Entertainment", published = video.isPublished,
                        onTogglePublish = { publish -> scope.launch { runCatching { repository.setVideoPublished(video.id, publish); videos = repository.getVideos() }.onFailure { status = it.message } } },
                        onDelete = { scope.launch { runCatching { repository.deleteVideo(video); videos = repository.getVideos() }.onFailure { status = it.message } } }
                    )
                }
                if (isLoading) item { CircularProgressIndicator(color = FounderGold) }
            }
        }
        status?.let { Text(it, color = FounderGold, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun FounderContentRow(title: String, subtitle: String, published: Boolean, onTogglePublish: (Boolean) -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = FounderPanel), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = FounderMuted, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (published) "Published" else "Draft", color = if (published) FounderGold else FounderMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { onTogglePublish(!published) }) { Text(if (published) "Unpublish" else "Publish", color = FounderGold) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete content", tint = FounderMuted) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this content?") },
            text = { Text("This permanently removes the selected item from HTE Universe. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

private fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) return cursor.getString(0) ?: "Selected file"
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "Selected file"
}

private fun fileSize(context: Context, uri: Uri): Long {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val size = cursor.getLong(0)
            if (size > 0L) return size
        }
    }
    return context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
}
