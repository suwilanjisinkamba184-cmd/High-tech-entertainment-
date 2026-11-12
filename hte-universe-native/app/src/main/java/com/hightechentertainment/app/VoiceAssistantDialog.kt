package com.hightechentertainment.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    @SerialName("user_id") val userId: String
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
    SideEffect {
        toolContext.tracks = currentTracks
        toolContext.favourites = currentFavourites
        toolContext.onPlayFavourites = currentOnPlayFavourites
        toolContext.onNavigate = currentOnNavigate
        toolContext.onSearch = currentOnSearch
        toolContext.onUserTranscript = { userTranscript = it }
        toolContext.onAgentResponse = { agentResponse = it }
        toolContext.onError = { errorMessage = it; status = "Error" }
    }

    var session by remember { mutableStateOf<ConversationSession?>(null) }
    var isConnecting by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Disconnected") }
    var mode by remember { mutableStateOf("listening") }
    var muted by remember { mutableStateOf(false) }
    var audioLevel by remember { mutableStateOf(0f) }
    var userTranscript by remember { mutableStateOf("") }
    var agentResponse by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceSession(scope, context, toolContext,
            onConnecting = { isConnecting = true; status = "Connecting"; errorMessage = null },
            onSession = { active -> session = active; isConnecting = false },
            onFailure = { error -> isConnecting = false; status = "Disconnected"; errorMessage = error }
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
                onFailure = { error -> isConnecting = false; status = "Disconnected"; errorMessage = error }
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
        Surface(Modifier.fillMaxWidth().padding(18.dp), color = VoicePanel, shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("HIGH-TECH ENTERTAINMENT", color = VoiceGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                        Text("Voice Companion", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close Voice Companion", tint = Color.White) }
                }
                val orbScale = 1f + (audioLevel.coerceIn(0f, 1f) * 0.22f)
                Box(Modifier.size(94.dp).graphicsLayer { scaleX = orbScale; scaleY = orbScale }.clip(CircleShape).background(VoiceGold.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.GraphicEq, null, tint = VoiceGold, modifier = Modifier.size(48.dp))
                }
                Text(status.replaceFirstChar { it.uppercase() } + if (session != null) " · ${mode.replaceFirstChar { it.uppercase() }}" else "", color = VoiceGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Talk naturally to discover music, control playback and navigate HTE Universe.", color = VoiceMuted, fontSize = 12.sp, textAlign = TextAlign.Center)

                if (userTranscript.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2026)), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("YOU", color = VoiceMuted, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                            Text(userTranscript, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
                if (agentResponse.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF201D17)), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("VOICE COMPANION", color = VoiceGold, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                            Text(agentResponse, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
                errorMessage?.let { Text(it, color = Color(0xFFFFA8A8), fontSize = 11.sp, textAlign = TextAlign.Center) }

                if (isConnecting) LinearProgressIndicator(Modifier.fillMaxWidth(), color = VoiceGold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (session == null) {
                        Button(onClick = { connect() }, enabled = !isConnecting, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = VoiceGold, contentColor = Color.Black)) {
                            Icon(Icons.Outlined.Mic, null); Spacer(Modifier.width(8.dp)); Text(if (isConnecting) "Connecting…" else "Start talking")
                        }
                    } else {
                        OutlinedButton(onClick = { scope.launch { session?.toggleMute() } }, modifier = Modifier.weight(1f)) {
                            Icon(if (muted) Icons.Outlined.MicOff else Icons.Outlined.Mic, null); Spacer(Modifier.width(6.dp)); Text(if (muted) "Unmute" else "Mute")
                        }
                        Button(onClick = { session = null; status = "Disconnecting" }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8E3434), contentColor = Color.White)) {
                            Icon(Icons.Outlined.CallEnd, null); Spacer(Modifier.width(6.dp)); Text("End session")
                        }
                    }
                }
                Text("Your private session is authenticated through HTE Supabase. The ElevenLabs API key stays server-side.", color = VoiceMuted, fontSize = 9.sp, textAlign = TextAlign.Center)
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
