package com.snigtus.dost

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class FirebaseAuthentication(context: Context) {
    private val appContext = context.applicationContext
    private val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()

    val currentUser: FirebaseUser?
        get() = auth?.currentUser

    val isConfigured: Boolean
        get() = auth != null

    suspend fun signIn(email: String, password: String): FirebaseUser =
        requireAuth().signInWithEmailAndPassword(email.trim(), password).awaitResult().user
            ?: error("Firebase returned no user.")

    suspend fun createAccount(email: String, password: String): FirebaseUser =
        requireAuth().createUserWithEmailAndPassword(email.trim(), password).awaitResult().user
            ?: error("Firebase returned no user.")

    suspend fun sendVerificationEmail(user: FirebaseUser = requireUser()) {
        user.sendEmailVerification().awaitResult()
    }

    suspend fun sendPasswordReset(email: String) {
        requireAuth().sendPasswordResetEmail(email.trim()).awaitResult()
    }

    suspend fun reloadCurrentUser(): FirebaseUser {
        val user = requireUser()
        user.reload().awaitResult()
        return requireUser()
    }

    suspend fun signInWithGoogle(activity: Activity): FirebaseUser {
        val clientIdResource = appContext.resources.getIdentifier(
            "default_web_client_id",
            "string",
            appContext.packageName
        )
        if (clientIdResource == 0) throw FirebaseAuthenticationNotConfiguredException()

        val clientId = appContext.getString(clientIdResource)
        val googleOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        return requireAuth().signInWithCredential(firebaseCredential).awaitResult().user
            ?: error("Firebase returned no user.")
    }

    fun signOut() {
        auth?.signOut()
    }

    private fun requireAuth(): FirebaseAuth =
        auth ?: throw FirebaseAuthenticationNotConfiguredException()

    private fun requireUser(): FirebaseUser =
        requireAuth().currentUser ?: error("No signed-in Firebase account.")

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: IllegalStateException("Firebase request failed."))
            }
        }
    }
}

class FirebaseAuthenticationNotConfiguredException :
    IllegalStateException("Firebase configuration is missing.")

fun authErrorMessage(language: AppLanguage, code: String?): String {
    val message = when (code) {
        "CONFIGURATION_MISSING" -> Triple(
            "Firebase সেটআপ করা নেই। পরে আবার চেষ্টা করুন।",
            "Firebase सेटअप नहीं है। बाद में फिर कोशिश करें।",
            "Firebase isn't set up yet. Please try again later."
        )
        "ERROR_INVALID_EMAIL" -> Triple(
            "ইমেইল ঠিকানাটি সঠিক নয়।",
            "ईमेल पता सही नहीं है।",
            "That email address doesn't look right."
        )
        "ERROR_EMAIL_ALREADY_IN_USE" -> Triple(
            "এই ইমেইলে অ্যাকাউন্ট আছে। সাইন ইন করার চেষ্টা করুন।",
            "इस ईमेल पर पहले से खाता है। साइन इन करें।",
            "An account already uses this email. Try signing in."
        )
        "ERROR_WEAK_PASSWORD" -> Triple(
            "আরও শক্তিশালী পাসওয়ার্ড দিন (কমপক্ষে ৬ অক্ষর)।",
            "मज़बूत पासवर्ड दें (कम से कम 6 अक्षर)।",
            "Use a stronger password (at least 6 characters)."
        )
        "ERROR_NETWORK_REQUEST_FAILED" -> Triple(
            "ইন্টারনেট সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।",
            "इंटरनेट कनेक्शन जाँचकर फिर कोशिश करें।",
            "Check your internet connection and try again."
        )
        else -> Triple(
            "এখনই সাইন ইন করা যাচ্ছে না। আবার চেষ্টা করুন।",
            "अभी साइन इन नहीं हो सका। फिर कोशिश करें।",
            "Couldn't complete that just now. Please try again."
        )
    }
    return when (language) {
        AppLanguage.BANGLA -> message.first
        AppLanguage.HINDI -> message.second
        AppLanguage.ENGLISH -> message.third
    }
}

fun Throwable.authErrorCode(): String? = when (this) {
    is FirebaseAuthenticationNotConfiguredException -> "CONFIGURATION_MISSING"
    is GetCredentialCancellationException -> "CANCELED"
    is FirebaseAuthException -> errorCode
    else -> null
}
