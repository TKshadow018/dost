package com.snigtus.dost

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SignInScreen(
    language: AppLanguage,
    mode: AuthMode,
    loading: Boolean,
    error: String?,
    status: String?,
    verifiedEmail: String,
    onSubmit: (email: String, password: String, createAccount: Boolean) -> Unit,
    onGoogleSignIn: () -> Unit,
    onPasswordReset: (email: String) -> Unit,
    onResendVerification: () -> Unit,
    onCheckVerification: () -> Unit,
    onModeChange: (AuthMode) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit
) {
    var email by rememberSaveable { mutableStateOf(verifiedEmail) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<AuthValidationError?>(null) }
    val creatingAccount = mode == AuthMode.CREATE_ACCOUNT
    val verifying = mode == AuthMode.VERIFY_EMAIL

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.size(12.dp))
            Image(
                painter = painterResource(R.drawable.logo1),
                contentDescription = translated(language, "Dost", "দোস্ত", "दोस्त"),
                modifier = Modifier.size(108.dp).clip(CircleShape)
            )
            Text(
                text = translated(language, "A little closer, every day.", "প্রতিদিন একটু কাছে।", "हर दिन, थोड़ा और करीब।"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 18.dp)
            )
            Text(
                text = translated(language, "A warm space for the conversations that matter.", "গুরুত্বপূর্ণ কথাগুলোর জন্য একটি আপন জায়গা।", "ज़रूरी बातों के लिए एक अपनापन भरी जगह।"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 22.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (verifying) {
                        Text(
                            translated(language, "Check your inbox", "আপনার ইনবক্স দেখুন", "अपना इनबॉक्स देखें"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            translated(
                                language,
                                "We sent a verification link to $verifiedEmail. Verify your email before continuing.",
                                "$verifiedEmail ঠিকানায় একটি যাচাইকরণ লিংক পাঠানো হয়েছে। এগিয়ে যাওয়ার আগে ইমেইল যাচাই করুন।",
                                "$verifiedEmail पर सत्यापन लिंक भेजा है। आगे बढ़ने से पहले ईमेल सत्यापित करें।"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(
                            onClick = onCheckVerification,
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
                        ) {
                            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(translated(language, "I've verified my email", "ইমেইল যাচাই করেছি", "मैंने ईमेल सत्यापित किया"))
                        }
                        OutlinedButton(
                            onClick = onResendVerification,
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
                        ) { Text(translated(language, "Resend verification email", "যাচাইকরণ ইমেইল আবার পাঠান", "सत्यापन ईमेल फिर भेजें")) }
                    } else {
                        Text(
                            text = if (creatingAccount) {
                                translated(language, "Create your account", "আপনার অ্যাকাউন্ট তৈরি করুন", "अपना खाता बनाएँ")
                            } else {
                                translated(language, "Welcome back", "আবার স্বাগতম", "वापस स्वागत है")
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; validationError = null },
                            label = { Text(translated(language, "Email", "ইমেইল", "ईमेल")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            enabled = !loading
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; validationError = null },
                            label = { Text(translated(language, "Password", "পাসওয়ার্ড", "पासवर्ड")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            enabled = !loading,
                            trailingIcon = {
                                TextButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    enabled = !loading
                                ) {
                                    Text(
                                        text = if (passwordVisible) translated(language, "Hide", "লুকান", "छिपाएँ") else translated(language, "Show", "দেখান", "दिखाएँ"),
                                    )
                                }
                            }
                        )
                        validationError?.let {
                            Text(authValidationText(it, language), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        status?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Button(
                            onClick = {
                                val invalid = authValidationError(email, password, creatingAccount)
                                validationError = invalid
                                if (invalid == null) onSubmit(email.trim(), password, creatingAccount)
                            },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
                        ) {
                            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(
                                if (creatingAccount) translated(language, "Create account", "অ্যাকাউন্ট তৈরি করুন", "खाता बनाएँ")
                                else translated(language, "Sign in", "সাইন ইন", "साइन इन")
                            )
                        }
                        if (!creatingAccount) {
                            TextButton(
                                onClick = {
                                    if (!isValidAuthEmail(email)) validationError = AuthValidationError.INVALID_EMAIL
                                    else onPasswordReset(email.trim())
                                },
                                enabled = !loading,
                                modifier = Modifier.align(Alignment.End).defaultMinSize(minHeight = 48.dp)
                            ) { Text(translated(language, "Forgot password?", "পাসওয়ার্ড ভুলে গেছেন?", "पासवर्ड भूल गए?")) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f))
                            Text(
                                translated(language, "  or  ", "  অথবা  ", "  या  "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(Modifier.weight(1f))
                        }
                        OutlinedButton(
                            onClick = onGoogleSignIn,
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
                        ) {
                            Text(translated(language, "Continue with Google", "Google দিয়ে চালিয়ে যান", "Google से जारी रखें"))
                        }
                        TextButton(
                            onClick = {
                                validationError = null
                                onModeChange(if (creatingAccount) AuthMode.SIGN_IN else AuthMode.CREATE_ACCOUNT)
                            },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(
                                if (creatingAccount) {
                                    translated(language, "Already have an account? Sign in", "আগে থেকেই অ্যাকাউন্ট আছে? সাইন ইন করুন", "पहले से खाता है? साइन इन करें")
                                } else {
                                    translated(language, "New to Dost? Create an account", "দোস্তে নতুন? অ্যাকাউন্ট তৈরি করুন", "Dost पर नए हैं? खाता बनाएँ")
                                }
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                AppLanguage.entries.forEach { option ->
                    TextButton(
                        onClick = { onLanguageChange(option) },
                        enabled = option != language,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(
                            when (option) {
                                AppLanguage.ENGLISH -> "English"
                                AppLanguage.BANGLA -> "বাংলা"
                                AppLanguage.HINDI -> "हिन्दी"
                            }
                        )
                    }
                }
            }
            Text(
                localizedAuthText(language, "Your profile and chats stay on this device.", "আপনার প্রোফাইল ও কথোপকথন এই ডিভাইসেই থাকবে।", "आपकी प्रोफ़ाइल और चैट इसी डिवाइस पर रहेंगी।"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp)
            )
        }
    }
}

private fun authValidationText(error: AuthValidationError, language: AppLanguage): String = when (error) {
    AuthValidationError.INVALID_EMAIL -> translated(language, "Enter a valid email address.", "সঠিক ইমেইল ঠিকানা লিখুন।", "सही ईमेल पता लिखें।")
    AuthValidationError.PASSWORD_REQUIRED -> translated(language, "Enter your password.", "আপনার পাসওয়ার্ড লিখুন।", "अपना पासवर्ड लिखें।")
    AuthValidationError.PASSWORD_TOO_SHORT -> translated(language, "Use at least 6 characters for your password.", "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।", "पासवर्ड कम से कम 6 अक्षरों का होना चाहिए।")
}
