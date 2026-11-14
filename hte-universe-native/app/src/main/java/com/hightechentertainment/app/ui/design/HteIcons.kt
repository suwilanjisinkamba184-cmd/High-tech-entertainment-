package com.hightechentertainment.app.ui.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Central HTE navigation icon catalogue. Keep all navigation glyph choices here
 * so phone and tablet navigation remain visually consistent.
 */
object HteIcons {
    val Home: ImageVector = Icons.Outlined.Home
    val Discover: ImageVector = Icons.Outlined.Explore
    val Cinema: ImageVector = Icons.Outlined.VideoLibrary
    val Library: ImageVector = Icons.Outlined.LibraryMusic
    val Account: ImageVector = Icons.Outlined.Person

    fun navigation(label: String): ImageVector = when (label) {
        "Home" -> Home
        "Discover" -> Discover
        "Videos" -> Cinema
        "Library" -> Library
        "Account" -> Account
        else -> Home
    }
}
