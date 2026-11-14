from pathlib import Path
import xml.etree.ElementTree as ET

def replace_once(source, old, new, label):
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}; refusing unsafe patch")
    return source.replace(old, new, 1)

root = Path(".")
settings_path = root / "hte-universe-native/app/src/main/java/com/hightechentertainment/app/SettingsHub.kt"
member_path = root / "hte-universe-native/app/src/main/java/com/hightechentertainment/app/MemberCenter.kt"
player_path = root / "hte-universe-native/app/src/main/java/com/hightechentertainment/app/player/HtePlayer.kt"
shell_path = root / "hte-universe-native/app/src/main/java/com/hightechentertainment/app/UniverseShell.kt"
gradle_path = root / "hte-universe-native/app/build.gradle.kts"
icon_path = root / "hte-universe-native/app/src/main/res/drawable/ic_google_g.xml"

S = settings_path.read_text()
S = S.replace("import androidx.compose.foundation.horizontalScroll\n", "")
S = S.replace("import androidx.compose.foundation.rememberScrollState\n", "")
S = replace_once(S, "import androidx.compose.foundation.layout.*\n", "import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.layout.ExperimentalLayoutApi\nimport androidx.compose.foundation.layout.FlowRow\n", "settings FlowRow imports")
S = replace_once(S, 'Text(title, color = SettingsGold, fontSize = 10.sp, letterSpacing = 1.7.sp, fontWeight = FontWeight.Bold)', 'Text(title, color = SettingsGold, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)', "settings section title")
S = replace_once(S, 'Text(subtitle, color = SettingsMuted, fontSize = 10.sp)', 'Text(subtitle, color = SettingsMuted, fontSize = 12.sp)', "settings section subtitle")
S = replace_once(S, 'Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)', 'Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)', "settings switch title")
S = replace_once(S, 'Text(subtitle, color = SettingsMuted, fontSize = 10.sp)', 'Text(subtitle, color = SettingsMuted, fontSize = 12.sp)', "settings switch subtitle")
S = replace_once(S, 'Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)', 'Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)', "settings choice title")
old_chips = '''@Composable
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
}'''
new_chips = '''@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val label = when (option) {
                "WIFI_ONLY" -> "Wi-Fi only"
                "WIFI + MOBILE", "WIFI_AND_MOBILE" -> "Wi-Fi + mobile"
                else -> option.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
            }
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label, fontSize = 12.sp, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SettingsGold.copy(alpha = .18f),
                    selectedLabelColor = SettingsGold
                )
            )
        }
    }
}'''
S = replace_once(S, old_chips, new_chips, "settings chips")
settings_path.write_text(S)

M = member_path.read_text()
M = replace_once(M, "import androidx.compose.ui.graphics.Color\n", "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.res.painterResource\n", "Google icon painter import")
old_google = '''                    }, enabled = !isWorking, modifier = Modifier.fillMaxWidth()) { Text("Continue with Google") }'''
new_google = '''                    }, enabled = !isWorking, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(painter = painterResource(R.drawable.ic_google_g), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                        }
                    }'''
M = replace_once(M, old_google, new_google, "Google sign-in button")
member_path.write_text(M)

google_vector = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#4285F4" android:pathData="M22.56,12.25c0,-0.78 -0.07,-1.53 -0.2,-2.25H12v4.26h5.92c-0.26,1.37 -1.04,2.53 -2.21,3.31v2.77h3.57c2.08,-1.92 3.28,-4.74 3.28,-8.09z" />
    <path android:fillColor="#34A853" android:pathData="M12,23c2.97,0 5.46,-0.98 7.28,-2.66l-3.57,-2.77c-0.98,0.66 -2.23,1.06 -3.71,1.06 -2.86,0 -5.29,-1.93 -6.16,-4.53H2.15v2.84A11,11 0,0 0,12 23z" />
    <path android:fillColor="#FBBC05" android:pathData="M5.84,14.1A6.6,6.6 0,0 1,5.5 12c0,-0.73 0.13,-1.43 0.34,-2.1V7.06H2.15A11,11 0,0 0,1 12c0,1.78 0.43,3.46 1.15,4.94l3.69,-2.84z" />
    <path android:fillColor="#EA4335" android:pathData="M12,5.38c1.62,0 3.06,0.56 4.21,1.64l3.15,-3.15C17.45,2.09 14.97,1 12,1A11,11 0,0 0,2.15 7.06l3.69,2.84C6.71,7.31 9.14,5.38 12,5.38z" />
