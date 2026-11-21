package com.nayama.music.auth

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Native Android Google Account picker helper.
 * Uses AccountManager so sideloaded debug/release APKs work cleanly without requiring a registered
 * GCP OAuth Web Client SHA-1 (zero DEVELOPER_ERROR). Music playback stays anonymous/cookie-free.
 */
object GoogleAuthManager {

    data class PickedGoogleAccount(
        val email: String,
        val displayName: String
    )

    @Suppress("DEPRECATION")
    fun createGoogleAccountChooserIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AccountManager.newChooseAccountIntent(
                /* selectedAccount = */ null,
                /* allowableAccounts = */ null,
                /* allowableAccountTypes = */ arrayOf("com.google"),
                /* descriptionOverrideText = */ "Choose a Google account for Rezona",
                /* addAccountAuthTokenType = */ null,
                /* addAccountRequiredFeatures = */ null,
                /* addAccountOptions = */ null
            )
        } else {
            AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                false,
                null,
                null,
                null,
                null
            )
        }
    }

    fun getDeviceGoogleAccounts(context: Context): List<PickedGoogleAccount> {
        return runCatching {
            val manager = AccountManager.get(context)
            manager.getAccountsByType("com.google").mapNotNull { account ->
                val email = account.name.trim()
                if (email.isBlank()) null
                else PickedGoogleAccount(
                    email = email,
                    displayName = deriveDisplayNameFromEmail(email)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun parseResult(data: Intent?): PickedGoogleAccount? {
        val email = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return PickedGoogleAccount(
            email = email,
            displayName = deriveDisplayNameFromEmail(email)
        )
    }

    fun deriveDisplayNameFromEmail(email: String): String {
        val local = email.substringBefore("@").trim()
        if (local.isBlank()) return "Google User"
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
