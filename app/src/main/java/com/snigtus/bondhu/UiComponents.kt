package com.snigtus.dost

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

@Composable
internal fun DostBackButton(language: AppLanguage, onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = when (language) {
                AppLanguage.BANGLA -> "ফিরে যান"
                AppLanguage.HINDI -> "वापस"
                AppLanguage.ENGLISH -> "Back"
            }
        )
    }
}
