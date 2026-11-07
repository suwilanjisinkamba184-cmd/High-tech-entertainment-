package com.hightechentertainment.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AuthGold = Color(0xFFE4B653)
private val AuthInk = Color(0xFF08090B)
private val AuthPanel = Color(0xFF15171B)
private val AuthMuted = Color(0xFF9B9DA3)

@Composable
internal fun NativeAuthScreen(
    busy: Boolean,
    message: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onReset: (String) -> Unit,
    onGoogleSignIn: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var createAccount by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF241B0D), Color(0xFF111113), AuthInk, Color(0xFF050506)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 12.dp)
                .size(190.dp).background(
                    Brush.radialGradient(listOf(Color(0x33E4B653), Color.Transparent)), CircleShape
                )
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(Modifier.fillMaxWidth().widthIn(max = 460.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(54.dp).background(
                        Brush.linearGradient(listOf(Color(0xFFFFE8A8), AuthGold, Color(0xFF875A17))),
                        RoundedCornerShape(17.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) { Text("HT", color = AuthInk, fontWeight = FontWeight.Black, fontSize = 22.sp) }
                Column(Modifier.padding(start = 13.dp)) {
                    Text("HIGH-TECH", color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 2.4.sp)
                    Text("ENTERTAINMENT", color = AuthGold, fontSize = 10.sp, letterSpacing = 2.5.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("3.1", color = AuthMuted, fontSize = 11.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(34.dp))
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp)
                    .border(1.dp, Color(0xFF514126), RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = AuthPanel)
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Surface(color = Color(0xFF2A2113), shape = RoundedCornerShape(50)) {
                        Text("YOUR SOUND. YOUR WORLD.", Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            color = AuthGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(if (createAccount) "Join the sound." else "Welcome back.",
                        color = Color.White, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        if (createAccount) "Create your High-Tech account and build your music library."
                        else "Sign in to your music, videos, playlists and bookings.",
                        color = AuthMuted, style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(25.dp))
                    Text("EMAIL ADDRESS", color = AuthMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(7.dp))
                    OutlinedTextField(
                        value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("you@example.com", color = AuthMuted) }, singleLine = true, enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(15.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuthGold, unfocusedBorderColor = Color(0xFF3B3D42),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = AuthGold
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("PASSWORD", color = AuthMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(7.dp))
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter your password", color = AuthMuted) },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(15.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AuthGold, unfocusedBorderColor = Color(0xFF3B3D42),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = AuthGold
                        )
                    )
                    if (!message.isNullOrBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Surface(color = Color(0xFF302315), shape = RoundedCornerShape(12.dp)) {
                            Text(message, Modifier.fillMaxWidth().padding(12.dp), color = Color(0xFFFFD38A),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = { if (createAccount) onSignUp(email.trim(), password) else onSignIn(email.trim(), password) },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AuthGold, contentColor = AuthInk)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(21.dp), color = AuthInk, strokeWidth = 2.dp)
                        else Text(if (createAccount) "CREATE ACCOUNT" else "SIGN IN", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onGoogleSignIn, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !busy,
                        shape = RoundedCornerShape(15.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF41434A)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("G", color = AuthGold, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text("CONTINUE WITH GOOGLE", fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { createAccount = !createAccount }, enabled = !busy) {
                            Text(if (createAccount) "I already have an account" else "Create an account", color = AuthGold)
                        }
                        TextButton(onClick = { onReset(email.trim()) }, enabled = !busy && email.isNotBlank()) {
                            Text("Forgot password?", color = AuthMuted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("MUSIC. BEATS. PRODUCTION. BEYOND.", color = AuthGold, fontSize = 10.sp,
                letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("QUALITY SOUND. GLOBAL VIBES.", color = AuthMuted, fontSize = 10.sp, letterSpacing = 1.2.sp)
        }
    }
}
