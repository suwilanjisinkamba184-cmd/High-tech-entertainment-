package com.hightechentertainment.app

import android.util.Log

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hightechentertainment.app.data.SupabaseClientProvider
import com.hightechentertainment.app.data.TrackDto
import com.hightechentertainment.app.data.VideoDto
import com.hightechentertainment.app.media.HtePlaybackController
import io.elevenlabs.ClientTool
import io.elevenlabs.ClientToolResult
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationConfig
import io.elevenlabs.ConversationOverrides
import io.elevenlabs.ConversationSession
import io.elevenlabs.Overrides
import io.elevenlabs.TtsOverrides
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val VoiceGold = com.hightechentertainment.app.ui.design.HteColors.Gold
private val VoicePanel = com.hightechentertainment.app.ui.design.HteColors.Panel
private val VoiceMuted = com.hightechentertainment.app.ui.design.HteColors.Muted
private val voiceJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class VoiceTokenResponse(
    val token: String,
    @SerialName("user_id") val userId: String
)

private val voiceHttp401Pattern = Regex("""(?<![A-Za-z0-9_])401(?![A-Za-z0-9_])""")
private val voiceHttp403Pattern = Regex("""(?<![A-Za-z0-9_])403(?![A-Za-z0-9_])""")

internal fun voiceFailureMessage(details: String): String {
    val normalized = details.lowercase()
    val isSessionFailure = listOf("sign in", "session expired").any(normalized::contains) ||
        voiceHttp401Pattern.containsMatchIn(details)
    val isProviderAuthorizationFailure =
        listOf("convai_write", "missing permission", "forbidden", "credentials", "api key", "not configured", "conversation token").any(normalized::contains) ||
            voiceHttp403Pattern.containsMatchIn(details)

    return when {
        // Authorization responses must win over transport wording: provider SDKs can
        // include "connection" or "WebSocket" in a 403 diagnostic.
        isProviderAuthorizationFailure ->
            "HTE AI Voice Assistant was rejected by the voice provider. The project owner needs to check the agent permissions and server-side API credentials."
        isSessionFailure ->
            "Your HTE session may have expired. Sign in again, then reopen HTE AI Voice Assistant."
        listOf("timeout", "network", "connection", "offline", "unreachable").any(normalized::contains) ->
            "HTE AI Voice Assistant could not connect. Check your internet connection and try again."
        else -> "HTE AI Voice Assistant is temporarily unavailable. Please try again."
    }
}

