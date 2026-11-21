package com.nayama.music.auth

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.nayama.music.data.preferences.RezonaPreferences
import com.nayama.music.sync.RezonaSyncLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class PendingAccountLink(
    val email: String,
    val pendingCredential: AuthCredential,
    val pendingProviderLabel: String,
    val suggestedExistingProviders: List<String>
)

data class AuthSessionState(
    val isFirebaseConfigured: Boolean = false,
    val isSignedIn: Boolean = false,
    val isEmailVerified: Boolean = false,
    val uid: String? = null,
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val providerIds: List<String> = emptyList(),
    val providerLabel: String = "Guest",
    val needsEmailVerification: Boolean = false,
    val isPasswordUser: Boolean = false,
    val isGoogleUser: Boolean = false
)

sealed class FirebaseAuthActionResult {
    data class SignedIn(val uid: String, val email: String, val displayName: String) : FirebaseAuthActionResult()
    data class NeedsVerification(val email: String) : FirebaseAuthActionResult()
    data class AccountCollision(val linkInfo: PendingAccountLink) : FirebaseAuthActionResult()
    data class Message(val text: String) : FirebaseAuthActionResult()
    data object Cancelled : FirebaseAuthActionResult()
    data class Error(val message: String) : FirebaseAuthActionResult()
}

