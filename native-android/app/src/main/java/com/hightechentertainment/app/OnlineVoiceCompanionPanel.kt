package com.hightechentertainment.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hightechentertainment.app.data.NativeTrack
import com.hightechentertainment.app.data.VoiceSessionTokenRepository
import io.elevenlabs.ClientTool
import io.elevenlabs.ClientToolResult
import io.elevenlabs.ConversationClient
import io.elevenlabs.ConversationConfig
import io.elevenlabs.ConversationSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val VoiceGold = Color(0xFFE4B653)
private val VoicePanel = Color(0xFF17181B)

@Composable
internal fun OnlineVoiceCompanionPanel(
    tokenRepository: VoiceSessionTokenRepository,
    userId: String,
    tracks: List<NativeTrack>,
    favouriteIds: Set<String>,
    onVoiceAudioActive: (Boolean) -> Unit,
    isFounder: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onStop: () -> Unit,
    onNavigate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onPlayFavourites: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<ConversationSession?>(null) }
    var connecting by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready to connect") }
    var lastUserText by remember { mutableStateOf("") }
    var lastAgentText by remember { mutableStateOf("") }

    val connectOnline: () -> Unit = {
        scope.launch {
            connecting = true
            status = "Securing your private voice session…"
            try {
                val token = tokenRepository.fetchToken()
                val activity = context.findActivity()
                    ?: throw IllegalStateException("Voice sessions must be started from the app screen.")
                val tools = mapOf(
                    "control_playback" to object : ClientTool {
                        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                            val action = parameters["action"] as? String
                                ?: return ClientToolResult.failure("A playback action was not supplied.")
                            if (action == "play_favourites" && favouriteIds.isEmpty()) {
                                return ClientToolResult.failure("There are no saved favourites to play yet.")
                            }
                            withContext(Dispatchers.Main.immediate) {
                                when (action) {
                                    "pause" -> onPause()
                                    "resume" -> onResume()
                                    "next" -> onNext()
                                    "previous" -> onPrevious()
                                    "stop" -> onStop()
                                    "play_favourites" -> onPlayFavourites()
                                }
                            }
                            return ClientToolResult.success("The app received the $action playback command.")
                        }
                    },
                    "navigate_app" to object : ClientTool {
                        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                            val destination = parameters["destination"] as? String
                                ?: return ClientToolResult.failure("A destination was not supplied.")
                            if (destination == "Founder Studio" && !isFounder) {
                                return ClientToolResult.failure("This account does not have Founder Studio access.")
                            }
                            withContext(Dispatchers.Main.immediate) { onNavigate(destination) }
                            return ClientToolResult.success("Opened $destination in the app.")
                        }
                    },
                    "search_catalogue" to object : ClientTool {
                        override suspend fun execute(parameters: Map<String, Any>): ClientToolResult? {
                            val query = parameters["query"] as? String
                                ?: return ClientToolResult.failure("A search term was not supplied.")
                            val matches = tracks.filter {
                                it.title.contains(query, true) || it.artist.contains(query, true) ||
                                    (it.genre?.contains(query, true) == true)
                            }.take(5)
                            if (matches.isEmpty()) {
                                return ClientToolResult.failure("No published tracks matched that search in the loaded catalogue.")
                            }
                            withContext(Dispatchers.Main.immediate) { onSearch(query) }
                            val summary = matches.joinToString("; ") { "${it.title} by ${it.artist}" }
                            return ClientToolResult.success("Found these published tracks: $summary")
                        }
                    }
                )
                val config = ConversationConfig(
                    conversationToken = token,
                    userId = userId,
                    onStatusChange = { newStatus ->
                        scope.launch(Dispatchers.Main.immediate) {
                            status = newStatus.toString()
                            if (newStatus.toString().contains("DISCONNECTED", true) || newStatus.toString().contains("ERROR", true)) {
                                onVoiceAudioActive(false)
                            }
                        }
                    },
                    onModeChange = { mode ->
                        scope.launch(Dispatchers.Main.immediate) {
                            val speaking = mode.toString().contains("SPEAKING", true)
                            status = if (speaking) "Speaking" else "Listening"
                            onVoiceAudioActive(speaking)
                        }
                    },
                    onUserTranscriptEvent = { text, _ ->
                        scope.launch(Dispatchers.Main.immediate) { lastUserText = text }
                    },
                    onAgentResponseEvent = { text, _ ->
                        scope.launch(Dispatchers.Main.immediate) { lastAgentText = text }
                    },
                    clientTools = tools
                )
                session = ConversationClient.startSession(config, activity)
                status = "Connecting to High-Tech Voice Companion…"
            } catch (error: Exception) {
                onVoiceAudioActive(false)
                status = error.message ?: "Could not start the online voice session."
            } finally {
                connecting = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) connectOnline()
        else status = "Microphone permission is required for live voice conversations."
    }

    DisposableEffect(Unit) {
        onDispose {
            onVoiceAudioActive(false)
            val activeSession = session
            if (activeSession != null) {
                CoroutineScope(Dispatchers.Main.immediate).launch { activeSession.endSession() }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = VoicePanel)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("HIGH-TECH VOICE COMPANION", color = VoiceGold, fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                    Text("Live AI voice • app controls • private session", color = Color.LightGray, fontSize = 11.sp)
                }
                Surface(color = Color(0xFF292113), shape = RoundedCornerShape(50)) {
                    Text("ONLINE", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = VoiceGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(status, color = Color.White, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        if (session != null) {
                            val activeSession = session
                            session = null
                            muted = false
                            onVoiceAudioActive(false)
                            status = "Disconnected"
                            scope.launch { activeSession?.endSession() }
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else connectOnline()
                    },
                    enabled = !connecting,
                    colors = ButtonDefaults.buttonColors(containerColor = VoiceGold, contentColor = Color(0xFF111111))
                ) {
                    if (connecting) CircularProgressIndicator()
                    else Text(if (session == null) "Start voice" else "End session", fontWeight = FontWeight.Bold)
                }
                if (session != null) {
                    OutlinedButton(onClick = {
                        muted = !muted
                        scope.launch { session?.setMicMuted(muted) }
                    }) { Text(if (muted) "Unmute mic" else "Mute mic", color = Color.White) }
                }
            }
            if (lastUserText.isNotBlank()) {
                Surface(color = Color(0xFF202226), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("YOU", color = VoiceGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(lastUserText, color = Color.White)
                    }
                }
            }
            if (lastAgentText.isNotBlank()) {
                Surface(color = Color(0xFF242017), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("HIGH-TECH", color = VoiceGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(lastAgentText, color = Color.White)
                    }
                }
            }
            Text(
                "Live speech uses ElevenLabs. Your API key stays on the server; the app receives only a short-lived session token.",
                color = Color(0xFFAAA39A), fontSize = 11.sp
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
