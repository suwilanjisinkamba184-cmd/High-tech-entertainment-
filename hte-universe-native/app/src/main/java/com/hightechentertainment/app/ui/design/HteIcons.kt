package com.hightechentertainment.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Original HTE navigation glyphs. The five icons share a 24dp optical grid,
 * rounded 1.8dp strokes and a single tintable vector palette.
 */
object HteIcons {
    val Home: ImageVector by lazy {
        ImageVector.Builder("HteHomePortal", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(3f, 10f); lineTo(12f, 3f); lineTo(21f, 10f)
                moveTo(5.5f, 9f); lineTo(5.5f, 20f); lineTo(18.5f, 20f); lineTo(18.5f, 9f)
                moveTo(9.5f, 20f); lineTo(9.5f, 14f); lineTo(14.5f, 14f); lineTo(14.5f, 20f)
                moveTo(2.5f, 12f); lineTo(1.5f, 12f); moveTo(22.5f, 12f); lineTo(21.5f, 12f)
            }
        }.build()
    }
    val Discover: ImageVector by lazy {
        ImageVector.Builder("HteDiscoverRadar", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 2.5f); curveTo(17.25f, 2.5f, 21.5f, 6.75f, 21.5f, 12f)
                curveTo(21.5f, 17.25f, 17.25f, 21.5f, 12f, 21.5f)
                curveTo(6.75f, 21.5f, 2.5f, 17.25f, 2.5f, 12f)
                curveTo(2.5f, 6.75f, 6.75f, 2.5f, 12f, 2.5f)
                moveTo(12f, 6f); curveTo(15.3f, 6f, 18f, 8.7f, 18f, 12f)
                curveTo(18f, 15.3f, 15.3f, 18f, 12f, 18f)
                moveTo(12f, 2.5f); lineTo(12f, 12f); lineTo(19.5f, 6.5f)
                moveTo(12f, 12f); lineTo(7.5f, 17f)
            }
            path(fill = SolidColor(Color.Black)) { moveTo(13.8f, 12f); curveTo(13.8f, 13f, 13f, 13.8f, 12f, 13.8f); curveTo(11f, 13.8f, 10.2f, 13f, 10.2f, 12f); curveTo(10.2f, 11f, 11f, 10.2f, 12f, 10.2f); curveTo(13f, 10.2f, 13.8f, 11f, 13.8f, 12f); close() }
        }.build()
    }
    val Cinema: ImageVector by lazy {
        ImageVector.Builder("HteCinemaStream", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(4f, 4.5f); lineTo(20f, 4.5f); curveTo(21.1f, 4.5f, 22f, 5.4f, 22f, 6.5f)
                lineTo(22f, 17.5f); curveTo(22f, 18.6f, 21.1f, 19.5f, 20f, 19.5f)
                lineTo(4f, 19.5f); curveTo(2.9f, 19.5f, 2f, 18.6f, 2f, 17.5f)
                lineTo(2f, 6.5f); curveTo(2f, 5.4f, 2.9f, 4.5f, 4f, 4.5f); close()
                moveTo(10f, 8f); lineTo(16f, 12f); lineTo(10f, 16f); close()
                moveTo(6f, 4.5f); lineTo(6f, 7f); moveTo(18f, 4.5f); lineTo(18f, 7f)
                moveTo(6f, 17f); lineTo(6f, 19.5f); moveTo(18f, 17f); lineTo(18f, 19.5f)
            }
        }.build()
    }
    val Library: ImageVector by lazy {
        ImageVector.Builder("HteWaveLibrary", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(3f, 5f); lineTo(7f, 3.5f); lineTo(7f, 20.5f); lineTo(3f, 19f); close()
                moveTo(10f, 4f); lineTo(14f, 4f); lineTo(14f, 20f); lineTo(10f, 20f); close()
                moveTo(17f, 5f); lineTo(21f, 6.5f); lineTo(21f, 18f); lineTo(17f, 19.5f); close()
                moveTo(4.5f, 8f); lineTo(5.5f, 8f); moveTo(11.5f, 8f); lineTo(12.5f, 8f)
                moveTo(18.5f, 9f); lineTo(19.5f, 9f)
            }
        }.build()
    }
    val Account: ImageVector by lazy {
        ImageVector.Builder("HteCreatorProfile", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
                moveTo(12f, 12f); curveTo(14.5f, 12f, 16.5f, 10f, 16.5f, 7.5f)
                curveTo(16.5f, 5f, 14.5f, 3f, 12f, 3f); curveTo(9.5f, 3f, 7.5f, 5f, 7.5f, 7.5f)
                curveTo(7.5f, 10f, 9.5f, 12f, 12f, 12f); close()
                moveTo(4f, 20.5f); curveTo(4.5f, 16.5f, 7.5f, 14.5f, 12f, 14.5f)
                curveTo(16.5f, 14.5f, 19.5f, 16.5f, 20f, 20.5f)
                moveTo(2.5f, 20.5f); lineTo(21.5f, 20.5f)
            }
        }.build()
    }

    fun navigation(label: String): ImageVector = when (label) {
        "Home" -> Home
        "Discover" -> Discover
        "Videos" -> Cinema
        "Library" -> Library
        "Account" -> Account
        else -> Home
    }
}
