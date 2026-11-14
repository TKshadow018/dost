package com.snigtus.dost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun LanguageSetupScreen(
    appLanguage: AppLanguage,
    chatLanguage: AppLanguage,
    onAppLanguageChange: (AppLanguage) -> Unit,
    onChatLanguageChange: (AppLanguage) -> Unit,
    onContinue: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Dost", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                when (appLanguage) {
                    AppLanguage.BANGLA -> "শুরু করার আগে আপনার ভাষা বেছে নিন"
                    AppLanguage.HINDI -> "शुरू करने से पहले अपनी भाषाएँ चुनें"
                    AppLanguage.ENGLISH -> "Choose your languages to get started"
                },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                when (appLanguage) {
                    AppLanguage.BANGLA -> "অ্যাপের ভাষা ও AI বন্ধুর চ্যাটের ভাষা আলাদাভাবে বেছে নিতে পারেন।"
                    AppLanguage.HINDI -> "ऐप और AI दोस्त की चैट के लिए अलग-अलग भाषाएँ चुन सकते हैं।"
                    AppLanguage.ENGLISH -> "The app and your AI friend's replies can use different languages."
                },
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            Text(
                when (appLanguage) {
                    AppLanguage.BANGLA -> "অ্যাপের ভাষা"
                    AppLanguage.HINDI -> "ऐप की भाषा"
                    AppLanguage.ENGLISH -> "App language"
                },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium
            )
            LanguageChoices(selected = appLanguage, onSelect = onAppLanguageChange)
            Spacer(Modifier.height(20.dp))
            Text(
                when (appLanguage) {
                    AppLanguage.BANGLA -> "চ্যাটের ভাষা"
                    AppLanguage.HINDI -> "चैट की भाषा"
                    AppLanguage.ENGLISH -> "Chat language"
                },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium
            )
            LanguageChoices(selected = chatLanguage, onSelect = onChatLanguageChange)
            Spacer(Modifier.height(28.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when (appLanguage) {
                        AppLanguage.BANGLA -> "চালিয়ে যান"
                        AppLanguage.HINDI -> "जारी रखें"
                        AppLanguage.ENGLISH -> "Continue"
                    }
                )
            }
        }
    }
}

@Composable
private fun LanguageChoices(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        listOf(
            AppLanguage.ENGLISH to "English",
            AppLanguage.BANGLA to "বাংলা",
            AppLanguage.HINDI to "हिन्दी"
        ).forEach { (option, label) ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(label) }
            )
        }
    }
}
