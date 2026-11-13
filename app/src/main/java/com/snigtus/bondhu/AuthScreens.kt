package com.snigtus.dost

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.util.Patterns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.snigtus.dost.ui.theme.DostAuthBorder
import com.snigtus.dost.ui.theme.DostAuthCanvas
import com.snigtus.dost.ui.theme.DostAuthInk
import com.snigtus.dost.ui.theme.DostAuthMuted
import com.snigtus.dost.ui.theme.DostAuthTint
import com.snigtus.dost.ui.theme.DostTeal

@Composable
fun AuthScreen(
    language: AppLanguage,
    initialEmail: String,
    mode: AuthMode,
    onModeChange: (AuthMode) -> Unit,
    requestState: AuthRequestState,
    emailSignInEnabled: Boolean,
    googleSignInEnabled: Boolean,
    onSignIn: (String, String) -> Unit,
    onCreateAccount: (String, String) -> Unit,
    onPasswordReset: (String) -> Unit,
    onResendVerification: () -> Unit,
    onCheckVerification: () -> Unit,
    onGoogleSignIn: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var dismissRequestMessage by remember { mutableStateOf(false) }
    val passwordFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val loading = requestState is AuthRequestState.Loading
    val requestMessage = if (dismissRequestMessage) null else when (requestState) {
        is AuthRequestState.Error -> requestState.message
        is AuthRequestState.Success -> requestState.message.takeIf { mode == AuthMode.FORGOT_PASSWORD || mode == AuthMode.VERIFY_EMAIL }
        else -> null
    }

    LaunchedEffect(mode, initialEmail) {
        localError = null
        if (mode == AuthMode.VERIFY_EMAIL) {
            dismissRequestMessage = false
            if (initialEmail.isNotBlank()) email = initialEmail
        }
    }

    fun submit() {
        if (mode == AuthMode.VERIFY_EMAIL) {
            localError = null
            onCheckVerification()
            return
        }
        val validEmail = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
        localError = when {
            !emailSignInEnabled -> authLabel(
                language,
                "সাইন ইন চালু করতে অ্যাপ-মালিককে Firebase সেট আপ করতে হবে।",
                "साइन-इन चालू करने के लिए ऐप के मालिक को Firebase सेट अप करना होगा।",
                "The app owner needs to configure Firebase to enable sign-in."
            )
            !validEmail -> authLabel(language, "সঠিক ইমেইল লিখুন।", "मान्य ईमेल पता लिखें।", "Enter a valid email address.")
            mode != AuthMode.FORGOT_PASSWORD && password.isBlank() ->
                authLabel(language, "আপনার পাসওয়ার্ড লিখুন।", "अपना पासवर्ड लिखें।", "Enter your password.")
            mode == AuthMode.CREATE_ACCOUNT && password.length < 6 ->
                authLabel(language, "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের হতে হবে।", "पासवर्ड कम से कम 6 अक्षरों का होना चाहिए।", "Use a password with at least 6 characters.")
            else -> null
        }
        if (localError != null) return
        dismissRequestMessage = false
        keyboard?.hide()
        focusManager.clearFocus()
        when (mode) {
            AuthMode.SIGN_IN -> onSignIn(email.trim(), password)
            AuthMode.CREATE_ACCOUNT -> onCreateAccount(email.trim(), password)
            AuthMode.FORGOT_PASSWORD -> onPasswordReset(email.trim())
            AuthMode.VERIFY_EMAIL -> onCheckVerification()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(Brush.verticalGradient(listOf(DostAuthCanvas, DostAuthTint, DostAuthCanvas)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.logo1),
                contentDescription = authLabel(language, "দোস্তের লোগো", "दोस्त का लोगो", "Dost logo"),
                modifier = Modifier.size(72.dp)
            )
            Text(
                text = authLabel(language, "দোস্ত", "दोस्त", "Dost"),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = DostAuthInk,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = authLabel(
                    language,
                    "একটু কথা, একটু বোঝাপড়া—শুধু তোমার জন্য।",
                    "थोड़ी बातचीत, थोड़ी समझ—सिर्फ़ आपके लिए।",
                    "A little conversation, a little understanding—just for you."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = DostAuthMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 22.dp)
            )

            Surface(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                contentColor = DostAuthInk,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, DostAuthBorder)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (mode == AuthMode.FORGOT_PASSWORD) {
                        Text(
                            authLabel(language, "পাসওয়ার্ড রিসেট", "पासवर्ड रीसेट करें", "Reset your password"),
                            style = MaterialTheme.typography.headlineSmall,
                            color = DostAuthInk,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            authLabel(
                                language,
                                "তোমার ইমেইলে রিসেট করার লিংক পাঠাব।",
                                "हम आपके ईमेल पर रीसेट लिंक भेजेंगे।",
                                "We’ll send a reset link to your email."
                            ),
                            color = DostAuthMuted
                        )
                    } else if (mode == AuthMode.VERIFY_EMAIL) {
                        Text(
                            authLabel(language, "ইমেইল যাচাই করুন", "ईमेल सत्यापित करें", "Verify your email"),
                            style = MaterialTheme.typography.headlineSmall,
                            color = DostAuthInk,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            authLabel(
                                language,
                                "যাচাইকরণ লিংক পাঠানো হয়েছে। ইমেইল যাচাই করে এখানে ফিরে আসুন: $email",
                                "सत्यापन लिंक भेज दिया गया है। ईमेल सत्यापित करके यहाँ लौटें: $email",
                                "A verification link was sent. Verify your email, then return here: $email"
                            ),
                            color = DostAuthMuted
                        )
                    } else {
                        Text(
                            authLabel(language, "তোমার দোস্তের কাছে ফিরে এসো", "अपने दोस्त के पास लौटें", "Come on in"),
                            style = MaterialTheme.typography.headlineSmall,
                            color = DostAuthInk,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            authLabel(
                                language,
                                "তোমার অ্যাকাউন্টে ঢুকো, অথবা নতুন করে শুরু করো।",
                                "अपने खाते में आएँ या नई शुरुआत करें।",
                                "Sign in to your account, or create a new one."
                            ),
                            color = DostAuthMuted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (mode == AuthMode.SIGN_IN) {
                                Button(onClick = { onModeChange(AuthMode.SIGN_IN); localError = null; dismissRequestMessage = true }, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = DostTeal, contentColor = Color.White)) {
                                    Text(authLabel(language, "লগ ইন", "लॉग इन", "Sign in"))
                                }
                                OutlinedButton(onClick = { onModeChange(AuthMode.CREATE_ACCOUNT); localError = null; dismissRequestMessage = true }, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = DostTeal)) {
                                    Text(authLabel(language, "নতুন অ্যাকাউন্ট", "नया खाता", "Create account"))
                                }
                            } else {
                                OutlinedButton(onClick = { onModeChange(AuthMode.SIGN_IN); localError = null; dismissRequestMessage = true }, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = DostTeal)) {
                                    Text(authLabel(language, "লগ ইন", "लॉग इन", "Sign in"))
                                }
                                Button(onClick = { onModeChange(AuthMode.CREATE_ACCOUNT); localError = null; dismissRequestMessage = true }, modifier = Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = DostTeal, contentColor = Color.White)) {
                                    Text(authLabel(language, "নতুন অ্যাকাউন্ট", "नया खाता", "Create account"))
                                }
                            }
                        }
                    }

                    if (mode != AuthMode.FORGOT_PASSWORD && mode != AuthMode.VERIFY_EMAIL) {
                        OutlinedButton(
                            onClick = {
                                localError = null
                                keyboard?.hide()
                                focusManager.clearFocus()
                                onGoogleSignIn()
                            },
                            enabled = googleSignInEnabled && !loading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DostAuthInk),
                            border = BorderStroke(1.dp, DostAuthBorder)
                        ) {
                            Text("G", color = Color(0xFF4285F4), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                authLabel(language, "Google দিয়ে চালিয়ে যান", "Google से जारी रखें", "Continue with Google"),
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                        if (!googleSignInEnabled) {
                            Text(
                                authLabel(
                                    language,
                                    "Google সাইন-ইন চালু করতে Firebase-এর ওয়েব OAuth ক্লায়েন্ট সেট করুন।",
                                    "Google साइन-इन चालू करने के लिए Firebase वेब OAuth क्लाइंट सेट करें।",
                                    "Google sign-in needs a Firebase web OAuth client ID."
                                ),
                                color = DostAuthMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (mode != AuthMode.FORGOT_PASSWORD && mode != AuthMode.VERIFY_EMAIL) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = DostAuthBorder)
                            Text(authLabel(language, "অথবা ইমেইল", "या ईमेल", "or email"), color = DostAuthMuted, style = MaterialTheme.typography.labelMedium)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = DostAuthBorder)
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; localError = null; dismissRequestMessage = true },
                        label = { Text(authLabel(language, "ইমেইল", "ईमेल", "Email address")) },
                        leadingIcon = { Text("@", color = DostAuthMuted) },
                        singleLine = true,
                        enabled = !loading && mode != AuthMode.VERIFY_EMAIL,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = if (mode == AuthMode.FORGOT_PASSWORD || mode == AuthMode.VERIFY_EMAIL) ImeAction.Done else ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { passwordFocusRequester.requestFocus() },
                            onDone = { submit() }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (mode != AuthMode.FORGOT_PASSWORD && mode != AuthMode.VERIFY_EMAIL) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; localError = null; dismissRequestMessage = true },
                            label = { Text(authLabel(language, "পাসওয়ার্ড", "पासवर्ड", "Password")) },
                            singleLine = true,
                            enabled = !loading,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                TextButton(onClick = { showPassword = !showPassword }, enabled = !loading) {
                                    Text(if (showPassword) authLabel(language, "লুকান", "छिपाएँ", "Hide") else authLabel(language, "দেখুন", "दिखाएँ", "Show"))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocusRequester)
                        )
                    }

                    (localError ?: requestMessage)?.let { message ->
                        Text(
                            message,
                            color = if (requestState is AuthRequestState.Success && localError == null) DostTeal else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                    if (!emailSignInEnabled && mode != AuthMode.VERIFY_EMAIL) {
                        Text(
                            authLabel(
                                language,
                                "ইমেইল সাইন-ইন চালু করতে Firebase Authentication সেট আপ করুন।",
                                "ईमेल साइन-इन चालू करने के लिए Firebase Authentication सेट अप करें।",
                                "Set up Firebase Authentication to enable email sign-in."
                            ),
                            color = DostAuthMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = { submit() },
                        enabled = emailSignInEnabled && !loading,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DostTeal, contentColor = Color.White)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(
                                when (mode) {
                                    AuthMode.SIGN_IN -> authLabel(language, "লগ ইন করুন", "लॉग इन करें", "Sign in")
                                    AuthMode.CREATE_ACCOUNT -> authLabel(language, "অ্যাকাউন্ট তৈরি করুন", "खाता बनाएँ", "Create account")
                                    AuthMode.FORGOT_PASSWORD -> authLabel(language, "রিসেট লিংক পাঠান", "रीसेट लिंक भेजें", "Send reset link")
                                    AuthMode.VERIFY_EMAIL -> authLabel(language, "আমি যাচাই করেছি", "मैंने सत्यापित किया", "I've verified my email")
                                }
                            )
                        }
                    }

                    if (mode == AuthMode.SIGN_IN) {
                        TextButton(
                            onClick = { onModeChange(AuthMode.FORGOT_PASSWORD); localError = null; password = ""; dismissRequestMessage = true },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(authLabel(language, "পাসওয়ার্ড ভুলে গেছ?", "पासवर्ड भूल गए?", "Forgot password?"))
                        }
                    } else if (mode == AuthMode.FORGOT_PASSWORD) {
                        TextButton(onClick = { onModeChange(AuthMode.SIGN_IN); localError = null; dismissRequestMessage = true }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                            Text(authLabel(language, "লগ ইন-এ ফিরে যান", "लॉग इन पर वापस जाएँ", "Back to sign in"))
                        }
                    } else if (mode == AuthMode.VERIFY_EMAIL) {
                        TextButton(onClick = onResendVerification, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                            Text(authLabel(language, "যাচাইকরণ ইমেইল আবার পাঠান", "सत्यापन ईमेल फिर भेजें", "Resend verification email"))
                        }
                        TextButton(onClick = { onModeChange(AuthMode.SIGN_IN); localError = null; dismissRequestMessage = true }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                            Text(authLabel(language, "লগ ইন-এ ফিরে যান", "लॉग इन पर वापस जाएँ", "Back to sign in"))
                        }
                    } else {
                        Text(
                            authLabel(language, "পাসওয়ার্ড কমপক্ষে ৬ অক্ষরের রাখুন।", "पासवर्ड कम से कम 6 अक्षरों का रखें।", "Use at least 6 characters for your password."),
                            color = DostAuthMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

private fun authLabel(language: AppLanguage, bangla: String, hindi: String, english: String): String = when (language) {
    AppLanguage.BANGLA -> bangla
    AppLanguage.HINDI -> hindi
    AppLanguage.ENGLISH -> english
}

@Composable
private fun AuthLayout(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, modifier = Modifier.padding(top = 8.dp, bottom = 28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(language: AppLanguage, step: Int, name: String, email: String, age: String, gender: String, onNameChange: (String) -> Unit, onEmailChange: (String) -> Unit, onAgeChange: (String) -> Unit, onGenderChange: (String) -> Unit, onNext: () -> Unit, onFinish: (Friend?) -> Unit, onBack: () -> Unit, emailEditable: Boolean = true) {
    var friendName by remember { mutableStateOf("") }
    var friendAge by remember { mutableStateOf("") }
    var friendGender by remember { mutableStateOf("") }
    var friendPersonality by remember { mutableStateOf("") }
    var friendGreeting by remember { mutableStateOf("") }
    var friendPhotoUri by remember { mutableStateOf("") }
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            friendPhotoUri = it.toString()
        }
    }
    val hindi = language == AppLanguage.HINDI
    Scaffold(topBar = { TopAppBar(title = { Text(when { language == AppLanguage.BANGLA -> "অ্যাকাউন্ট তৈরি - ধাপ $step/২"; hindi -> "खाता बनाएं - चरण $step/2"; else -> "Create account - Step $step of 2" }) }, navigationIcon = { TextButton(onClick = onBack) { Text(when { language == AppLanguage.BANGLA -> "ফিরুন"; hindi -> "वापस"; else -> "Back" }) } }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (step == 1) {
                Text(when { language == AppLanguage.BANGLA -> "আপনার প্রোফাইল তৈরি করুন"; hindi -> "अपनी प्रोफ़ाइल बनाएं"; else -> "Create your profile" }, style = MaterialTheme.typography.headlineSmall)
                Text(when { language == AppLanguage.BANGLA -> "নিজের সম্পর্কে কিছু তথ্য দিন।"; hindi -> "अपने बारे में कुछ जानकारी दें।"; else -> "Tell us a little about yourself." })
                OutlinedTextField(name, onNameChange, label = { Text(when { language == AppLanguage.BANGLA -> "নাম"; hindi -> "नाम"; else -> "Name" }) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, onEmailChange, readOnly = !emailEditable, label = { Text(when { language == AppLanguage.BANGLA -> "ইমেইল"; hindi -> "ईमेल"; else -> "Email" }) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(age, onAgeChange, label = { Text(when { language == AppLanguage.BANGLA -> "বয়স"; hindi -> "उम्र"; else -> "Age" }) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                accountAgeError(age, language)?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } ?: Text(accountAgeHint(language), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                GenderField(gender, onGenderChange, language)
                Button(onClick = onNext, enabled = name.isNotBlank() && email.isNotBlank() && accountAgeResult(age) == AccountAgeResult.VALID && gender.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(when { language == AppLanguage.BANGLA -> "চালিয়ে যান"; hindi -> "जारी रखें"; else -> "Continue" }) }
            } else {
                Text(when { language == AppLanguage.BANGLA -> "আপনার প্রথম বন্ধু তৈরি করুন"; hindi -> "अपना पहला दोस्त बनाएं"; else -> "Create your first friend" }, style = MaterialTheme.typography.headlineSmall)
                Text(when { language == AppLanguage.BANGLA -> "হোম স্ক্রিন থেকে যেকোনো সময় আরও বন্ধু যোগ করতে পারবেন।"; hindi -> "आप होम स्क्रीन से कभी भी और दोस्त जोड़ सकते हैं।"; else -> "You can add more friends anytime from the home screen." })
                OutlinedTextField(friendName, { friendName = it }, label = { Text(when { language == AppLanguage.BANGLA -> "বন্ধুর নাম"; hindi -> "दोस्त का नाम"; else -> "Friend name" }) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(friendAge, { friendAge = it }, label = { Text(when { language == AppLanguage.BANGLA -> "বন্ধুর বয়স"; hindi -> "दोस्त की उम्र"; else -> "Friend age" }) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                friendAgeError(friendAge, language)?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (isUnderRomanceAge(age) && accountAgeResult(age) == AccountAgeResult.VALID) {
                    Text(userUnder18RomanceNote(language), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } else {
                    friendUnder18RomanceNote(friendAge, language)?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                GenderField(friendGender, { friendGender = it }, language)
                OutlinedTextField(
                    friendPersonality,
                    { friendPersonality = it },
                    label = { Text(when (language) { AppLanguage.BANGLA -> "ব্যক্তিত্ব"; AppLanguage.HINDI -> "व्यक्तित्व"; AppLanguage.ENGLISH -> "Personality" }) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    friendGreeting,
                    { friendGreeting = it },
                    label = { Text(when (language) { AppLanguage.BANGLA -> "প্রথম বার্তা"; AppLanguage.HINDI -> "पहला संदेश"; AppLanguage.ENGLISH -> "First message" }) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text(if (friendPhotoUri.isBlank()) if (hindi) "दोस्त की फोटो जोड़ें" else "Add friend photo" else if (hindi) "फोटो चुनी गई" else "Photo selected") }
                Button(onClick = { onFinish(Friend(id = java.util.UUID.randomUUID().toString(), name = friendName, age = friendAge.trim(), gender = friendGender, personality = friendPersonality.trim(), photoUri = friendPhotoUri, greeting = friendGreeting.trim())) }, enabled = friendName.isNotBlank() && friendAgeResult(friendAge) == AccountAgeResult.VALID && friendGender.isNotBlank() && friendPersonality.isNotBlank() && friendGreeting.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(when { language == AppLanguage.BANGLA -> "শেষ করুন"; hindi -> "समाप्त करें"; else -> "Finish" }) }
                TextButton(onClick = { onFinish(null) }, modifier = Modifier.fillMaxWidth()) { Text(when { language == AppLanguage.BANGLA -> "এখন বাদ দিন"; hindi -> "अभी छोड़ें"; else -> "Skip for now" }) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenderField(value: String, onValueChange: (String) -> Unit, language: AppLanguage = AppLanguage.ENGLISH) {
    var expanded by remember { mutableStateOf(false) }
    val options = when (language) {
        AppLanguage.BANGLA -> listOf("নারী", "পুরুষ", "নন-বাইনারি", "বলতে চাই না")
        AppLanguage.HINDI -> listOf("महिला", "पुरुष", "नॉन-बाइनरी", "बताना पसंद नहीं")
        AppLanguage.ENGLISH -> listOf("Female", "Male", "Non-binary", "Prefer not to say")
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(value, {}, readOnly = true, label = { Text(when (language) { AppLanguage.BANGLA -> "লিঙ্গ"; AppLanguage.HINDI -> "लिंग"; AppLanguage.ENGLISH -> "Gender" }) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onValueChange(option); expanded = false }) }
        }
    }
}

fun accountAgeHint(language: AppLanguage): String = when (language) {
    AppLanguage.BANGLA -> "দোস্ত ১৩ বছর বা তার বেশি বয়সীদের জন্য। ভালোবাসার মাপকাঠি ১৮ বছরের আগে বন্ধ থাকে।"
    AppLanguage.HINDI -> "दोस्त 13 साल या उससे बड़े लोगों के लिए है। प्रेम मीटर 18 साल से पहले बंद रहता है।"
    AppLanguage.ENGLISH -> "Dost is for people 13 and older. The love meter stays off until you are 18."
}

fun accountAgeError(age: String, language: AppLanguage): String? = when (accountAgeResult(age)) {
    AccountAgeResult.VALID, AccountAgeResult.EMPTY -> null
    AccountAgeResult.UNDER_MINIMUM -> when (language) {
        AppLanguage.BANGLA -> "অ্যাকাউন্ট তৈরি করতে কমপক্ষে ১৩ বছর বয়স লাগবে।"
        AppLanguage.HINDI -> "खाता बनाने के लिए कम से कम 13 साल की उम्र होनी चाहिए।"
        AppLanguage.ENGLISH -> "You need to be 13 or older to create an account."
    }
    AccountAgeResult.NOT_A_WHOLE_NUMBER -> when (language) {
        AppLanguage.BANGLA -> "বয়স পুরো সংখ্যায় লিখুন।"
        AppLanguage.HINDI -> "उम्र पूरे अंकों में लिखें।"
        AppLanguage.ENGLISH -> "Enter your age as a whole number of years."
    }
    AccountAgeResult.ABOVE_MAXIMUM -> when (language) {
        AppLanguage.BANGLA -> "বয়স ১২০-এর বেশি হতে পারবে না।"
        AppLanguage.HINDI -> "उम्र 120 से ज़्यादा नहीं हो सकती।"
        AppLanguage.ENGLISH -> "Enter an age of 120 or less."
    }
}

fun friendAgeError(age: String, language: AppLanguage): String? {
    if (age.isBlank() || friendAgeResult(age) == AccountAgeResult.VALID) return null
    return when (language) {
        AppLanguage.BANGLA -> "বন্ধুর বয়স ১ থেকে ১২০-এর মধ্যে একটি পুরো সংখ্যা লিখুন।"
        AppLanguage.HINDI -> "दोस्त की उम्र 1 से 120 के बीच एक पूरी संख्या लिखें।"
        AppLanguage.ENGLISH -> "Enter the friend's age as a whole number from 1 to 120."
    }
}

fun friendUnder18RomanceNote(age: String, language: AppLanguage): String? {
    if (friendAgeResult(age) != AccountAgeResult.VALID || !isUnderRomanceAge(age)) return null
    return when (language) {
        AppLanguage.BANGLA -> "এই বন্ধুর বয়স ১৮-এর কম, তাই ভালোবাসার মাপকাঠি বন্ধ থাকবে।"
        AppLanguage.HINDI -> "इस दोस्त की उम्र 18 से कम है, इसलिए प्रेम मीटर बंद रहेगा।"
        AppLanguage.ENGLISH -> "This friend is under 18, so the love meter stays off."
    }
}

fun userUnder18RomanceNote(language: AppLanguage): String = when (language) {
    AppLanguage.BANGLA -> "তোমার বয়স ১৮-এর কম, তাই সব বন্ধুর সঙ্গে ভালোবাসার মাপকাঠি বন্ধ থাকবে।"
    AppLanguage.HINDI -> "आपकी उम्र 18 से कम है, इसलिए हर दोस्त के साथ प्रेम मीटर बंद रहेगा।"
    AppLanguage.ENGLISH -> "You are under 18, so the love meter stays off with every friend."
}