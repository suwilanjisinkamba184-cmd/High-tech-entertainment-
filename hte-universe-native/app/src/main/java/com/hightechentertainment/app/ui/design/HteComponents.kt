package com.hightechentertainment.app.ui.design

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class HteButtonStyle { Primary, Secondary }

@Composable
fun HteButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: HteButtonStyle = HteButtonStyle.Primary, enabled: Boolean = true, icon: ImageVector? = null) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
    if (style == HteButtonStyle.Primary) Button(
        onClick, modifier.heightIn(min = 48.dp), enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = HteColors.Gold, contentColor = HteColors.Obsidian), content = content
    ) else OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, border = BorderStroke(1.dp, HteColors.Hairline), content = content)
}

@Composable
fun HteChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected, onClick = onClick, modifier = modifier,
        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = HteColors.PanelRaised, labelColor = HteColors.Silver,
            selectedContainerColor = HteColors.Gold.copy(alpha = .2f), selectedLabelColor = HteColors.Gold
        ),
        border = FilterChipDefaults.filterChipBorder(true, selected, borderColor = HteColors.Hairline, selectedBorderColor = HteColors.Gold.copy(alpha = .6f))
    )
}

@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.small) {
    val reduce = LocalHteReduceMotion.current
    val alpha = rememberInfiniteTransition(label = "hte-skeleton").animateFloat(
        .35f, .7f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "hte-alpha"
    ).value
    Box(modifier.clip(shape).background(HteColors.PanelRaised.copy(alpha = if (reduce) .5f else alpha)))
}

@Composable
fun HteCover(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 148.dp, round: Boolean = false, artwork: @Composable (Modifier) -> Unit) {
    Column(modifier.width(size).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        artwork(Modifier.size(size).clip(if (round) CircleShape else MaterialTheme.shapes.medium).background(HteColors.PanelRaised))
        Column(Modifier.padding(horizontal = 2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HteColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun HteShelf(title: String, modifier: Modifier = Modifier, onSeeAll: (() -> Unit)? = null, content: LazyListScope.() -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (onSeeAll != null) TextButton(onClick = onSeeAll) { Text("See all", color = HteColors.Gold) }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
fun HteShelfSkeleton(modifier: Modifier = Modifier, count: Int = 4, size: Dp = 148.dp) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonBox(Modifier.padding(horizontal = 20.dp).size(160.dp, 24.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), userScrollEnabled = false) {
            items(count) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBox(Modifier.size(size))
                    SkeletonBox(Modifier.size(size * .8f, 14.dp))
                    SkeletonBox(Modifier.size(size * .5f, 12.dp))
                }
            }
        }
    }
}

@Composable
private fun HteStateMessage(icon: ImageVector, tint: Color, title: String, message: String, actionLabel: String?, onAction: (() -> Unit)?, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = HteColors.Muted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) HteButton(actionLabel, onAction)
    }
}
@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) =
    HteStateMessage(Icons.Rounded.MusicNote, HteColors.Gold, title, message, actionLabel, onAction, modifier)
@Composable
fun ErrorState(title: String, message: String, modifier: Modifier = Modifier, actionLabel: String? = "Try again", onAction: (() -> Unit)? = null) =
    HteStateMessage(Icons.Rounded.CloudOff, HteColors.Danger, title, message, actionLabel, onAction, modifier)
