package com.hightechentertainment.app

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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.hightechentertainment.app.data.SupabaseClientProvider
import com.hightechentertainment.app.data.TrackDto
import com.hightechentertainment.app.media.HtePlaybackController
import io.elevenlabs.ClientTool
import io.elevenlabs.ClientToolResult
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationConfig
import io.elevenlabs.ConversationOverrides
import io.elevenlabs.ConversationSession
import io.elevenlabs.Overrides
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val VoiceGold = Color(0xFFD6B15E)
private val VoicePanel = Color(0xFF121418)
private val VoiceMuted = Color(0xFF8B929D)

@Serializable
private data class VoiceTokenResponse(
    val token: String,
    @SerialName("user_id") val userId: String = ""
)

@Composable
fun VoiceAssistantDialog(
    tracks: List<TrackDto>,
    favouriteTracks: List<TrackDto>,
    onPlayFavourites: (List<TrackDto>) -> Unit,
    onNavigate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentTracks by rememberUpdatedState(tracks)
    val currentFavourites by rememberUpdatedState(favouriteTracks)
    val currentOnPlayFavourites by rememberUpdatedState(onPlayFavourites)
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    val currentOnSearch by rememberUpdatedState(onSearch)
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

    SideEffect {
        toolContext.tracks = currentTracks
        toolContext.favourites = currentFavourites
        toolContext.onPlayFavourites = currentOnPlayFavourites
        toolContext.onNavigate = currentOnNavigate
        toolContext.onSearch = currentOnSearch
        toolContext.onUserTranscript = { value -> scope.launch { userTranscript = value } }
        toolContext.onAgentResponse = { value -> scope.launch { agentResponse = value } }
        toolContext.onError = { value -> scope.launch { errorMessage = value; status = "Error" } }
    }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceSession(scope, context, toolContext,
            onConnecting = { isConnecting = true; status = "Connecting"; errorMessage = null },
            onSession = { active -> session = active; isConnecting = false },
            onFailure = { error -> isConnecting = false; status = "Disconnected"; errorMessage = voiceFailureMessage(error) }
        ) else errorMessage = "Microphone permission is required for Voice Companion."
    }

    fun connect() {
        if (session != null || isConnecting) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startVoiceSession(scope, context, toolContext,
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF08090B)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("HTE UNIVERSE / NEURAL CO-PILOT", color = VoiceGold, fontSize = 9.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
                        Text("Synapse AI", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Your voice-controlled HTE companion", color = VoiceMuted, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close Synapse AI", tint = Color.White) }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF11161C)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    Modifier.fillMaxWidth().height(210.dp)
                        .background(Brush.radialGradient(listOf(Color(0xFF3C3220), Color(0xFF18212A), Color(0xFF08090B))), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(164.dp).clip(CircleShape).background(VoiceGold.copy(alpha = .07f)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(126.dp).clip(CircleShape).background(VoiceGold.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(88.dp).clip(CircleShape).background(VoiceGold.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.GraphicEq, null, tint = VoiceGold, modifier = Modifier.size(46.dp))
                            }
                        }
                    }
                    Text("SYNAPSE / AUDIO CORE", color = VoiceMuted, fontSize = 8.sp, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
                }
                Text(status.replaceFirstChar { it.uppercase() } + if (session != null) " · ${mode.replaceFirstChar { it.uppercase() }}" else "", color = VoiceGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Speak naturally to find music, control playback, search the catalogue and move around HTE Universe.", color = VoiceMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
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
                            Text("SYNAPSE / RESPONSE", color = VoiceGold, fontSize = 9.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
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
                            modifier = Modifier.weight(1f).height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = VoiceGold, contentColor = Color.Black),
                            shape = RoundedCornerShape(15.dp)
                        ) {
                            Icon(Icons.Outlined.Mic, null); Spacer(Modifier.width(8.dp)); Text(if (isConnecting) "Connecting…" else "Start voice session")
                        }
                    } else {
                        OutlinedButton(onClick = { scope.launch { session?.toggleMute() } }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(15.dp)) {
                            Icon(if (muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, null); Spacer(Modifier.width(6.dp)); Text(if (muted) "Unmute" else "Mute")
                        }
                        Button(onClick = { session = null; status = "Disconnecting" }, modifier = Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E3434), contentColor = Color.White), shape = RoundedCornerShape(15.dp)) {
                            Icon(Icons.Outlined.CallEnd, null); Spacer(Modifier.width(6.dp)); Text("End session")
                        }
                    }
                }
                Text("QUICK LAUNCH", color = VoiceGold, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { currentOnSearch("Zambian") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.Search, null); Spacer(Modifier.width(4.dp)); Text("Zambian music", fontSize = 10.sp)
                    }
                    OutlinedButton(onClick = { currentOnNavigate("DISCOVER") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.Explore, null); Spacer(Modifier.width(4.dp)); Text("Discover", fontSize = 10.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { currentOnNavigate("LIBRARY") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.LibraryMusic, null); Spacer(Modifier.width(4.dp)); Text("My library", fontSize = 10.sp)
                    }
                    OutlinedButton(onClick = { currentOnNavigate("HOME") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.Home, null); Spacer(Modifier.width(4.dp)); Text("Home", fontSize = 10.sp)
                    }
                }
                Text("The ElevenLabs API key remains server-side. Sign in to HTE Universe before starting a private voice session.", color = VoiceMuted, fontSize = 9.sp, textAlign = TextAlign.Center)
            }
        }
    }

    // Keep the callbacks current for the registered SDK client tools.
    @Suppress("UNUSED_VARIABLE") val activeMode = mode
}

