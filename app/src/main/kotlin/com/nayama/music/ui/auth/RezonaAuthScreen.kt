package com.nayama.music.ui.auth

import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayama.music.R
import com.nayama.music.auth.FirebaseAuthActionResult
import com.nayama.music.auth.FirebaseAuthManager
import com.nayama.music.auth.PendingAccountLink
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.sync.PasswordStrengthLevel
import com.nayama.music.sync.RezonaSyncLogic
import com.nayama.music.ui.components.GlassSurface
import com.nayama.music.ui.components.GlassSurfaceVariant
import com.nayama.music.ui.components.RezonaGlassButton
import com.nayama.music.ui.components.RezonaGlassCard
import com.nayama.music.ui.components.RezonaGlassDialog
import com.nayama.music.ui.components.RezonaGlassTextField
import com.nayama.music.ui.components.RezonaHairlineDivider
import com.nayama.music.ui.theme.PearlWhite
import com.nayama.music.ui.theme.PureWhite
import com.nayama.music.ui.theme.SmokySilver
import com.nayama.music.ui.theme.SoftSilver
import com.nayama.music.ui.theme.SpecularCardBorder
import com.nayama.music.ui.theme.Typography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Monochrome Liquid Glass Firebase Authentication Screen:
 * - Sign in with Google via Jetpack Credential Manager
 * - Email Sign In, Sign Up (display name, password strength meter, confirm password, accept terms)
 * - Email Verification screen (60s resend cooldown + "I verified" reload)
 * - Forgot Password modal
 * - Provider collision Account Linking modal
 * - Continue as Guest (local-only mode)
 */
