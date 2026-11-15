package com.example.monatechickensteak.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

sealed class GoogleSignInResult {
    data class Success(val email: String, val name: String) : GoogleSignInResult()
    data class Failure(val message: String) : GoogleSignInResult()
}

/**
 * Single sign-on with Google using Android's Credential Manager.
 * Needs a Web client ID from Firebase / Google Cloud Console, see docs/SSO_SETUP.md.
 */
object GoogleSignInHelper {

    /** False while the placeholder from strings.xml has not been replaced yet. */
    fun isConfigured(clientId: String): Boolean =
        clientId.isNotBlank() && !clientId.startsWith("YOUR_")

    suspend fun signIn(activity: Activity, serverClientId: String): GoogleSignInResult {
        if (!isConfigured(serverClientId)) {
            return GoogleSignInResult.Failure(
                "Google sign-in is not set up yet. Add the Web client ID (see docs/SSO_SETUP.md)."
            )
        }
        val manager = CredentialManager.create(activity)
        return try {
            try {
                // First try the quick account sheet, which lists the Google accounts already on the phone
                fetch(
                    manager, activity,
                    GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(serverClientId)
                        .build()
                )
            } catch (e: NoCredentialException) {
                // Nothing to offer there. Fall back to the full "Sign in with Google" screen,
                // which lets the person pick an account or add a new one.
                fetch(manager, activity, GetSignInWithGoogleOption.Builder(serverClientId).build())
            }
        } catch (e: GetCredentialCancellationException) {
            // Android also reports this when the SHA-1 / package is not registered, so say so
            android.util.Log.w("GoogleSignIn", "Cancelled: ${e.message}", e)
            GoogleSignInResult.Failure(
                "Sign-in cancelled. If you did not cancel it yourself, the SHA-1 of this app's " +
                        "signing key is probably not added in Firebase (see docs/SSO_SETUP.md)."
            )
        } catch (e: NoCredentialException) {
            GoogleSignInResult.Failure(
                "No Google account is available on this phone. Add one in Settings > Accounts " +
                        "(use an emulator with Google Play), then try again."
            )
        } catch (e: GetCredentialException) {
            // Show the real reason: it tells you whether the Web client ID, the SHA-1 or the phone is the problem
            android.util.Log.e("GoogleSignIn", "Credential error: ${e.type}", e)
            GoogleSignInResult.Failure(
                "Google sign-in failed: ${e.message ?: e.type}. " +
                        "Check the Web client ID and SHA-1 (docs/SSO_SETUP.md) and your connection."
            )
        } catch (e: GoogleIdTokenParsingException) {
            GoogleSignInResult.Failure("Could not read the Google account details.")
        }
    }

    private suspend fun fetch(
        manager: CredentialManager,
        activity: Activity,
        option: CredentialOption
    ): GoogleSignInResult {
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = manager.getCredential(activity, request).credential
        return if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            GoogleSignInResult.Success(google.id, google.displayName ?: "")
        } else {
            GoogleSignInResult.Failure("Google sign-in returned something unexpected. Please try again.")
        }
    }
}