@Composable
fun VoiceAssistantDialog(
    tracks: List<TrackDto>,
    favouriteTracks: List<TrackDto>,
    videos: List<VideoDto>,
    onPlayFavourites: (List<TrackDto>) -> Unit,
    onPlayTrack: (TrackDto) -> Unit,
    onDownloadTrack: (TrackDto) -> Unit,
    onPlayVideo: (VideoDto) -> Unit,
    onDownloadVideo: (VideoDto) -> Unit,
    onNavigate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onSearchVideos: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentTracks by rememberUpdatedState(tracks)
    val currentFavourites by rememberUpdatedState(favouriteTracks)
    val currentVideos by rememberUpdatedState(videos)
    val currentOnPlayFavourites by rememberUpdatedState(onPlayFavourites)
    val currentOnPlayTrack by rememberUpdatedState(onPlayTrack)
    val currentOnDownloadTrack by rememberUpdatedState(onDownloadTrack)
    val currentOnPlayVideo by rememberUpdatedState(onPlayVideo)
    val currentOnDownloadVideo by rememberUpdatedState(onDownloadVideo)
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    val currentOnSearch by rememberUpdatedState(onSearch)
    val currentOnSearchVideos by rememberUpdatedState(onSearchVideos)
    val settingsStore = remember(context) { AppSettingsStore(context) }
    var selectedVoiceId by remember(settingsStore) { mutableStateOf(settingsStore.voiceAssistantVoiceId) }
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    val selectedVoice = VoiceAssistantVoices.find(selectedVoiceId) ?: VoiceAssistantVoices.default
    LaunchedEffect(selectedVoiceId) {
        if (selectedVoiceId != selectedVoice.id) {
            selectedVoiceId = selectedVoice.id
            settingsStore.voiceAssistantVoiceId = selectedVoice.id
        }
    }
    val toolContext = remember { VoiceToolContext() }
    var session by remember { mutableStateOf<ConversationSession?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Disconnected") }
    var mode by remember { mutableStateOf("listening") }
    var muted by remember { mutableStateOf(false) }
    var audioLevel by remember { mutableStateOf(0f) }
    var userTranscript by remember { mutableStateOf("") }
    var agentResponse by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var minimized by remember { mutableStateOf(false) }

    SideEffect {
        toolContext.onActionCompleted = { minimized = true }
        toolContext.tracks = currentTracks
        toolContext.favourites = currentFavourites
        toolContext.videos = currentVideos
        toolContext.onPlayFavourites = currentOnPlayFavourites
        toolContext.onPlayTrack = currentOnPlayTrack
        toolContext.onDownloadTrack = currentOnDownloadTrack
        toolContext.onPlayVideo = currentOnPlayVideo
        toolContext.onDownloadVideo = currentOnDownloadVideo
        toolContext.onNavigate = currentOnNavigate
        toolContext.onSearch = currentOnSearch
        toolContext.onSearchVideos = currentOnSearchVideos
        toolContext.onUserTranscript = { value -> scope.launch { userTranscript = value } }
        toolContext.onAgentResponse = { value -> scope.launch { agentResponse = value } }
        toolContext.onError = { value -> scope.launch { errorMessage = value; status = "Error" } }
    }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceSession(scope, context, toolContext, selectedVoiceId,
            onConnecting = { isConnecting = true; status = "Connecting"; errorMessage = null },
            onSession = { active -> session = active; isConnecting = false },
            onFailure = { error -> isConnecting = false; status = "Disconnected"; errorMessage = voiceFailureMessage(error) }
        ) else errorMessage = "Microphone permission is required for HTE AI Voice Assistant."
    }

    fun connect() {
        if (session != null || isConnecting) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startVoiceSession(scope, context, toolContext, selectedVoiceId,
                onConnecting = { isConnecting = true; status = "Connecting"; errorMessage = null },
                onSession = { active -> session = active; isConnecting = false },
                onFailure = { error -> isConnecting = false; status = "Disconnected"; errorMessage = voiceFailureMessage(error) }
            )
        }
    }

    LaunchedEffect(session) {
        val active = session ?: return@LaunchedEffect
        launch { active.status.collect { status = it.name.lowercase().replace('_', ' ') } }
        launch { active.mode.collect { mode = it.name.lowercase() } }
        launch { active.isMuted.collect { muted = it } }
        launch { active.audioLevel.collect { audioLevel = it } }
    }

    DisposableEffect(session) {
        val active = session
        onDispose {
            if (active != null) CoroutineScope(Dispatchers.Main.immediate).launch { runCatching { active.endSession() } }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.BottomCenter) {
            if (minimized) {
                Surface(Modifier.align(Alignment.BottomEnd).padding(bottom = if (maxWidth < 720.dp) 84.dp else 16.dp).fillMaxWidth().widthIn(max = 320.dp), color = VoicePanel, shape = RoundedCornerShape(22.dp), shadowElevation = 18.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.GraphicEq, null, tint = VoiceGold, modifier = Modifier.size(25.dp))
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("HTE AI", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(if (session == null) "HTE AI · standby" else if (muted) "Microphone muted" else "Connected · say Hey HTE", color = VoiceMuted, fontSize = 9.sp)
                        }
                        if (session != null) IconButton(onClick = { scope.launch { session?.toggleMute() } }) { Icon(if (muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, if (muted) "Unmute HTE AI Voice Assistant" else "Mute HTE AI Voice Assistant", tint = if (muted) VoiceMuted else VoiceGold) }
                        IconButton(onClick = { minimized = false }) { Icon(Icons.Outlined.ExpandLess, "Expand HTE AI Voice Assistant", tint = VoiceGold) }
                        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "End HTE AI Voice Assistant", tint = VoiceMuted) }
                    }
                }
            } else {
                Surface(Modifier.align(Alignment.BottomCenter).padding(bottom = if (maxWidth < 720.dp) 84.dp else 16.dp).widthIn(max = 420.dp).fillMaxWidth(.96f).heightIn(max = maxHeight * .48f), color = com.hightechentertainment.app.ui.design.HteColors.Obsidian, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 18.dp, bottomEnd = 18.dp), shadowElevation = 20.dp) {
                    Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.width(32.dp).height(4.dp).clip(RoundedCornerShape(50)).background(VoiceMuted.copy(alpha = .5f)))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("HTE UNIVERSE / AI VOICE ASSISTANT", color = VoiceGold, fontSize = 9.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
                        Text("HTE AI Voice Assistant", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Talk to HTE AI and control your HTE Universe experience", color = VoiceMuted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { minimized = true }) { Icon(Icons.Outlined.ExpandMore, "Minimize HTE AI Voice Assistant", tint = Color.White) }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "End HTE AI Voice Assistant", tint = Color.White) }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF11161C)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Shield, null, tint = VoiceGold, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("SECURE VOICE LINK", color = VoiceGold, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                            Text("Microphone audio is sent to the configured voice service only while a session is active.", color = VoiceMuted, fontSize = 10.sp)
                        }
                        Text(if (session != null) "LIVE" else "STANDBY", color = if (session != null) Color(0xFF8BCB9B) else VoiceMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(92.dp)
                        .background(Brush.radialGradient(listOf(Color(0xFF3C3220), Color(0xFF18212A), Color(0xFF08090B))), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(VoiceGold.copy(alpha = .07f)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(62.dp).clip(CircleShape).background(VoiceGold.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(42.dp).clip(CircleShape).background(VoiceGold.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.GraphicEq, null, tint = VoiceGold, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    Text("HTE AI / AUDIO CORE", color = VoiceMuted, fontSize = 8.sp, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
                }
                Text(status.replaceFirstChar { it.uppercase() } + if (session != null) " · ${mode.replaceFirstChar { it.uppercase() }}" else "", color = VoiceGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Speak naturally to find music, control playback, search the catalogue and move around HTE Universe.", color = VoiceMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                Text("Say ‘Hey HTE’ while the voice link is connected. Minimise this panel and continue using the app.", color = VoiceGold.copy(alpha = .88f), fontSize = 10.sp, textAlign = TextAlign.Center)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("ASSISTANT VOICE", color = VoiceGold, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    Box {
                        OutlinedButton(
                            onClick = { voiceMenuExpanded = true },
                            enabled = session == null && !isConnecting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text(selectedVoice.label, color = Color.White, fontSize = 12.sp)
                                Text(selectedVoice.description, color = VoiceMuted, fontSize = 10.sp)
                            }
                            Icon(Icons.Outlined.ExpandMore, contentDescription = "Choose assistant voice", tint = VoiceGold)
                        }
                        DropdownMenu(expanded = voiceMenuExpanded, onDismissRequest = { voiceMenuExpanded = false }) {
                            VoiceAssistantVoices.all.forEach { choice ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(choice.label, fontWeight = FontWeight.SemiBold)
                                            Text(choice.description, color = VoiceMuted, fontSize = 10.sp)
                                        }
                                    },
                                    onClick = {
                                        selectedVoiceId = choice.id
                                        settingsStore.voiceAssistantVoiceId = choice.id
                                        voiceMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Text("Your choice is saved and used for the next voice session.", color = VoiceMuted, fontSize = 9.sp)
                }
                if (userTranscript.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151B22)), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("YOU / VOICE INPUT", color = VoiceMuted, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                            Text(userTranscript, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
                if (agentResponse.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF211D16)), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("HTE AI / RESPONSE", color = VoiceGold, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                            Text(agentResponse, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
                errorMessage?.let { Text(it, color = Color(0xFFFFA8A8), fontSize = 11.sp, textAlign = TextAlign.Center) }
                if (isConnecting) LinearProgressIndicator(Modifier.fillMaxWidth(), color = VoiceGold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (session == null) {
                        Button(
                            onClick = { connect() }, enabled = !isConnecting,
                            modifier = Modifier.weight(1f).height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VoiceGold, contentColor = Color.Black),
                            shape = RoundedCornerShape(15.dp)
                        ) {
                            Icon(Icons.Outlined.Mic, null); Spacer(Modifier.width(8.dp)); Text(if (isConnecting) "Connecting…" else "Start voice session")
                        }
                    } else {
                        OutlinedButton(onClick = { scope.launch { session?.toggleMute() } }, modifier = Modifier.weight(1f).height(42.dp), shape = RoundedCornerShape(15.dp)) {
                            Icon(if (muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, null); Spacer(Modifier.width(6.dp)); Text(if (muted) "Unmute" else "Mute")
                        }
                        Button(onClick = { session = null; status = "Disconnecting" }, modifier = Modifier.weight(1f).height(42.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E3434), contentColor = Color.White), shape = RoundedCornerShape(15.dp)) {
                            Icon(Icons.Outlined.CallEnd, null); Spacer(Modifier.width(6.dp)); Text("End session")
                        }
                    }
                }
                Text("QUICK LAUNCH", color = VoiceGold, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { currentOnSearch("Zambian"); minimized = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.Search, null); Spacer(Modifier.width(4.dp)); Text("Zambian music", fontSize = 10.sp)
                    }
                    OutlinedButton(onClick = { currentOnNavigate("DISCOVER"); minimized = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.Explore, null); Spacer(Modifier.width(4.dp)); Text("Discover", fontSize = 10.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { currentOnNavigate("LIBRARY"); minimized = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.LibraryMusic, null); Spacer(Modifier.width(4.dp)); Text("My library", fontSize = 10.sp)
                    }
                    OutlinedButton(onClick = { currentOnNavigate("HOME"); minimized = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.Home, null); Spacer(Modifier.width(4.dp)); Text("Home", fontSize = 10.sp)
                    }
                }
                Text("The ElevenLabs API key remains server-side. Sign in to HTE Universe before starting a private voice session.", color = VoiceMuted, fontSize = 9.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }

    // Keep the callbacks current for the registered SDK client tools.
    @Suppress("UNUSED_VARIABLE") val activeMode = mode
}

private fun findVoiceTrack(tracks: List<TrackDto>, query: String): TrackDto? {
    val term = query.trim()
    if (term.isBlank()) return null
    return tracks.firstOrNull { it.title.equals(term, ignoreCase = true) }
        ?: tracks.firstOrNull { it.title.contains(term, ignoreCase = true) || it.artist.contains(term, ignoreCase = true) || it.genre.orEmpty().contains(term, ignoreCase = true) }
}

private fun voicePlaybackConfirmation(track: TrackDto): String = when (track.sourcePlatform.lowercase()) {
    "spotify" -> "Opened the official Spotify player for " + track.title + " by " + track.artist + ". Spotify controls playback access."
    "youtube" -> "Opened the official music link for " + track.title + " by " + track.artist + "."
    else -> "Sent " + track.title + " by " + track.artist + " to the HTE audio player."
}
private fun findVoiceVideo(videos: List<VideoDto>, query: String): VideoDto? {
    val term = query.trim()
    if (term.isBlank()) return null
    return videos.firstOrNull { it.title.equals(term, ignoreCase = true) }
        ?: videos.firstOrNull { it.title.contains(term, ignoreCase = true) || it.artist.orEmpty().contains(term, ignoreCase = true) || it.description.orEmpty().contains(term, ignoreCase = true) }
}

private class VoiceToolContext {
    var tracks: List<TrackDto> = emptyList()
    var favourites: List<TrackDto> = emptyList()
    var videos: List<VideoDto> = emptyList()
    var onPlayFavourites: (List<TrackDto>) -> Unit = {}
    var onPlayTrack: (TrackDto) -> Unit = {}
    var onDownloadTrack: (TrackDto) -> Unit = {}
    var onPlayVideo: (VideoDto) -> Unit = {}
    var onDownloadVideo: (VideoDto) -> Unit = {}
    var onNavigate: (String) -> Unit = {}
    var onSearch: (String) -> Unit = {}
    var onSearchVideos: (String) -> Unit = {}
    var onActionCompleted: () -> Unit = {}
    var onUserTranscript: (String) -> Unit = {}
    var onAgentResponse: (String) -> Unit = {}
    var onError: (String) -> Unit = {}
}

private fun startVoiceSession(
    scope: CoroutineScope,
    context: android.content.Context,
    toolContext: VoiceToolContext,
    voiceId: String,
    onConnecting: () -> Unit,
    onSession: (ConversationSession) -> Unit,
    onFailure: (String) -> Unit
) {
    scope.launch {
        onConnecting()
        val supportedVoiceId = VoiceAssistantVoices.resolveId(voiceId)
        Log.i("HTEVoice", "Starting authenticated voice session with supported voice preset")
        try {
            val user = SupabaseClientProvider.client.auth.currentUserOrNull()
                ?: error("Sign in to use HTE AI Voice Assistant.")
            Log.d("HTEVoice", "Requesting conversation token from Supabase Edge Function")
            val response = SupabaseClientProvider.client.functions.invoke("elevenlabs-voice-token")
            val token = voiceJson.decodeFromString<VoiceTokenResponse>(response.bodyAsText())
            check(token.token.isNotBlank()) { "The voice service did not return a conversation token." }
            check(token.userId == user.id) { "The voice service returned a session for a different account. Please sign in again." }
            Log.d("HTEVoice", "Authenticated token received; initializing ElevenLabs Android SDK session")

            val clientTools = mapOf(
                "control_playback" to object : ClientTool {
                    override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                        val action = parameters["action"] as? String ?: return ClientToolResult.failure("Missing playback action.")
                        val query = (parameters["query"] as? String)?.trim().orEmpty()
                        return when (action) {
                            "play_favourites" -> {
                                val favourites = toolContext.favourites
                                if (favourites.isEmpty()) ClientToolResult.failure("There are no saved favourites in the current library.")
                                else {
                                    withContext(Dispatchers.Main.immediate) { toolContext.onPlayFavourites(favourites); toolContext.onActionCompleted() }
                                    ClientToolResult.success("Selected the saved favourites queue with " + favourites.size + " tracks.")
                                }
                            }
                            "play_music", "play_track" -> {
                                val track = if (action == "play_music") toolContext.tracks.firstOrNull { !it.audioUrl.isNullOrBlank() || !it.sourceUrl.isNullOrBlank() }
                                    else findVoiceTrack(toolContext.tracks, query)
                                if (track == null) ClientToolResult.failure("I could not find a playable published HTE track for that request.")
                                else {
                                    withContext(Dispatchers.Main.immediate) { toolContext.onPlayTrack(track); toolContext.onActionCompleted() }
                                    ClientToolResult.success(voicePlaybackConfirmation(track))
                                }
                            }
                            "download_track" -> {
                                val track = findVoiceTrack(toolContext.tracks, query)
                                if (track == null) ClientToolResult.failure("I could not find a published HTE track matching that request.")
                                else if (!track.downloadAllowed || track.rightsStatus !in setOf("owned_or_licensed", "legacy_existing") || track.sourcePlatform.lowercase() in setOf("spotify", "youtube", "external") || !track.sourceUrl.isNullOrBlank()) {
                                    ClientToolResult.failure("This track is not an eligible HTE-owned or licensed download. Spotify and YouTube links must stay on their original platforms.")
                                } else {
                                    withContext(Dispatchers.Main.immediate) { toolContext.onDownloadTrack(track); toolContext.onActionCompleted() }
                                    if (track.brandedAudioUrl.isNullOrBlank() || track.downloadBrandingStatus != "ready") {
                                        ClientToolResult.success("Opened the HTE-branded download page for " + track.title + ". Complete the download in your browser; it is not saved to the in-app Library yet.")
                                    } else {
                                        ClientToolResult.success("Sent the approved HTE download for " + track.title + " to the in-app Download Manager. Check Library for progress.")
                                    }
                                }
                            }
                            "search_videos" -> {
                                if (query.isBlank()) ClientToolResult.failure("Tell me the video title or artist you want to find.")
                                else {
                                    val matches = toolContext.videos.filter { it.title.contains(query, true) || it.artist.orEmpty().contains(query, true) || it.description.orEmpty().contains(query, true) }
                                    withContext(Dispatchers.Main.immediate) {
                                        toolContext.onNavigate("VIDEOS")
                                        toolContext.onSearchVideos(query)
                                        toolContext.onActionCompleted()
                                    }
                                    val summary = if (matches.isEmpty()) "No published HTE videos match that search. Videos is now open."
                                        else "Found " + matches.size + " matching video(s): " + matches.take(4).joinToString { it.title + " by " + (it.artist ?: "High-Tech Entertainment") } + ". Videos is now open."
                                    ClientToolResult.success(summary)
                                }
                            }
                            "play_video" -> {
                                val video = findVoiceVideo(toolContext.videos, query)
                                if (video == null) ClientToolResult.failure("I could not find a published HTE video matching that request.")
                                else {
                                    withContext(Dispatchers.Main.immediate) { toolContext.onPlayVideo(video); toolContext.onActionCompleted() }
                                    ClientToolResult.success("Opened " + video.title + " in the HTE video player.")
                                }
                            }
                            "download_video" -> {
                                val video = findVoiceVideo(toolContext.videos, query)
                                if (video == null) ClientToolResult.failure("I could not find a published HTE video matching that request.")
                                else if (video.downloadUrl.isNullOrBlank()) ClientToolResult.failure("An original downloadable video file is not available for that item.")
                                else {
                                    withContext(Dispatchers.Main.immediate) { toolContext.onDownloadVideo(video); toolContext.onActionCompleted() }
                                    ClientToolResult.success("Sent the original video download request for " + video.title + ". Check Library for its progress.")
                                }
                            }
                            else -> if (HtePlaybackController.performAction(action)) {
                                withContext(Dispatchers.Main.immediate) { toolContext.onActionCompleted() }
                                ClientToolResult.success("Playback action '" + action + "' executed by HTE Universe.")
                            } else {
                                ClientToolResult.failure(when (action) {
                                    "next" -> "There is no next track in the current playback queue."
                                    "previous" -> "There is no active track to return from."
                                    "pause", "resume", "stop" -> "There is no active track in the music player yet."
                                    else -> "Unsupported playback action: " + action
                                })
                            }
                        }
                    }
                },
                "navigate_app" to object : ClientTool {
                    override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                        val destination = parameters["destination"] as? String ?: return ClientToolResult.failure("Missing app destination.")
                        val target = when (destination) {
                            "Home" -> "HOME"
                            "Discover" -> "DISCOVER"
                            "Videos" -> "VIDEOS"
                            "Library", "Favourites", "Downloads" -> "LIBRARY"
                            "Bookings", "Profile", "Founder Studio" -> "ACCOUNT"
                            "Voice Companion" -> null
                            "App Updates" -> "ACCOUNT"
                            else -> return ClientToolResult.failure("Unknown app destination: $destination")
                        }
                        if (target != null) withContext(Dispatchers.Main.immediate) { toolContext.onNavigate(target); toolContext.onActionCompleted() }
                        return ClientToolResult.success(if (destination == "Founder Studio") "Opened Account. Founder Studio is shown only when the native app verifies the signed-in owner role." else if (destination == "Voice Companion") "Voice Companion is already open." else if (destination == "App Updates") "Opened Account. Open Settings, then App Updates to manage this application." else "Opened $destination.")
                    }
                },
                "search_catalogue" to object : ClientTool {
                    override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                        val query = (parameters["query"] as? String)?.trim().orEmpty()
                        if (query.isBlank()) return ClientToolResult.failure("Enter a song, artist or genre to search.")
                        val matches = toolContext.tracks.filter { track ->
                            track.title.contains(query, true) || track.artist.contains(query, true) || track.genre.orEmpty().contains(query, true)
                        }
                        withContext(Dispatchers.Main.immediate) { toolContext.onSearch(query); toolContext.onActionCompleted() }
                        val summary = if (matches.isEmpty()) "No matching published tracks are currently loaded for '$query'. Discover search has been opened." else
                            "Found ${matches.size} published track(s): ${matches.take(5).joinToString { "${it.title} by ${it.artist}" }}. Discover search has been opened."
                        return ClientToolResult.success(summary)
                    }
                }
            )

            val config = ConversationConfig(
                conversationToken = token.token,
                userId = token.userId.ifBlank { user.id },
                textOnly = false,
                audioInputSampleRate = 16000,
                overrides = Overrides(tts = TtsOverrides(voiceId = supportedVoiceId), conversation = ConversationOverrides(textOnly = false)),
                clientTools = clientTools,
                onUserTranscriptEvent = { text, _ -> toolContext.onUserTranscript(text) },
                onAgentResponseEvent = { text, _ -> toolContext.onAgentResponse(text) },
                onError = { code, message ->
                    Log.e("HTEVoice", "ElevenLabs SDK session error code=$code: ${message.orEmpty().take(220)}")
                    toolContext.onError(voiceFailureMessage(listOfNotNull(message, "provider error $code").joinToString(" ")))
                }
            )
            val active = ConversationClient.startSession(config, context)
            onSession(active)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Include both the transport/provider detail and the app's safe fallback
            // so token-endpoint failures use the same actionable guidance as SDK errors.
            val details = listOfNotNull(
                error.message,
                error.toHteUserMessage("HTE AI Voice Assistant could not connect. Please try again.")
            ).joinToString(" ")
            val safeFailure = voiceFailureMessage(details)
            Log.e("HTEVoice", "Conversation session startup failed (${error.javaClass.simpleName}): $safeFailure")
            onFailure(safeFailure)
        }
    }
}



