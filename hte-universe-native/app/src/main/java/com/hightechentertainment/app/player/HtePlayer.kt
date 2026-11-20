package com.hightechentertainment.app.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

data class PlayerTrack(val id: String, val title: String, val artist: String, val artworkUrl: String?, val accent: Color = HteColors.Gold)
enum class HteRepeatMode { OFF, ALL, ONE }

internal fun shouldRestartEndedPlayback(playbackState: Int): Boolean = playbackState == Player.STATE_ENDED

data class PlayerQueueItem(val id: String, val title: String, val artist: String)
data class PlayerUiState(
    val track: PlayerTrack? = null, val isPlaying: Boolean = false, val isBuffering: Boolean = false,
    val positionMs: Long = 0L, val durationMs: Long = 0L, val shuffle: Boolean = false,
    val repeat: HteRepeatMode = HteRepeatMode.OFF,
    val playbackError: Boolean = false,
    val queue: List<PlayerQueueItem> = emptyList(),
    val currentQueueIndex: Int = -1,
)
interface PlayerController {
    val state: StateFlow<PlayerUiState>
    fun togglePlay()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun retry()
    fun toggleShuffle()
    fun cycleRepeat()
    fun playQueueItem(index: Int)
}

/** Main-thread adapter over the app-scoped Media3 controller. release() also stops its polling job. */
class Media3PlayerController(private val player: Player, scope: CoroutineScope) : PlayerController {
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
                // Half-second position updates keep scrubbing responsive without needlessly
                // waking/recomposing the UI four times per second on lower-memory phones.
                delay(500)
            }
        }
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
            playbackError = player.playerError != null,
            shuffle = player.shuffleModeEnabled,
            repeat = when (player.repeatMode) { Player.REPEAT_MODE_ONE -> HteRepeatMode.ONE; Player.REPEAT_MODE_ALL -> HteRepeatMode.ALL; else -> HteRepeatMode.OFF },
            queue = (0 until player.mediaItemCount).map { index ->
                val queuedItem = player.getMediaItemAt(index)
                val queuedMetadata = queuedItem.mediaMetadata
                PlayerQueueItem(queuedItem.mediaId.ifBlank { "queue-$index" }, queuedMetadata.title?.toString() ?: "Unknown title", queuedMetadata.artist?.toString() ?: "High-Tech Entertainment")
            },
            currentQueueIndex = player.currentMediaItemIndex,
        )
    }
    override fun togglePlay() {
        if (player.isPlaying) player.pause() else {
            if (shouldRestartEndedPlayback(player.playbackState)) player.seekToDefaultPosition()
            player.play()
        }
    }
    override fun next() { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
    override fun previous() { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() }
    override fun seekTo(positionMs: Long) { player.seekTo(positionMs.coerceAtLeast(0L)) }
    override fun retry() {
        if (player.currentMediaItem != null) {
            if (shouldRestartEndedPlayback(player.playbackState)) player.seekToDefaultPosition()
            player.prepare()
            player.play()
        }
    }
    override fun toggleShuffle() { player.shuffleModeEnabled = !player.shuffleModeEnabled }
    override fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
    }
    override fun playQueueItem(index: Int) {
        if (index in 0 until player.mediaItemCount) { player.seekToDefaultPosition(index); player.play() }
    }
}

private val MiniHeight = 68.dp

@Composable
fun HteStudioPlayerScreen(controller: PlayerController, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, collapsedBottomInset: Dp = 0.dp, immersive: Boolean = false, artwork: @Composable (String?, Modifier) -> Unit) {
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
            if (fullAlpha > 0f) FullPlayer(state, track, controller, { onExpandedChange(false) }, artwork, immersive, Modifier.graphicsLayer { alpha = fullAlpha })
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
                if (state.isBuffering) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = HteColors.Gold, strokeWidth = 2.dp)
                } else {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play", tint = HteColors.OnDark)
                }
            }
        }
        LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(2.dp), color = HteColors.Gold, trackColor = Color.Transparent)
    }
}

