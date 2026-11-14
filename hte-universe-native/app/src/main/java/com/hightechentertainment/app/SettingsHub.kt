package com.hightechentertainment.app

import android.Manifest
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.content.Intent
import android.net.ConnectivityManager
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
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
import com.hightechentertainment.app.media.HteAudioEffects
import com.hightechentertainment.app.media.HtePlaybackService
import com.hightechentertainment.app.media.OfflineDownloadManager
import com.hightechentertainment.app.update.HteDownloadProgress
import com.hightechentertainment.app.update.HteUpdateInfo
import com.hightechentertainment.app.update.HteUpdateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val SettingsGold = Color(0xFFD6B15E)
private val SettingsSilver = Color(0xFFC4CAD2)
private val SettingsMuted = Color(0xFF8B929D)
private val SettingsPanel = Color(0xFF121418)
private val SettingsBackground = Color(0xFF08090B)

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
fun SettingsHub(onClose: () -> Unit, settingsStore: AppSettingsStore, downloadManager: OfflineDownloadManager, onSettingsChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updater = remember(context) { HteUpdateManager(context) }
    var notifications by remember { mutableStateOf(settingsStore.notificationsEnabled) }
    var autoplay by remember { mutableStateOf(settingsStore.autoplayNext) }
    var dataSaver by remember { mutableStateOf(settingsStore.dataSaver) }
    var backgroundPlayback by remember { mutableStateOf(settingsStore.backgroundPlayback) }
    var startupSound by remember { mutableStateOf(settingsStore.startupSoundEnabled) }
    var reduceMotion by remember { mutableStateOf(settingsStore.reduceMotion) }
    var eqEnabled by remember { mutableStateOf(settingsStore.equalizerEnabled) }
    var eqPreset by remember { mutableStateOf(settingsStore.equalizerPreset) }
    var eqBands by remember { mutableStateOf(settingsStore.equalizerBands()) }
    var networkPolicy by remember { mutableStateOf(settingsStore.networkPolicy) }
    var updateNetworkPolicy by remember { mutableStateOf(settingsStore.updateNetworkPolicy) }
    var streamingQuality by remember { mutableStateOf(settingsStore.streamingQuality) }
    var downloadQuality by remember { mutableStateOf(settingsStore.downloadQuality) }
    var videoQuality by remember { mutableStateOf(settingsStore.videoQuality) }
    var downloads by remember { mutableStateOf(emptyList<com.hightechentertainment.app.media.OfflineDownloadRecord>()) }
    var showClearDownloads by remember { mutableStateOf(false) }
    var showClearData by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<HteUpdateInfo?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf(HteDownloadProgress(updater.partialDownloadSize(), 0L)) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifications = granted
        settingsStore.notificationsEnabled = granted
        onSettingsChanged()
    }

    fun refreshDownloads() {
        scope.launch { downloads = withContext(Dispatchers.IO) { downloadManager.getDownloads() } }
    }
    fun checkForUpdates() {
        scope.launch {
            isChecking = true
            updateError = null
            try {
                updateInfo = updater.checkForUpdate(BuildConfig.VERSION_NAME)
                downloadedApk = updater.cachedUpdate()
                statusMessage = if (updateInfo?.updateAvailable == true) "A newer HTE Universe release is available." else "No newer public release is available for this build."
            } catch (error: Throwable) {
                updateError = error.message?.take(180) ?: "Could not check for updates. Check your connection."
            } finally { isChecking = false }
        }
    }
    fun startUpdateDownload() {
        val info = updateInfo ?: return
        val url = info.downloadUrl ?: run { updateError = "This release does not include an APK installer."; return }
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        if (updateNetworkPolicy == "WIFI_ONLY" && connectivity?.isActiveNetworkMetered == true) {
            updateError = "Update downloads are set to Wi-Fi only. Connect to Wi-Fi or allow mobile data in Settings."
            return
        }
        downloadJob?.cancel()
        downloadJob = scope.launch {
            isDownloading = true
            updateError = null
            try {
                downloadedApk = updater.downloadUpdate(url, info.downloadSize, info.sha256) { value -> android.os.Handler(android.os.Looper.getMainLooper()).post { progress = value } }
                statusMessage = "Update downloaded. Android will verify the signing identity during installation."
            } catch (_: CancellationException) {
                updateError = "Download paused. Your partial file is saved; tap Resume to continue."
            } catch (error: Throwable) {
                updateError = (error.message ?: "Download interrupted. Your partial file is saved.") + " Tap Resume to continue."
            } finally { isDownloading = false }
        }
    }

    LaunchedEffect(Unit) {
        refreshDownloads()
        checkForUpdates()
    }
    DisposableEffect(eqEnabled, eqBands, HtePlaybackService.activeAudioSessionId) {
        val session = HtePlaybackService.activeAudioSessionId
        if (session > 0) HteAudioEffects.apply(session, eqEnabled, eqBands)
        onDispose { }
    }

    Column(Modifier.fillMaxSize().background(SettingsBackground)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("HTE CONTROL CENTRE", color = SettingsGold, fontSize = 10.sp, letterSpacing = 2.2.sp, fontWeight = FontWeight.Bold)
                Text("Settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Personalise your universe.", color = SettingsMuted, fontSize = 12.sp)
            }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close settings", tint = SettingsSilver) }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SettingsHero()
            }
            item {
                SettingsSection("PLAYBACK & SOUND", "Tune the way HTE Universe sounds")
                SettingsCard {
                    SettingsSwitch("Background playback", "Keep music playing when you leave the app", backgroundPlayback) {
                        backgroundPlayback = it; settingsStore.backgroundPlayback = it; onSettingsChanged()
                    }
                    SettingsDivider()
                    SettingsSwitch("Autoplay next track", "Continue through the current queue", autoplay) {
                        autoplay = it; settingsStore.autoplayNext = it; onSettingsChanged()
                    }
                    SettingsDivider()
                    SettingsSwitch("Equalizer", "Apply sound processing to HTE music playback", eqEnabled) {
                        eqEnabled = it; settingsStore.equalizerEnabled = it
                        val applied = HteAudioEffects.apply(HtePlaybackService.activeAudioSessionId, it, eqBands)
                        if (it && !applied) statusMessage = "Start music playback to activate the equalizer on this device."
                    }
                    Text("SOUND PROFILE", color = SettingsGold, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    SettingsChips(listOf("FLAT", "BASS", "VOCAL", "STUDIO", "AFROBEAT", "DANCEHALL", "DRILL", "GOSPEL"), eqPreset) { preset ->
                        eqPreset = preset; settingsStore.equalizerPreset = preset
                        val values = when (preset) {
                            "BASS" -> listOf(8, 6, 2, 0, -1)
                            "VOCAL" -> listOf(-3, 1, 6, 5, 2)
                            "STUDIO" -> listOf(0, 1, -1, 1, 0)
                            "AFROBEAT" -> listOf(6, 4, 1, 3, 5)
                            "DANCEHALL" -> listOf(8, 5, 0, 3, 4)
                            "DRILL" -> listOf(9, 4, -2, 2, 6)
                            "GOSPEL" -> listOf(1, 3, 6, 5, 3)
                            else -> listOf(0, 0, 0, 0, 0)
                        }
                        eqBands = values; settingsStore.setEqualizerBands(values)
                        HteAudioEffects.apply(HtePlaybackService.activeAudioSessionId, eqEnabled, values)
                    }
                    val bandLabels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
                    bandLabels.forEachIndexed { index, label ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, color = SettingsSilver, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text(if (eqBands[index] > 0) "+${eqBands[index]} dB" else "${eqBands[index]} dB", color = SettingsGold, fontSize = 11.sp)
                            }
                            Slider(
                                value = eqBands[index].toFloat(),
                                onValueChange = { value ->
                                    val updated = eqBands.toMutableList().also { it[index] = value.toInt() }
                                    eqBands = updated; eqPreset = "CUSTOM"
                                    settingsStore.equalizerPreset = "CUSTOM"; settingsStore.setEqualizerBands(updated)
                                    HteAudioEffects.apply(HtePlaybackService.activeAudioSessionId, eqEnabled, updated)
                                },
                                valueRange = -12f..12f,
                                enabled = eqEnabled,
                                colors = SliderDefaults.colors(thumbColor = SettingsGold, activeTrackColor = SettingsGold)
                            )
                        }
                    }
                    Text("Equalizer availability depends on the phone's audio-effect support. Your selected preset is applied when the HTE playback audio session becomes active.", color = SettingsMuted, fontSize = 10.sp)
                    SettingsDivider()
                    SettingsChoice("Music streaming quality", streamingQuality, listOf("AUTO", "DATA SAVER", "NORMAL", "HIGH")) {
                        streamingQuality = it; settingsStore.streamingQuality = it
                    }
                    SettingsChoice("Preferred video quality (when variants are available)", videoQuality, listOf("AUTO", "LOW", "MEDIUM", "HIGH")) {
                        videoQuality = it; settingsStore.videoQuality = it
                    }
                }
            }
            item {
                SettingsSection("NETWORK & DOWNLOADS", "Control data use and offline files")
                SettingsCard {
                    Text("Media download network", color = Color.White, fontWeight = FontWeight.SemiBold)
                    SettingsChips(listOf("WIFI_ONLY", "WIFI + MOBILE"), if (networkPolicy == "WIFI_ONLY") "WIFI_ONLY" else "WIFI + MOBILE") {
                        networkPolicy = if (it == "WIFI_ONLY") "WIFI_ONLY" else "WIFI_AND_MOBILE"
                        settingsStore.networkPolicy = networkPolicy
                        onSettingsChanged()
                    }
                    Text("Update download network", color = Color.White, fontWeight = FontWeight.SemiBold)
                    SettingsChips(listOf("WIFI_ONLY", "WIFI + MOBILE"), if (updateNetworkPolicy == "WIFI_ONLY") "WIFI_ONLY" else "WIFI + MOBILE") {
                        updateNetworkPolicy = if (it == "WIFI_ONLY") "WIFI_ONLY" else "WIFI_AND_MOBILE"
                        settingsStore.updateNetworkPolicy = updateNetworkPolicy
                    }
                    SettingsDivider()
                    SettingsChoice("Preferred audio download quality (when variants are available)", downloadQuality, listOf("STANDARD", "HIGH", "HIGHEST")) {
                        downloadQuality = it; settingsStore.downloadQuality = it
                    }
                    SettingsSwitch("Data saver", "Restrict downloads over metered mobile connections", dataSaver) {
                        dataSaver = it; settingsStore.dataSaver = it; onSettingsChanged()
                    }
                    SettingsDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Downloaded files", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("${downloads.size} saved or in-progress items", color = SettingsMuted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { refreshDownloads() }) { Text("Refresh", color = SettingsGold) }
                    }
                    if (downloads.isNotEmpty()) {
                        downloads.take(8).forEach { record ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(record.title, color = SettingsSilver, fontSize = 12.sp, maxLines = 1)
                                    Text("${record.mediaType.uppercase()} · ${record.status}", color = SettingsMuted, fontSize = 10.sp)
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { downloadManager.remove(record.id) }
                                        refreshDownloads()
                                    }
                                }) { Icon(Icons.Outlined.DeleteOutline, "Delete ${record.title}", tint = SettingsGold) }
                            }
                        }
                        if (downloads.size > 8) Text("Showing 8 of ${downloads.size} downloads. Manage the full library in Library.", color = SettingsMuted, fontSize = 10.sp)
                        TextButton(onClick = { showClearDownloads = true }) { Text("Delete all downloaded files", color = Color(0xFFFF8A80)) }
                    } else {
                        Text("No downloaded files are registered on this device.", color = SettingsMuted, fontSize = 11.sp)
                    }
                }
            }
            item {
                SettingsSection("APP UPDATES", "Check, download and resume safely")
                SettingsCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.SystemUpdate, null, tint = SettingsGold, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("HTE Universe ${BuildConfig.VERSION_NAME}", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Installed version · build ${BuildConfig.VERSION_CODE}", color = SettingsMuted, fontSize = 10.sp)
                        }
                        if (updateInfo?.updateAvailable == true) Text("UPDATE AVAILABLE", color = SettingsGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { checkForUpdates() }, enabled = !isChecking, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SettingsGold, contentColor = SettingsBackground)) {
                        if (isChecking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = SettingsBackground)
                        else Icon(Icons.Outlined.Refresh, null)
                        Spacer(Modifier.width(8.dp)); Text(if (isChecking) "Checking…" else "Check for updates")
                    }
                    updateInfo?.let { info ->
                        Text("Latest published version: ${info.latestVersion}", color = SettingsSilver, fontSize = 12.sp)
                        Text(if (info.updateAvailable) "A newer version is available." else "No newer public release is available.", color = if (info.updateAvailable) SettingsGold else Color(0xFF8BCB9B), fontSize = 11.sp)
                        if (info.releaseNotes.isNotBlank()) Text(info.releaseNotes.take(900), color = SettingsMuted, fontSize = 10.sp, maxLines = 8)
                        if (info.updateAvailable) {
                            val partialBytes = updater.partialDownloadSize()
                            if (isDownloading || partialBytes > 0L) {
                                LinearProgressIndicator(progress = { if (progress.total > 0) progress.fraction else 0f }, modifier = Modifier.fillMaxWidth(), color = SettingsGold)
                                Text(if (progress.total > 0) "${(progress.fraction * 100).toInt()}% · ${formatBytes(progress.downloaded)} / ${formatBytes(progress.total)}" else "Downloaded ${formatBytes(partialBytes)} · waiting for size information", color = SettingsMuted, fontSize = 10.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { startUpdateDownload() }, enabled = !isDownloading && info.downloadUrl != null && downloadedApk == null, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SettingsGold, contentColor = SettingsBackground)) {
                                    Text(if (partialBytes > 0L) "Resume download" else "Download update")
                                }
                                if (isDownloading) OutlinedButton(onClick = { downloadJob?.cancel() }, modifier = Modifier.weight(1f)) { Text("Pause") }
                            }
                        }
                    }
                    downloadedApk?.takeIf { updateInfo?.updateAvailable == true }?.let { file ->
                        Text("Update installer ready · ${formatBytes(file.length())}", color = Color(0xFF8BCB9B), fontSize = 11.sp)
                        Button(onClick = { openUpdateInstaller(context, file) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SettingsGold, contentColor = SettingsBackground)) {
                            Icon(Icons.Outlined.InstallMobile, null); Spacer(Modifier.width(8.dp)); Text("Install update")
                        }
                    }
                    updateError?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 11.sp) }
                    statusMessage?.let { Text(it, color = SettingsSilver, fontSize = 11.sp) }
                    Text("Downloads can be paused and resumed. Android will verify the app signature when you install an update.", color = SettingsMuted, fontSize = 10.sp)
                }
            }
            item {
                SettingsSection("PERSONALISATION & ACCESSIBILITY", "Make the app comfortable to use")
                SettingsCard {
                    SettingsInfoRow("Personalised recommendations", "Saved as a future discovery preference; personalised ranking is not yet connected to the catalogue.")
                    SettingsDivider()
                    SettingsSwitch("Reduce motion", "Reduce decorative movement and transitions", reduceMotion) {
                        reduceMotion = it; settingsStore.reduceMotion = it
                    }
                    SettingsSwitch("Startup sound", "Play the HTE branded intro audio at launch", startupSound) {
                        startupSound = it; settingsStore.startupSoundEnabled = it
                    }
                    SettingsSwitch("Notifications", "Show account and booking updates", notifications) { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notifications = enabled; settingsStore.notificationsEnabled = enabled; onSettingsChanged()
                        }
                    }
                    Text("HTE Universe keeps its signature dark visual identity. More appearance controls can be added without compromising the brand.", color = SettingsMuted, fontSize = 10.sp)
                }
            }
            item {
                SettingsSection("STORAGE, PRIVACY & SUPPORT", "Manage local app data safely")
                SettingsCard {
                    Text("Temporary cache", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Clear temporary cached files without deleting your downloaded music or account.", color = SettingsMuted, fontSize = 11.sp)
                    OutlinedButton(onClick = {
                        val updatesDir = File(context.cacheDir, "updates")
                        context.cacheDir.listFiles()?.filter { it != updatesDir }?.forEach { it.deleteRecursively() }
                        statusMessage = "Temporary cache cleared."
                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.CleaningServices, null); Spacer(Modifier.width(8.dp)); Text("Clear temporary cache") }
                    SettingsDivider()
                    Text("Clear app data", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Resets local preferences and signs you out. Downloaded media may also be removed by Android.", color = SettingsMuted, fontSize = 11.sp)
                    OutlinedButton(onClick = { showClearData = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A80))) {
                        Icon(Icons.Outlined.DeleteForever, null); Spacer(Modifier.width(8.dp)); Text("Clear application data")
                    }
                    SettingsDivider()
                    SettingsInfoRow("Privacy & security", "Account and permission controls remain in your account and Android settings.")
                    SettingsInfoRow("AI & voice assistant", "Voice controls are available from the main navigation.")
                    SettingsInfoRow("Artist & Founder tools", "Publishing permissions are controlled by your verified account role.")
                    SettingsInfoRow("Help & support", "Contact High-Tech Entertainment for assistance.")
                    Text("HTE Universe · The Pride of Africa", color = SettingsGold, fontSize = 10.sp, letterSpacing = 1.2.sp)
                }
            }
        }
    }

    if (showClearDownloads) {
        AlertDialog(
            onDismissRequest = { showClearDownloads = false },
            title = { Text("Delete all downloads?") },
            text = { Text("This removes the downloaded audio and video files registered by HTE Universe from this device. It does not delete your online account or catalogue.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { downloadManager.clearAll() }
                        refreshDownloads()
                        showClearDownloads = false
                        statusMessage = "All HTE downloads were removed from this device."
                    }
                }) { Text("Delete downloads", color = Color(0xFFFF8A80)) }
            },
            dismissButton = { TextButton(onClick = { showClearDownloads = false }) { Text("Cancel") } }
        )
    }
    if (showClearData) {
        AlertDialog(
            onDismissRequest = { showClearData = false },
            title = { Text("Clear all application data?") },
            text = { Text("This is destructive. It resets HTE Universe local data and signs you out. You will need to sign in again. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    showClearData = false
                    val activityManager = context.getSystemService(ActivityManager::class.java)
                    val accepted = runCatching { activityManager.clearApplicationUserData() }.getOrDefault(false)
                    if (!accepted) statusMessage = "Android did not allow the app to clear its data. Use Android Settings > Apps > HTE Universe > Storage."
                }) { Text("Clear data", color = Color(0xFFFF8A80)) }
            },
            dismissButton = { TextButton(onClick = { showClearData = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsHero() {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SettingsPanel)) {
        Box(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2A2111), SettingsPanel, Color(0xFF0D1015)))).padding(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("PERSONAL CONTROL SYSTEM", color = SettingsGold, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text("Your sound. Your universe.", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("A unified control centre for playback, downloads, updates and your HTE experience.", color = SettingsSilver, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, subtitle: String) {
    Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Text(title, color = SettingsGold, fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = SettingsMuted, fontSize = 10.sp)
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = SettingsPanel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SettingsSwitch(title: String, subtitle: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = SettingsMuted, fontSize = 10.sp)
        }
        Switch(checked = checked, onCheckedChange = onChanged, colors = SwitchDefaults.colors(checkedThumbColor = SettingsBackground, checkedTrackColor = SettingsGold))
    }
}

@Composable
private fun SettingsChoice(title: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        SettingsChips(options, selected, onSelect)
    }
}

@Composable
private fun SettingsChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        options.forEach { option ->
            val label = option.replace("_", " ").replace("WIFI", "Wi-Fi")
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label, fontSize = 9.sp, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SettingsGold.copy(alpha = .18f), selectedLabelColor = SettingsGold)
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = SettingsSilver, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(description, color = SettingsMuted, fontSize = 10.sp)
    }
}

@Composable
private fun SettingsDivider() { HorizontalDivider(color = Color.White.copy(alpha = .07f)) }

private fun formatBytes(value: Long): String {
    if (value < 1024) return "$value B"
    val kb = value / 1024.0
    if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb)
    return String.format(Locale.ROOT, "%.1f MB", kb / 1024.0)
}

private fun openUpdateInstaller(context: android.content.Context, file: File) {
    if (!context.packageManager.canRequestPackageInstalls()) {
        val permissionIntent = Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
        permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(permissionIntent)
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