private class VoiceToolContext {
    var tracks: List<TrackDto> = emptyList()
    var favourites: List<TrackDto> = emptyList()
    var onPlayFavourites: (List<TrackDto>) -> Unit = {}
    var onNavigate: (String) -> Unit = {}
    var onSearch: (String) -> Unit = {}
    var onUserTranscript: (String) -> Unit = {}
    var onAgentResponse: (String) -> Unit = {}
    var onError: (String) -> Unit = {}
}

private fun startVoiceSession(
    scope: CoroutineScope,
    context: android.content.Context,
    toolContext: VoiceToolContext,
    onConnecting: () -> Unit,
    onSession: (ConversationSession) -> Unit,
    onFailure: (String) -> Unit
) {
    scope.launch {
        onConnecting()
        try {
            val user = SupabaseClientProvider.client.auth.currentUserOrNull()
                ?: error("Sign in to use the High-Tech Entertainment Voice Companion.")
            val response = SupabaseClientProvider.client.functions.invoke("elevenlabs-voice-token")
            val token = Json { ignoreUnknownKeys = true }.decodeFromString<VoiceTokenResponse>(response.bodyAsText())
            check(token.token.isNotBlank()) { "The voice service did not return a conversation token." }

            val clientTools = mapOf(
                "control_playback" to object : ClientTool {
                    override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                        val action = parameters["action"] as? String ?: return ClientToolResult.failure("Missing playback action.")
                        return withContext(Dispatchers.Main.immediate) {
                            if (action == "play_favourites") {
                                val favourites = toolContext.favourites
                                if (favourites.isEmpty()) ClientToolResult.failure("There are no saved favourites in the current library.")
                                else {
                                    toolContext.onPlayFavourites(favourites)
                                    ClientToolResult.success("Started the saved favourites queue with ${favourites.size} tracks.")
                                }
                            } else if (!HtePlaybackController.isConnected()) {
                                ClientToolResult.failure("There is no active music player session yet.")
                            } else {
                                when (action) {
                                    "pause" -> HtePlaybackController.pause()
                                    "resume" -> HtePlaybackController.resume()
                                    "next" -> HtePlaybackController.next()
                                    "previous" -> HtePlaybackController.previous()
                                    "stop" -> HtePlaybackController.stop()
                                    else -> return@withContext ClientToolResult.failure("Unsupported playback action: $action")
                                }
                                ClientToolResult.success("Playback action '$action' executed by HTE Universe.")
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
                            "App Updates" -> return ClientToolResult.failure("The App Updates page is not available in this build.")
                            else -> return ClientToolResult.failure("Unknown app destination: $destination")
                        }
                        if (target != null) withContext(Dispatchers.Main.immediate) { toolContext.onNavigate(target) }
                        return ClientToolResult.success(if (destination == "Founder Studio") "Opened Account. Founder Studio is shown only when the native app verifies the signed-in owner role." else if (destination == "Voice Companion") "Voice Companion is already open." else "Opened $destination.")
                    }
                },
                "search_catalogue" to object : ClientTool {
                    override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                        val query = (parameters["query"] as? String)?.trim().orEmpty()
                        if (query.isBlank()) return ClientToolResult.failure("Enter a song, artist or genre to search.")
                        val matches = toolContext.tracks.filter { track ->
                            track.title.contains(query, true) || track.artist.contains(query, true) || track.genre.orEmpty().contains(query, true)
                        }
                        withContext(Dispatchers.Main.immediate) { toolContext.onSearch(query) }
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
                overrides = Overrides(conversation = ConversationOverrides(textOnly = false)),
                clientTools = clientTools,
                onUserTranscriptEvent = { text, _ -> toolContext.onUserTranscript(text) },
                onAgentResponseEvent = { text, _ -> toolContext.onAgentResponse(text) },
                onError = { code, message -> toolContext.onError(message ?: "Voice service error ($code).") }
            )
            val active = ConversationClient.startSession(config, context)
            onSession(active)
        } catch (error: Throwable) {
            onFailure(error.message?.take(220) ?: "Could not connect to the HTE Voice Companion.")
        }
    }
}


private fun voiceFailureMessage(message: String): String {
    val normalized = message.lowercase()
    return when {
        "convai_write" in normalized || "missing permission" in normalized || "401" in normalized || "502" in normalized ->
            "Voice service authentication failed. The HTE ElevenLabs API key must include Conversational AI (ConvAI) write permission. Please update the key stored in Supabase and try again."
        "sign in" in normalized || "unauthorized" in normalized ->
            "Please sign in again, then reconnect Voice Companion."
        else -> message.take(220).ifBlank { "Could not connect to the HTE Voice Companion. Please try again." }
    }
}
