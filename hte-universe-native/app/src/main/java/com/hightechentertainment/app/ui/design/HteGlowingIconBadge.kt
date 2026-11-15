package com.hightechentertainment.app.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Selected-state HTE halo; the icon itself remains a real accessible vector asset. */
@Composable
fun HteGlowingIconBadge(
    icon: ImageVector,
    selected: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val tint by animateColorAsState(
        targetValue = if (selected) HteColors.Gold else HteColors.Muted,
        label = "hte-navigation-icon-tint"
    )
    Box(
        modifier = modifier
            .size(34.dp)
            .shadow(if (selected) 9.dp else 0.dp, CircleShape, ambientColor = HteColors.Gold.copy(alpha = .42f), spotColor = HteColors.Gold.copy(alpha = .42f))
            .background(if (selected) HteColors.Gold.copy(alpha = .12f) else Color.Transparent, CircleShape)
            .border(1.dp, if (selected) HteColors.Gold.copy(alpha = .42f) else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(21.dp))
    }
}
