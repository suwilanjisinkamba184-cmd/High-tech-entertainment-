package com.hightechentertainment.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
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
import com.hightechentertainment.app.data.FounderArtistDto
import com.hightechentertainment.app.data.FounderReleaseDto
import com.hightechentertainment.app.data.FounderInboxMessageDto
import com.hightechentertainment.app.data.FounderAudioAdDto
import com.hightechentertainment.app.data.FounderVoiceDto
import com.hightechentertainment.app.data.FounderMarketingCampaignDto
import com.hightechentertainment.app.data.FounderSocialConnectionDto
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private val FounderGold = Color(0xFFD6B15E)
private val FounderPanel = Color(0xFF121418)
private val FounderMuted = Color(0xFF8B929D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FounderStudioScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { FounderStudioRepository() }
    val tabs = listOf("Overview", "Music catalogue", "Video library", "Artists", "Releases", "Enquiries inbox", "AI Ad Studio", "AI Marketing Agent", "Settings")
    var selectedTab by remember { mutableStateOf(0) }
    var tracks by remember { mutableStateOf<List<FounderTrackDto>>(emptyList()) }
    var videos by remember { mutableStateOf<List<FounderVideoDto>>(emptyList()) }
    var artists by remember { mutableStateOf<List<FounderArtistDto>>(emptyList()) }
    var releases by remember { mutableStateOf<List<FounderReleaseDto>>(emptyList()) }
    var inboxMessages by remember { mutableStateOf<List<FounderInboxMessageDto>>(emptyList()) }
    var audioAds by remember { mutableStateOf<List<FounderAudioAdDto>>(emptyList()) }
    var adVoices by remember { mutableStateOf<List<FounderVoiceDto>>(emptyList()) }
    var campaigns by remember { mutableStateOf<List<FounderMarketingCampaignDto>>(emptyList()) }
    var socialConnections by remember { mutableStateOf<List<FounderSocialConnectionDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingModule by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var isGeneratingAd by remember { mutableStateOf(false) }
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

    var artistName by remember { mutableStateOf("") }
    var artistBio by remember { mutableStateOf("") }
    var artistImageUri by remember { mutableStateOf<Uri?>(null) }
    var releaseTitle by remember { mutableStateOf("") }
    var releaseDate by remember { mutableStateOf("") }
    var releaseSpotifyUrl by remember { mutableStateOf("") }
    var releaseCoverUri by remember { mutableStateOf<Uri?>(null) }

    var adTitle by remember { mutableStateOf("") }
    var adScript by remember { mutableStateOf("") }
    var adVoiceId by remember { mutableStateOf("") }
    var adModelId by remember { mutableStateOf("eleven_multilingual_v2") }
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    var campaignTitle by remember { mutableStateOf("") }
    var campaignMessage by remember { mutableStateOf("") }
    var campaignType by remember { mutableStateOf("announcement") }
    var campaignTypeMenuExpanded by remember { mutableStateOf(false) }
    val campaignChannels = remember { mutableStateListOf("instagram", "facebook", "x", "youtube", "tiktok", "whatsapp_status") }
    val channelLabels = listOf(
        "instagram" to "Instagram", "facebook" to "Facebook", "x" to "X",
        "youtube" to "YouTube", "tiktok" to "TikTok", "whatsapp_status" to "WhatsApp Status"
    )

    fun refresh() {
        scope.launch {
            isLoading = true
            status = null
            runCatching {
                tracks = repository.getTracks()
                videos = repository.getVideos()
                artists = repository.getArtists()
                releases = repository.getReleases()
            }.onFailure { status = it.toHteUserMessage("Founder catalogue could not be loaded. Please retry.") }
            isLoading = false
        }
    }

    fun loadInbox() {
        scope.launch {
            isLoadingModule = true
            runCatching { repository.getInboxMessages() }
                .onSuccess { inboxMessages = it }
                .onFailure { status = it.toHteUserMessage("Founder inbox is unavailable. Please retry.") }
            isLoadingModule = false
        }
    }

    fun loadAds() {
        scope.launch {
            isLoadingModule = true
            runCatching { repository.getAudioAds() }
                .onSuccess { audioAds = it }
                .onFailure { status = it.toHteUserMessage("Advertisement library is unavailable. Please retry.") }
            runCatching { repository.getAdVoices() }
                .onSuccess {
                    adVoices = it
                    if (adVoiceId.isBlank()) adVoiceId = it.firstOrNull()?.voiceId.orEmpty()
                }
                .onFailure { status = it.toHteUserMessage("Voice options are unavailable. Check the service connection and retry.") }
            isLoadingModule = false
        }
    }

    fun loadCampaigns() {
        scope.launch {
            isLoadingModule = true
            runCatching { repository.getMarketingCampaigns() }
                .onSuccess { campaigns = it }
                .onFailure { status = it.toHteUserMessage("Campaign library is unavailable. Please retry.") }
            runCatching { repository.getSocialConnections() }
                .onSuccess { socialConnections = it }
                .onFailure { status = it.toHteUserMessage("Platform connection status is unavailable. Please retry.") }
            isLoadingModule = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            5 -> loadInbox()
            6 -> loadAds()
            7 -> loadCampaigns()
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { audioUri = it; status = null }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { coverUri = it; status = null }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { videoUri = it; status = null }
    val thumbnailPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { thumbnailUri = it; status = null }
    val artistImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { artistImageUri = it; status = null }
    val releaseCoverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { releaseCoverUri = it; status = null }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = FounderPanel), shape = RoundedCornerShape(22.dp)) {
            Box(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF352818), FounderPanel, Color(0xFF0D1117)))).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("HIGH-TECH ENTERTAINMENT / PRIVATE CONSOLE", color = FounderGold, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                            Text("Founder Studio", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { refresh() }) { Icon(Icons.Outlined.Refresh, "Refresh Studio", tint = FounderGold) }
                        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close Founder Studio", tint = Color.White) }
                    }
                    Text("Owner-only workspace. Uploads and generated adverts remain drafts until you approve publication.", color = FounderMuted, fontSize = 11.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FounderMetric(tracks.size.toString(), "MUSIC", Modifier.weight(1f))
                        FounderMetric(videos.size.toString(), "VIDEOS", Modifier.weight(1f))
                        FounderMetric(artists.size.toString(), "ARTISTS", Modifier.weight(1f))
                        FounderMetric(releases.size.toString(), "RELEASES", Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp, containerColor = FounderPanel, contentColor = FounderGold) {
            tabs.forEachIndexed { index, label ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label, fontSize = 10.sp, maxLines = 1) })
            }
        }
        Spacer(Modifier.height(10.dp))
        when (selectedTab) {
            0 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                item { Text("STUDIO OVERVIEW", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        FounderMetric(tracks.size.toString(), "TRACKS", Modifier.weight(1f))
                        FounderMetric(videos.size.toString(), "VIDEOS", Modifier.weight(1f))
                        FounderMetric(artists.size.toString(), "ARTISTS", Modifier.weight(1f))
                        FounderMetric(releases.size.toString(), "RELEASES", Modifier.weight(1f))
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = FounderPanel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("QUICK ACTIONS", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { selectedTab = 1 }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.AudioFile, null); Spacer(Modifier.width(5.dp)); Text("Music", fontSize = 11.sp) }
                                OutlinedButton(onClick = { selectedTab = 2 }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.VideoFile, null); Spacer(Modifier.width(5.dp)); Text("Video", fontSize = 11.sp) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { selectedTab = 3 }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.PersonAdd, null); Spacer(Modifier.width(5.dp)); Text("Artist", fontSize = 11.sp) }
                                OutlinedButton(onClick = { selectedTab = 4 }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Album, null); Spacer(Modifier.width(5.dp)); Text("Release", fontSize = 11.sp) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { selectedTab = 6 }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.RecordVoiceOver, null); Spacer(Modifier.width(5.dp)); Text("AI Ad Studio", fontSize = 11.sp) }
                                OutlinedButton(onClick = { selectedTab = 7 }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Campaign, null); Spacer(Modifier.width(5.dp)); Text("Marketing", fontSize = 11.sp) }
                            }
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = FounderPanel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("PUBLISHING WORKFLOW", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                            Text("New music, videos, artists, releases and generated adverts are saved as drafts. You control when each item becomes public.", color = Color.White, fontSize = 12.sp)
                            Text("Social campaigns are saved as drafts only. Nothing is queued or published until platform authorization and a separate approval workflow are verified.", color = FounderMuted, fontSize = 10.sp)
                        }
                    }
                }
                if (isLoading) item { CircularProgressIndicator(color = FounderGold, modifier = Modifier.size(22.dp)) }
            }
            1 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { Text("UPLOAD MUSIC", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold) }
                item { OutlinedTextField(trackTitle, { trackTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Track title") }, singleLine = true) }
                item { OutlinedTextField(trackArtist, { trackArtist = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Artist") }, singleLine = true) }
                item { OutlinedTextField(trackGenre, { trackGenre = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Genre") }, singleLine = true) }
                item { OutlinedButton(onClick = { audioPicker.launch("audio/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.AudioFile, null); Spacer(Modifier.width(8.dp)); Text(audioUri?.let { displayName(context, it) } ?: "Choose audio file") } }
                item { OutlinedButton(onClick = { coverPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(8.dp)); Text(coverUri?.let { displayName(context, it) } ?: "Choose cover artwork (optional)") } }
                item {
                    Button(onClick = {
                        val uri = audioUri
                        if (trackTitle.isBlank() || uri == null) { status = "Enter a title and choose an audio file."; return@Button }
                        scope.launch {
                            isUploading = true; status = null
                            runCatching {
                                val coverUrl = coverUri?.let { selected ->
                                    val size = fileSize(context, selected); val mime = context.contentResolver.getType(selected) ?: "image/jpeg"
                                    repository.uploadArtwork(context.contentResolver.openInputStream(selected) ?: error("Could not open cover artwork."), size, mime, displayName(context, selected))
                                }
                                val size = fileSize(context, uri); val mime = context.contentResolver.getType(uri) ?: "audio/mpeg"
                                repository.uploadTrack(trackTitle, trackArtist, trackGenre, context.contentResolver.openInputStream(uri) ?: error("Could not open audio file."), size, mime, coverUrl)
                            }.onSuccess { status = "Track uploaded as a draft. Publish it when ready."; trackTitle = ""; trackGenre = ""; audioUri = null; coverUri = null; refresh() }
                                .onFailure { status = it.toHteUserMessage("Track upload failed. Check the file and try again.") }
                            isUploading = false
                        }
                    }, enabled = !isUploading, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = FounderGold, contentColor = Color.Black)) {
                        if (isUploading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else { Icon(Icons.Outlined.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text("Upload track as draft") }
                    }
                }
                item { Text("YOUR MUSIC · ${tracks.size}", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold) }
                items(tracks, key = { it.id }) { track -> FounderContentRow(track.title, "${track.artist} · ${track.genre ?: "No genre"}", track.isPublished,
                    onTogglePublish = { publish -> scope.launch { runCatching { repository.setTrackPublished(track.id, publish); tracks = repository.getTracks() }.onFailure { status = it.toHteUserMessage("Could not update track publication. Please retry.") } } },
                    onDelete = { scope.launch { runCatching { repository.deleteTrack(track); tracks = repository.getTracks() }.onFailure { status = it.toHteUserMessage("Could not delete this track. Please retry.") } } }) }
                if (tracks.isEmpty()) item { Text("No tracks have been uploaded yet.", color = FounderMuted, fontSize = 11.sp) }
            }
            2 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { Text("UPLOAD VIDEO", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold) }
                item { OutlinedTextField(videoTitle, { videoTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Video title") }, singleLine = true) }
                item { OutlinedTextField(videoArtist, { videoArtist = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Artist / channel") }, singleLine = true) }
                item { OutlinedTextField(videoDescription, { videoDescription = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 3) }
                item { OutlinedButton(onClick = { videoPicker.launch("video/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.VideoFile, null); Spacer(Modifier.width(8.dp)); Text(videoUri?.let { displayName(context, it) } ?: "Choose video file") } }
                item { OutlinedButton(onClick = { thumbnailPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(8.dp)); Text(thumbnailUri?.let { displayName(context, it) } ?: "Choose thumbnail (optional)") } }
                item {
                    Button(onClick = {
                        val uri = videoUri
                        if (videoTitle.isBlank() || uri == null) { status = "Enter a title and choose a video file."; return@Button }
                        scope.launch {
                            isUploading = true; status = null
                            runCatching {
                                val thumbnailUrl = thumbnailUri?.let { selected ->
                                    val size = fileSize(context, selected); val mime = context.contentResolver.getType(selected) ?: "image/jpeg"
                                    repository.uploadArtwork(context.contentResolver.openInputStream(selected) ?: error("Could not open thumbnail."), size, mime, displayName(context, selected))
                                }
                                val size = fileSize(context, uri); val mime = context.contentResolver.getType(uri) ?: "video/mp4"
                                repository.uploadVideo(videoTitle, videoArtist, videoDescription, context.contentResolver.openInputStream(uri) ?: error("Could not open video file."), size, mime, thumbnailUrl)
                            }.onSuccess { status = "Video uploaded as a draft. Publish it when ready."; videoTitle = ""; videoDescription = ""; videoUri = null; thumbnailUri = null; refresh() }
                                .onFailure { status = it.toHteUserMessage("Video upload failed. Check the file and try again.") }
                            isUploading = false
                        }
                    }, enabled = !isUploading, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = FounderGold, contentColor = Color.Black)) {
                        if (isUploading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else { Icon(Icons.Outlined.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text("Upload video as draft") }
                    }
                }
                item { Text("YOUR VIDEOS · ${videos.size}", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold) }
                items(videos, key = { it.id }) { video -> FounderContentRow(video.title, video.artist ?: "High-Tech Entertainment", video.isPublished,
                    onTogglePublish = { publish -> scope.launch { runCatching { repository.setVideoPublished(video.id, publish); videos = repository.getVideos() }.onFailure { status = it.toHteUserMessage("Could not update video publication. Please retry.") } } },
                    onDelete = { scope.launch { runCatching { repository.deleteVideo(video); videos = repository.getVideos() }.onFailure { status = it.toHteUserMessage("Could not delete this video. Please retry.") } } }) }
                if (videos.isEmpty()) item { Text("No videos have been uploaded yet.", color = FounderMuted, fontSize = 11.sp) }
            }
            3 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { Text("ADD ARTIST", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold) }
                item { OutlinedTextField(artistName, { artistName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Artist name") }, singleLine = true) }
                item { OutlinedTextField(artistBio, { artistBio = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Artist biography") }, minLines = 3) }
                item { OutlinedButton(onClick = { artistImagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(8.dp)); Text(artistImageUri?.let { displayName(context, it) } ?: "Choose artist image (optional)") } }
                item {
                    Button(onClick = {
                        if (artistName.isBlank()) { status = "Enter an artist name."; return@Button }
                        scope.launch {
                            isUploading = true; status = null
                            runCatching {
                                val imageUrl = artistImageUri?.let { selected ->
                                    val size = fileSize(context, selected); val mime = context.contentResolver.getType(selected) ?: "image/jpeg"
                                    repository.uploadArtwork(context.contentResolver.openInputStream(selected) ?: error("Could not open artist image."), size, mime, displayName(context, selected))
                                }
                                repository.createArtist(artistName, artistBio, imageUrl)
                            }.onSuccess { status = "Artist profile created as a draft."; artistName = ""; artistBio = ""; artistImageUri = null; refresh() }
                                .onFailure { status = it.toHteUserMessage("Artist profile could not be created. Please retry.") }
                            isUploading = false
                        }
                    }, enabled = !isUploading, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = FounderGold, contentColor = Color.Black)) { Text("Create artist draft") }
                }
                item { Text("ARTIST PROFILES · ${artists.size}", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold) }
                items(artists, key = { it.id }) { artist -> FounderContentRow(artist.name, artist.bio?.takeIf(String::isNotBlank) ?: "Artist profile", artist.isPublished,
                    onTogglePublish = { publish -> scope.launch { runCatching { repository.setArtistPublished(artist.id, publish); artists = repository.getArtists() }.onFailure { status = it.toHteUserMessage("Could not update artist publication. Please retry.") } } },
                    onDelete = { scope.launch { runCatching { repository.deleteArtist(artist); artists = repository.getArtists() }.onFailure { status = it.toHteUserMessage("Could not delete this artist. Please retry.") } } }) }
                if (artists.isEmpty()) item { Text("No artist profiles yet.", color = FounderMuted, fontSize = 11.sp) }
            }
            4 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { Text("ADD RELEASE", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold) }
                item { OutlinedTextField(releaseTitle, { releaseTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Release title") }, singleLine = true) }
                item { OutlinedTextField(releaseDate, { releaseDate = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Release date (YYYY-MM-DD)") }, singleLine = true) }
                item { OutlinedTextField(releaseSpotifyUrl, { releaseSpotifyUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Spotify URL (optional)") }, singleLine = true) }
                item { OutlinedButton(onClick = { releaseCoverPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(8.dp)); Text(releaseCoverUri?.let { displayName(context, it) } ?: "Choose cover artwork (optional)") } }
                item {
                    Button(onClick = {
                        if (releaseTitle.isBlank()) { status = "Enter a release title."; return@Button }
                        scope.launch {
                            isUploading = true; status = null
                            runCatching {
                                val coverUrl = releaseCoverUri?.let { selected ->
                                    val size = fileSize(context, selected); val mime = context.contentResolver.getType(selected) ?: "image/jpeg"
                                    repository.uploadArtwork(context.contentResolver.openInputStream(selected) ?: error("Could not open release artwork."), size, mime, displayName(context, selected))
                                }
                                repository.createRelease(releaseTitle, releaseDate, releaseSpotifyUrl, coverUrl)
                            }.onSuccess { status = "Release created as a draft."; releaseTitle = ""; releaseDate = ""; releaseSpotifyUrl = ""; releaseCoverUri = null; refresh() }
                                .onFailure { status = it.toHteUserMessage("Release could not be created. Please retry.") }
                            isUploading = false
                        }
                    }, enabled = !isUploading, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = FounderGold, contentColor = Color.Black)) { Text("Create release draft") }
                }
                item { Text("RELEASES · ${releases.size}", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold) }
                items(releases, key = { it.id }) { release -> FounderContentRow(release.title, release.releaseDate ?: "Release date not set", release.isPublished,
                    onTogglePublish = { publish -> scope.launch { runCatching { repository.setReleasePublished(release.id, publish); releases = repository.getReleases() }.onFailure { status = it.toHteUserMessage("Could not update release publication. Please retry.") } } },
                    onDelete = { scope.launch { runCatching { repository.deleteRelease(release); releases = repository.getReleases() }.onFailure { status = it.toHteUserMessage("Could not delete this release. Please retry.") } } }) }
                if (releases.isEmpty()) item { Text("No releases have been added yet.", color = FounderMuted, fontSize = 11.sp) }
            }
            5 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { Row(verticalAlignment = Alignment.CenterVertically) { Text("ENQUIRIES INBOX", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); IconButton(onClick = { loadInbox() }) { Icon(Icons.Outlined.Refresh, "Refresh inbox", tint = FounderGold) } } }
                if (isLoadingModule) item { CircularProgressIndicator(color = FounderGold, modifier = Modifier.size(22.dp)) }
                if (!isLoadingModule && inboxMessages.isEmpty()) item { Text("No website enquiries yet.", color = FounderMuted, fontSize = 11.sp) }
                items(inboxMessages, key = { it.id }) { message -> FounderInboxCard(message, context) }
            }
            6 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { Text("AI AD STUDIO", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold) }
                item { OutlinedTextField(adTitle, { adTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Advertisement title") }, singleLine = true) }
                item { OutlinedTextField(adScript, { if (it.length <= 2500) adScript = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Presenter script") }, minLines = 5, maxLines = 8) }
                item { Text("${adScript.length} / 2,500 characters", color = FounderMuted, fontSize = 10.sp) }
                item {
                    OutlinedButton(onClick = { adScript = "HIGH-TECH ENTERTAINMENT — Quality Sound, Global Vibes. Music. Beats. Production. Beyond." }, modifier = Modifier.fillMaxWidth()) {
                        Text("Insert HTE sample script")
                    }
                }
                item {
                    Box {
                        OutlinedButton(onClick = { voiceMenuExpanded = true }, modifier = Modifier.fillMaxWidth(), enabled = adVoices.isNotEmpty()) {
                            Text(adVoices.firstOrNull { it.voiceId == adVoiceId }?.name ?: "Choose an eligible presenter voice")
                        }
                        DropdownMenu(expanded = voiceMenuExpanded, onDismissRequest = { voiceMenuExpanded = false }) {
                            adVoices.forEach { voice -> DropdownMenuItem(text = { Text(voice.name) }, onClick = { adVoiceId = voice.voiceId; voiceMenuExpanded = false }) }
                        }
                    }
                }
                item {
                    Box {
                        OutlinedButton(onClick = { modelMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(if (adModelId == "eleven_turbo_v2_5") "Turbo v2.5 · faster generation" else "Multilingual v2 · expressive delivery") }
                        DropdownMenu(expanded = modelMenuExpanded, onDismissRequest = { modelMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Multilingual v2 · expressive delivery") }, onClick = { adModelId = "eleven_multilingual_v2"; modelMenuExpanded = false })
                            DropdownMenuItem(text = { Text("Turbo v2.5 · faster generation") }, onClick = { adModelId = "eleven_turbo_v2_5"; modelMenuExpanded = false })
                        }
                    }
                }
                item {
                    Button(onClick = {
                        scope.launch {
                            isGeneratingAd = true; status = null
                            runCatching { repository.generateAudioAd(adTitle, adScript, adVoiceId, adModelId) }
                                .onSuccess { status = it; adTitle = ""; adScript = ""; loadAds() }
                                .onFailure { status = it.toHteUserMessage("Voice advertisement could not be generated. Check service availability and try again.") }
                            isGeneratingAd = false
                        }
                    }, enabled = !isGeneratingAd && adTitle.isNotBlank() && adScript.isNotBlank() && adVoiceId.isNotBlank(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = FounderGold, contentColor = Color.Black)) {
                        if (isGeneratingAd) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else { Icon(Icons.Outlined.RecordVoiceOver, null); Spacer(Modifier.width(8.dp)); Text("Generate voice advertisement") }
                    }
                }
                item { Text("GENERATED ADVERTISEMENTS · ${audioAds.size}", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold) }
                if (isLoadingModule) item { CircularProgressIndicator(color = FounderGold, modifier = Modifier.size(22.dp)) }
                items(audioAds, key = { it.id }) { ad -> FounderAdCard(ad,
                    onPreview = { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { status = "No app is available to preview this audio." } },
                    onTogglePublish = { published -> scope.launch { runCatching { repository.setAudioAdPublished(ad.id, published); audioAds = repository.getAudioAds() }.onFailure { status = it.toHteUserMessage("Could not update this advertisement. Please retry.") } } },
                    onDelete = { scope.launch { runCatching { repository.deleteAudioAd(ad.id); audioAds = repository.getAudioAds() }.onFailure { status = it.toHteUserMessage("Could not delete this advertisement. Please retry.") } } }) }
                if (audioAds.isEmpty() && !isLoadingModule) item { Text("No generated advertisements yet. Choose a voice and create your first draft.", color = FounderMuted, fontSize = 11.sp) }
            }
            7 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { Text("AI MARKETING AGENT", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold) }
                item { OutlinedTextField(campaignTitle, { campaignTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Campaign title") }, singleLine = true) }
                item { OutlinedTextField(campaignMessage, { campaignMessage = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Campaign message") }, minLines = 4) }
                item {
                    Box {
                        OutlinedButton(onClick = { campaignTypeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Campaign type · ${campaignType.replace('_', ' ').replaceFirstChar { it.uppercase() }}") }
                        DropdownMenu(expanded = campaignTypeMenuExpanded, onDismissRequest = { campaignTypeMenuExpanded = false }) {
                            listOf("announcement", "release", "event", "promotion").forEach { type -> DropdownMenuItem(text = { Text(type.replaceFirstChar { it.uppercase() }) }, onClick = { campaignType = type; campaignTypeMenuExpanded = false }) }
                        }
                    }
                }
                item { Text("TARGET CHANNELS", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold) }
                items(channelLabels, key = { it.first }) { (key, label) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = key in campaignChannels, onCheckedChange = { checked -> if (checked && key !in campaignChannels) campaignChannels.add(key) else if (!checked) campaignChannels.remove(key) })
                        Text(label, color = Color.White, fontSize = 12.sp)
                    }
                }
                item {
                    Button(onClick = {
                        scope.launch {
                            isUploading = true; status = null
                            runCatching { repository.saveMarketingCampaignDraft(campaignTitle, campaignMessage, campaignType, campaignChannels.toList()) }
                                .onSuccess { status = "Campaign draft saved. Nothing has been queued or published."; campaignTitle = ""; campaignMessage = ""; loadCampaigns() }
                                .onFailure { status = it.toHteUserMessage("Campaign draft could not be saved. Please retry.") }
                            isUploading = false
                        }
                    }, enabled = !isUploading && campaignTitle.isNotBlank() && campaignMessage.isNotBlank() && campaignChannels.isNotEmpty(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = FounderGold, contentColor = Color.Black)) { Text("Save campaign draft") }
                }
                item { Text("PLATFORM CONNECTIONS", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold) }
                if (isLoadingModule) item { CircularProgressIndicator(color = FounderGold, modifier = Modifier.size(22.dp)) }
                items(socialConnections, key = { it.platform }) { connection -> FounderConnectionCard(connection) }
                item { Text("WhatsApp Status uses manual sharing. Campaign saving never queues or publishes content.", color = FounderMuted, fontSize = 10.sp) }
                item { Text("SAVED CAMPAIGNS · ${campaigns.size}", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold) }
                items(campaigns, key = { it.id }) { campaign -> FounderCampaignCard(campaign) }
                if (campaigns.isEmpty() && !isLoadingModule) item { Text("No campaigns yet.", color = FounderMuted, fontSize = 11.sp) }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item { Text("FOUNDER SETTINGS & ACCESS", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold) }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = FounderPanel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("OWNER ACCOUNT", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                            Text(repository.currentOwnerEmail() ?: "Signed-in owner", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Founder Studio access is checked against the owner role and protected again by Supabase row-level security.", color = FounderMuted, fontSize = 11.sp)
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = FounderPanel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("MEDIA STORAGE", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                            Text("Music: hte-audio · Video: hte-videos · Artwork: hte-artwork", color = Color.White, fontSize = 11.sp)
                            Text("New content is uploaded as a draft. Storage objects are cleaned up when a managed record is deleted.", color = FounderMuted, fontSize = 10.sp)
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = FounderPanel), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("PUBLISHING SAFETY", color = FounderGold, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                            Text("Social campaign publishing remains disabled until platform OAuth, granted scopes, media validation and an explicit approval workflow are verified.", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
                item { Button(onClick = { refresh(); loadCampaigns() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = FounderGold, contentColor = Color.Black)) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Refresh Studio data") } }
            }
        }
        status?.let { Text(it, color = FounderGold, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp)) }
    }
}

@Composable
private fun FounderInboxCard(message: FounderInboxMessageDto, context: Context) {
    Card(colors = CardDefaults.cardColors(containerColor = FounderPanel), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(message.subject?.takeIf(String::isNotBlank) ?: "Website enquiry", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("${message.name} · ${message.email}", color = FounderGold, fontSize = 10.sp)
            Text(message.message, color = FounderMuted, fontSize = 11.sp)
            TextButton(onClick = {
                val subject = "Re: ${message.subject?.takeIf(String::isNotBlank) ?: "Website enquiry"}"
                runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(message.email)}?subject=${Uri.encode(subject)}"))) }
            }) { Icon(Icons.Outlined.Email, null); Spacer(Modifier.width(5.dp)); Text("Reply by email", color = FounderGold) }
        }
    }
}

@Composable
private fun FounderAdCard(ad: FounderAudioAdDto, onPreview: (String) -> Unit, onTogglePublish: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = FounderPanel), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(ad.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (ad.isPublished) "Published advert" else "Draft advert", color = if (ad.isPublished) FounderGold else FounderMuted, fontSize = 10.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!ad.audioUrl.isNullOrBlank()) TextButton(onClick = { onPreview(ad.audioUrl) }) { Icon(Icons.Outlined.PlayArrow, null); Text("Preview", color = FounderGold) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onTogglePublish(!ad.isPublished) }) { Text(if (ad.isPublished) "Unpublish" else "Publish", color = FounderGold) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete advert", tint = FounderMuted) }
            }
        }
    }
}

@Composable
private fun FounderConnectionCard(connection: FounderSocialConnectionDto) {
    Card(colors = CardDefaults.cardColors(containerColor = FounderPanel), shape = RoundedCornerShape(13.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(connection.platform.replace('_', ' ').replaceFirstChar { it.uppercase() }, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(connection.accountLabel ?: "No account linked", color = FounderMuted, fontSize = 10.sp)
            }
            Text((connection.connectionStatus ?: "not_connected").replace('_', ' ').uppercase(), color = if (connection.connectionStatus == "connected") FounderGold else FounderMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FounderCampaignCard(campaign: FounderMarketingCampaignDto) {
    val message = campaign.content?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty()
    Card(colors = CardDefaults.cardColors(containerColor = FounderPanel), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(campaign.title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(campaign.status.uppercase(), color = FounderGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Text(campaign.campaignType.replace('_', ' ').replaceFirstChar { it.uppercase() }, color = FounderMuted, fontSize = 10.sp)
            Text(message, color = Color.White.copy(alpha = .85f), fontSize = 11.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FounderContentRow(title: String, subtitle: String, published: Boolean, onTogglePublish: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = FounderPanel), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = FounderMuted, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (published) "Published" else "Draft", color = if (published) FounderGold else FounderMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { onTogglePublish(!published) }) { Text(if (published) "Unpublish" else "Publish", color = FounderGold) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete content", tint = FounderMuted) }
            }
        }
    }
}


@Composable
private fun FounderMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.White.copy(alpha = .035f), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(label, color = FounderMuted, fontSize = 8.sp, letterSpacing = 1.sp)
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
