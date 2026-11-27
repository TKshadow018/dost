package com.snigtus.dost

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.play.agesignals.AgeSignalsAccessRequest
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.play.agesignals.model.AgeSignalsStatus
import kotlinx.coroutines.tasks.await
import java.util.Calendar

internal sealed interface PlayAgeAccess {
    data object Underage : PlayAgeAccess
    data object Unknown : PlayAgeAccess
    data class SharedAgeRange(val lower: Int?, val upper: Int?) : PlayAgeAccess
}

internal fun ageSignalBlocksAccess(shared: Boolean, ageLower: Int?): Boolean =
    shared && ageLower != null && ageLower < MINIMUM_ACCOUNT_AGE

internal fun ageSignalCohort(lower: Int?, upper: Int?): DostAgeCohort? {
    if (lower != null && lower !in 0..MAX_PROFILE_AGE) return null
    if (upper != null && upper !in 0..MAX_PROFILE_AGE) return null
    if (lower != null && upper != null && upper < lower) return null
    return when {
        lower != null && lower >= ROMANCE_MINIMUM_AGE -> DostAgeCohort.ADULT
        lower != null && lower >= MINIMUM_ACCOUNT_AGE && upper != null && upper < ROMANCE_MINIMUM_AGE ->
            DostAgeCohort.TEEN
        else -> null
    }
}

