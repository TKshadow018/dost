package com.snigtus.dost

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.play.agesignals.AgeSignalsAccessRequest
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.play.agesignals.model.AgeSignalsStatus
import kotlinx.coroutines.tasks.await

internal sealed interface PlayAgeAccess {
    data object Checking : PlayAgeAccess
    data object Allowed : PlayAgeAccess
    data object Underage : PlayAgeAccess
    data object NotShared : PlayAgeAccess
    data object VerificationRequired : PlayAgeAccess
    data class Unavailable(val detail: String) : PlayAgeAccess
}

internal fun adultAgeSignalAllowsAccess(shared: Boolean, ageLower: Int?, ageUpper: Int?): Boolean =
    shared && ageLower != null && ageLower >= ROMANCE_MINIMUM_AGE && (ageUpper == null || ageUpper >= ageLower)

internal fun appAgeSignalAllowsAccess(shared: Boolean, ageLower: Int?, ageUpper: Int?): Boolean =
    shared && ageLower != null && ageLower >= MINIMUM_ACCOUNT_AGE && (ageUpper == null || ageUpper >= ageLower)

internal fun canSelfAttestAdultAge(access: PlayAgeAccess): Boolean =
    access == PlayAgeAccess.NotShared || access is PlayAgeAccess.Unavailable

internal class PlayAgeSignalsEligibility {
    suspend fun check(activity: Activity): PlayAgeAccess {
        val manager = AgeSignalsManagerFactory.create(activity.applicationContext)
        val access = manager.requestAgeSignalsAccess(
            AgeSignalsAccessRequest.builder()
                .setActivity(activity)
                .build()
        ).await()

        when (access.ageSignalsStatus()) {
            AgeSignalsStatus.NOT_SHARED -> return PlayAgeAccess.NotShared
            AgeSignalsStatus.VERIFICATION_REQUIRED -> return PlayAgeAccess.VerificationRequired
            AgeSignalsStatus.SHARED -> Unit
            else -> return PlayAgeAccess.Unavailable("Google Play returned an unknown age-sharing status.")
        }

        val signal = manager.checkAgeSignals(AgeSignalsRequest.builder().build()).await()
        return if (appAgeSignalAllowsAccess(true, signal.ageLower(), signal.ageUpper())) {
            PlayAgeAccess.Allowed
        } else {
            PlayAgeAccess.Underage
        }
    }
}

@Composable
internal fun PlayAgeSignalsGate(
    activity: Activity,
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val eligibility = remember(activity) { PlayAgeSignalsEligibility() }
    val preferences = remember(activity) { activeProfilePreferences(activity) }
    var attempt by remember { mutableIntStateOf(0) }
    var access by remember { mutableStateOf<PlayAgeAccess>(PlayAgeAccess.Checking) }

    LaunchedEffect(attempt, activity) {
        access = PlayAgeAccess.Checking
        val result = try {
            eligibility.check(activity)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            PlayAgeAccess.Unavailable(
                error.message?.take(160)?.ifBlank { null }
                    ?: "Google Play age signals are unavailable."
            )
        }
        if (result == PlayAgeAccess.Underage) {
            preferences.edit().remove(ADULT_SELF_ATTESTATION_KEY).apply()
        }
        access = result
    }

    val selfAttested = canSelfAttestAdultAge(access)
        && preferences.getBoolean(ADULT_SELF_ATTESTATION_KEY, false)
    if (access == PlayAgeAccess.Allowed || selfAttested) {
        content()
    } else {
        PlayAgeSignalsScreen(
            language = language,
            access = access,
            onRetry = { attempt++ },
            onConfirmAdult = {
                preferences.edit().putBoolean(ADULT_SELF_ATTESTATION_KEY, true).apply()
                access = PlayAgeAccess.Allowed
            }
        )
    }
}

private const val ADULT_SELF_ATTESTATION_KEY = "adult_self_attestation"

