package com.snigtus.dost

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun DostSplashScreen(language: AppLanguage, onFinished: () -> Unit) {
    var showLetters by remember { mutableStateOf(false) }
    var fadeToBlack by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(120)
        showLetters = true
        delay(2200)
        fadeToBlack = true
        delay(900)
        onFinished()
    }
    val backgroundColor by animateColorAsState(
        targetValue = if (fadeToBlack) Color.Black else Color.White,
        animationSpec = tween(900),
        label = "splashBackground"
    )

    val letters = when (language) {
        AppLanguage.BANGLA -> listOf(
            "দো" to Color(0xFF2563EB),
            "স্ত" to Color(0xFFEC4899)
        )
        AppLanguage.HINDI -> listOf(
            "दो" to Color(0xFF2563EB),
            "स्त" to Color(0xFFEC4899)
        )
        AppLanguage.ENGLISH -> listOf(
            "D" to Color(0xFF2563EB),
            "O" to Color(0xFFEC4899),
            "S" to Color(0xFFF59E0B),
            "T" to Color(0xFF10B981)
        )
    }
    val wordmarkStyle = MaterialTheme.typography.displaySmall.copy(
        fontFamily = FontFamily.Cursive,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        letterSpacing = 1.sp
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize().background(backgroundColor))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(R.drawable.logo1),
                contentDescription = "Dost logo",
                modifier = Modifier.size(150.dp)
            )
            Row {
                letters.forEachIndexed { index, (letter, color) ->
                    AnimatedVisibility(
                        visible = showLetters,
                        enter = fadeIn(tween(400, index * 450)) +
                            slideInVertically(tween(400, index * 450)) { -it / 2 }
                    ) {
                        Text(
                            text = letter,
                            color = color,
                            style = wordmarkStyle
                        )
                    }
                }
            }
        }
    }
}