@Composable
fun RezonaAuthScreen(
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferences = remember { RezonaPreferences(context) }
    val authManager = remember { FirebaseAuthManager.getInstance(context) }
    val authSession by authManager.session.collectAsState()
    val scope = rememberCoroutineScope()

    var isSignUpMode by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var acceptedTerms by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Forgot password dialog state
    var showForgotDialog by remember { mutableStateOf(false) }
    var forgotEmail by remember { mutableStateOf("") }

    // Email verification cooldown state (60s)
    var resendCooldownSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(authSession.needsEmailVerification) {
        if (authSession.needsEmailVerification && resendCooldownSec == 0) {
            resendCooldownSec = 60
        }
    }
    LaunchedEffect(resendCooldownSec) {
        if (resendCooldownSec > 0) {
            delay(1000L)
            resendCooldownSec -= 1
        }
    }

    // Account collision linking state
    var pendingCollision by remember { mutableStateOf<PendingAccountLink?>(null) }
    var linkPassword by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }

    val strength = remember(password) { RezonaSyncLogic.evaluatePasswordStrength(password) }

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
                text = if (authSession.needsEmailVerification) "Verify Your Email" else "Welcome to Rezona",
                style = Typography.displayLarge.copy(fontSize = 27.sp, fontWeight = FontWeight.Bold),
                color = PureWhite,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (authSession.needsEmailVerification) {
                    "Confirm your address to unlock cloud library sync across installs."
                } else {
                    "Sign in to sync your likes, playlists, and library across devices."
                },
                style = Typography.bodySmall,
                color = SoftSilver,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            if (authSession.needsEmailVerification) {
                VerificationCard(
                    email = authSession.email,
                    resendCooldownSec = resendCooldownSec,
                    isBusy = isBusy,
                    errorMessage = errorMessage,
                    onVerifiedCheck = {
                        scope.launch {
                            isBusy = true
                            errorMessage = null
                            when (val res = authManager.reloadAndCheckEmailVerified()) {
                                is FirebaseAuthActionResult.SignedIn -> {
                                    Toast.makeText(context, "Email verified", Toast.LENGTH_SHORT).show()
                                    onAuthenticated()
                                }
                                is FirebaseAuthActionResult.Error -> errorMessage = res.message
                                else -> Unit
                            }
                            isBusy = false
                        }
                    },
                    onResend = {
                        if (resendCooldownSec > 0 || isBusy) return@VerificationCard
                        scope.launch {
                            isBusy = true
                            errorMessage = null
                            when (val res = authManager.resendVerificationEmail()) {
                                is FirebaseAuthActionResult.Message -> {
                                    resendCooldownSec = 60
                                    Toast.makeText(context, res.text, Toast.LENGTH_SHORT).show()
                                }
                                is FirebaseAuthActionResult.Error -> errorMessage = res.message
                                else -> Unit
                            }
                            isBusy = false
                        }
                    },
                    onSwitchAccount = {
                        scope.launch {
                            authManager.signOut()
                            errorMessage = null
                        }
                    }
                )
            } else {
                RezonaGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!authSession.isFirebaseConfigured) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x18FFFFFF))
                                    .border(0.75.dp, Color(0x38FFFFFF), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Firebase config missing: add app/google-services.json from Firebase Console to enable cloud sign-in.",
                                    style = Typography.bodySmall,
                                    color = SoftSilver
                                )
                            }
                        }

                        // Continue with Google via Credential Manager
                        RezonaGlassButton(
                            text = if (isBusy) "Signing in..." else "G   Continue with Google",
                            isPrimary = true,
                            onClick = {
                                if (isBusy) return@RezonaGlassButton
                                scope.launch {
                                    isBusy = true
                                    errorMessage = null
                                    when (val res = authManager.signInWithGoogle(context)) {
                                        is FirebaseAuthActionResult.SignedIn -> {
                                            Toast.makeText(context, "Welcome, ${res.displayName}", Toast.LENGTH_SHORT).show()
                                            onAuthenticated()
                                        }
                                        is FirebaseAuthActionResult.AccountCollision -> {
                                            pendingCollision = res.linkInfo
                                        }
                                        FirebaseAuthActionResult.Cancelled -> Unit
                                        is FirebaseAuthActionResult.Error -> errorMessage = res.message
                                        else -> Unit
                                    }
                                    isBusy = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
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

                        // Segmented Sign In / Sign Up Switch (48dp touch target)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0x16FFFFFF))
                                .border(0.75.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
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
                                placeholder = "Display name",
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }

                        RezonaGlassTextField(
                            value = email,
                            onValueChange = { email = it; errorMessage = null },
                            placeholder = "Email address",
                            modifier = Modifier.heightIn(min = 48.dp)
                        )

                        AuthPasswordField(
                            value = password,
                            onValueChange = { password = it; errorMessage = null },
                            placeholder = if (isSignUpMode) "Password (min. 6 chars)" else "Password",
                            showPassword = showPassword,
                            onToggleVisibility = { showPassword = !showPassword }
                        )

                        if (isSignUpMode) {
                            // Password strength meter
                            if (password.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Password strength",
                                            style = Typography.labelSmall,
                                            color = SmokySilver
                                        )
                                        Text(
                                            text = strength.level.label,
                                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = when (strength.level) {
                                                PasswordStrengthLevel.STRONG -> PureWhite
                                                PasswordStrengthLevel.GOOD -> PearlWhite
                                                PasswordStrengthLevel.FAIR -> SoftSilver
                                                else -> Color(0xFFFF8E96)
                                            }
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x22FFFFFF))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(strength.fraction.coerceAtLeast(0.12f))
                                                .height(5.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when (strength.level) {
                                                        PasswordStrengthLevel.WEAK -> Color(0xFFFF8E96)
                                                        PasswordStrengthLevel.FAIR -> Color(0xFFD0D4DC)
                                                        else -> PureWhite
                                                    }
                                                )
                                        )
                                    }
                                }
                            }

                            AuthPasswordField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it; errorMessage = null },
                                placeholder = "Confirm password",
                                showPassword = showConfirmPassword,
                                onToggleVisibility = { showConfirmPassword = !showConfirmPassword }
                            )

                            // Accept terms row (>= 48dp touch target)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { acceptedTerms = !acceptedTerms }
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (acceptedTerms) PureWhite else Color(0x18FFFFFF))
                                        .border(1.dp, SpecularCardBorder, RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (acceptedTerms) {
                                        Text("✓", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "I accept the Terms of Service & Privacy Policy",
                                    style = Typography.bodySmall,
                                    color = SoftSilver
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            forgotEmail = email
                                            showForgotDialog = true
                                        }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Forgot password?",
                                        style = Typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = SoftSilver
                                    )
                                }
                            }
                        }

                        if (!errorMessage.isNullOrBlank()) {
                            Text(
                                text = errorMessage!!,
                                style = Typography.bodySmall,
                                color = Color(0xFFFF8E96)
                            )
                        }

                        RezonaGlassButton(
                            text = if (isBusy) {
                                "Please wait..."
                            } else if (isSignUpMode) {
                                "Create Account"
                            } else {
                                "Sign in with Email"
                            },
                            isPrimary = false,
                            onClick = {
                                if (isBusy) return@RezonaGlassButton
                                if (isSignUpMode) {
                                    when {
                                        displayName.isBlank() -> {
                                            errorMessage = "Enter your display name."
                                            return@RezonaGlassButton
                                        }
                                        !RezonaSyncLogic.isValidEmailFormat(email) -> {
                                            errorMessage = "Enter a valid email address."
                                            return@RezonaGlassButton
                                        }
                                        !strength.isAcceptableForSignUp -> {
                                            errorMessage = "Password must be at least 6 characters."
                                            return@RezonaGlassButton
                                        }
                                        password != confirmPassword -> {
                                            errorMessage = "Passwords do not match."
                                            return@RezonaGlassButton
                                        }
                                        !acceptedTerms -> {
                                            errorMessage = "Accept the Terms & Privacy Policy to continue."
                                            return@RezonaGlassButton
                                        }
                                    }
                                }
                                scope.launch {
                                    isBusy = true
                                    errorMessage = null
                                    val res = if (isSignUpMode) {
                                        authManager.signUpWithEmail(displayName, email, password)
                                    } else {
                                        authManager.signInWithEmail(email, password)
                                    }
                                    when (res) {
                                        is FirebaseAuthActionResult.SignedIn -> {
                                            Toast.makeText(context, "Welcome, ${res.displayName}", Toast.LENGTH_SHORT).show()
                                            onAuthenticated()
                                        }
                                        is FirebaseAuthActionResult.NeedsVerification -> {
                                            resendCooldownSec = 60
                                        }
                                        is FirebaseAuthActionResult.AccountCollision -> {
                                            pendingCollision = res.linkInfo
                                        }
                                        is FirebaseAuthActionResult.Error -> errorMessage = res.message
                                        else -> Unit
                                    }
                                    isBusy = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        scope.launch {
                            preferences.continueAsGuest()
                            onAuthenticated()
                        }
                    }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Continue as Guest (local only)",
                    style = Typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = SoftSilver
                )
            }
        }
    }

    if (showForgotDialog) {
        RezonaGlassDialog(
            title = "Reset Password",
            onDismissRequest = { showForgotDialog = false },
            confirmText = "Send Reset Link",
            onConfirm = {
                scope.launch {
                    val res = authManager.sendPasswordResetEmail(forgotEmail)
                    when (res) {
                        is FirebaseAuthActionResult.Message -> {
                            showForgotDialog = false
                            Toast.makeText(context, res.text, Toast.LENGTH_LONG).show()
                        }
                        is FirebaseAuthActionResult.Error -> {
                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                        }
                        else -> Unit
                    }
                }
            },
            dismissText = "Cancel"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter your email address to receive a password reset link.",
                    style = Typography.bodySmall,
                    color = SoftSilver
                )
                RezonaGlassTextField(
                    value = forgotEmail,
                    onValueChange = { forgotEmail = it },
                    placeholder = "Email address",
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }
    }

    pendingCollision?.let { collision ->
        RezonaGlassDialog(
            title = "Link Existing Account",
            onDismissRequest = {
                authManager.clearPendingLink()
                pendingCollision = null
                linkPassword = ""
                linkError = null
            },
            confirmText = "Sign in & Link",
            onConfirm = {
                scope.launch {
                    linkError = null
                    when (val signInRes = authManager.signInWithEmail(collision.email, linkPassword)) {
                        is FirebaseAuthActionResult.SignedIn -> {
                            val linked = authManager.linkPendingAfterExistingSignIn()
                            pendingCollision = null
                            linkPassword = ""
                            Toast.makeText(
                                context,
                                if (linked) "Accounts linked cleanly" else "Signed in with existing account",
                                Toast.LENGTH_SHORT
                            ).show()
                            onAuthenticated()
                        }
                        is FirebaseAuthActionResult.Error -> linkError = signInRes.message
                        else -> Unit
                    }
                }
            },
            dismissText = "Cancel"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "An account for ${collision.email} already exists under another sign-in method. Sign in with your existing password to link ${collision.pendingProviderLabel} so both share one cloud library.",
                    style = Typography.bodySmall,
                    color = SoftSilver
                )
                RezonaGlassTextField(
                    value = linkPassword,
                    onValueChange = { linkPassword = it; linkError = null },
                    placeholder = "Existing account password",
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                if (!linkError.isNullOrBlank()) {
                    Text(linkError!!, style = Typography.bodySmall, color = Color(0xFFFF8E96))
                }
            }
        }
    }
}

