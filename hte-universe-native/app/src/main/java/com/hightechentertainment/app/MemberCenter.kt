package com.hightechentertainment.app

import androidx.compose.foundation.layout.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hightechentertainment.app.data.AuthRepository
import com.hightechentertainment.app.data.BookingDto
import com.hightechentertainment.app.data.MemberRepository
import com.hightechentertainment.app.data.NotificationDto
import com.hightechentertainment.app.data.ProfileDto
import com.hightechentertainment.app.data.SupabaseClientProvider
import com.hightechentertainment.app.media.YouTubeLinkClassifier
import com.hightechentertainment.app.media.YouTubeSubmissionRoute
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

private val MemberGold = com.hightechentertainment.app.ui.design.HteColors.Gold
private val MemberPanel = com.hightechentertainment.app.ui.design.HteColors.Panel
private val MemberMuted = com.hightechentertainment.app.ui.design.HteColors.Muted
private const val MAX_PROFILE_IMAGE_BYTES = 5L * 1024L * 1024L

private suspend fun stageProfileImage(context: android.content.Context, uri: android.net.Uri): Pair<File, Long> =
    withTemporaryFileCleanup { registerFile ->
        withContext(Dispatchers.IO) {
            val file = File.createTempFile("hte-profile-", ".upload", context.cacheDir)
            registerFile(file)
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("The selected image could not be opened. Choose another image.")
                var total = 0L
                input.use { source ->
                    FileOutputStream(file).use { destination ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= MAX_PROFILE_IMAGE_BYTES) { "Choose an image file no larger than 5 MB." }
                            destination.write(buffer, 0, count)
                        }
                    }
                }
                require(total > 0L) { "The selected image is empty." }
                file to total
            } catch (error: Throwable) {
                file.delete()
                throw error
            }
        }
    }