</vector>
'''
icon_path.parent.mkdir(parents=True, exist_ok=True)
icon_path.write_text(google_vector)
ET.parse(icon_path)

P = player_path.read_text()
start = P.index("@Composable\nprivate fun FullPlayer(")
end = P.index("\n\n@Composable\nfun WaveformScrubber", start)
new_full_player = '''@Composable
private fun FullPlayer(state: PlayerUiState, track: PlayerTrack, controller: PlayerController, onCollapse: () -> Unit, artwork: @Composable (String?, Modifier) -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalHteReduceMotion.current
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (state.isPlaying || reduce) 1f else .97f, if (reduce) snap() else spring(stiffness = Spring.StiffnessLow), label = "hte-art-scale")
    val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val bars = state.amplitudes ?: remember(track.id) { placeholderWaveform(track.id) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < 680.dp
        val artworkSize = minOf(maxWidth * .82f, maxHeight * if (compact) .28f else .38f).coerceAtMost(340.dp)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 20.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = HteColors.OnDark, modifier = Modifier.size(30.dp)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW PLAYING", style = MaterialTheme.typography.labelLarge, color = HteColors.Silver, letterSpacing = 1.6.sp)
                    Text("HIGH-TECH ENTERTAINMENT", style = MaterialTheme.typography.labelSmall, color = HteColors.Muted, letterSpacing = 1.sp)
                }
                Spacer(Modifier.size(48.dp))
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                artwork(track.artworkUrl, Modifier.size(artworkSize).graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(MaterialTheme.shapes.large).background(HteColors.Panel))
            }
            Text(track.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodyLarge, color = HteColors.Silver, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
            WaveformScrubber(progress, bars, HteColors.Gold, { if (state.durationMs > 0) controller.seekTo((it * state.durationMs).toLong()) }, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(state.positionMs), color = HteColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("-${formatTime((state.durationMs - state.positionMs).coerceAtLeast(0L))}", color = HteColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = controller::toggleShuffle, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (state.shuffle) HteColors.Gold else HteColors.Muted) }
                IconButton(onClick = controller::previous, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = HteColors.OnDark, modifier = Modifier.size(32.dp)) }
                Box(Modifier.size(if (compact) 62.dp else 70.dp).clip(CircleShape).background(HteColors.Gold).clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); controller.togglePlay() }, contentAlignment = Alignment.Center) {
                    if (state.isBuffering) CircularProgressIndicator(Modifier.size(28.dp), color = HteColors.Obsidian, strokeWidth = 3.dp)
                    else Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play", tint = HteColors.Obsidian, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = controller::next, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.SkipNext, "Next", tint = HteColors.OnDark, modifier = Modifier.size(32.dp)) }
                IconButton(onClick = controller::cycleRepeat, modifier = Modifier.size(48.dp)) { Icon(if (state.repeat == HteRepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat ${state.repeat.name.lowercase()}", tint = if (state.repeat == HteRepeatMode.OFF) HteColors.Muted else HteColors.Gold) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}'''
P = P[:start] + new_full_player + P[end:]
player_path.write_text(P)

U = shell_path.read_text()
old_state = '''    var playbackError by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(false) }'''
new_state = '''    var playbackError by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
            isPlaying = player.isPlaying
            delay(400)
        }
    }'''
U = replace_once(U, old_state, new_state, "video player state")
old_listener = '''            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) playbackError = null
            }'''
new_listener = '''            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) playbackError = null
                durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }'''
U = replace_once(U, old_listener, new_listener, "video player listener")
old_view = '''                            useController = true
                            controllerAutoShow = true
                            controllerHideOnTouch = true
                            controllerShowTimeoutMs = 3500'''
U = replace_once(U, old_view, '''                            useController = false''', "disable stock video controls")
old_controls = '''                if (isBuffering) LinearProgressIndicator(Modifier.fillMaxWidth(), color = ShellGold)
                playbackError?.let { message ->'''
new_controls = '''                if (validUrl) {
                    Column(Modifier.fillMaxWidth().background(ShellPanel, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatVideoTime(positionMs), color = ShellSilver, fontSize = 11.sp)
                            Slider(
                                value = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                                onValueChange = { fraction -> if (durationMs > 0) { positionMs = (fraction * durationMs).toLong(); player.seekTo(positionMs) } },
                                enabled = durationMs > 0,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(thumbColor = ShellGold, activeTrackColor = ShellGold)
                            )
                            Text(formatVideoTime(durationMs), color = ShellSilver, fontSize = 11.sp)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, modifier = Modifier.size(48.dp)) {
                                Icon(if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (isPlaying) "Pause video" else "Play video", tint = ShellGold, modifier = Modifier.size(30.dp))
                            }
                            Text(if (isPlaying) "Playing" else "Paused", color = ShellSilver, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { player.seekTo(0); player.play(); playbackError = null }) { Text("Restart", color = ShellGold) }
                        }
                    }
                }
                if (isBuffering) LinearProgressIndicator(Modifier.fillMaxWidth(), color = ShellGold)
                playbackError?.let { message ->'''
U = replace_once(U, old_controls, new_controls, "video Compose controls")
U += '''

private fun formatVideoTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return String.format(java.util.Locale.US, "%d:%02d", seconds / 60L, seconds % 60L)
}
'''
shell_path.write_text(U)

G = gradle_path.read_text()
G = replace_once(G, "versionCode = 34", "versionCode = 35", "version code")
G = replace_once(G, 'versionName = "4.0.2"', 'versionName = "4.0.3"', "version name")
gradle_path.write_text(G)

for path in [settings_path, member_path, player_path, shell_path]:
    source = path.read_text()
    if source.count("{") != source.count("}"):
        raise SystemExit(f"Unbalanced Kotlin braces in {path}")
ET.parse(icon_path)
assert "FlowRow(" in S and "horizontalScroll" not in S
assert "R.drawable.ic_google_g" in M
assert "useController = false" in U and "formatVideoTime" in U
assert "BoxWithConstraints" in P
print("Applied Compose UI patch and static preflight checks.")
print("Changed: SettingsHub, MemberCenter, UniverseShell, HtePlayer, Google vector, app version 4.0.3 (35).")
