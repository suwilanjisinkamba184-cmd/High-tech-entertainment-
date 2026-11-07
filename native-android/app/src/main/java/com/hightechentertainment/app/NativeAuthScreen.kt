package com.hightechentertainment.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            Brush.verticalGradient(listOf(Color(0xFF21190B), Color(0xFF080808), Color(0xFF050505)))
        ).padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF151515))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(62.dp).background(Color(0xFFD7A83E), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("HT", color = Color(0xFF080808), fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
                Spacer(Modifier.height(18.dp))
                Text("HIGH-TECH", color = Color.White, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                Text("ENTERTAINMENT", color = Color(0xFFD7A83E), fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(22.dp))
                Text(
                    if (createAccount) "Create your account" else "Welcome back",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text("Sign in to access your music and library.", color = Color(0xFFAAA39A))
                Spacer(Modifier.height(22.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email address") },
                    singleLine = true,
                    enabled = !busy
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !busy
                )
                if (!message.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(message, color = Color(0xFFFFC56A), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        if (createAccount) onSignUp(email.trim(), password)
                        else onSignIn(email.trim(), password)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD7A83E))
                ) {
                    if (busy) CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color(0xFF080808),
                        strokeWidth = 2.dp
                    ) else Text(
                        if (createAccount) "CREATE ACCOUNT" else "SIGN IN",
                        color = Color(0xFF080808),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onGoogleSignIn,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !busy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("CONTINUE WITH GOOGLE", fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = { createAccount = !createAccount },
                    enabled = !busy
                ) {
                    Text(
                        if (createAccount) "Already have an account? Sign in"
                        else "Create a new account",
                        color = Color(0xFFD7A83E)
                    )
                }
                TextButton(
                    onClick = { onReset(email.trim()) },
                    enabled = !busy && email.isNotBlank()
                ) {
                    Text("Forgot password?", color = Color(0xFFAAA39A))
                }
                Text(
                    "Music. Beats. Production. Beyond.",
                    color = Color(0xFFAAA39A),
                    fontSize = 11.sp
                )
            }
        }
    }
}
