package com.hightechentertainment.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.hightechentertainment.app.data.TrackDto
import com.hightechentertainment.app.media.HtePlaybackController

private val VoiceGold = Color(0xFFD6B15E)
private val VoicePanel = Color(0xFF121418)
private val VoiceMuted = Color(0xFF8B929D)

@Composable
fun VoiceAssistantDialog(
    tracks: List<TrackDto>,
    onPlay: (TrackDto) -> Unit,
    onNavigate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentTracks by rememberUpdatedState(tracks)
    val currentOnPlay by rememberUpdatedState(onPlay)
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    val currentOnSearch by rememberUpdatedState(onSearch)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    var transcript by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("Try: play a song, pause music, next track, open videos, or search an artist.") }
    var isListening by remember { mutableStateOf(false) }
    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            transcript = ""
            response = "Listening…"
            isListening = true
            recognizer?.startListening(speechIntent)
        } else response = "Microphone permission is needed for voice commands."
    }

    fun executeCommand(raw: String) {
        val command = raw.trim()
        val lower = command.lowercase()
        when {
            lower.startsWith("play ") && lower !in listOf("play music", "play audio") -> {
                val query = command.substringAfter(' ').trim()
                val match = currentTracks.firstOrNull { it.title.contains(query, true) || it.artist.contains(query, true) }
                if (match != null) { response = "Playing ${match.title}."; currentOnPlay(match); currentOnDismiss() }
                else response = "I couldn't find a published track matching '$query'."
            }
            lower in listOf("play", "play music", "resume", "resume music") -> { HtePlaybackController.resume(); response = "Resuming music." }
            lower in listOf("pause", "pause music", "stop music") -> { HtePlaybackController.pause(); response = "Music paused." }
            lower in listOf("next", "next track", "skip") -> { HtePlaybackController.next(); response = "Moving to the next track." }
            lower in listOf("previous", "previous track", "go back") -> { HtePlaybackController.previous(); response = "Moving to the previous track." }
            lower.startsWith("search ") -> { currentOnSearch(command.substringAfter(' ').trim()); response = "Opening search."; currentOnDismiss() }
            lower.contains("open video") || lower.contains("show video") -> { currentOnNavigate("VIDEOS"); response = "Opening videos."; currentOnDismiss() }
            lower.contains("open library") || lower.contains("my library") -> { currentOnNavigate("LIBRARY"); response = "Opening your library."; currentOnDismiss() }
            lower.contains("open account") || lower.contains("open settings") -> { currentOnNavigate("ACCOUNT"); response = "Opening your account and settings."; currentOnDismiss() }
            lower.contains("founder studio") -> { currentOnNavigate("ACCOUNT"); response = "Open Founder Studio from your owner account card."; currentOnDismiss() }
            lower.contains("open home") || lower == "home" -> { currentOnNavigate("HOME"); response = "Opening Home."; currentOnDismiss() }
            lower.contains("discover") || lower.contains("open search") -> { currentOnNavigate("DISCOVER"); response = "Opening Discover."; currentOnDismiss() }
            lower == "help" || lower.contains("what can you do") -> response = "Commands: play [song], pause music, resume music, next track, previous track, open videos, open library, open account, open Founder Studio, or search [artist/song]."
            else -> response = "I didn't recognise that command. Say 'help' to hear the available commands."
        }
    }

    DisposableEffect(recognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true; response = "Listening…" }
            override fun onBeginningOfSpeech() { isListening = true }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false; response = "Speech recognition error ($error). Tap the microphone and try again." }
            override fun onResults(results: Bundle?) {
                isListening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                transcript = spoken
                if (spoken.isNotBlank()) executeCommand(spoken) else response = "I didn't hear a command. Please try again."
            }
            override fun onPartialResults(partialResults: Bundle?) {
                transcript = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        recognizer?.setRecognitionListener(listener)
        onDispose { recognizer?.destroy() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(20.dp), color = VoicePanel, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("HTE ASSISTANT", color = VoiceGold, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close assistant", tint = Color.White) }
                }
                Icon(Icons.Outlined.GraphicEq, null, tint = VoiceGold, modifier = Modifier.size(48.dp))
                Text("Voice command centre", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Control music and navigate HTE Universe with your voice.", color = VoiceMuted, fontSize = 12.sp)
                if (recognizer == null) {
                    Text("Speech recognition is not available on this device.", color = VoiceGold, fontSize = 12.sp)
                } else {
                    Text(if (transcript.isBlank()) "Say a command" else "“$transcript”", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Button(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            transcript = ""; response = "Listening…"; isListening = true; recognizer.startListening(speechIntent)
                        } else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }, enabled = !isListening, colors = ButtonDefaults.buttonColors(containerColor = VoiceGold, contentColor = Color.Black)) {
                        Icon(if (isListening) Icons.Outlined.MicOff else Icons.Outlined.Mic, null)
                        Spacer(Modifier.width(8.dp)); Text(if (isListening) "Listening…" else "Start listening")
                    }
                }
                Text(response, color = VoiceGold, fontSize = 12.sp)
                Text("Voice commands use the Android speech recognizer. Generative AI chat is not connected in this build.", color = VoiceMuted, fontSize = 10.sp)
            }
        }
    }
}