@Composable
private fun PlayAgeSignalsScreen(
    language: AppLanguage,
    access: PlayAgeAccess,
    onRetry: () -> Unit,
    onConfirmAdult: () -> Unit
) {
    val checking = access == PlayAgeAccess.Checking
    val title = when (language) {
        AppLanguage.ENGLISH -> "Dost is for people aged 13 and older"
        AppLanguage.BANGLA -> "দোস্ত ১৩ বছর বা তার বেশি বয়সীদের জন্য"
        AppLanguage.HINDI -> "Dost 13 वर्ष और उससे अधिक आयु वालों के लिए है"
    }
    val message = when (access) {
        PlayAgeAccess.Checking -> when (language) {
            AppLanguage.ENGLISH -> "Checking your age range with Google Play…"
            AppLanguage.BANGLA -> "Google Play-এ আপনার বয়সের সীমা যাচাই করা হচ্ছে…"
            AppLanguage.HINDI -> "Google Play से आपकी आयु सीमा जाँची जा रही है…"
        }
        PlayAgeAccess.Allowed -> ""
        PlayAgeAccess.Underage -> when (language) {
            AppLanguage.ENGLISH -> "Dost is available only to people aged 13 or older."
            AppLanguage.BANGLA -> "দোস্ত শুধুমাত্র ১৩ বছর বা তার বেশি বয়সীদের জন্য।"
            AppLanguage.HINDI -> "Dost केवल 13 वर्ष या उससे अधिक आयु वालों के लिए उपलब्ध है।"
        }
        PlayAgeAccess.NotShared -> when (language) {
            AppLanguage.ENGLISH -> "Google Play did not share an age range. You can continue by confirming that you are 13 or older."
            AppLanguage.BANGLA -> "Google Play বয়সের সীমা শেয়ার করেনি। ১৩ বছর বা তার বেশি বয়স নিশ্চিত করে এগিয়ে যেতে পারেন।"
            AppLanguage.HINDI -> "Google Play ने आयु सीमा साझा नहीं की। आप पुष्टि कर सकते हैं कि आपकी उम्र 13 वर्ष या उससे अधिक है।"
        }
        PlayAgeAccess.VerificationRequired -> when (language) {
            AppLanguage.ENGLISH -> "Complete the required age steps in Google Play, return here, and try again."
            AppLanguage.BANGLA -> "Google Play-এ প্রয়োজনীয় বয়স যাচাই সম্পন্ন করে এখানে ফিরে আবার চেষ্টা করুন।"
            AppLanguage.HINDI -> "Google Play में आवश्यक आयु प्रक्रिया पूरी करें, फिर यहाँ लौटकर दोबारा कोशिश करें।"
        }
        is PlayAgeAccess.Unavailable -> when (language) {
            AppLanguage.ENGLISH -> "Dost could not get an age range from Google Play. Retry, or continue by confirming that you are 13 or older. Details: ${access.detail}"
            AppLanguage.BANGLA -> "Google Play থেকে বয়সের সীমা পাওয়া যায়নি। আবার চেষ্টা করুন অথবা ১৩ বছর বা তার বেশি বয়স নিশ্চিত করে এগিয়ে যান। বিবরণ: ${access.detail}"
            AppLanguage.HINDI -> "Google Play से आयु सीमा नहीं मिली। फिर कोशिश करें या पुष्टि करें कि आपकी उम्र 13 वर्ष या उससे अधिक है। विवरण: ${access.detail}"
        }
    }
    val retryLabel = when (language) {
        AppLanguage.ENGLISH -> "Try again"
        AppLanguage.BANGLA -> "আবার চেষ্টা করুন"
        AppLanguage.HINDI -> "फिर कोशिश करें"
    }
    val confirmAdultLabel = when (language) {
        AppLanguage.ENGLISH -> "I am 13 or older"
        AppLanguage.BANGLA -> "আমার বয়স ১৩ বছর বা তার বেশি"
        AppLanguage.HINDI -> "मेरी उम्र 13 वर्ष या उससे अधिक है"
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (checking) {
                CircularProgressIndicator()
                Spacer(Modifier.height(24.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (!checking) {
                Spacer(Modifier.height(24.dp))
                Button(onClick = onRetry) { Text(retryLabel) }
                if (canSelfAttestAdultAge(access)) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onConfirmAdult) { Text(confirmAdultLabel) }
                }
            }
        }
    }
}