@Composable
private fun VerificationCard(
    email: String,
    resendCooldownSec: Int,
    isBusy: Boolean,
    errorMessage: String?,
    onVerifiedCheck: () -> Unit,
    onResend: () -> Unit,
    onSwitchAccount: () -> Unit
) {
    RezonaGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "A verification email was sent to:",
                style = Typography.bodySmall,
                color = SoftSilver,
                textAlign = TextAlign.Center
            )
            Text(
                text = email.ifBlank { "your inbox" },
                style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PureWhite,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Open the verification link in your inbox, then tap below to refresh.",
                style = Typography.bodySmall,
                color = SmokySilver,
                textAlign = TextAlign.Center
            )

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = Typography.bodySmall,
                    color = Color(0xFFFF8E96),
                    textAlign = TextAlign.Center
                )
            }

            RezonaGlassButton(
                text = if (isBusy) "Checking..." else "I verified",
                isPrimary = true,
                onClick = onVerifiedCheck,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            )

            RezonaGlassButton(
                text = if (resendCooldownSec > 0) "Resend email (${resendCooldownSec}s)" else "Resend email",
                isPrimary = false,
                onClick = onResend,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            )

            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onSwitchAccount)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Use a different account",
                    style = Typography.labelMedium,
                    color = SoftSilver
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
    val shape = RoundedCornerShape(21.dp)
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
    placeholder: String,
    showPassword: Boolean,
    onToggleVisibility: () -> Unit
) {
    GlassSurface(
        variant = GlassSurfaceVariant.Field,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
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
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleVisibility)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (showPassword) "Hide" else "Show",
                    style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = SoftSilver
                )
            }
        }
    }
}