private suspend fun <T> captureAccountRequest(request: suspend () -> T): Result<T> = try {
    Result.success(request())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

@Composable
fun MemberCenter(
    configured: Boolean,
    onSettingsChanged: () -> Unit,
    onVoiceAssistant: () -> Unit = {},
    onVideoSubmitted: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsStore = remember(context) { AppSettingsStore(context) }
    var notificationsEnabled by remember { mutableStateOf(settingsStore.notificationsEnabled) }
    var showFounderStudio by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var createAccount by remember { mutableStateOf(false) }
    var isSignedIn by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<ProfileDto?>(null) }
    var bookings by remember { mutableStateOf<List<BookingDto>>(emptyList()) }
    var notifications by remember { mutableStateOf<List<NotificationDto>>(emptyList()) }
    var role by remember { mutableStateOf("member") }
    var accountDataLoading by remember { mutableStateOf(false) }
    var accountDataError by remember { mutableStateOf<String?>(null) }
    var accountRefreshKey by remember { mutableIntStateOf(0) }
    var profileRevision by remember { mutableIntStateOf(0) }
    var showProfileEditor by remember { mutableStateOf(false) }
    var showBookingForm by remember { mutableStateOf(false) }
    var artistVideoUrl by remember { mutableStateOf("") }
    var artistVideoRightsConfirmed by remember { mutableStateOf(false) }
    var artistVideoSubmitting by remember { mutableStateOf(false) }
    var artistVideoStatus by remember { mutableStateOf<String?>(null) }
    var artistVideoSuccess by remember { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }
    val auth = remember(configured) { if (configured) runCatching { SupabaseClientProvider.client.auth }.getOrNull() else null }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            isWorking = true
            try {
                val mimeType = context.contentResolver.getType(uri).orEmpty()
                val (stagedFile, measuredSize) = stageProfileImage(context, uri)
                try {
                    val updatedProfile = stagedFile.inputStream().use { stream ->
                        MemberRepository().updateMyAvatar(stream, measuredSize, mimeType)
                    }
                    profileRevision++
                    profile = updatedProfile
                    statusMessage = "Profile photo updated."
                } finally {
                    stagedFile.delete()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                statusMessage = error.toHteUserMessage("Your profile photo could not be uploaded. Please retry.")
            } finally {
                isWorking = false
            }
        }
    }

    LaunchedEffect(auth) {
        if (auth != null) {
            isSignedIn = auth.currentUserOrNull() != null
            auth.sessionStatus.collect { isSignedIn = auth.currentUserOrNull() != null }
        }
    }

    LaunchedEffect(auth, isSignedIn, accountRefreshKey) {
        if (auth != null && isSignedIn) {
            accountDataLoading = true
            accountDataError = null
            try {
                val repo = MemberRepository()
                val profileRevisionAtStart = profileRevision
                val profileResult = captureAccountRequest { repo.getMyProfile() }
                val bookingsResult = captureAccountRequest { repo.getMyBookings() }
                val notificationsResult = captureAccountRequest { repo.getNotifications() }
                val roleResult = captureAccountRequest { repo.getCurrentRole() }
                profileResult.onSuccess { if (profileRevision == profileRevisionAtStart) profile = it }
                bookingsResult.onSuccess { bookings = it }
                notificationsResult.onSuccess { notifications = it }
                roleResult.onSuccess { role = it }
                val firstFailure = listOf(profileResult, bookingsResult, notificationsResult, roleResult)
                    .firstNotNullOfOrNull { it.exceptionOrNull() }
                accountDataError = firstFailure?.toHteUserMessage(
                    "Some account information could not be loaded. Please try again."
                )?.let { "Some account information could not be loaded. $it" }
                email = auth.currentUserOrNull()?.email.orEmpty()
            } finally {
                accountDataLoading = false
            }
        } else if (!isSignedIn) {
            profile = null
            bookings = emptyList()
            notifications = emptyList()
            role = "member"
            accountDataError = null
            accountDataLoading = false
        }
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MemberPanel), shape = RoundedCornerShape(22.dp)) {
                Box(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(com.hightechentertainment.app.ui.design.HteColors.PanelRaised, MemberPanel, com.hightechentertainment.app.ui.design.HteColors.Obsidian))).padding(18.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.AccountCircle, null, tint = MemberGold, modifier = Modifier.size(34.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("HTE UNIVERSE / MEMBER NODE", color = MemberGold, fontSize = 9.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                                Text("Your account", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(if (isSignedIn) "ONLINE" else "GUEST", color = if (isSignedIn) Color(0xFF8BCB9B) else MemberMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Manage your identity, bookings, notifications and founder access.", color = MemberMuted, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MemberMetric(if (isSignedIn) role.uppercase() else "GUEST", "ACCESS", Modifier.weight(1f))
                            MemberMetric(bookings.size.toString(), "BOOKINGS", Modifier.weight(1f))
                            MemberMetric(notifications.size.toString(), "ALERTS", Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if (accountDataLoading) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = MemberGold, strokeWidth = 2.dp)
                    Text("Syncing your account…", color = MemberMuted, fontSize = 12.sp)
                }
            }
        }
        accountDataError?.let { message ->
            item {
                MemberCard {
                    Text("ACCOUNT SYNC NEEDS ATTENTION", color = MemberGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(message, color = MemberMuted, fontSize = 12.sp)
                    OutlinedButton(onClick = { accountRefreshKey++ }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Retry account sync")
                    }
                }
            }
        }
        item {
            MemberCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.GraphicEq, null, tint = MemberGold, modifier = Modifier.size(27.dp))
                    Column(Modifier.weight(1f)) {
                        Text("HTE AI Voice Assistant", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Start a live voice session, then say ‘Hey HTE’ to play music, pause, find videos and request eligible downloads. Minimise the assistant while you browse.", color = MemberMuted, fontSize = 11.sp)
                    }
                }
                Button(
                    onClick = onVoiceAssistant,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MemberGold, contentColor = Color.Black)
                ) {
                    Icon(Icons.Outlined.Mic, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open HTE AI Voice Assistant")
                }
            }
        }
        if (!configured || auth == null) {
            item {
                MemberCard {
                    Icon(Icons.Outlined.Lock, null, tint = MemberGold, modifier = Modifier.size(28.dp))
                    Text("Secure account connection required", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("This build needs the Supabase project URL and publishable key supplied through protected build configuration. The service-role key is never placed in the app.", color = MemberMuted, fontSize = 12.sp)
                }
            }
        } else if (!isSignedIn) {
            item {
                MemberCard {
                    Text(if (createAccount) "Create your HTE account" else "Welcome back", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Email address") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
                    OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                    Button(onClick = {
                        scope.launch {
                            isWorking = true
                            statusMessage = null
                            captureAccountRequest { if (createAccount) AuthRepository().signUp(email, password) else AuthRepository().signIn(email, password) }
                                .onSuccess {
                                    isSignedIn = auth.currentUserOrNull() != null
                                    statusMessage = if (createAccount && !isSignedIn) "Account created. Check your email if confirmation is required." else "Authentication request completed."
                                }
                                .onFailure { statusMessage = it.toHteUserMessage("Sign-in could not be completed. Please try again.") }
                            isWorking = false
                        }
                    }, enabled = !isWorking, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MemberGold, contentColor = Color.Black)) {
                        Text(if (isWorking) "Please wait…" else if (createAccount) "Create account" else "Sign in")
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            isWorking = true
                            captureAccountRequest { AuthRepository().signInWithGoogle(context) }
                                .onSuccess { native ->
                                    isSignedIn = auth.currentUserOrNull() != null
                                    statusMessage = if (native && isSignedIn) "Google sign-in successful. Welcome to HTE Universe." else if (native) "Google verification completed. Loading your HTE account…" else "Continue in the Google sign-in tab. HTE Universe will reopen automatically when sign-in completes."
                                }
                                .onFailure { statusMessage = it.toHteUserMessage("Google sign-in could not complete. Check Google Play services and try again.") }
                            isWorking = false
                        }
                    }, enabled = !isWorking, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GoogleBrandMark(Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    TextButton(onClick = { createAccount = !createAccount; statusMessage = null }) {
                        Text(if (createAccount) "Already registered? Sign in" else "New to HTE? Create an account", color = MemberGold)
                    }
                }
            }
        } else {
            item {
                MemberCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(64.dp).clip(CircleShape)
                                .background(MemberGold.copy(alpha = .12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val avatarUrl = profile?.avatarUrl?.takeIf(String::isNotBlank)
                            if (avatarUrl != null) {
                                AsyncImage(
                                    model = avatarUrl,
                                    contentDescription = "Account profile photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Icon(Icons.Outlined.AccountCircle, null, tint = MemberGold, modifier = Modifier.size(42.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile?.displayName?.takeIf { it.isNotBlank() } ?: "HTE Member", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(auth.currentUserOrNull()?.email ?: email, color = MemberMuted, fontSize = 12.sp)
                        }
                    }
                    Text("Account role: $role", color = MemberGold, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showProfileEditor = true }, modifier = Modifier.weight(1f)) { Text("Edit profile") }
                        Button(onClick = { showBookingForm = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MemberGold, contentColor = Color.Black)) { Text("Book a session") }
                    }
                    if (role.equals("owner", true)) {
                        Text("Founder account verified by Supabase role function.", color = MemberGold, fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            captureAccountRequest { AuthRepository().signOut() }
                                .onSuccess { isSignedIn = false; statusMessage = "You have signed out." }
                                .onFailure { statusMessage = "Sign-out failed. Please try again." }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Outlined.Logout, null); Spacer(Modifier.width(8.dp)); Text("Sign out") }
                }
            }
            if (role.equals("owner", true)) {
                item {
                    MemberCard {
                        Text("FOUNDER VIDEO PUBLISHING", color = MemberGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text("Submit your YouTube video", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Add a public YouTube video to the shared HTE catalogue. Only the verified founder account can publish it to the website and Android app.", color = MemberMuted, fontSize = 12.sp)
                        OutlinedTextField(value = artistVideoUrl, onValueChange = { artistVideoUrl = it; artistVideoStatus = null }, modifier = Modifier.fillMaxWidth(), label = { Text("Public YouTube video URL") }, placeholder = { Text("https://www.youtube.com/watch?v=...") }, singleLine = true, enabled = !artistVideoSubmitting)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = artistVideoRightsConfirmed, onCheckedChange = { artistVideoRightsConfirmed = it })
                            Text("I own this video or have permission from the rights holder to submit and share it on HTE.", color = MemberMuted, fontSize = 11.sp)
                        }
                        Button(onClick = {
                            scope.launch {
                                artistVideoSubmitting = true; artistVideoStatus = null; artistVideoSuccess = false
                                captureAccountRequest {
                                    require(artistVideoUrl.isNotBlank()) { "Enter a public YouTube video URL." }
                                    require(artistVideoRightsConfirmed) { "Confirm that you own or are authorised to submit this video." }
                                    val canonicalVideoUrl = when (val route = YouTubeLinkClassifier.classify(artistVideoUrl)) {
                                        is YouTubeSubmissionRoute.SingleVideo -> route.canonicalWatchUrl
                                        is YouTubeSubmissionRoute.Playlist -> throw IllegalArgumentException("Playlist links cannot be submitted as a single video. Paste an individual video link.")
                                        is YouTubeSubmissionRoute.Channel -> throw IllegalArgumentException("Channel links cannot be submitted as a single video. Paste an individual video link.")
                                        is YouTubeSubmissionRoute.Invalid -> throw IllegalArgumentException(route.userMessage)
                                    }
                                    SupabaseClientProvider.client.functions.invoke("submit-public-video", body = buildJsonObject {
                                        put("url", canonicalVideoUrl)
                                        put("rights_confirmed", true)
                                    })
                                }.onSuccess {
                                    artistVideoUrl = ""; artistVideoRightsConfirmed = false; artistVideoSuccess = true
                                    artistVideoStatus = "Your video has been published to the shared HTE catalogue."
                                    onVideoSubmitted()
                                }.onFailure { artistVideoStatus = it.toHteUserMessage("Video submission failed. Check the YouTube link and try again.") }
                                artistVideoSubmitting = false
                            }
                        }, enabled = !artistVideoSubmitting && artistVideoUrl.isNotBlank() && artistVideoRightsConfirmed, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MemberGold, contentColor = Color.Black)) {
                            if (artistVideoSubmitting) CircularProgressIndicator(Modifier.size(17.dp), color = Color.Black, strokeWidth = 2.dp) else Icon(Icons.Outlined.VideoLibrary, null)
                            Spacer(Modifier.width(8.dp)); Text(if (artistVideoSubmitting) "Verifying video…" else "Submit video to HTE")
                        }
                        artistVideoStatus?.let { Text(it, color = if (artistVideoSuccess) Color(0xFF8BCB9B) else MemberGold, fontSize = 11.sp) }
                        Text("Only public YouTube videos can be submitted. HTE embeds the original video; it does not copy or re-upload YouTube media.", color = MemberMuted, fontSize = 10.sp)
                    }
                }
            }
            if (role.equals("owner", true)) {
                item {
                    MemberCard {
                        Text("Founder Studio", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Upload and manage HTE music, videos and artwork. Access is verified by the server-side owner role.", color = MemberMuted, fontSize = 12.sp)
                        Button(onClick = { showFounderStudio = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MemberGold, contentColor = Color.Black)) {
                            Icon(Icons.Outlined.AdminPanelSettings, null); Spacer(Modifier.width(8.dp)); Text("Open Founder Studio")
                        }
                    }
                }
            }
            item { Text("BOOKING HISTORY", color = MemberGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold) }
            if (bookings.isEmpty() && accountDataError == null && !accountDataLoading) {
                item { Text("Your booking requests will appear here.", color = MemberMuted, fontSize = 12.sp) }
            } else if (bookings.isNotEmpty()) {
                items(bookings, key = { it.id }) { booking ->
                    MemberCard {
                        Text(booking.bookingType, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(booking.requestedDate ?: "Date to be confirmed", color = MemberMuted, fontSize = 12.sp)
                        Text("Status: ${booking.status}", color = MemberGold, fontSize = 11.sp)
                    }
                }
            }
            item { Text("NOTIFICATIONS", color = MemberGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold) }
            if (!notificationsEnabled) {
                item { Text("In-app notifications are disabled in settings.", color = MemberMuted, fontSize = 12.sp) }
            } else if (notifications.isEmpty() && accountDataError == null && !accountDataLoading) {
                item { Text("No account notifications yet.", color = MemberMuted, fontSize = 12.sp) }
            } else if (notifications.isNotEmpty()) {
                items(notifications, key = { it.id }) { notification ->
                    MemberCard {
                        Text(notification.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(notification.body, color = MemberMuted, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            MemberCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Tune, null, tint = MemberGold, modifier = Modifier.size(28.dp))
                    Column(Modifier.weight(1f)) {
                        Text("HTE Control Centre", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Advanced sound, updates, downloads and app controls.", color = MemberMuted, fontSize = 11.sp)
                    }
                }
                Button(onClick = { showSettings = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MemberGold, contentColor = Color.Black)) {
                    Icon(Icons.Outlined.Settings, null); Spacer(Modifier.width(8.dp)); Text("Open Settings")
                }
            }
        }
        statusMessage?.let { message -> item { Text(message, color = MemberGold, fontSize = 12.sp) } }
    }

    if (showSettings) {
        Dialog(onDismissRequest = { showSettings = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = com.hightechentertainment.app.ui.design.HteColors.Obsidian) {
                SettingsHub(
                    onClose = { showSettings = false },
                    settingsStore = settingsStore,
                    downloadManager = com.hightechentertainment.app.media.OfflineDownloadManager(context),
                    onSettingsChanged = { notificationsEnabled = settingsStore.notificationsEnabled; onSettingsChanged() },
                    onVoiceAssistant = {
                        showSettings = false
                        onVoiceAssistant()
                    }
                )
            }
        }
    }

    if (showProfileEditor && auth != null) {
        ProfileEditorDialog(profile = profile, accountId = auth.currentUserOrNull()?.id.orEmpty(), onDismiss = { showProfileEditor = false }, onPickAvatar = { avatarPicker.launch("image/*") }, onSave = { name, bio, phone ->
            scope.launch {
                isWorking = true
                try {
                    captureAccountRequest { MemberRepository().saveMyProfile(name, bio, phone) }
                        .onSuccess {
                            profileRevision++
                            statusMessage = "Profile saved."
                            showProfileEditor = false
                            captureAccountRequest { MemberRepository().getMyProfile() }
                                .onSuccess { refreshed -> profile = refreshed }
                                .onFailure { accountDataError = it.toHteUserMessage("Your profile was saved, but the refreshed details could not be loaded.") }
                        }
                        .onFailure { statusMessage = it.toHteUserMessage("Your profile could not be saved. Please retry.") }
                } finally {
                    isWorking = false
                }
            }
        }, isWorking = isWorking)
    }

    if (showBookingForm && auth != null) {
        BookingRequestDialog(
            initialName = profile?.displayName.orEmpty(),
            initialEmail = auth.currentUserOrNull()?.email.orEmpty(),
            onDismiss = { showBookingForm = false },
            onSubmit = { name, requestEmail, type, date, message ->
                scope.launch {
                    isWorking = true
                    try {
                        captureAccountRequest { MemberRepository().submitBooking(name, requestEmail, type, date, message) }
                            .onSuccess {
                                statusMessage = "Your booking request has been submitted."
                                showBookingForm = false
                                if (settingsStore.notificationsEnabled) {
                                    AppNotificationHelper.show(context, "Booking request received", "Your HTE booking request has been submitted.")
                                }
                                captureAccountRequest { MemberRepository().getMyBookings() }
                                    .onSuccess { bookings = it }
                                    .onFailure {
                                        accountDataError = it.toHteUserMessage(
                                            "Your booking was submitted, but booking history could not be refreshed. Tap Retry account sync."
                                        )
                                    }
                            }
                            .onFailure { statusMessage = it.toHteUserMessage("Your booking request could not be sent. Please retry.") }
                    } finally {
                        isWorking = false
                    }
                }
            }, isWorking = isWorking
        )
    }

    if (showFounderStudio) {
        Dialog(onDismissRequest = { showFounderStudio = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = com.hightechentertainment.app.ui.design.HteColors.Obsidian) {
                FounderStudioScreen(onClose = { showFounderStudio = false })
            }
        }
    }
}

@Composable
private fun MemberMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.White.copy(alpha = .035f), RoundedCornerShape(12.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = MemberMuted, fontSize = 8.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun MemberCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MemberPanel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun ProfileEditorDialog(profile: ProfileDto?, accountId: String, onDismiss: () -> Unit, onPickAvatar: () -> Unit, onSave: (String, String, String) -> Unit, isWorking: Boolean) {
    var draft by remember(accountId) {
        mutableStateOf(
            ProfileDraftState(
                name = profile?.displayName.orEmpty(),
                bio = profile?.bio.orEmpty(),
                phone = profile?.phone.orEmpty()
            )
        )
    }

    // Hydrate each field independently. Editing one field must never prevent
    // untouched fields from receiving the asynchronously loaded profile values.
    LaunchedEffect(accountId, profile?.id, profile?.displayName, profile?.bio, profile?.phone) {
        if (profile != null) draft = draft.hydrate(profile.displayName, profile.bio, profile.phone)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPickAvatar, enabled = !isWorking) { Text("Change profile photo") }
                Text("JPG, PNG or WebP · maximum 5 MB", color = MemberMuted, fontSize = 11.sp)
                OutlinedTextField(draft.name, { draft = draft.editName(it) }, label = { Text("Display name") }, singleLine = true)
                OutlinedTextField(draft.phone, { draft = draft.editPhone(it) }, label = { Text("Phone") }, singleLine = true)
                OutlinedTextField(draft.bio, { draft = draft.editBio(it) }, label = { Text("Bio") }, minLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft.name, draft.bio, draft.phone) }, enabled = !isWorking && draft.name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BookingRequestDialog(
    initialName: String, initialEmail: String, onDismiss: () -> Unit,
    onSubmit: (String, String, String, String?, String) -> Unit, isWorking: Boolean
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    var type by remember { mutableStateOf("Studio session") }
    var date by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }
    val types = listOf("Studio session", "Music production", "Mixing & mastering", "Artist booking", "Other")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request a booking") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Your name") }, singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true)
                Text("Booking type", fontWeight = FontWeight.SemiBold)
                types.forEach { option -> FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option) }) }
                OutlinedTextField(date, { date = it; validation = null }, label = { Text("Preferred date (YYYY-MM-DD, optional)") }, singleLine = true)
                OutlinedTextField(message, { message = it }, label = { Text("Additional details") }, minLines = 2)
                validation?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = !isWorking, onClick = {
                val parsedDate = if (date.isBlank()) null else runCatching { LocalDate.parse(date).toString() }.getOrNull()
                if (date.isNotBlank() && parsedDate == null) validation = "Enter the date as YYYY-MM-DD."
                else if (name.isBlank() || email.isBlank()) validation = "Enter your name and email."
                else onSubmit(name, email, type, parsedDate, message)
            }) { Text(if (isWorking) "Sending…" else "Submit request") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun GoogleBrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * .2f, cap = StrokeCap.Butt)
        val arcs = listOf(
            Color(0xFF4285F4) to -45f,
            Color(0xFF34A853) to 45f,
            Color(0xFFFBBC05) to 135f,
            Color(0xFFEA4335) to 225f
        )
        arcs.forEach { (color, start) ->
            drawArc(color, startAngle = start, sweepAngle = 88f, useCenter = false, style = stroke)
        }
        drawLine(
            color = Color(0xFF4285F4),
            start = androidx.compose.ui.geometry.Offset(size.width * .53f, size.height * .5f),
            end = androidx.compose.ui.geometry.Offset(size.width * .98f, size.height * .5f),
            strokeWidth = size.minDimension * .2f
        )
    }
}