@Composable
private fun FullPlayer(state: PlayerUiState, track: PlayerTrack, controller: PlayerController, onCollapse: () -> Unit, artwork: @Composable (String?, Modifier) -> Unit, immersive: Boolean, modifier: Modifier = Modifier) {
    val reduce = LocalHteReduceMotion.current
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (state.isPlaying || reduce) 1f else .94f, if (reduce) snap() else spring(stiffness = Spring.StiffnessLow), label = "hte-art-scale")
    val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
    var showQueue by remember(track.id) { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Bound artwork by both available height and width so compact and wide screens stay balanced.
        val artworkMaxHeight = minOf(maxHeight * .26f, maxWidth * .68f)
        val horizontalInset = when {
            maxWidth < 360.dp -> 12.dp
            maxWidth < 600.dp -> 18.dp
            else -> 28.dp
        }
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .then(if (immersive) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
                .padding(horizontal = horizontalInset, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, "Close player", tint = HteColors.OnDark, modifier = Modifier.size(32.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("HTE STUDIO PLAYER", color = HteColors.Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Text("NOW PLAYING", style = MaterialTheme.typography.labelMedium, color = HteColors.Silver)
            }
            Surface(shape = RoundedCornerShape(50), color = HteColors.Gold.copy(alpha = .12f)) {
                Text("HTE AUDIO", color = HteColors.Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        artwork(track.artworkUrl, Modifier.fillMaxWidth().heightIn(max = artworkMaxHeight).aspectRatio(1f).graphicsLayer { scaleX = scale; scaleY = scale }.clip(MaterialTheme.shapes.large).background(HteColors.Panel))
        Spacer(Modifier.height(4.dp))
        Text(track.title, color = HteColors.OnDark, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(track.artist, style = MaterialTheme.typography.bodyLarge, color = HteColors.Silver, maxLines = 1)
        Text("NOW STREAMING  •  HIGH-TECH ENTERTAINMENT", color = HteColors.Gold.copy(alpha = .82f),
            fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        if (state.playbackError) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Playback interrupted. Check your connection and try again.",
                    color = HteColors.Muted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = controller::retry) { Text("Retry", color = HteColors.Gold) }
            }
        }
        var scrubPosition by remember(track.id) { mutableStateOf<Float?>(null) }
        Slider(
            value = (scrubPosition ?: progress).coerceIn(0f, 1f),
            onValueChange = { scrubPosition = it },
            onValueChangeFinished = {
                scrubPosition?.let { fraction ->
                    if (state.durationMs > 0L) controller.seekTo((fraction * state.durationMs).toLong())
                }
                scrubPosition = null
            },
            enabled = state.durationMs > 0L,
            colors = SliderDefaults.colors(
                thumbColor = HteColors.Gold,
                activeTrackColor = HteColors.Gold,
                inactiveTrackColor = Color.White.copy(alpha = .18f),
                disabledActiveTrackColor = HteColors.Muted
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(state.positionMs), color = HteColors.Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(formatTime(state.durationMs), color = HteColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = controller::toggleShuffle) { Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (state.shuffle) HteColors.Gold else HteColors.Muted) }
            IconButton(onClick = controller::previous, modifier = Modifier.size(50.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = HteColors.OnDark, modifier = Modifier.size(30.dp)) }
            Box(Modifier.size(64.dp).clip(CircleShape).background(HteColors.Gold).clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); controller.togglePlay() }, contentAlignment = Alignment.Center) {
                if (state.isBuffering) CircularProgressIndicator(Modifier.size(30.dp), color = HteColors.Obsidian, strokeWidth = 3.dp)
                else Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (state.isPlaying) "Pause" else "Play", tint = HteColors.Obsidian, modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = controller::next, modifier = Modifier.size(50.dp)) { Icon(Icons.Rounded.SkipNext, "Next", tint = HteColors.OnDark, modifier = Modifier.size(30.dp)) }
            IconButton(onClick = controller::cycleRepeat) { Icon(if (state.repeat == HteRepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat " + state.repeat.name.lowercase(), tint = if (state.repeat == HteRepeatMode.OFF) HteColors.Muted else HteColors.Gold) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("UP NEXT", color = HteColors.Silver, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { showQueue = !showQueue }) { Text(if (showQueue) "Hide queue" else "View queue · ${state.queue.size}", color = HteColors.Gold, fontSize = 11.sp) }
        }
        if (showQueue) {
            if (state.queue.isEmpty()) Text("There are no tracks in the current queue.", color = HteColors.Muted, style = MaterialTheme.typography.bodySmall)
            else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.queue.forEachIndexed { index, queuedTrack ->
                    val isCurrent = index == state.currentQueueIndex
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (isCurrent) HteColors.Gold.copy(alpha = .12f) else HteColors.Panel.copy(alpha = .72f)).clickable { controller.playQueueItem(index) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(queuedTrack.title, color = if (isCurrent) HteColors.Gold else HteColors.OnDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(queuedTrack.artist, color = HteColors.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(if (isCurrent) "PLAYING" else "${index + 1}", color = if (isCurrent) HteColors.Gold else HteColors.Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
}

@Composable
fun PlayerHost(controller: PlayerController, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, collapsedBottomInset: Dp = 0.dp, immersive: Boolean = false, artwork: @Composable (String?, Modifier) -> Unit) =
    HteStudioPlayerScreen(controller, expanded, onExpandedChange, modifier, collapsedBottomInset, immersive, artwork)

