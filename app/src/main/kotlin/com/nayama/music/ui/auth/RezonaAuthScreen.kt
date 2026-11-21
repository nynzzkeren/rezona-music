package com.nayama.music.ui.auth

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.auth.GoogleAuthManager
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.ui.components.GlassSurface
import com.nayama.music.ui.components.GlassSurfaceVariant
import com.nayama.music.ui.components.RezonaGlassButton
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.components.RezonaGlassDialog
import com.nayama.music.ui.components.RezonaGlassPill
import com.nayama.music.ui.components.RezonaGlassTextField
import com.nayama.music.ui.components.RezonaHairlineDivider
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularCardBorder
import com.nayama.music.ui.theme.Typography
import kotlinx.coroutines.launch

/**
 * First-launch Monochrome Liquid Glass Welcome & Auth Gate:
 * - Continue with Google (Native Android Google Account chooser)
 * - Email Sign In & Sign Up (SHA-256 salted credential storage on-device)
 * - Continue as Guest option at bottom
 */
@Composable
fun RezonaAuthScreen(
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferences = remember { RezonaPreferences(context) }
    val scope = rememberCoroutineScope()

    var isSignUpMode by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showGoogleFallbackDialog by remember { mutableStateOf(false) }
    var manualGoogleEmail by remember { mutableStateOf("") }

    val googlePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val picked = GoogleAuthManager.parseResult(result.data)
            if (picked != null) {
                scope.launch {
                    preferences.setGoogleAccount(picked.email, picked.displayName)
                    Toast.makeText(context, "Signed in as ${picked.email}", Toast.LENGTH_SHORT).show()
                    onAuthenticated()
                }
            } else {
                showGoogleFallbackDialog = true
            }
        } else if (GoogleAuthManager.getDeviceGoogleAccounts(context).isEmpty()) {
            showGoogleFallbackDialog = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF10131C), Color(0xFF06070A), Color(0xFF040507))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Logo Mark
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF000000))
                    .border(1.dp, SpecularCardBorder, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_rezona_foreground),
                    contentDescription = "Rezona",
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome to Rezona",
                style = Typography.displayLarge.copy(fontSize = 27.sp, fontWeight = FontWeight.Bold),
                color = PureWhite,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Sign in or create an account to save your profile on this device.",
                style = Typography.bodySmall,
                color = SoftSilver,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            RezonaGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Continue with Google CTA
                    RezonaGlassButton(
                        text = "G   Continue with Google",
                        isPrimary = true,
                        onClick = {
                            val launched = runCatching {
                                googlePickerLauncher.launch(GoogleAuthManager.createGoogleAccountChooserIntent())
                                true
                            }.getOrDefault(false)
                            if (!launched) showGoogleFallbackDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RezonaHairlineDivider(modifier = Modifier.weight(1f))
                        Text("or email", style = Typography.labelSmall, color = SmokySilver)
                        RezonaHairlineDivider(modifier = Modifier.weight(1f))
                    }

                    // Segmented Sign In / Sign Up Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0x16FFFFFF))
                            .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(22.dp))
                            .padding(3.dp)
                    ) {
                        AuthTabPill(
                            label = "Sign in",
                            selected = !isSignUpMode,
                            onClick = {
                                isSignUpMode = false
                                errorMessage = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                        AuthTabPill(
                            label = "Sign up",
                            selected = isSignUpMode,
                            onClick = {
                                isSignUpMode = true
                                errorMessage = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (isSignUpMode) {
                        RezonaGlassTextField(
                            value = displayName,
                            onValueChange = { displayName = it; errorMessage = null },
                            placeholder = "Full name"
                        )
                    }

                    RezonaGlassTextField(
                        value = email,
                        onValueChange = { email = it; errorMessage = null },
                        placeholder = "Email address"
                    )

                    AuthPasswordField(
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        showPassword = showPassword,
                        onToggleVisibility = { showPassword = !showPassword }
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage!!,
                            style = Typography.bodySmall,
                            color = Color(0xFFFF8E96)
                        )
                    }

                    RezonaGlassButton(
                        text = if (isSignUpMode) "Create Account" else "Sign in with Email",
                        isPrimary = false,
                        onClick = {
                            scope.launch {
                                val res = if (isSignUpMode) {
                                    preferences.signUpWithEmail(displayName, email, password)
                                } else {
                                    preferences.signInWithEmail(email, password)
                                }
                                when (res) {
                                    is RezonaPreferences.AccountAuthResult.Success -> {
                                        Toast.makeText(context, "Welcome, ${res.displayName}", Toast.LENGTH_SHORT).show()
                                        onAuthenticated()
                                    }
                                    is RezonaPreferences.AccountAuthResult.Error -> {
                                        errorMessage = res.message
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Continue as Guest",
                style = Typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = SoftSilver,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        scope.launch {
                            preferences.continueAsGuest()
                            onAuthenticated()
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }

    if (showGoogleFallbackDialog) {
        val deviceAccounts = remember(showGoogleFallbackDialog) {
            GoogleAuthManager.getDeviceGoogleAccounts(context)
        }
        RezonaGlassDialog(
            title = "Choose Google Account",
            onDismissRequest = { showGoogleFallbackDialog = false },
            confirmText = "Continue",
            onConfirm = {
                val trimmed = manualGoogleEmail.trim()
                if (trimmed.contains("@") && trimmed.contains(".")) {
                    scope.launch {
                        preferences.setGoogleAccount(trimmed, "")
                        showGoogleFallbackDialog = false
                        onAuthenticated()
                    }
                } else {
                    Toast.makeText(context, "Enter a valid Gmail address", Toast.LENGTH_SHORT).show()
                }
            },
            dismissText = "Cancel"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (deviceAccounts.isNotEmpty()) {
                    deviceAccounts.forEach { acc ->
                        RezonaGlassPill(
                            isActive = false,
                            onClick = {
                                scope.launch {
                                    preferences.setGoogleAccount(acc.email, acc.displayName)
                                    showGoogleFallbackDialog = false
                                    onAuthenticated()
                                }
                            }
                        ) {
                            Text(acc.email, style = Typography.bodySmall, color = PureWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    RezonaHairlineDivider()
                } else {
                    Text(
                        text = "No device Google account found. Add one in Android Settings or type your Google email:",
                        style = Typography.bodySmall,
                        color = SoftSilver
                    )
                    RezonaGlassPill(
                        isActive = false,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                                        putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                                    }
                                )
                            }
                        }
                    ) {
                        Text("Open Android Add Account", style = Typography.labelMedium, color = PearlWhite)
                    }
                }
                RezonaGlassTextField(
                    value = manualGoogleEmail,
                    onValueChange = { manualGoogleEmail = it },
                    placeholder = "yourname@gmail.com"
                )
            }
        }
    }
}

@Composable
private fun AuthTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(19.dp)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .then(
                if (selected) {
                    Modifier
                        .background(Brush.verticalGradient(listOf(Color(0x36FFFFFF), Color(0x18FFFFFF))))
                        .border(0.75.dp, Color(0x52FFFFFF), shape)
                } else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = Typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) PureWhite else SoftSilver
        )
    }
}

@Composable
private fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    showPassword: Boolean,
    onToggleVisibility: () -> Unit
) {
    GlassSurface(
        variant = GlassSurfaceVariant.Field,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = "Password (min. 6 chars)",
                        style = Typography.bodyMedium,
                        color = SmokySilver
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = Typography.bodyMedium.copy(color = PureWhite),
                    cursorBrush = SolidColor(PureWhite),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (showPassword) "Hide" else "Show",
                style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = SoftSilver,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleVisibility)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}
