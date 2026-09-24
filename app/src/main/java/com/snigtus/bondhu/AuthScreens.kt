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
        Column(modifier = Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (step == 1) {
                Text(when { language == AppLanguage.BANGLA -> "আপনার প্রোফাইল তৈরি করুন"; hindi -> "अपनी प्रोफ़ाइल बनाएं"; else -> "Create your profile" }, style = MaterialTheme.typography.headlineSmall)
                Text(when { language == AppLanguage.BANGLA -> "নিজের সম্পর্কে কিছু তথ্য দিন।"; hindi -> "अपने बारे में कुछ जानकारी दें।"; else -> "Tell us a little about yourself." })
                OutlinedTextField(name, onNameChange, label = { Text(when { language == AppLanguage.BANGLA -> "নাম"; hindi -> "नाम"; else -> "Name" }) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, onEmailChange, label = { Text(when { language == AppLanguage.BANGLA -> "ইমেইল"; hindi -> "ईमेल"; else -> "Email" }) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(age, onAgeChange, label = { Text(when { language == AppLanguage.BANGLA -> "বয়স"; hindi -> "उम्र"; else -> "Age" }) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                GenderField(gender, onGenderChange, language)
                Button(onClick = onNext, enabled = name.isNotBlank() && email.isNotBlank() && age.isNotBlank() && gender.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(when { language == AppLanguage.BANGLA -> "চালিয়ে যান"; hindi -> "जारी रखें"; else -> "Continue" }) }
            } else {
                Text(when { language == AppLanguage.BANGLA -> "আপনার প্রথম বন্ধু তৈরি করুন"; hindi -> "अपना पहला दोस्त बनाएं"; else -> "Create your first friend" }, style = MaterialTheme.typography.headlineSmall)
                Text(when { language == AppLanguage.BANGLA -> "সাইন ইন করার পর আরও বন্ধু যোগ করতে পারবেন।"; hindi -> "साइन इन करने के बाद आप और दोस्त जोड़ सकते हैं।"; else -> "You can add more friends after signing in." })
                OutlinedTextField(friendName, { friendName = it }, label = { Text(when { language == AppLanguage.BANGLA -> "বন্ধুর নাম"; hindi -> "दोस्त का नाम"; else -> "Friend name" }) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(friendAge, { friendAge = it }, label = { Text(when { language == AppLanguage.BANGLA -> "বন্ধুর বয়স"; hindi -> "दोस्त की उम्र"; else -> "Friend age" }) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                GenderField(friendGender, { friendGender = it }, language)
                OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text(if (friendPhotoUri.isBlank()) if (hindi) "दोस्त की फोटो जोड़ें" else "Add friend photo" else if (hindi) "फोटो चुनी गई" else "Photo selected") }
                Button(onClick = { onFinish(Friend(id = java.util.UUID.randomUUID().toString(), name = friendName, age = friendAge, gender = friendGender, photoUri = friendPhotoUri)) }, enabled = friendName.isNotBlank() && friendAge.isNotBlank() && friendGender.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(when { language == AppLanguage.BANGLA -> "শেষ করুন"; hindi -> "समाप्त करें"; else -> "Finish" }) }
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