class FirebaseAuthManager private constructor(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = RezonaPreferences(appContext)

    private val _session = MutableStateFlow(AuthSessionState())
    val session: StateFlow<AuthSessionState> = _session.asStateFlow()

    @Volatile
    private var pendingLink: PendingAccountLink? = null

    private var authListenerRegistered = false

    init {
        refreshSessionFromFirebase()
    }

    fun getFirebaseAuthOrNull(): FirebaseAuth? {
        val apps = runCatching { FirebaseApp.getApps(appContext) }.getOrDefault(emptyList())
        if (apps.isEmpty()) {
            runCatching { FirebaseApp.initializeApp(appContext) }
        }
        val initialized = runCatching { FirebaseApp.getApps(appContext).isNotEmpty() }.getOrDefault(false)
        if (!initialized) return null
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull() ?: return null
        if (!authListenerRegistered) {
            authListenerRegistered = true
            auth.addAuthStateListener { firebaseAuth ->
                emitUserState(firebaseAuth.currentUser, isConfigured = true)
            }
        }
        return auth
    }

    fun refreshSessionFromFirebase(): AuthSessionState {
        val auth = getFirebaseAuthOrNull()
        val state = if (auth == null) {
            AuthSessionState(isFirebaseConfigured = false)
        } else {
            buildSessionState(auth.currentUser, isConfigured = true)
        }
        _session.value = state
        return state
    }

    fun currentVerifiedUid(): String? {
        val st = _session.value
        return if (st.isSignedIn && st.isEmailVerified) st.uid else null
    }

    private fun emitUserState(user: FirebaseUser?, isConfigured: Boolean) {
        val next = buildSessionState(user, isConfigured)
        _session.value = next
        scope.launch {
            if (next.isSignedIn && next.isEmailVerified && !next.uid.isNullOrBlank()) {
                preferences.setFirebaseSignedInState(
                    uid = next.uid,
                    email = next.email,
                    displayName = next.displayName,
                    provider = next.providerLabel
                )
            }
        }
    }

    private fun buildSessionState(user: FirebaseUser?, isConfigured: Boolean): AuthSessionState {
        if (user == null) {
            return AuthSessionState(isFirebaseConfigured = isConfigured)
        }
        val providers = user.providerData
            .map { it.providerId }
            .filter { it != "firebase" }
            .distinct()
        val hasPassword = EmailAuthProvider.PROVIDER_ID in providers
        val hasGoogle = GoogleAuthProvider.PROVIDER_ID in providers
        val verified = if (hasGoogle) true else user.isEmailVerified
        val label = when {
            hasGoogle && hasPassword -> "Google + Email"
            hasGoogle -> "Google"
            hasPassword -> "Email & Password"
            else -> "Firebase"
        }
        val resolvedEmail = user.email.orEmpty()
        val resolvedName = user.displayName?.takeIf { it.isNotBlank() }
            ?: GoogleAuthManager.deriveDisplayNameFromEmail(resolvedEmail)

        return AuthSessionState(
            isFirebaseConfigured = isConfigured,
            isSignedIn = true,
            isEmailVerified = verified,
            uid = user.uid,
            email = resolvedEmail,
            displayName = resolvedName,
            photoUrl = user.photoUrl?.toString().orEmpty(),
            providerIds = providers,
            providerLabel = label,
            needsEmailVerification = hasPassword && !hasGoogle && !verified,
            isPasswordUser = hasPassword,
            isGoogleUser = hasGoogle
        )
    }

    suspend fun signUpWithEmail(
        displayName: String,
        email: String,
        password: String
    ): FirebaseAuthActionResult {
        val auth = getFirebaseAuthOrNull()
            ?: return FirebaseAuthActionResult.Error(FIREBASE_NOT_CONFIGURED_MSG)
        val cleanEmail = email.trim().lowercase()
        val cleanName = displayName.trim().ifBlank { GoogleAuthManager.deriveDisplayNameFromEmail(cleanEmail) }
        if (!RezonaSyncLogic.isValidEmailFormat(cleanEmail)) {
            return FirebaseAuthActionResult.Error("Enter a valid email address.")
        }
        if (password.length < 6) {
            return FirebaseAuthActionResult.Error("Password must be at least 6 characters.")
        }

        return try {
            val res = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
            val user = res.user ?: return FirebaseAuthActionResult.Error("Account creation failed.")
            runCatching {
                user.updateProfile(
                    UserProfileChangeRequest.Builder()
                        .setDisplayName(cleanName)
                        .build()
                ).await()
            }
            user.sendEmailVerification().await()
            emitUserState(auth.currentUser, isConfigured = true)
            FirebaseAuthActionResult.NeedsVerification(cleanEmail)
        } catch (collision: FirebaseAuthUserCollisionException) {
            val methods = runCatching {
                @Suppress("DEPRECATION")
                auth.fetchSignInMethodsForEmail(cleanEmail).await().signInMethods.orEmpty()
            }.getOrDefault(emptyList())
            val emailCred = EmailAuthProvider.getCredential(cleanEmail, password)
            val linkInfo = PendingAccountLink(
                email = cleanEmail,
                pendingCredential = emailCred,
                pendingProviderLabel = "Email & Password",
                suggestedExistingProviders = methods
            )
            pendingLink = linkInfo
            FirebaseAuthActionResult.AccountCollision(linkInfo)
        } catch (e: FirebaseAuthException) {
            Log.w(TAG, "Sign-up failed with code=${e.errorCode}")
            when (e.errorCode) {
                "ERROR_WEAK_PASSWORD" -> FirebaseAuthActionResult.Error("Choose a stronger password (at least 6 characters).")
                "ERROR_INVALID_EMAIL" -> FirebaseAuthActionResult.Error("Enter a valid email address.")
                else -> FirebaseAuthActionResult.Error(RezonaSyncLogic.toGenericSignInError(e))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Sign-up failed: ${e.javaClass.simpleName}")
            FirebaseAuthActionResult.Error(RezonaSyncLogic.toGenericSignInError(e))
        }
    }

    suspend fun signInWithEmail(email: String, password: String): FirebaseAuthActionResult {
        val auth = getFirebaseAuthOrNull()
            ?: return FirebaseAuthActionResult.Error(FIREBASE_NOT_CONFIGURED_MSG)
        val cleanEmail = email.trim().lowercase()
        if (!RezonaSyncLogic.isValidEmailFormat(cleanEmail) || password.isEmpty()) {
            return FirebaseAuthActionResult.Error("Enter a valid email and password.")
        }

        return try {
            val res = auth.signInWithEmailAndPassword(cleanEmail, password).await()
            val user = res.user ?: return FirebaseAuthActionResult.Error(RezonaSyncLogic.toGenericSignInError(null))
            maybeLinkPendingCredential(user)
            val state = buildSessionState(auth.currentUser, isConfigured = true)
            _session.value = state
            if (state.needsEmailVerification) {
                FirebaseAuthActionResult.NeedsVerification(state.email)
            } else {
                preferences.setFirebaseSignedInState(
                    uid = state.uid.orEmpty(),
                    email = state.email,
                    displayName = state.displayName,
                    provider = state.providerLabel
                )
                FirebaseAuthActionResult.SignedIn(state.uid.orEmpty(), state.email, state.displayName)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Email sign-in failed: ${e.javaClass.simpleName}")
            FirebaseAuthActionResult.Error(RezonaSyncLogic.toGenericSignInError(e))
        }
    }

    suspend fun signInWithGoogle(activityContext: Context): FirebaseAuthActionResult {
        val auth = getFirebaseAuthOrNull()
            ?: return FirebaseAuthActionResult.Error(FIREBASE_NOT_CONFIGURED_MSG)

        val credResult = GoogleAuthManager.requestGoogleCredential(activityContext)
        val successCred = when (credResult) {
            is GoogleAuthManager.GoogleCredentialResult.Success -> credResult
            GoogleAuthManager.GoogleCredentialResult.Cancelled -> return FirebaseAuthActionResult.Cancelled
            is GoogleAuthManager.GoogleCredentialResult.Error ->
                return FirebaseAuthActionResult.Error(credResult.message)
        }

        return try {
            val res = auth.signInWithCredential(successCred.authCredential).await()
            val user = res.user ?: return FirebaseAuthActionResult.Error(RezonaSyncLogic.toGenericSignInError(null))
            maybeLinkPendingCredential(user)
            val state = buildSessionState(auth.currentUser, isConfigured = true)
            _session.value = state
            preferences.setFirebaseSignedInState(
                uid = state.uid.orEmpty(),
                email = state.email,
                displayName = state.displayName,
                provider = state.providerLabel
            )
            FirebaseAuthActionResult.SignedIn(state.uid.orEmpty(), state.email, state.displayName)
        } catch (collision: FirebaseAuthUserCollisionException) {
            val collisionEmail = successCred.email ?: collision.email.orEmpty()
            val methods = if (collisionEmail.isNotBlank()) {
                runCatching {
                    @Suppress("DEPRECATION")
                    auth.fetchSignInMethodsForEmail(collisionEmail).await().signInMethods.orEmpty()
                }.getOrDefault(emptyList())
            } else emptyList()
            val linkInfo = PendingAccountLink(
                email = collisionEmail,
                pendingCredential = successCred.authCredential,
                pendingProviderLabel = "Google",
                suggestedExistingProviders = methods
            )
            pendingLink = linkInfo
            FirebaseAuthActionResult.AccountCollision(linkInfo)
        } catch (e: Throwable) {
            Log.w(TAG, "Google Firebase sign-in failed: ${e.javaClass.simpleName}")
            FirebaseAuthActionResult.Error(RezonaSyncLogic.toGenericSignInError(e))
        }
    }

    private suspend fun maybeLinkPendingCredential(user: FirebaseUser) {
        val pending = pendingLink ?: return
        val result = runCatching { user.linkWithCredential(pending.pendingCredential).await() }
        if (result.isSuccess) {
            pendingLink = null
        } else {
            Log.w(TAG, "Pending credential link skipped/failed: ${result.exceptionOrNull()?.javaClass?.simpleName}")
        }
    }

    fun clearPendingLink() {
        pendingLink = null
    }

    suspend fun linkPendingAfterExistingSignIn(): Boolean {
        val user = getFirebaseAuthOrNull()?.currentUser ?: return false
        val pending = pendingLink ?: return true
        return try {
            user.linkWithCredential(pending.pendingCredential).await()
            pendingLink = null
            emitUserState(user, isConfigured = true)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Explicit link failed: ${e.javaClass.simpleName}")
            false
        }
    }

    suspend fun resendVerificationEmail(): FirebaseAuthActionResult {
        val user = getFirebaseAuthOrNull()?.currentUser
            ?: return FirebaseAuthActionResult.Error("No active verification session.")
        return try {
            user.sendEmailVerification().await()
            FirebaseAuthActionResult.Message("Verification email sent to ${user.email.orEmpty()}.")
        } catch (e: Throwable) {
            FirebaseAuthActionResult.Error(e.localizedMessage ?: "Failed to resend verification email.")
        }
    }

    suspend fun reloadAndCheckEmailVerified(): FirebaseAuthActionResult {
        val auth = getFirebaseAuthOrNull()
            ?: return FirebaseAuthActionResult.Error(FIREBASE_NOT_CONFIGURED_MSG)
        val user = auth.currentUser
            ?: return FirebaseAuthActionResult.Error("Session expired. Please sign in again.")
        return try {
            user.reload().await()
            val state = buildSessionState(auth.currentUser, isConfigured = true)
            _session.value = state
            if (state.isEmailVerified) {
                preferences.setFirebaseSignedInState(
                    uid = state.uid.orEmpty(),
                    email = state.email,
                    displayName = state.displayName,
                    provider = state.providerLabel
                )
                FirebaseAuthActionResult.SignedIn(state.uid.orEmpty(), state.email, state.displayName)
            } else {
                FirebaseAuthActionResult.Error("Email not verified yet. Open the link in your inbox, then tap \"I verified\".")
            }
        } catch (e: Throwable) {
            FirebaseAuthActionResult.Error(e.localizedMessage ?: "Unable to refresh verification status.")
        }
    }

    suspend fun sendPasswordResetEmail(email: String): FirebaseAuthActionResult {
        val auth = getFirebaseAuthOrNull()
            ?: return FirebaseAuthActionResult.Error(FIREBASE_NOT_CONFIGURED_MSG)
        val clean = email.trim().lowercase()
        if (!RezonaSyncLogic.isValidEmailFormat(clean)) {
            return FirebaseAuthActionResult.Error("Enter a valid email address.")
        }
        return try {
            auth.sendPasswordResetEmail(clean).await()
            FirebaseAuthActionResult.Message("If an account matches $clean, a password reset link has been sent.")
        } catch (e: Throwable) {
            Log.w(TAG, "Password reset error: ${e.javaClass.simpleName}")
            FirebaseAuthActionResult.Message("If an account matches $clean, a password reset link has been sent.")
        }
    }

    suspend fun reauthenticateWithPassword(password: String): Result<Unit> {
        val user = getFirebaseAuthOrNull()?.currentUser
            ?: return Result.failure(IllegalStateException("Not signed in."))
        val email = user.email ?: return Result.failure(IllegalStateException("Account has no email."))
        return runCatching {
            val cred = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(cred).await()
            Unit
        }
    }

    suspend fun reauthenticateWithGoogle(activityContext: Context): Result<Unit> {
        val user = getFirebaseAuthOrNull()?.currentUser
            ?: return Result.failure(IllegalStateException("Not signed in."))
        return when (val res = GoogleAuthManager.requestGoogleCredential(activityContext)) {
            is GoogleAuthManager.GoogleCredentialResult.Success -> runCatching {
                user.reauthenticate(res.authCredential).await()
                Unit
            }
            GoogleAuthManager.GoogleCredentialResult.Cancelled ->
                Result.failure(IllegalStateException("Re-authentication cancelled."))
            is GoogleAuthManager.GoogleCredentialResult.Error ->
                Result.failure(IllegalStateException(res.message))
        }
    }

    suspend fun deleteAuthenticatedFirebaseUser(): Result<Unit> {
        val user = getFirebaseAuthOrNull()?.currentUser
            ?: return Result.failure(IllegalStateException("Not signed in."))
        return runCatching {
            user.delete().await()
            pendingLink = null
            GoogleAuthManager.clearCredentialSession(appContext)
            emitUserState(null, isConfigured = true)
        }
    }

    suspend fun signOut() {
        pendingLink = null
        runCatching { getFirebaseAuthOrNull()?.signOut() }
        GoogleAuthManager.clearCredentialSession(appContext)
        _session.value = AuthSessionState(isFirebaseConfigured = getFirebaseAuthOrNull() != null)
        preferences.clearGoogleAccount()
    }

    companion object {
        private const val TAG = "FirebaseAuthManager"
        const val FIREBASE_NOT_CONFIGURED_MSG =
            "Firebase Auth is not configured yet. Add app/google-services.json from Firebase Console."

        @Volatile
        private var INSTANCE: FirebaseAuthManager? = null

        fun getInstance(context: Context): FirebaseAuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FirebaseAuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
