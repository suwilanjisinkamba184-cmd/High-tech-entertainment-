package com.hightechentertainment.app.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.hightechentertainment.app.ui.design.HteColors
import com.hightechentertainment.app.ui.design.LocalHteReduceMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

data class PlayerTrack(val id: String, val title: String, val artist: String, val artworkUrl: String?, val accent: Color = HteColors.Gold)
enum class HteRepeatMode { OFF, ALL, ONE }
data class PlayerUiState(
    val track: PlayerTrack? = null, val isPlaying: Boolean = false, val isBuffering: Boolean = false,
    val positionMs: Long = 0L, val durationMs: Long = 0L, val shuffle: Boolean = false,
    val repeat: HteRepeatMode = HteRepeatMode.OFF, val amplitudes: List<Float>? = null,
)
interface PlayerController {
    val state: StateFlow<PlayerUiState>
    fun togglePlay()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
}

/** Main-thread adapter over the app-scoped Media3 controller. release() also stops its polling job. */
class Media3PlayerController(private val player: Player, scope: CoroutineScope) : PlayerController {
    private var waveform: Pair<String, List<Float>>? = null
    private val _state = MutableStateFlow(snapshot())
    override val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private var pollJob: Job? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { _state.value = snapshot() }
    }
    init {
        player.addListener(listener)
        pollJob = scope.launch {
            while (isActive) {
                if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING) _state.value = snapshot()
                delay(250)
            }
        }
    }
    fun setWaveform(trackId: String, amplitudes: List<Float>) {
        waveform = trackId to amplitudes.map { it.coerceIn(0f, 1f) }
        _state.value = snapshot()
    }
    fun release() {
        pollJob?.cancel()
        pollJob = null
        player.removeListener(listener)
    }
    private fun snapshot(): PlayerUiState {
        val item = player.currentMediaItem
        val meta = item?.mediaMetadata
        return PlayerUiState(
            track = item?.let { PlayerTrack(it.mediaId.ifBlank { meta?.title?.toString().orEmpty() }, meta?.title?.toString() ?: "Unknown title", meta?.artist?.toString() ?: "High-Tech Entertainment", meta?.artworkUri?.toString()) },
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L,
            shuffle = player.shuffleModeEnabled,
            repeat = when (player.repeatMode) { Player.REPEAT_MODE_ONE -> HteRepeatMode.ONE; Player.REPEAT_MODE_ALL -> HteRepeatMode.ALL; else -> HteRepeatMode.OFF },
            amplitudes = waveform?.takeIf { it.first == item?.mediaId }?.second,
        )
    }
    override fun togglePlay() { if (player.isPlaying) player.pause() else player.play() }
    override fun next() { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
    override fun previous() { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() }
    override fun seekTo(positionMs: Long) { player.seekTo(positionMs.coerceAtLeast(0L)) }
    override fun toggleShuffle() { player.shuffleModeEnabled = !player.shuffleModeEnabled }
    override fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
    }
}

private val MiniHeight = 68.dp

@Composable
fun PlayerHost(controller: PlayerController, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, collapsedBottomInset: Dp = 0.dp, artwork: @Composable (String?, Modifier) -> Unit) {
    val state by controller.state.collectAsState()
    val track = state.track ?: return
    val reduce = LocalHteReduceMotion.current
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    val fraction by animateFloatAsState(if (expanded) 1f else 0f, if (reduce) snap() else spring(dampingRatio = .9f), label = "hte-expand")
    val accent by animateColorAsState(track.accent, label = "hte-accent")
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val height = lerp(MiniHeight, maxHeight, fraction)
        val bottom = lerp(collapsedBottomInset, 0.dp, fraction)
        val side = lerp(8.dp, 0.dp, fraction)
        val surfaceTop = lerp(HteColors.PanelRaised, lerp(HteColors.Obsidian, accent, .42f), fraction)
        Box(Modifier.padding(start = side, end = side, bottom = bottom).fillMaxWidth().height(height)
            .clip(RoundedCornerShape(lerp(18.dp, 0.dp, fraction)))
            .background(Brush.verticalGradient(listOf(surfaceTop, HteColors.Obsidian)))) {
            val miniAlpha = (1f - fraction * 2.2f).coerceIn(0f, 1f)
            val fullAlpha = ((fraction - .45f) / .55f).coerceIn(0f, 1f)
            if (miniAlpha > 0f) MiniPlayer(state, track, controller, { onExpandedChange(true) }, artwork, Modifier.graphicsLayer { alpha = miniAlpha })
            if (fullAlpha > 0f) FullPlayer(state, track, controller, { onExpandedChange(false) }, artwork, Modifier.graphicsLayer { alpha = fullAlpha })
        }
    }
}

