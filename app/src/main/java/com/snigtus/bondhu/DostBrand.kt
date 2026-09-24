package com.snigtus.dost

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

private val dostColorCombinations = listOf(
    listOf(0xFF2563EB, 0xFFEC4899, 0xFFF59E0B, 0xFF10B981),
    listOf(0xFFDC2626, 0xFFF97316, 0xFF16A34A, 0xFF2563EB),
    listOf(0xFF7C3AED, 0xFFDB2777, 0xFF0891B2, 0xFF65A30D),
    listOf(0xFF0F766E, 0xFFEA580C, 0xFF4F46E5, 0xFFCA8A04),
    listOf(0xFFBE123C, 0xFF0284C7, 0xFF7C2D12, 0xFF15803D),
    listOf(0xFF4338CA, 0xFFE11D48, 0xFF0891B2, 0xFFB45309),
    listOf(0xFF166534, 0xFF9333EA, 0xFFEA580C, 0xFF0369A1),
    listOf(0xFF1D4ED8, 0xFFB91C1C, 0xFF047857, 0xFFA16207),
    listOf(0xFF86198F, 0xFF0E7490, 0xFFB45309, 0xFF15803D),
    listOf(0xFF9F1239, 0xFF1E40AF, 0xFF166534, 0xFFC2410C),
    listOf(0xFF6D28D9, 0xFFBE185D, 0xFF0F766E, 0xFFA16207),
    listOf(0xFF075985, 0xFF9F1239, 0xFF3F6212, 0xFF9A3412),
    listOf(0xFF312E81, 0xFF9D174D, 0xFF115E59, 0xFF854D0E),
    listOf(0xFF1E3A8A, 0xFF881337, 0xFF14532D, 0xFF7C2D12),
    listOf(0xFF4C1D95, 0xFF831843, 0xFF134E4A, 0xFF713F12),
    listOf(0xFF0369A1, 0xFFC026D3, 0xFF15803D, 0xFF9A3412),
    listOf(0xFF4338CA, 0xFFBE123C, 0xFF0F766E, 0xFF854D0E),
    listOf(0xFF1D4ED8, 0xFFBE185D, 0xFF047857, 0xFF9A3412),
    listOf(0xFF7E22CE, 0xFF0369A1, 0xFF166534, 0xFFC2410C),
    listOf(0xFF9F1239, 0xFF6D28D9, 0xFF0E7490, 0xFFA16207)
)

@Composable
fun DostBrand() {
    val colors = remember { dostColorCombinations.random() }
    Image(
        painter = painterResource(R.drawable.logo1),
        contentDescription = "Dost logo",
        modifier = Modifier.size(132.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.padding(bottom = 10.dp)) {
        "dost".forEachIndexed { index, letter ->
            Text(letter.toString(), color = Color(colors[index]), style = MaterialTheme.typography.headlineLarge)
        }
    }
}