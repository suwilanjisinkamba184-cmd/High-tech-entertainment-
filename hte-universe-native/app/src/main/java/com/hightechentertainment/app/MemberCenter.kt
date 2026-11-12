package com.hightechentertainment.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hightechentertainment.app.data.AuthRepository
import com.hightechentertainment.app.data.BookingDto
import com.hightechentertainment.app.data.MemberRepository
import com.hightechentertainment.app.data.NotificationDto
import com.hightechentertainment.app.data.ProfileDto
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.LocalDate

private val MemberGold = Color(0xFFD6B15E)
private val MemberPanel = Color(0xFF121418)
private val MemberMuted = Color(0xFF8B929D)

@Composable
fun MemberCenter(configured: Boolean, onSettingsChanged: () -> Unit) {
    val context = LocalContext.current
    val settingsStore = remember(context) { AppSettingsStore(context) }
    var notificationsEnabled by remember { mutableStateOf(settingsStore.notificationsEnabled) }
    var autoplayNext by remember { mutableStateOf(settingsStore.autoplayNext) }
    var dataSaver by remember { mutableStateOf(settingsStore.dataSaver) }
    var showFounderStudio by remember { mutableStateOf(false) }
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
    var showProfileEditor by remember { mutableStateOf(false) }
    var showBookingForm by remember { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }
    val auth = remember(configured) { if (configured) runCatching { SupabaseClientProvider.client.auth }.getOrNull() else null }

    LaunchedEffect(auth) {
        if (auth != null) {
            isSignedIn = auth.currentUserOrNull() != null
            auth.sessionStatus.collect { isSignedIn = auth.currentUserOrNull() != null }
        }
    }

    LaunchedEffect(auth, isSignedIn) {
        if (auth != null && isSignedIn) {
            val repo = MemberRepository()
            profile = runCatching { repo.getMyProfile() }.getOrNull()
            bookings = runCatching { repo.getMyBookings() }.getOrDefault(emptyList())
            notifications = runCatching { repo.getNotifications() }.getOrDefault(emptyList())
            role = runCatching { repo.getCurrentRole() }.getOrDefault("member")
            email = auth.currentUserOrNull()?.email.orEmpty()
        } else if (!isSignedIn) {
            profile = null
            bookings = emptyList()
            notifications = emptyList()
            role = "member"
        }
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Account", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Your HTE Universe membership.", color = MemberMuted, fontSize = 13.sp)
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
                    OutlinedTextField(value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Email address") }, singleLine = true)
                    OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true)
                    Button(onClick = {
                        scope.launch {
                            isWorking = true
                            statusMessage = null
                            runCatching { if (createAccount) AuthRepository().signUp(email, password) else AuthRepository().signIn(email, password) }
                                .onSuccess {
                                    isSignedIn = auth.currentUserOrNull() != null
                                    statusMessage = if (createAccount && !isSignedIn) "Account created. Check your email if confirmation is required." else "Authentication request completed."
                                }
                                .onFailure { statusMessage = it.message?.take(180) ?: "Authentication failed. Please try again." }
                            isWorking = false
                        }
                    }, enabled = !isWorking, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MemberGold, contentColor = Color.Black)) {
                        Text(if (isWorking) "Please wait…" else if (createAccount) "Create account" else "Sign in")
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            isWorking = true
                            runCatching { AuthRepository().signInWithGoogle() }
                                .onSuccess { statusMessage = "Complete Google sign-in in your browser, then return to HTE Universe." }
                                .onFailure { statusMessage = it.message?.take(180) ?: "Google sign-in could not start." }
                            isWorking = false
                        }
                    }, enabled = !isWorking, modifier = Modifier.fillMaxWidth()) { Text("Continue with Google") }
                    TextButton(onClick = { createAccount = !createAccount; statusMessage = null }) {
                        Text(if (createAccount) "Already registered? Sign in" else "New to HTE? Create an account", color = MemberGold)
                    }
                }
            }
        } else {
            item {
                MemberCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AccountCircle, null, tint = MemberGold, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.width(10.dp))
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
                            runCatching { AuthRepository().signOut() }
                                .onSuccess { isSignedIn = false; statusMessage = "You have signed out." }
                                .onFailure { statusMessage = "Sign-out failed. Please try again." }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Logout, null); Spacer(Modifier.width(8.dp)); Text("Sign out") }
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
            if (bookings.isEmpty()) {
                item { Text("Your booking requests will appear here.", color = MemberMuted, fontSize = 12.sp) }
            } else {
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
            } else if (notifications.isEmpty()) {
                item { Text("No account notifications yet.", color = MemberMuted, fontSize = 12.sp) }
            } else {
                items(notifications, key = { it.id }) { notification ->
                    MemberCard {
                        Text(notification.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(notification.body, color = MemberMuted, fontSize = 12.sp)
                    }
                }
            }
        }
        item { Text("APP SETTINGS", color = MemberGold, fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold) }
        item {
            PreferenceRow("Notifications", "Show account and booking updates in the app", notificationsEnabled) {
                notificationsEnabled = it; settingsStore.notificationsEnabled = it; onSettingsChanged()
            }
        }
        item {
            PreferenceRow("Autoplay next track", "Continue through the current music queue", autoplayNext) {
                autoplayNext = it; settingsStore.autoplayNext = it; onSettingsChanged()
            }
        }
        item {
            PreferenceRow("Data saver", "Do not start downloads over metered mobile data", dataSaver) {
                dataSaver = it; settingsStore.dataSaver = it; onSettingsChanged()
            }
        }
        if (statusMessage != null) item { Text(statusMessage!!, color = MemberGold, fontSize = 12.sp) }
    }

    if (showProfileEditor && auth != null) {
        ProfileEditorDialog(profile = profile, onDismiss = { showProfileEditor = false }, onSave = { name, bio, phone ->
            scope.launch {
                isWorking = true
                runCatching { MemberRepository().saveMyProfile(name, bio, phone) }
                    .onSuccess { profile = MemberRepository().getMyProfile(); statusMessage = "Profile saved."; showProfileEditor = false }
                    .onFailure { statusMessage = it.message?.take(180) ?: "Profile could not be saved." }
                isWorking = false
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
                    runCatching { MemberRepository().submitBooking(name, requestEmail, type, date, message) }
                        .onSuccess {
                            bookings = MemberRepository().getMyBookings()
                            statusMessage = "Your booking request has been submitted."
                            showBookingForm = false
                        }
                        .onFailure { statusMessage = it.message?.take(180) ?: "Booking request failed. Please retry." }
                    isWorking = false
                }
            }, isWorking = isWorking
        )
    }
}

    if (showFounderStudio) {
        Dialog(onDismissRequest = { showFounderStudio = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF08090B)) {
                FounderStudioScreen(onClose = { showFounderStudio = false })
            }
        }
    }
}

@Composable
private fun PreferenceRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MemberPanel), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MemberMuted, fontSize = 11.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun MemberCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MemberPanel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun ProfileEditorDialog(profile: ProfileDto?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit, isWorking: Boolean) {
    var name by remember(profile) { mutableStateOf(profile?.displayName.orEmpty()) }
    var bio by remember(profile) { mutableStateOf(profile?.bio.orEmpty()) }
    var phone by remember(profile) { mutableStateOf(profile?.phone.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Display name") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true)
                OutlinedTextField(bio, { bio = it }, label = { Text("Bio") }, minLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, bio, phone) }, enabled = !isWorking && name.isNotBlank()) { Text("Save") } },
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
