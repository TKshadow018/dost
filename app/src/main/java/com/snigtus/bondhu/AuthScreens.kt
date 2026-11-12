package com.snigtus.dost

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

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
fun RegisterScreen(language: AppLanguage, step: Int, name: String, email: String, age: String, gender: String, onNameChange: (String) -> Unit, onEmailChange: (String) -> Unit, onAgeChange: (String) -> Unit, onGenderChange: (String) -> Unit, onNext: () -> Unit, onFinish: (Friend?) -> Unit, onBack: () -> Unit) {
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
                OutlinedTextField(email, onEmailChange, label = { Text(when { language == AppLanguage.BANGLA -> "ইমেইল"; hindi -> "ईमेल"; else -> "Email" }) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(age, onAgeChange, label = { Text(when { language == AppLanguage.BANGLA -> "বয়স"; hindi -> "उम्र"; else -> "Age" }) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                accountAgeError(age, language)?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } ?: Text(accountAgeHint(language), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                GenderField(gender, onGenderChange, language)
                Button(onClick = onNext, enabled = name.isNotBlank() && email.isNotBlank() && accountAgeResult(age) == AccountAgeResult.VALID && gender.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(when { language == AppLanguage.BANGLA -> "চালিয়ে যান"; hindi -> "जारी रखें"; else -> "Continue" }) }
            } else {
                Text(when { language == AppLanguage.BANGLA -> "আপনার প্রথম বন্ধু তৈরি করুন"; hindi -> "अपना पहला दोस्त बनाएं"; else -> "Create your first friend" }, style = MaterialTheme.typography.headlineSmall)
                Text(when { language == AppLanguage.BANGLA -> "সাইন ইন করার পর আরও বন্ধু যোগ করতে পারবেন।"; hindi -> "साइन इन करने के बाद आप और दोस्त जोड़ सकते हैं।"; else -> "You can add more friends after signing in." })
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