@Composable
private fun MiniPlayer(state: PlayerUiState, track: PlayerTrack, controller: PlayerController, onExpand: () -> Unit, artwork: @Composable (String?, Modifier) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    var drag by remember { mutableFloatStateOf(0f) }
    val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    Column(modifier.fillMaxWidth().height(MiniHeight).clickable(onClickLabel = "Open player", onClick = onExpand).pointerInput(Unit) {
        detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = { if (drag < -96f) controller.next() else if (drag > 96f) controller.previous(); drag = 0f }, onDragCancel = { drag = 0f }) { _, dx -> drag += dx }
    }) {
        Row(Modifier.weight(1f).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            artwork(track.artworkUrl, Modifier.size(46.dp).clip(RoundedCornerShape(9.dp)).background(HteColors.Panel))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = HteColors.Silver, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); controller.togglePlay() }) {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play", tint = HteColors.OnDark)
            }
        }
        LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(2.dp), color = HteColors.Gold, trackColor = Color.Transparent)
    }
}

@Composable
private fun FullPlayer(state: PlayerUiState, track: PlayerTrack, controller: PlayerController, onCollapse: () -> Unit, artwork: @Composable (String?, Modifier) -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalHteReduceMotion.current
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (state.isPlaying || reduce) 1f else .94f, if (reduce) snap() else spring(stiffness = Spring.StiffnessLow), label = "hte-art-scale")
    val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    val bars = state.amplitudes ?: remember(track.id) { placeholderWaveform(track.id) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val artworkMaxHeight = maxHeight * .40f
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = HteColors.OnDark, modifier = Modifier.size(32.dp)) }
            Text("NOW PLAYING", style = MaterialTheme.typography.labelLarge, color = HteColors.Silver, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.size(48.dp))
        }
        Spacer(Modifier.weight(1f))
        artwork(track.artworkUrl, Modifier.fillMaxWidth().heightIn(max = artworkMaxHeight).aspectRatio(1f).graphicsLayer { scaleX = scale; scaleY = scale }.clip(MaterialTheme.shapes.large).background(HteColors.Panel))
        Spacer(Modifier.weight(1f))
        Text(track.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(track.artist, style = MaterialTheme.typography.bodyLarge, color = HteColors.Silver, maxLines = 1)
        Spacer(Modifier.height(18.dp))
        WaveformScrubber(progress, bars, HteColors.Gold, { if (state.durationMs > 0) controller.seekTo((it * state.durationMs).toLong()) }, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(state.positionMs), color = HteColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(formatTime(state.durationMs), color = HteColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = controller::toggleShuffle) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (state.shuffle) HteColors.Gold else HteColors.Muted) }
            IconButton(onClick = controller::previous, modifier = Modifier.size(54.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = HteColors.OnDark, modifier = Modifier.size(34.dp)) }
            Box(Modifier.size(70.dp).clip(CircleShape).background(HteColors.Gold).clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); controller.togglePlay() }, contentAlignment = Alignment.Center) {
                if (state.isBuffering) CircularProgressIndicator(Modifier.size(30.dp), color = HteColors.Obsidian, strokeWidth = 3.dp)
                else Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play", tint = HteColors.Obsidian, modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = controller::next, modifier = Modifier.size(54.dp)) { Icon(Icons.Rounded.SkipNext, "Next", tint = HteColors.OnDark, modifier = Modifier.size(34.dp)) }
            IconButton(onClick = controller::cycleRepeat) { Icon(if (state.repeat == HteRepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat " + state.repeat.name.lowercase(), tint = if (state.repeat == HteRepeatMode.OFF) HteColors.Muted else HteColors.Gold) }
        }
        Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun WaveformScrubber(progress: Float, amplitudes: List<Float>, playedColor: Color, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = (dragging ?: progress).coerceIn(0f, 1f)
    Canvas(modifier.height(48.dp).pointerInput(Unit) { detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) } }
        .pointerInput(Unit) { detectHorizontalDragGestures(onDragStart = { dragging = it.x / size.width }, onDragEnd = { dragging?.let(onSeek); dragging = null }, onDragCancel = { dragging = null }) { change, _ -> dragging = (change.position.x / size.width).coerceIn(0f, 1f) } }
        .semantics { contentDescription = "Seek bar"; progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f); setProgress { onSeek(it.coerceIn(0f, 1f)); true } }) {
        if (amplitudes.isEmpty()) return@Canvas
        val bar = 3.dp.toPx(); val gap = 2.dp.toPx(); val count = (size.width / (bar + gap)).toInt().coerceAtLeast(1)
        repeat(count) { i ->
            val amp = amplitudes[(i * amplitudes.size / count).coerceIn(0, amplitudes.lastIndex)]
            val h = (amp * size.height).coerceAtLeast(4.dp.toPx())
            drawRoundRect(if (i / count.toFloat() <= shown) playedColor else Color.White.copy(alpha = .22f), Offset(i * (bar + gap), (size.height - h) / 2), Size(bar, h), CornerRadius(bar / 2))
        }
    }
}
fun placeholderWaveform(seed: String, bars: Int = 72): List<Float> {
    val random = Random(seed.hashCode()); var last = .5f
    return List(bars) { last = (last * .6f + random.nextFloat() * .4f + .05f).coerceIn(.12f, 1f); last }
}
private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}