internal class PlayAgeSignalsEligibility {
    suspend fun check(activity: Activity): PlayAgeAccess {
        val manager = AgeSignalsManagerFactory.create(activity.applicationContext)
        val access = manager.requestAgeSignalsAccess(
            AgeSignalsAccessRequest.builder()
                .setActivity(activity)
                .build()
        ).await()

        if (access.ageSignalsStatus() != AgeSignalsStatus.SHARED) return PlayAgeAccess.Unknown
        val signal = manager.checkAgeSignals(AgeSignalsRequest.builder().build()).await()
        return if (ageSignalBlocksAccess(true, signal.ageLower())) {
            PlayAgeAccess.Underage
        } else {
            PlayAgeAccess.SharedAgeRange(signal.ageLower(), signal.ageUpper())
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
    var access by remember { mutableStateOf<PlayAgeAccess>(PlayAgeAccess.Unknown) }
    var month by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var ageSignalChecked by remember { mutableStateOf(false) }
    var unlocked by remember {
        mutableStateOf(preferences.getBoolean(AGE_CONFIRMATION_COMPLETE_KEY, false))
    }
    var blocked by remember {
        mutableStateOf(preferences.getBoolean(UNDER_13_LOCK_KEY, false))
    }

    LaunchedEffect(activity) {
        if (blocked || unlocked) {
            ageSignalChecked = true
            return@LaunchedEffect
        }
        val result = try {
            eligibility.check(activity)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PlayAgeAccess.Unknown
        }
        ageSignalChecked = true
        if (result == PlayAgeAccess.Underage) {
            preferences.edit()
                .putBoolean(UNDER_13_LOCK_KEY, true)
                .remove(BIRTH_MONTH_KEY)
                .remove(BIRTH_YEAR_KEY)
                .remove("user_age")
                .remove(AGE_CONFIRMATION_COMPLETE_KEY)
                .apply()
            unlocked = false
            blocked = true
        }
        access = result
    }

    if (unlocked) {
        content()
        return
    }

    val under13 = blocked || access == PlayAgeAccess.Underage
    val title = when (language) {
        AppLanguage.ENGLISH -> if (under13) "Dost is for ages 13+" else "A little about you"
        AppLanguage.BANGLA -> if (under13) "দোস্ত ১৩ বছর বা তার বেশি বয়সীদের জন্য" else "আপনার সম্পর্কে একটু জানি"
        AppLanguage.HINDI -> if (under13) "दोस्त 13 वर्ष और उससे अधिक उम्र के लोगों के लिए है" else "आपके बारे में थोड़ी जानकारी"
    }
    val message = when (language) {
        AppLanguage.ENGLISH -> if (under13) {
            "Thanks for your interest. You can use Dost when you are 13 or older."
        } else {
            "Enter your birth month and year so we can provide an age-appropriate experience."
        }
        AppLanguage.BANGLA -> if (under13) {
            "আগ্রহের জন্য ধন্যবাদ। ১৩ বছর বা তার বেশি হলে দোস্ত ব্যবহার করতে পারবেন।"
        } else {
            "বয়স-উপযোগী অভিজ্ঞতা দিতে আপনার জন্মের মাস ও বছর লিখুন।"
        }
        AppLanguage.HINDI -> if (under13) {
            "रुचि दिखाने के लिए धन्यवाद। 13 वर्ष या उससे अधिक होने पर आप दोस्त इस्तेमाल कर सकते हैं।"
        } else {
            "उम्र के अनुसार अनुभव देने के लिए अपना जन्म-महीना और वर्ष दर्ज करें।"
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (!under13) {
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = month,
                    onValueChange = { month = it.filter { digit -> digit in '0'..'9' }.take(2); error = null },
                    label = {
                        Text(when (language) {
                            AppLanguage.ENGLISH -> "Birth month (1–12)"
                            AppLanguage.BANGLA -> "জন্মের মাস (১–১২)"
                            AppLanguage.HINDI -> "जन्म का महीना (1–12)"
                        })
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it.filter { digit -> digit in '0'..'9' }.take(4); error = null },
                    label = {
                        Text(when (language) {
                            AppLanguage.ENGLISH -> "Birth year"
                            AppLanguage.BANGLA -> "জন্মের বছর"
                            AppLanguage.HINDI -> "जन्म का वर्ष"
                        })
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        if (!ageSignalChecked || blocked) return@Button
                        val today = Calendar.getInstance()
                        val age = birthMonthYearAge(
                            month,
                            year,
                            today.get(Calendar.MONTH) + 1,
                            today.get(Calendar.YEAR)
                        )
                        if (age == null) {
                            error = when (language) {
                                AppLanguage.ENGLISH -> "Enter a valid birth month and year."
                                AppLanguage.BANGLA -> "জন্মের সঠিক মাস ও বছর লিখুন।"
                                AppLanguage.HINDI -> "जन्म का सही महीना और वर्ष दर्ज करें।"
                            }
                        } else if (age < MINIMUM_ACCOUNT_AGE) {
                            month = ""
                            year = ""
                            preferences.edit()
                                .putBoolean(UNDER_13_LOCK_KEY, true)
                                .remove(BIRTH_MONTH_KEY)
                                .remove(BIRTH_YEAR_KEY)
                                .remove("user_age")
                                .apply()
                            blocked = true
                        } else if (ageSignalCohort(
                                (access as? PlayAgeAccess.SharedAgeRange)?.lower,
                                (access as? PlayAgeAccess.SharedAgeRange)?.upper
                            ) == DostAgeCohort.TEEN && age >= ROMANCE_MINIMUM_AGE
                        ) {
                            error = when (language) {
                                AppLanguage.ENGLISH -> "The information could not be confirmed. Please check and try again."
                                AppLanguage.BANGLA -> "তথ্যটি নিশ্চিত করা যায়নি। যাচাই করে আবার চেষ্টা করুন।"
                                AppLanguage.HINDI -> "जानकारी की पुष्टि नहीं हो सकी। जाँचकर फिर कोशिश करें।"
                            }
                        } else {
                            preferences.edit()
                                .putString(BIRTH_MONTH_KEY, month.trim())
                                .putString(BIRTH_YEAR_KEY, year.trim())
                                .putString(AGE_COHORT_KEY, ageCohortForAge(age)?.name?.lowercase())
                                .putString("user_age", age.toString())
                                .putBoolean(AGE_CONFIRMATION_COMPLETE_KEY, true)
                                .apply()
                            unlocked = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = ageSignalChecked && !blocked
                ) {
                    Text(when (language) {
                        AppLanguage.ENGLISH -> "Continue"
                        AppLanguage.BANGLA -> "চালিয়ে যান"
                        AppLanguage.HINDI -> "जारी रखें"
                    })
                }
            }
        }
    }
}

internal const val AGE_CONFIRMATION_COMPLETE_KEY = "age_confirmation_complete"
internal const val UNDER_13_LOCK_KEY = "under_13_lock"
internal const val BIRTH_MONTH_KEY = "birth_month"
internal const val BIRTH_YEAR_KEY = "birth_year"
internal const val AGE_COHORT_KEY = "dost_age_cohort"
