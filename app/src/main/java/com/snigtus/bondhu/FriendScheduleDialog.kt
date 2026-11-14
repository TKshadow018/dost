package com.snigtus.dost

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

@Composable
internal fun FriendScheduleDialog(
    friend: Friend,
    language: AppLanguage,
    onSave: (List<RoutineBlock>, BusyReplyBehavior, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val blocks = remember(friend.id) { mutableStateListOf(*friend.effectiveRoutineBlocks().toTypedArray()) }
    var behavior by remember(friend.id) { mutableStateOf(friend.busyReplyBehavior) }
    var delayMinutes by remember(friend.id) { mutableStateOf(friend.busyReplyDelayMinutes.coerceIn(0, 120)) }
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    val blockTitle = when (language) {
        AppLanguage.BANGLA -> "রুটিন"
        AppLanguage.HINDI -> "दिनचर्या"
        AppLanguage.ENGLISH -> "Routine"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (language) {
            AppLanguage.BANGLA -> "${friend.name}-এর রুটিন"
            AppLanguage.HINDI -> "${friend.name} की दिनचर्या"
            AppLanguage.ENGLISH -> "${friend.name}'s routine"
        }) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                blocks.toList().forEachIndexed { index, block ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = block.reason,
                                onValueChange = { value -> blocks[index] = block.copy(reason = value.take(80)) },
                                label = { Text(when (language) {
                                    AppLanguage.BANGLA -> "কারণ"
                                    AppLanguage.HINDI -> "कारण"
                                    AppLanguage.ENGLISH -> "Reason"
                                }) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            TextButton(
                                enabled = blocks.size > 1,
                                onClick = { blocks.removeAt(index) }
                            ) {
                                Text(when (language) {
                                    AppLanguage.BANGLA -> "সরান"
                                    AppLanguage.HINDI -> "हटाएँ"
                                    AppLanguage.ENGLISH -> "Remove"
                                })
                            }
                        }
                        OutlinedButton(onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    blocks[index] = block.copy(startMinute = hour * 60 + minute)
                                },
                                block.startMinute / 60,
                                block.startMinute % 60,
                                false
                            ).show()
                        }) {
                            Text(
                                when (language) {
                                    AppLanguage.BANGLA -> "শুরু ${LocalTime.of(block.startMinute / 60, block.startMinute % 60).format(DateTimeFormatter.ofPattern("h:mm a"))}"
                                    AppLanguage.HINDI -> "शुरू ${LocalTime.of(block.startMinute / 60, block.startMinute % 60).format(DateTimeFormatter.ofPattern("h:mm a"))}"
                                    AppLanguage.ENGLISH -> "Starts ${LocalTime.of(block.startMinute / 60, block.startMinute % 60).format(DateTimeFormatter.ofPattern("h:mm a"))}"
                                }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            dayLabels.forEachIndexed { dayIndex, label ->
                                FilterChip(
                                    selected = dayIndex + 1 in block.daysOfWeek,
                                    onClick = {
                                        val day = dayIndex + 1
                                        val updatedDays = if (day in block.daysOfWeek) block.daysOfWeek - day else block.daysOfWeek + day
                                        if (updatedDays.isNotEmpty()) blocks[index] = block.copy(daysOfWeek = updatedDays)
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Text(
                            when (language) {
                                AppLanguage.BANGLA -> "সময়কাল: ${block.durationMinutes / 60} ঘণ্টা ${block.durationMinutes % 60} মিনিট"
                                AppLanguage.HINDI -> "अवधि: ${block.durationMinutes / 60} घंटे ${block.durationMinutes % 60} मिनट"
                                AppLanguage.ENGLISH -> "Duration: ${block.durationMinutes / 60} hr ${block.durationMinutes % 60} min"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = block.durationMinutes.toFloat().coerceIn(30f, 1440f),
                            onValueChange = { value ->
                                val minutes = (value.roundToInt() / 30 * 30).coerceIn(30, 1440)
                                blocks[index] = block.copy(durationMinutes = minutes)
                            },
                            valueRange = 30f..1440f,
                            steps = 46
                        )
                    }
                }
                TextButton(onClick = {
                    blocks.add(
                        RoutineBlock(
                            id = UUID.randomUUID().toString(),
                            reason = if (blocks.isEmpty()) "Work" else "Routine",
                            startMinute = 9 * 60,
                            durationMinutes = 8 * 60,
                            daysOfWeek = (1..5).toSet()
                        )
                    )
                }) {
                    Text(when (language) {
                        AppLanguage.BANGLA -> "আরেকটি রুটিন যোগ করুন"
                        AppLanguage.HINDI -> "एक और दिनचर्या जोड़ें"
                        AppLanguage.ENGLISH -> "Add routine block"
                    })
                }
                Text(blockTitle, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = behavior == BusyReplyBehavior.WAIT,
                        onClick = { behavior = BusyReplyBehavior.WAIT },
                        label = { Text(when (language) {
                            AppLanguage.BANGLA -> "পরে উত্তর"
                            AppLanguage.HINDI -> "बाद में जवाब"
                            AppLanguage.ENGLISH -> "Reply later"
                        }) }
                    )
                    FilterChip(
                        selected = behavior == BusyReplyBehavior.IMMEDIATE,
                        onClick = { behavior = BusyReplyBehavior.IMMEDIATE },
                        label = { Text(when (language) {
                            AppLanguage.BANGLA -> "এখনই উত্তর"
                            AppLanguage.HINDI -> "अभी जवाब"
                            AppLanguage.ENGLISH -> "Reply now"
                        }) }
                    )
                }
                Text(
                    when (language) {
                        AppLanguage.BANGLA -> "ব্যস্ততার সময় শেষ হওয়ার পর অতিরিক্ত অপেক্ষা: $delayMinutes মিনিট"
                        AppLanguage.HINDI -> "व्यस्त समय के बाद अतिरिक्त प्रतीक्षा: $delayMinutes मिनट"
                        AppLanguage.ENGLISH -> "Extra wait after a busy period: $delayMinutes minutes"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = delayMinutes.toFloat(),
                    onValueChange = { delayMinutes = (it.roundToInt() / 15 * 15).coerceIn(0, 120) },
                    valueRange = 0f..120f,
                    steps = 7
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(blocks.toList(), behavior, delayMinutes); onDismiss() },
                enabled = blocks.isNotEmpty() && blocks.all { it.daysOfWeek.isNotEmpty() }
            ) {
                Text(when (language) {
                    AppLanguage.BANGLA -> "সংরক্ষণ"
                    AppLanguage.HINDI -> "सेव करें"
                    AppLanguage.ENGLISH -> "Save"
                })
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(when (language) {
                    AppLanguage.BANGLA -> "বাতিল"
                    AppLanguage.HINDI -> "रद्द करें"
                    AppLanguage.ENGLISH -> "Cancel"
                })
            }
        }
    )
}
