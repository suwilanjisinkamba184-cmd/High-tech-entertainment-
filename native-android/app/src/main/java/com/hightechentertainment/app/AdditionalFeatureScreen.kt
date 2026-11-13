package com.hightechentertainment.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.material3.Player as Media3ComposePlayer
import androidx.media3.ui.compose.material3.PlayerDefaults
import androidx.media3.exoplayer.ExoPlayer
import com.hightechentertainment.app.data.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hightechentertainment.app.update.NativeUpdateManager
import com.hightechentertainment.app.update.NativeUpdate
import com.hightechentertainment.app.playback.OfflineMusicDownloads
import com.hightechentertainment.app.playback.OfflineDownload
import com.hightechentertainment.app.playback.OfflineDownloadState

@Composable
fun AdditionalFeatureScreen(
    destination: String, tracks: List<NativeTrack>, features: FeatureRepository,
    library: UserLibraryRepository, sponsoredAudio: com.hightechentertainment.app.data.SponsoredAudioRepository,
    downloads: OfflineMusicDownloads,
    isFounder: Boolean, userId: String?, onPlayTrack: (NativeTrack) -> Unit,
    audioWasPlaying: Boolean = false, onPauseAudio: () -> Unit = {}, onResumeAudio: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var message by remember(destination) { mutableStateOf<String?>(null) }
    var busy by remember(destination) { mutableStateOf(false) }
    var videos by remember { mutableStateOf<List<NativeVideo>>(emptyList()) }
    var history by remember { mutableStateOf<List<String>>(emptyList()) }
    var offlineItems by remember { mutableStateOf<List<OfflineDownload>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<NativePlaylist>>(emptyList()) }
    var playlistTrackIdsById by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var expandedPlaylistId by remember { mutableStateOf<String?>(null) }
    var bookings by remember { mutableStateOf<List<NativeBooking>>(emptyList()) }
    var profile by remember { mutableStateOf(NativeProfile("", "", "", "")) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            if (bytes == null) message = "Could not read the selected image."
            else features.uploadAvatar(bytes, mime).onSuccess {
                profile = profile.copy(avatarUrl = it)
                message = "Avatar uploaded successfully."
            }.onFailure { message = it.message }
        }
    }
    var overview by remember { mutableStateOf<FounderOverview?>(null) }
    var drafts by remember { mutableStateOf<List<NativeDraftItem>>(emptyList()) }
    var selectedVideo by remember { mutableStateOf<NativeVideo?>(null) }
    var playlistName by remember { mutableStateOf("") }
    var bookingName by remember { mutableStateOf("") }
    var bookingEmail by remember { mutableStateOf("") }
    var bookingType by remember { mutableStateOf("Music production") }
    var bookingDate by remember { mutableStateOf("") }
    var bookingMessage by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var newVideoTitle by remember { mutableStateOf("") }
    var newVideoUrl by remember { mutableStateOf("") }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val mime = context.contentResolver.getType(uri) ?: "video/mp4"
            val extension = when (mime) {
                "video/webm" -> "webm"
                "video/quicktime" -> "mov"
                "video/x-m4v" -> "m4v"
                else -> "mp4"
            }
            features.uploadVideo(context, uri, mime, extension).onSuccess {
                newVideoUrl = it
                message = "Video uploaded. Its public URL has been added to the video form."
            }.onFailure { message = it.message }
            busy = false
        }
    }
    var newVideoDescription by remember { mutableStateOf("") }
    var newTrackTitle by remember { mutableStateOf("") }
    var newTrackArtist by remember { mutableStateOf("") }
    var newTrackUrl by remember { mutableStateOf("") }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val mime = context.contentResolver.getType(uri) ?: "audio/mpeg"
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            val extension = when (mime) {
                "audio/mp4" -> "m4a"
                "audio/aac" -> "aac"
                "audio/wav", "audio/x-wav" -> "wav"
                "audio/ogg" -> "ogg"
                else -> "mp3"
            }
            if (bytes == null) message = "Could not read the selected audio file."
            else features.uploadAudio(bytes, mime, extension).onSuccess {
                newTrackUrl = it
                message = "Audio uploaded. Its secure public URL has been added to the track form."
            }.onFailure { message = it.message }
            busy = false
        }
    }
    var newTrackGenre by remember { mutableStateOf("") }
    var newArtistName by remember { mutableStateOf("") }
    var newArtistBio by remember { mutableStateOf("") }
    var newReleaseTitle by remember { mutableStateOf("") }
    var newReleaseDescription by remember { mutableStateOf("") }
    var campaignTitle by remember { mutableStateOf("") }
    var campaignUrl by remember { mutableStateOf("") }
    val campaignAudioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val mime = context.contentResolver.getType(uri) ?: "audio/mpeg"
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            val extension = when (mime) {
                "audio/mp4" -> "m4a"
                "audio/aac" -> "aac"
                "audio/wav", "audio/x-wav" -> "wav"
                "audio/ogg" -> "ogg"
                else -> "mp3"
            }
            if (bytes == null) message = "Could not read the selected campaign audio."
            else features.uploadAudio(bytes, mime, extension).onSuccess {
                campaignUrl = it
                message = "Campaign audio uploaded."
            }.onFailure { message = it.message }
            busy = false
        }
    }
    var campaignStarts by remember { mutableStateOf("") }
    var campaignEnds by remember { mutableStateOf("") }
    var campaignPriority by remember { mutableStateOf("0") }
    var campaigns by remember { mutableStateOf<List<SponsoredAudioAd>>(emptyList()) }
    var availableUpdate by remember { mutableStateOf<NativeUpdate?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    val updateManager = remember(context) { NativeUpdateManager(context) }
    fun refresh() {
        busy = true
        message = null
        scope.launch {
            when (destination) {
                "Videos" -> features.publishedVideos().onSuccess { videos = it }.onFailure { message = it.message }
                "Library" -> {
                    library.listeningHistory().onSuccess { history = it }.onFailure { message = it.message }
                    library.playlists().onSuccess { list ->
                        playlists = list
                        val memberships = mutableMapOf<String, List<String>>()
                        list.forEach { playlist ->
                            memberships[playlist.id] = library.playlistTrackIds(playlist.id).getOrDefault(emptyList())
                        }
                        playlistTrackIdsById = memberships
                    }.onFailure { message = it.message }
                }
                "Downloads" -> offlineItems = downloads.downloads()
                "Bookings" -> features.myBookings().onSuccess { bookings = it }.onFailure { message = it.message }
                "Profile" -> features.getProfile().onSuccess { profile = it }.onFailure { message = it.message }
                "Founder Studio" -> if (isFounder) {
                    features.founderOverview().onSuccess { overview = it }.onFailure { message = it.message }
                    features.founderBookings().onSuccess { bookings = it }.onFailure { message = it.message }
                    sponsoredAudio.campaigns().onSuccess { campaigns = it }.onFailure { message = it.message }
                    features.founderDrafts().onSuccess { drafts = it }.onFailure { message = it.message }
                } else message = "Founder access is restricted to the authorized account."
            }
            busy = false
        }
    }
    LaunchedEffect(destination, userId) { refresh() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4A3920)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF17181B))
        ) {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.linearGradient(listOf(Color(0xFF49351A), Color(0xFF211A10), Color(0xFF151515)))
                ).padding(22.dp)
            ) {
                Text("HIGH-TECH ENTERTAINMENT", color = Color(0xFFE4B653), fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
                Spacer(Modifier.height(10.dp))
                Text(destination, color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(7.dp))
                Text(
                    when (destination) {
                        "Videos" -> "Watch published music videos and visual releases from the High-Tech family."
                        "Library" -> "Your listening history and playlists, organised in one place."
                        "Downloads" -> "Your offline listening collection, stored on this device."
                        "Bookings" -> "Manage your creative and production service requests."
                        "Profile" -> "Keep your account details and personal information up to date."
                        "Founder Studio" -> "Founder-only tools for releases, publishing and platform operations."
                        "App Updates" -> "Check for signed native updates and keep your app current."
                        else -> "Your High-Tech music and entertainment space."
                    },
                    color = Color(0xFFD0C7B8), style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("YOUR CONTENT", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            TextButton(onClick = { refresh() }, enabled = !busy) { Text("REFRESH") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (destination) {
            "Videos" -> {
                if (videos.isEmpty() && !busy) Text("No published videos are available yet.")
                videos.forEach { video ->
                    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { selectedVideo = video }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(video.title, style = MaterialTheme.typography.titleMedium)
                            Text(video.artist, style = MaterialTheme.typography.labelMedium)
                            if (video.description.isNotBlank()) Text(video.description)
                            Text("PLAY VIDEO", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            "Library" -> {
                Text("LISTENING HISTORY", style = MaterialTheme.typography.titleMedium)
                val historyTracks = history.mapNotNull { id -> tracks.firstOrNull { it.id == id } }
                if (historyTracks.isEmpty()) Text("Your recently played tracks will appear here.")
                historyTracks.forEach { track ->
                    ListItem(headlineContent = { Text(track.title) }, supportingContent = { Text(track.artist) },
                        modifier = Modifier.clickable { onPlayTrack(track) })
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                Text("YOUR PLAYLISTS", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(playlistName, { playlistName = it }, Modifier.weight(1f),
                        label = { Text("New playlist name") }, singleLine = true)
                    TextButton(enabled = playlistName.isNotBlank() && !busy, onClick = {
                        scope.launch {
                            busy = true
                            library.createPlaylist(playlistName).onSuccess {
                                playlists = playlists + it
                                playlistName = ""
                                message = "Playlist created."
                            }.onFailure { message = it.message }
                            busy = false
                        }
                    }) { Text("Create") }
                }
                playlists.forEach { playlist ->
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { Text("${playlistTrackIdsById[playlist.id].orEmpty().size} tracks • ${playlist.description.ifBlank { "Personal playlist" }}") },
                        trailingContent = {
                            Row {
                                Box {
                                    TextButton(onClick = { expandedPlaylistId = playlist.id }) { Text("ADD TRACK") }
                                    DropdownMenu(
                                        expanded = expandedPlaylistId == playlist.id,
                                        onDismissRequest = { expandedPlaylistId = null }
                                    ) {
                                        tracks.forEach { track ->
                                            DropdownMenuItem(text = { Text(track.title) }, onClick = {
                                                scope.launch {
                                                    val result = library.addTrackToPlaylist(playlist.id, track.id,
                                                        playlistTrackIdsById[playlist.id].orEmpty().size)
                                                    if (result.successful) {
                                                        val updated = library.playlistTrackIds(playlist.id).getOrDefault(emptyList())
                                                        playlistTrackIdsById = playlistTrackIdsById + (playlist.id to updated)
                                                        message = "Added ${track.title} to ${playlist.name}."
                                                    } else message = result.message
                                                    expandedPlaylistId = null
                                                }
                                            })
                                        }
                                    }
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        val result = library.removePlaylist(playlist.id)
                                        if (result.successful) {
                                            playlists = playlists.filterNot { it.id == playlist.id }
                                            playlistTrackIdsById = playlistTrackIdsById - playlist.id
                                            message = "Playlist deleted."
                                        } else message = result.message
                                    }
                                }) { Text("DELETE") }
                            }
                        }
                    )
                    playlistTrackIdsById[playlist.id].orEmpty().forEach { trackId ->
                        tracks.firstOrNull { it.id == trackId }?.let { track ->
                            ListItem(
                                headlineContent = { Text(track.title) },
                                supportingContent = { Text(track.artist) },
                                trailingContent = {
                                    val currentOrder = playlistTrackIdsById[playlist.id].orEmpty()
                                    val currentIndex = currentOrder.indexOf(track.id)
                                    Row {
                                        TextButton(enabled = currentIndex > 0, onClick = {
                                            scope.launch {
                                                val reordered = currentOrder.toMutableList()
                                                val previous = reordered[currentIndex - 1]
                                                reordered[currentIndex - 1] = reordered[currentIndex]
                                                reordered[currentIndex] = previous
                                                val result = library.reorderPlaylist(playlist.id, reordered)
                                                if (result.successful) {
                                                    playlistTrackIdsById = playlistTrackIdsById + (playlist.id to reordered)
                                                } else message = result.message
                                            }
                                        }) { Text("↑") }
                                        TextButton(enabled = currentIndex >= 0 && currentIndex < currentOrder.lastIndex, onClick = {
                                            scope.launch {
                                                val reordered = currentOrder.toMutableList()
                                                val next = reordered[currentIndex + 1]
                                                reordered[currentIndex + 1] = reordered[currentIndex]
                                                reordered[currentIndex] = next
                                                val result = library.reorderPlaylist(playlist.id, reordered)
                                                if (result.successful) {
                                                    playlistTrackIdsById = playlistTrackIdsById + (playlist.id to reordered)
                                                } else message = result.message
                                            }
                                        }) { Text("↓") }
                                        TextButton(onClick = {
                                            scope.launch {
                                                val result = library.removeTrackFromPlaylist(playlist.id, track.id)
                                                if (result.successful) {
                                                    val remaining = currentOrder.filterNot { it == track.id }
                                                    playlistTrackIdsById = playlistTrackIdsById + (playlist.id to remaining)
                                                } else message = result.message
                                            }
                                        }) { Text("REMOVE") }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            "Downloads" -> {
                Text("OFFLINE MUSIC", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${offlineItems.size} downloads • ${downloads.downloadedBytes() / (1024L * 1024L)} MB stored",
                    style = MaterialTheme.typography.bodySmall
                )
                if (offlineItems.isEmpty()) {
                    Text("Tracks you download for offline listening will appear here.")
                }
                offlineItems.forEach { item ->
                    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                when (item.state) {
                                    OfflineDownloadState.PENDING -> "Waiting to download"
                                    OfflineDownloadState.RUNNING -> "Downloading"
                                    OfflineDownloadState.PAUSED -> "Paused by Android"
                                    OfflineDownloadState.COMPLETE -> "Available offline"
                                    OfflineDownloadState.FAILED -> "Download failed (reason ${item.reason})"
                                    OfflineDownloadState.UNKNOWN -> "Download status unavailable"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (item.state == OfflineDownloadState.PENDING ||
                                item.state == OfflineDownloadState.RUNNING ||
                                item.state == OfflineDownloadState.PAUSED
                            ) {
                                LinearProgressIndicator(
                                    progress = item.progressPercent / 100f,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                )
                                Text("${item.progressPercent}% • ${item.bytesDownloaded / 1024L} KB / ${item.totalBytes.coerceAtLeast(0L) / 1024L} KB")
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    downloads.remove(item.trackId)
                                    offlineItems = downloads.downloads()
                                    message = "Download removed from this device."
                                }) { Text("REMOVE") }
                            }
                        }
                    }
                }
                Text("Downloads are stored in app-private storage and can be removed here. Android manages transfer notifications and network retries.")
            }
            "Bookings" -> {
                Text("REQUEST A BOOKING", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(bookingName, { bookingName = it }, Modifier.fillMaxWidth(), label = { Text("Full name") })
                OutlinedTextField(bookingEmail, { bookingEmail = it }, Modifier.fillMaxWidth(), label = { Text("Email") })
                OutlinedTextField(bookingType, { bookingType = it }, Modifier.fillMaxWidth(), label = { Text("Service type") })
                OutlinedTextField(bookingDate, { bookingDate = it }, Modifier.fillMaxWidth(), label = { Text("Requested date (YYYY-MM-DD, optional)") })
                OutlinedTextField(bookingMessage, { bookingMessage = it }, Modifier.fillMaxWidth(), label = { Text("Project details") }, minLines = 3)
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        val result = library.submitBooking(bookingName, bookingEmail, bookingType,
                            bookingDate.takeIf { it.isNotBlank() }, bookingMessage)
                        message = result.message ?: if (result.successful) "Booking request submitted." else "Booking request failed."
                        if (result.successful) { bookingName = ""; bookingMessage = ""; bookings = features.myBookings().getOrDefault(emptyList()) }
                        busy = false
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Submit booking request") }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("MY REQUESTS", style = MaterialTheme.typography.titleMedium)
                bookings.forEach { booking ->
                    ListItem(headlineContent = { Text(booking.type) },
                        supportingContent = { Text("${booking.requestedDate ?: "Date to be arranged"} • ${booking.status}") })
                }
            }
            "App Updates" -> {
                Text("Check GitHub for a signed High-Tech Entertainment Android release.")
                Button(enabled = !updateBusy, onClick = {
                    scope.launch {
                        updateBusy = true
                        updateManager.checkForUpdate().onSuccess { update ->
                            availableUpdate = update
                            message = if (update == null) "This installation is already up to date." else "Version ${update.versionName} is available."
                        }.onFailure { message = it.message }
                        updateBusy = false
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Check for updates") }
                availableUpdate?.let { update ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("High-Tech Entertainment ${update.versionName}", style = MaterialTheme.typography.titleLarge)
                            Text("Build ${update.versionCode}")
                            Text(update.releaseNotes)
                            Button(enabled = !updateBusy, onClick = {
                                scope.launch {
                                    updateBusy = true
                                    updateManager.downloadVerifyAndInstall(update)
                                        .onSuccess { message = "Android package installer opened. Follow the system confirmation." }
                                        .onFailure { message = it.message }
                                    updateBusy = false
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Verify and install update") }
                        }
                    }
                }
                Text("Updates are full signed APK installs. The app checks package identity, version, SHA-256 and signing certificate before opening Android's installer.")
            }
            "Profile" -> {
                OutlinedTextField(profile.displayName, { profile = profile.copy(displayName = it) },
                    Modifier.fillMaxWidth(), label = { Text("Display name") })
                OutlinedTextField(profile.bio, { profile = profile.copy(bio = it) },
                    Modifier.fillMaxWidth(), label = { Text("Bio") }, minLines = 3)
                OutlinedTextField(profile.phone, { profile = profile.copy(phone = it) },
                    Modifier.fillMaxWidth(), label = { Text("Phone") })
                OutlinedTextField(profile.avatarUrl, { profile = profile.copy(avatarUrl = it) },
                    Modifier.fillMaxWidth(), label = { Text("Avatar image URL") })
                OutlinedButton(onClick = { avatarPicker.launch("image/*") }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth()) { Text("Choose and upload profile photo") }
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        features.saveProfile(profile).onSuccess { message = "Profile saved." }
                            .onFailure { message = it.message }
                        busy = false
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Save profile") }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("CHANGE PASSWORD", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(newPassword, { newPassword = it }, Modifier.fillMaxWidth(),
                    label = { Text("New password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(confirmPassword, { confirmPassword = it }, Modifier.fillMaxWidth(),
                    label = { Text("Confirm new password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                Button(enabled = !busy && newPassword.length >= 8 && newPassword == confirmPassword,
                    onClick = {
                        scope.launch {
                            busy = true
                            features.updatePassword(newPassword).onSuccess {
                                message = "Password updated successfully."
                                newPassword = ""
                                confirmPassword = ""
                            }.onFailure { message = it.message }
                            busy = false
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Update password") }
                Text("Avatar files are public for profile display; each account can upload only into its own protected folder.")
            }
            "Founder Studio" -> {
                if (!isFounder) Text("This private studio is available only to the authorized founder account.")
                else {
                    overview?.let { stats ->
                        Text("STUDIO OVERVIEW", style = MaterialTheme.typography.titleMedium)
                        Text("Tracks: ${stats.tracks}   Artists: ${stats.artists}   Releases: ${stats.releases}")
                        Text("Videos: ${stats.videos}   Bookings: ${stats.bookings}")
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("PUBLISH VIDEO", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(newVideoTitle, { newVideoTitle = it }, Modifier.fillMaxWidth(), label = { Text("Video title") })
                    OutlinedTextField(newVideoUrl, { newVideoUrl = it }, Modifier.fillMaxWidth(), label = { Text("HTTPS video URL") })
                    OutlinedButton(onClick = { videoPicker.launch("video/*") }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) { Text("Choose and upload video (up to 50 MB)") }
                    OutlinedTextField(newVideoDescription, { newVideoDescription = it }, Modifier.fillMaxWidth(), label = { Text("Description") })
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            features.createVideo(newVideoTitle, newVideoUrl, newVideoDescription, "High-Tech Entertainment")
                                .onSuccess { message = "Video saved as an unpublished draft. Review/publish it in the web admin." }
                                .onFailure { message = it.message }
                            busy = false
                        }
                    }) { Text("Save video draft") }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("ADD MUSIC TRACK", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(newTrackTitle, { newTrackTitle = it }, Modifier.fillMaxWidth(), label = { Text("Track title") })
                    OutlinedTextField(newTrackArtist, { newTrackArtist = it }, Modifier.fillMaxWidth(), label = { Text("Artist") })
                    OutlinedTextField(newTrackUrl, { newTrackUrl = it }, Modifier.fillMaxWidth(), label = { Text("HTTPS audio URL") })
                    OutlinedButton(onClick = { audioPicker.launch("audio/*") }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) { Text("Choose and upload audio file (up to 20 MB)") }
                    OutlinedTextField(newTrackGenre, { newTrackGenre = it }, Modifier.fillMaxWidth(), label = { Text("Genre") })
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            features.createTrack(newTrackTitle, newTrackArtist, newTrackUrl, newTrackGenre)
                                .onSuccess { message = "Track saved as an unpublished draft." }
                                .onFailure { message = it.message }
                            busy = false
                        }
                    }) { Text("Save track draft") }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("ADD ARTIST", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(newArtistName, { newArtistName = it }, Modifier.fillMaxWidth(), label = { Text("Artist name") })
                    OutlinedTextField(newArtistBio, { newArtistBio = it }, Modifier.fillMaxWidth(), label = { Text("Artist bio") })
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            features.createArtist(newArtistName, newArtistBio).onSuccess { message = "Artist draft created." }.onFailure { message = it.message }
                            busy = false
                        }
                    }) { Text("Create artist") }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("ADD RELEASE", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(newReleaseTitle, { newReleaseTitle = it }, Modifier.fillMaxWidth(), label = { Text("Release title") })
                    OutlinedTextField(newReleaseDescription, { newReleaseDescription = it }, Modifier.fillMaxWidth(), label = { Text("Description") })
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            features.createRelease(newReleaseTitle, newReleaseDescription).onSuccess { message = "Release draft created." }.onFailure { message = it.message }
                            busy = false
                        }
                    }) { Text("Create release") }
                    Text("Tracks can upload audio files up to 20 MB. Videos can be uploaded directly up to 50 MB or added using an HTTPS media URL.", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("CONTENT DRAFTS", style = MaterialTheme.typography.titleMedium)
                    if (drafts.isEmpty()) Text("No unpublished content drafts.")
                    drafts.forEach { draft ->
                        ListItem(
                            headlineContent = { Text(draft.title) },
                            supportingContent = { Text(draft.table.removeSuffix("s").replaceFirstChar(Char::uppercase)) },
                            trailingContent = {
                                TextButton(onClick = {
                                    scope.launch {
                                        features.setDraftPublished(draft.table, draft.id, true)
                                            .onSuccess { message = "${draft.title} published." }
                                            .onFailure { message = it.message }
                                        drafts = features.founderDrafts().getOrDefault(emptyList())
                                        overview = features.founderOverview().getOrNull()
                                    }
                                }) { Text("Publish") }
                            }
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text("SPONSORED AUDIO CAMPAIGNS", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(campaignTitle, { campaignTitle = it }, Modifier.fillMaxWidth(), label = { Text("Campaign title") })
                    OutlinedTextField(campaignUrl, { campaignUrl = it }, Modifier.fillMaxWidth(), label = { Text("HTTPS audio advert URL") })
                    OutlinedButton(onClick = { campaignAudioPicker.launch("audio/*") }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) { Text("Choose and upload campaign audio") }
                    OutlinedTextField(campaignStarts, { campaignStarts = it }, Modifier.fillMaxWidth(), label = { Text("Starts at ISO-8601 (optional)") })
                    OutlinedTextField(campaignEnds, { campaignEnds = it }, Modifier.fillMaxWidth(), label = { Text("Ends at ISO-8601 (optional)") })
                    OutlinedTextField(campaignPriority, { campaignPriority = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("Priority 0-1000") })
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            sponsoredAudio.createCampaign(campaignTitle, campaignUrl,
                                campaignStarts.takeIf { it.isNotBlank() }, campaignEnds.takeIf { it.isNotBlank() },
                                campaignPriority.toIntOrNull() ?: 0)
                                .onSuccess {
                                    message = "Campaign saved as draft. Publish it below when ready."
                                    campaigns = sponsoredAudio.campaigns().getOrDefault(emptyList())
                                }.onFailure { message = it.message }
                            busy = false
                        }
                    }) { Text("Create campaign draft") }
                    campaigns.forEach { campaign ->
                        ListItem(headlineContent = { Text(campaign.title) },
                            supportingContent = { Text("Priority ${campaign.priority} • ${if (campaign.isPublished) "Published" else "Draft"}") },
                            trailingContent = {
                                TextButton(onClick = {
                                    scope.launch {
                                        sponsoredAudio.setCampaignPublished(campaign.id, !campaign.isPublished)
                                            .onSuccess { message = if (campaign.isPublished) "Campaign paused." else "Campaign published." }
                                            .onFailure { message = it.message }
                                        campaigns = sponsoredAudio.campaigns().getOrDefault(emptyList())
                                    }
                                }) { Text(if (campaign.isPublished) "Pause" else "Publish") }
                            })
                    }
                    Text("Scheduled campaigns play before music only while published and within their start/end window. Impressions are recorded against the signed-in listener.", style = MaterialTheme.typography.bodySmall)
                    Text("BOOKING INBOX", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp))
                    bookings.forEach { booking ->
                        ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(booking.type, style = MaterialTheme.typography.titleMedium)
                                Text("${booking.name} • ${booking.email}")
                                Text(booking.message)
                                Text("Status: ${booking.status}")
                                Row {
                                    listOf("confirmed", "declined", "completed").forEach { status ->
                                        TextButton(onClick = {
                                            scope.launch {
                                                features.updateBookingStatus(booking.id, status)
                                                    .onSuccess { message = "Booking $status." }
                                                    .onFailure { message = it.message }
                                                bookings = features.founderBookings().getOrDefault(emptyList())
                                            }
                                        }) { Text(status.replaceFirstChar(Char::uppercase)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    selectedVideo?.let { video ->
        val resumeAudioAfterVideo = remember(video.id) { audioWasPlaying }
        LaunchedEffect(video.id) {
            if (resumeAudioAfterVideo) onPauseAudio()
        }
        val closeVideo = {
            selectedVideo = null
            if (resumeAudioAfterVideo) onResumeAudio()
        }
        Dialog(onDismissRequest = closeVideo,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            VideoPlaybackDialog(video, onClose = closeVideo)
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalApi::class)
@Composable
private fun VideoPlaybackDialog(video: NativeVideo, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember(video.id) { ExoPlayer.Builder(context).build() }
    LaunchedEffect(player, video.videoUrl) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(video.id)
            .setUri(video.videoUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(video.title)
                    .setArtist(video.artist)
                    .setAlbumTitle("High-Tech Entertainment")
                    .build()
            )
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(video.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(video.artist, color = Color(0xFFB8B0A3), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = onClose) { Text("CLOSE", color = Color(0xFFD4AF37)) }
        }
        Media3ComposePlayer(
            player = player,
            modifier = Modifier.fillMaxWidth().weight(1f),
            showControls = true,
            topControls = { PlayerDefaults.TopControls(player) },
            centerControls = { PlayerDefaults.CenterControls(player) },
            bottomControls = { PlayerDefaults.BottomControls(player) }
        )
    }
}
