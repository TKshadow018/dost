package com.snigtus.dost

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseAuthRepository(context: Context) {
    private val auth: FirebaseAuth? = initializeAuth(context.applicationContext)

    val emailSignInEnabled: Boolean
        get() = auth != null

    val googleSignInEnabled: Boolean
        get() = auth != null && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    val currentAccount: AuthAccount?
        get() = auth?.currentUser?.toAuthAccount()

    suspend fun createAccount(email: String, password: String): AuthAccount =
        requireAuth().createUserWithEmailAndPassword(email.trim(), password).awaitTask().toAccount()

    suspend fun signIn(email: String, password: String): AuthAccount =
        requireAuth().signInWithEmailAndPassword(email.trim(), password).awaitTask().toAccount()

    suspend fun sendPasswordReset(email: String) {
        requireAuth().sendPasswordResetEmail(email.trim()).awaitTask()
    }

    suspend fun sendEmailVerification() {
        val user = requireAuth().currentUser ?: throw IllegalStateException("No signed-in Firebase account.")
        user.sendEmailVerification().awaitTask()
    }

    suspend fun reloadCurrentAccount(): AuthAccount {
        val user = requireAuth().currentUser ?: throw IllegalStateException("No signed-in Firebase account.")
        user.reload().awaitTask()
        return user.toAuthAccount()
    }

    suspend fun signInWithGoogle(activity: Activity): AuthAccount {
        check(googleSignInEnabled) { "Google sign-in is not configured." }
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw IllegalStateException("Google did not return an ID token.")
        }
        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return requireAuth()
            .signInWithCredential(GoogleAuthProvider.getCredential(token, null))
            .awaitTask()
            .toAccount()
    }

    fun signOut() {
        auth?.signOut()
    }

    private fun requireAuth(): FirebaseAuth =
        auth ?: throw IllegalStateException("Firebase Authentication is not configured.")

    private fun initializeAuth(context: Context): FirebaseAuth? {
        if (BuildConfig.FIREBASE_API_KEY.isBlank()
            || BuildConfig.FIREBASE_APP_ID.isBlank()
            || BuildConfig.FIREBASE_PROJECT_ID.isBlank()
            || BuildConfig.FIREBASE_SENDER_ID.isBlank()
        ) return null

        return runCatching {
            val options = FirebaseOptions.Builder()
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build()
            val app = FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
                ?: FirebaseApp.initializeApp(context, options)
            FirebaseAuth.getInstance(app)
        }.getOrNull()
    }

    private fun com.google.firebase.auth.AuthResult.toAccount(): AuthAccount {
        val user = this.user ?: throw IllegalStateException("Authentication returned no user.")
        return user.toAuthAccount()
    }

    private fun FirebaseUser.toAuthAccount(): AuthAccount = AuthAccount(
        uid = uid,
        email = email.orEmpty(),
        displayName = displayName.orEmpty(),
        emailVerified = isEmailVerified,
        googleAccount = providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
    )
}

fun authenticationAccountMismatchText(language: AppLanguage): String = when (language) {
    AppLanguage.BANGLA -> "এই ডিভাইসের তথ্য অন্য একটি অ্যাকাউন্টের। আগের অ্যাকাউন্টে লগ ইন করুন—তোমার চ্যাট এখানেই আছে।"
    AppLanguage.HINDI -> "इस डिवाइस का डेटा दूसरे खाते से जुड़ा है। उसी खाते से लॉग इन करें—आपकी चैट यहीं सुरक्षित है।"
    AppLanguage.ENGLISH -> "This device’s data belongs to another account. Sign in with that account; your local chats are still here."
}

fun authenticationErrorText(language: AppLanguage, error: Throwable): String {
    val code = (error as? FirebaseAuthException)?.errorCode.orEmpty()
    val bangla: String
    val hindi: String
    val english: String
    when {
        error is GetCredentialCancellationException -> {
            bangla = "Google সাইন-ইন বাতিল করা হয়েছে।"
            hindi = "Google साइन-इन रद्द कर दिया गया।"
            english = "Google sign-in was cancelled."
        }
        code == "ERROR_INVALID_EMAIL" -> {
            bangla = "একটি সঠিক ইমেইল ঠিকানা লিখুন।"
            hindi = "कृपया मान्य ईमेल पता लिखें।"
            english = "Enter a valid email address."
        }
        code == "ERROR_EMAIL_ALREADY_IN_USE" -> {
            bangla = "এই ইমেইলে অ্যাকাউন্ট আছে। লগ ইন করুন।"
            hindi = "इस ईमेल से खाता पहले से है। लॉग इन करें।"
            english = "An account already uses this email. Try signing in."
        }
        code == "ERROR_WEAK_PASSWORD" -> {
            bangla = "আরও শক্তিশালী পাসওয়ার্ড বেছে নিন।"
            hindi = "कृपया थोड़ा मज़बूत पासवर्ड चुनें।"
            english = "Choose a stronger password."
        }
        code in setOf("ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS") -> {
            bangla = "ইমেইল বা পাসওয়ার্ড মিলছে না। আবার চেষ্টা করুন।"
            hindi = "ईमेल या पासवर्ड मेल नहीं खाता। फिर कोशिश करें।"
            english = "That email and password don't match. Try again."
        }
        code == "ERROR_NETWORK_REQUEST_FAILED" -> {
            bangla = "ইন্টারনেট সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।"
            hindi = "इंटरनेट कनेक्शन जाँचकर फिर कोशिश करें।"
            english = "Check your internet connection and try again."
        }
        else -> {
            bangla = "এখন সাইন ইন করা যাচ্ছে না। আবার চেষ্টা করুন।"
            hindi = "अभी साइन इन नहीं हो पा रहा है। फिर कोशिश करें।"
            english = "We couldn't sign you in just now. Please try again."
        }
    }
    return when (language) {
        AppLanguage.BANGLA -> bangla
        AppLanguage.HINDI -> hindi
        AppLanguage.ENGLISH -> english
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        if (task.isSuccessful) continuation.resume(task.result)
        else continuation.resumeWithException(task.exception ?: IllegalStateException("Authentication failed."))
    }
}
