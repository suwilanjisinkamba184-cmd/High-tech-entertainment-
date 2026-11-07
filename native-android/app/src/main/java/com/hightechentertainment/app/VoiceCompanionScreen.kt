package com.hightechentertainment.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicNone
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale

@Composable
fun VoiceCompanionScreen(
    onPause: () -> Unit,
    onResume: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onStop: () -> Unit,
    onNavigate: (String) -> Unit,
    onSearch: (String) -> Unit,
    onPlayFavourites: () -> Unit,
    isFounder: Boolean,
    tokenRepository: VoiceSessionTokenRepository,
    userId: String,
    tracks: List<NativeTrack>,
    favouriteIds: Set<String>
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Checking on-device speech services…") }
    var transcript by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    val onDeviceRecognitionAvailable = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }

    val microphonePermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
    var hasMicrophonePermission by remember { mutableStateOf(microphonePermission) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicrophonePermission = granted
        if (!granted) status = "Microphone permission is needed only when you press the voice button."
    }

    DisposableEffect(context) {
        onDispose {
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
        }
    }

    LaunchedEffect(onDeviceRecognitionAvailable) {
        status = if (onDeviceRecognitionAvailable) {
            "Offline speech recognition is available. Spoken replies are disabled until the dedicated High-Tech voice engines are installed."
        } else {
            "This device does not currently report an on-device speech recognizer. Install an offline speech language pack or use the online voice service when it is enabled."
        }
    }

    fun executeCommand(raw: String) {
        val command = raw.lowercase(Locale.ROOT).trim()
        val response = when {
            command.contains("pause") || command == "hold music" -> {
                onPause(); "Music paused."
            }
            command.contains("resume") || command.contains("continue music") || command.contains("play music") -> {
                onResume(); "Resuming music."
            }
            command.contains("next track") || command == "next" || command == "skip" -> {
                onNext(); "Skipping to the next track."
            }
            command.contains("previous track") || command == "previous" || command == "go back" -> {
                onPrevious(); "Going to the previous track."
            }
            command.contains("stop music") || command.contains("stop playback") -> {
                onStop(); "Music stopped."
            }
            command.contains("play favourites") || command.contains("play favorites") -> {
                onPlayFavourites(); "Opening your favourites."
            }
            command.startsWith("search for ") -> {
                val query = raw.substringAfter("search for ", "").trim()
                if (query.isNotEmpty()) onSearch(query)
                "Searching for ${query}."
            }
            command.contains("open home") -> { onNavigate("Home"); "Opening home." }
            command.contains("open discover") -> { onNavigate("Discover"); "Opening discover." }
            command.contains("open library") -> { onNavigate("Library"); "Opening your library." }
            command.contains("open downloads") -> { onNavigate("Downloads"); "Opening downloads." }
            command.contains("open profile") || command.contains("open account") -> { onNavigate("Profile"); "Opening your profile." }
            command.contains("open bookings") -> { onNavigate("Bookings"); "Opening bookings." }
            command.contains("open founder studio") -> if (isFounder) { onNavigate("Founder Studio"); "Opening Founder Studio." } else "Your account does not have Founder Studio access."
            else -> "I heard $raw. That command is not available offline yet."
        }
        status = response
    }

    fun startOnDeviceListening() {
        if (!onDeviceRecognitionAvailable) {
            status = "Offline speech recognition is unavailable. Check your Android offline speech language pack."
            return
        }
        if (!hasMicrophonePermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        runCatching {
            recognizer?.cancel()
            recognizer?.destroy()
            val localRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = localRecognizer
            localRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    status = "Listening on this device…"
                }
                override fun onBeginningOfSpeech() { listening = true }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { listening = false }
                override fun onError(error: Int) {
                    listening = false
                    status = when (error) {
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Offline language pack is unavailable. Download it in Android speech settings."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission was denied."
                        SpeechRecognizer.ERROR_NO_MATCH -> "I did not catch that. Please try again."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy. Try again in a moment."
                        else -> "Offline speech recognition failed (code $error). Check the downloaded language pack."
                    }
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty()
                    transcript = words
                    if (words.isNotBlank()) executeCommand(words)
                    else status = "No speech was recognised. Please try again."
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            localRecognizer.startListening(intent)
        }.onFailure {
            listening = false
            status = "Could not start offline speech recognition: ${it.message ?: "unknown error"}"
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OnlineVoiceCompanionPanel(
            tokenRepository = tokenRepository,
            userId = userId,
            tracks = tracks,
            favouriteIds = favouriteIds,
            isFounder = isFounder,
            onPause = onPause,
            onResume = onResume,
            onNext = onNext,
            onPrevious = onPrevious,
            onStop = onStop,
            onNavigate = onNavigate,
            onSearch = onSearch,
            onPlayFavourites = onPlayFavourites
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF201A10)),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.OfflineBolt, contentDescription = null, tint = Color(0xFFD7A83E))
                    Text("  ON-DEVICE VOICE MODE", color = Color(0xFFD7A83E), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.size(10.dp))
                Text(status, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(16.dp))
                Button(
                    onClick = {
                        if (listening) {
                            recognizer?.cancel()
                            listening = false
                            status = "Listening cancelled."
                        } else startOnDeviceListening()
                    },
                    enabled = onDeviceRecognitionAvailable
                ) {
                    Icon(if (listening) Icons.Rounded.Mic else Icons.Rounded.MicNone, contentDescription = null)
                    Text(if (listening) "  Stop listening" else "  Press to speak")
                }
                if (transcript.isNotBlank()) {
                    Spacer(Modifier.size(10.dp))
                    Text("You said: $transcript", color = Color.LightGray)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151515))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dedicated AI speech output", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Android system Text-to-Speech is not used. Online ElevenLabs speech and offline sherpa-onnx speech are being integrated as separate providers. Spoken replies remain disabled until those providers pass validation.",
                    color = Color.LightGray
                )
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF151515))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Offline commands", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("• Pause music\n• Resume music\n• Next track\n• Previous track\n• Stop music\n• Play favourites\n• Search for Afrobeat\n• Open downloads\n• Open profile", color = Color.LightGray)
            }
        }
        Text(
            "Offline recognition requires an installed on-device speech language pack. Android system Text-to-Speech is disabled. This screen uses press-to-talk only; it does not continuously listen in the background.",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

