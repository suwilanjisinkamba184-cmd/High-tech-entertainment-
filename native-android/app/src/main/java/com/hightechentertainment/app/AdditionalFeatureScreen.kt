package com.hightechentertainment.app

import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.hightechentertainment.app.data.*
import kotlinx.coroutines.launch

private const val FounderUserId = "b268df9e-eff5-43b9-a38f-869b33add38b"

@Composable
fun AdditionalFeatureScreen(
    destination: String, tracks: List<NativeTrack>, features: FeatureRepository,
    library: UserLibraryRepository, userId: String?, onPlayTrack: (NativeTrack) -> Unit
) {
    val scope = rememberCoroutineScope()
    var message by remember(destination) { mutableStateOf<String?>(null) }
    var busy by remember(destination) { mutableStateOf(false) }
    var videos by remember { mutableStateOf<List<NativeVideo>>(emptyList()) }
    var history by remember { mutableStateOf<List<String>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<NativePlaylist>>(emptyList()) }
    var bookings by remember { mutableStateOf<List<NativeBooking>>(emptyList()) }
    var profile by remember { mutableStateOf(NativeProfile("", "", "", "")) }
    var overview by remember { mutableStateOf<FounderOverview?>(null) }
    var selectedVideo by remember { mutableStateOf<NativeVideo?>(null) }
    var playlistName by remember { mutableStateOf("") }
    var bookingName by remember { mutableStateOf("") }
    var bookingEmail by remember { mutableStateOf("") }
    var bookingType by remember { mutableStateOf("Music production") }
    var bookingDate by remember { mutableStateOf("") }
    var bookingMessage by remember { mutableStateOf("") }
    val isFounder = userId == FounderUserId

    fun refresh() {
        busy = true
        message = null
        scope.launch {
            when (destination) {
                "Videos" -> features.publishedVideos().onSuccess { videos = it }.onFailure { message = it.message }
                "Library" -> {
                    library.listeningHistory().onSuccess { history = it }.onFailure { message = it.message }
                    library.playlists().onSuccess { playlists = it }.onFailure { message = it.message }
                }
                "Bookings" -> features.myBookings().onSuccess { bookings = it }.onFailure { message = it.message }
                "Profile" -> features.getProfile().onSuccess { profile = it }.onFailure { message = it.message }
                "Founder Studio" -> if (isFounder) {
                    features.founderOverview().onSuccess { overview = it }.onFailure { message = it.message }
                    features.founderBookings().onSuccess { bookings = it }.onFailure { message = it.message }
                } else message = "Founder access is restricted to the authorized account."
            }
            busy = false
        }
    }
    LaunchedEffect(destination, userId) { refresh() }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(destination, style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { refresh() }, enabled = !busy) { Text("Refresh") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (destination) {
            "Videos" -> {
                if (videos.isEmpty() && !busy) Text("No published videos are available yet.")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(videos, key = { it.id }) { video ->
                        ElevatedCard(Modifier.fillMaxWidth().clickable { selectedVideo = video }) {
                            Column(Modifier.padding(16.dp)) {
                                Text(video.title, style = MaterialTheme.typography.titleMedium)
                                Text(video.artist, style = MaterialTheme.typography.labelMedium)
                                if (video.description.isNotBlank()) Text(video.description)
                                Text("PLAY VIDEO", color = MaterialTheme.colorScheme.primary)
                            }
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
                    ListItem(headlineContent = { Text(playlist.name) },
                        supportingContent = { Text(playlist.description.ifBlank { "Personal playlist" }) })
                    tracks.firstOrNull()?.let { firstTrack ->
                        TextButton(onClick = {
                            scope.launch {
                                val result = library.addTrackToPlaylist(playlist.id, firstTrack.id)
                                message = result.message ?: if (result.successful) "Added ${firstTrack.title} to ${playlist.name}." else "Could not add track."
                            }
                        }) { Text("Add ${firstTrack.title}") }
                    }
                }
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
            "Profile" -> {
                OutlinedTextField(profile.displayName, { profile = profile.copy(displayName = it) },
                    Modifier.fillMaxWidth(), label = { Text("Display name") })
                OutlinedTextField(profile.bio, { profile = profile.copy(bio = it) },
                    Modifier.fillMaxWidth(), label = { Text("Bio") }, minLines = 3)
                OutlinedTextField(profile.phone, { profile = profile.copy(phone = it) },
                    Modifier.fillMaxWidth(), label = { Text("Phone") })
                OutlinedTextField(profile.avatarUrl, { profile = profile.copy(avatarUrl = it) },
                    Modifier.fillMaxWidth(), label = { Text("Avatar HTTPS URL") })
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        features.saveProfile(profile).onSuccess { message = "Profile saved." }
                            .onFailure { message = it.message }
                        busy = false
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Save profile") }
                Text("Device photo upload is not yet connected; this field accepts a secure HTTPS image URL.")
            }
            "Founder Studio" -> {
                if (!isFounder) Text("This private studio is available only to the authorized founder account.")
                else {
                    overview?.let { stats ->
                        Text("STUDIO OVERVIEW", style = MaterialTheme.typography.titleMedium)
                        Text("Tracks: ${stats.tracks}   Artists: ${stats.artists}   Releases: ${stats.releases}")
                        Text("Videos: ${stats.videos}   Bookings: ${stats.bookings}")
                    }
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
        Dialog(onDismissRequest = { selectedVideo = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            VideoPlaybackDialog(video, onClose = { selectedVideo = null })
        }
    }
}

@Composable
private fun VideoPlaybackDialog(video: NativeVideo, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember(video.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video.videoUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text("CLOSE") }
        }
        AndroidView(factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = true
            }
        }, modifier = Modifier.fillMaxWidth().weight(1f))
        Text(video.title, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
    }
}
