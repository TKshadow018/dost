package com.snigtus.dost

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun LanguageFlagIcon(
    language: AppLanguage,
    modifier: Modifier = Modifier.size(width = 24.dp, height = 16.dp)
) {
    val flag = when (language) {
        AppLanguage.ENGLISH -> R.drawable.flag_united_kingdom
        AppLanguage.BANGLA -> R.drawable.flag_bangladesh
        AppLanguage.HINDI -> R.drawable.flag_india
    }
    Image(
        painter = painterResource(flag),
        contentDescription = null,
        modifier = modifier.clip(RoundedCornerShape(2.dp))
    )
}
