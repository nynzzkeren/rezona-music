package com.nayama.music.auth

import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException

/**
 * Sign in with Google helper using Jetpack Credential Manager (`androidx.credentials`)
 * and `com.google.android.libraries.identity.googleid:googleid`.
 * Never falls back to fake/local-only accounts.
 */
object GoogleAuthManager {

    sealed class GoogleCredentialResult {
        data class Success(
            val idToken: String,
            val authCredential: AuthCredential,
            val email: String?,
            val displayName: String?
        ) : GoogleCredentialResult()

        data object Cancelled : GoogleCredentialResult()
        data class Error(val message: String) : GoogleCredentialResult()
    }

    fun resolveWebClientId(context: Context): String? {
        val resId = context.resources.getIdentifier(
            "default_web_client_id",
            "string",
            context.packageName
        )
        if (resId == 0) return null
        val value = runCatching { context.getString(resId).trim() }.getOrNull().orEmpty()
        return value.takeIf { it.isNotBlank() && it.contains(".apps.googleusercontent.com") }
    }

    suspend fun requestGoogleCredential(activityContext: Context): GoogleCredentialResult {
        val webClientId = resolveWebClientId(activityContext)
            ?: return GoogleCredentialResult.Error(
                "Google Sign-In is not configured yet. Add google-services.json with OAuth Web Client ID and register SHA-1/SHA-256 in Firebase Console."
            )

        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val parsed = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCred = GoogleAuthProvider.getCredential(parsed.idToken, null)
                GoogleCredentialResult.Success(
                    idToken = parsed.idToken,
                    authCredential = firebaseCred,
                    email = parsed.id,
                    displayName = parsed.displayName
                )
            } else {
                GoogleCredentialResult.Error("Unsupported credential returned by Google.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: GetCredentialCancellationException) {
            GoogleCredentialResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleCredentialResult.Error(
                "No Google account available via Credential Manager. Verify a Google account is signed in on this device and SHA-1/SHA-256 match Firebase Console."
            )
        } catch (e: Throwable) {
            GoogleCredentialResult.Error(
                e.localizedMessage ?: "Google Sign-In failed. Check Play Services and Firebase configuration."
            )
        }
    }

    suspend fun clearCredentialSession(context: Context) {
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    fun deriveDisplayNameFromEmail(email: String): String {
        val local = email.substringBefore("@").trim()
        if (local.isBlank()) return "Rezona Listener"
        return local
            .split('.', '_', '-', '+')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                }
            }
            .ifBlank { local }
    }
}
