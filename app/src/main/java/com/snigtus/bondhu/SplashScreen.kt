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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun DostSplashScreen(onFinished: () -> Unit) {
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

    val letters = listOf(
        "d" to Color(0xFF2563EB),
        "o" to Color(0xFFEC4899),
        "s" to Color(0xFFF59E0B),
        "t" to Color(0xFF10B981)
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
                            style = MaterialTheme.typography.displaySmall
                        )
                    }
                }
            }
        }
    }
}
