@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.snigtus.dost

import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private const val WRESTLING_GAME_URL = "https://epicfurywrestling.web.app/"

private enum class MiniGame(val icon: ImageVector) {
    NUMBER_GUESS(Icons.Filled.Numbers),
    ROCK_PAPER_SCISSORS(Icons.Filled.SportsMma),
    TIC_TAC_TOE(Icons.Filled.GridView),
    MEMORY_MATCH(Icons.Filled.Style),
    WORD_SCRAMBLE(Icons.Filled.TextFields),
    QUICK_MATH(Icons.Filled.Calculate),
    DICE_DUEL(Icons.Filled.Casino),
    COIN_TOSS(Icons.Filled.MonetizationOn),
    COLOR_MATCH(Icons.Filled.Palette),
    EPIC_FURY(Icons.Filled.SportsEsports)
}

@Composable
internal fun MiniGamesScreen(language: AppLanguage, onBack: () -> Unit) {
    var selectedGame by remember { mutableStateOf<MiniGame?>(null) }

    if (selectedGame == MiniGame.EPIC_FURY) {
        WrestlingWebViewScreen(language, onBack = { selectedGame = null })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(gameText(language, "Games", "খেলা", "खेल")) },
                navigationIcon = { DostBackButton(language, onBack) }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(MiniGame.values().toList()) { game ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selectedGame = game },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = game.icon,
                            contentDescription = gameTitle(game, language),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(gameTitle(game, language), style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (game == MiniGame.EPIC_FURY) {
                                gameText(language, "Play online", "অনলাইনে খেলুন", "ऑनलाइन खेलें")
                            } else {
                                gameText(language, "Tap to play", "খেলতে ট্যাপ করুন", "खेलने के लिए टैप करें")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    selectedGame?.let { game ->
        if (game != MiniGame.EPIC_FURY) {
            MiniGameDialog(game, language, onDismiss = { selectedGame = null })
        }
    }
}

@Composable
private fun MiniGameDialog(game: MiniGame, language: AppLanguage, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(gameTitle(game, language)) },
        text = {
            when (game) {
                MiniGame.NUMBER_GUESS -> NumberGuessGame(language)
                MiniGame.ROCK_PAPER_SCISSORS -> RockPaperScissorsGame(language)
                MiniGame.TIC_TAC_TOE -> TicTacToeGame(language)
                MiniGame.MEMORY_MATCH -> MemoryMatchGame(language)
                MiniGame.WORD_SCRAMBLE -> WordScrambleGame(language)
                MiniGame.QUICK_MATH -> QuickMathGame(language)
                MiniGame.DICE_DUEL -> DiceDuelGame(language)
                MiniGame.COIN_TOSS -> CoinTossGame(language)
                MiniGame.COLOR_MATCH -> ColorMatchGame(language)
                MiniGame.EPIC_FURY -> Unit
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(LocalizedText.CLOSE.resolve(language)) }
        }
    )
}

@Composable
private fun NumberGuessGame(language: AppLanguage) {
    var target by remember { mutableIntStateOf((1..10).random()) }
    var guess by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var attempts by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(gameText(language, "Guess a number from 1 to 10.", "১ থেকে ১০-এর মধ্যে একটি সংখ্যা অনুমান করুন।", "1 से 10 के बीच एक संख्या का अनुमान लगाएँ।"))
        OutlinedTextField(
            value = guess,
            onValueChange = { guess = it.filter(Char::isDigit).take(2) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(gameText(language, "Your guess", "আপনার অনুমান", "आपका अनुमान")) }
        )
        Button(onClick = {
            val value = guess.toIntOrNull()
            if (value != null && value in 1..10) {
                attempts++
                result = when {
                    value == target -> gameText(language, "You got it in $attempts guesses!", "$attempts বারেই পেরেছেন!", "$attempts कोशिशों में सही!")
                    value < target -> gameText(language, "Try higher.", "আরও বড় সংখ্যা চেষ্টা করুন।", "बड़ी संख्या आज़माएँ।")
                    else -> gameText(language, "Try lower.", "আরও ছোট সংখ্যা চেষ্টা করুন।", "छोटी संख्या आज़माएँ।")
                }
            } else {
                result = gameText(language, "Enter a number from 1 to 10.", "১ থেকে ১০-এর মধ্যে সংখ্যা লিখুন।", "1 से 10 के बीच संख्या लिखें।")
            }
        }) {
            Text(gameText(language, "Guess", "অনুমান", "अनुमान"))
        }
        if (result.isNotBlank()) Text(result)
        TextButton(onClick = { target = (1..10).random(); guess = ""; result = ""; attempts = 0 }) {
            Text(gameText(language, "New round", "নতুন রাউন্ড", "नया दौर"))
        }
    }
}

@Composable
private fun RockPaperScissorsGame(language: AppLanguage) {
    val choices = listOf(
        gameText(language, "Rock", "পাথর", "पत्थर"),
        gameText(language, "Paper", "কাগজ", "कागज़"),
        gameText(language, "Scissors", "কাঁচি", "कैंची")
    )
    var result by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(gameText(language, "Choose one.", "একটি বেছে নিন।", "एक चुनें।"))
        choices.forEachIndexed { index, choice ->
            OutlinedButton(
                onClick = {
                    val computer = (0..2).random()
                    val outcome = when {
                        index == computer -> gameText(language, "It's a tie.", "ড্র হয়েছে।", "मैच बराबर रहा।")
                        (index - computer + 3) % 3 == 1 -> gameText(language, "You win!", "আপনি জিতেছেন!", "आप जीत गए!")
                        else -> gameText(language, "Dost wins this round.", "এই রাউন্ডে Dost জিতেছে।", "इस दौर में Dost जीता।")
                    }
                    result = gameText(language, "You: $choice · Dost: ${choices[computer]}. $outcome", "আপনি: $choice · Dost: ${choices[computer]}। $outcome", "आप: $choice · Dost: ${choices[computer]}। $outcome")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(choice)
            }
        }
        if (result.isNotBlank()) Text(result)
    }
}

private val ticTacToeLines = listOf(
    listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
    listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
    listOf(0, 4, 8), listOf(2, 4, 6)
)

private fun ticTacToeWinner(board: List<String>): String? =
    ticTacToeLines.firstOrNull { line ->
        board[line[0]].isNotEmpty() &&
            board[line[0]] == board[line[1]] &&
            board[line[1]] == board[line[2]]
    }?.let { board[it[0]] }

@Composable
private fun TicTacToeGame(language: AppLanguage) {
    var board by remember { mutableStateOf(List(9) { "" }) }
    val winner = ticTacToeWinner(board)
    val isDraw = winner == null && board.all(String::isNotEmpty)
    val status = when {
        winner == "X" -> gameText(language, "You win!", "আপনি জিতেছেন!", "आप जीत गए!")
        winner == "O" -> gameText(language, "Dost wins.", "Dost জিতেছে।", "Dost जीत गया।")
        isDraw -> gameText(language, "It's a draw.", "ড্র হয়েছে।", "मैच बराबर रहा।")
        else -> gameText(language, "Your turn (X)", "আপনার চাল (X)", "आपकी बारी (X)")
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(status)
        for (row in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (column in 0..2) {
                    val index = row * 3 + column
                    OutlinedButton(
                        onClick = {
                            if (board[index].isEmpty() && winner == null && !isDraw) {
                                val next = board.toMutableList()
                                next[index] = "X"
                                if (ticTacToeWinner(next) == null) {
                                    next.indices.filter { next[it].isEmpty() }.randomOrNull()?.let { next[it] = "O" }
                                }
                                board = next
                            }
                        },
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(board[index], style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
        TextButton(onClick = { board = List(9) { "" } }) {
            Text(gameText(language, "Restart", "আবার শুরু করুন", "फिर शुरू करें"))
        }
    }
}

@Composable
private fun MemoryMatchGame(language: AppLanguage) {
    val cards = remember { listOf("☀", "☾", "🌼", "⭐").flatMap { listOf(it, it) }.shuffled() }
    var revealed by remember { mutableStateOf(emptyList<Int>()) }
    var matched by remember { mutableStateOf(emptySet<Int>()) }
    LaunchedEffect(revealed) {
        if (revealed.size == 2) {
            delay(700)
            if (cards[revealed[0]] == cards[revealed[1]]) matched = matched + revealed
            revealed = emptyList()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(gameText(language, "Find all four matching pairs.", "চারটি মিল খুঁজে বের করুন।", "चारों जोड़े ढूँढ़ें।"))
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (column in 0..3) {
                    val index = row * 4 + column
                    val visible = index in revealed || index in matched
                    OutlinedButton(
                        onClick = {
                            if (!visible && revealed.size < 2) revealed = revealed + index
                        },
                        enabled = !visible && revealed.size < 2,
                        modifier = Modifier.weight(1f).aspectRatio(0.9f),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(if (visible) cards[index] else "?", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        if (matched.size == cards.size) Text(gameText(language, "All pairs found!", "সব জোড়া মিলেছে!", "सभी जोड़े मिल गए!"))
    }
}

private val scrambleWords = listOf("FRIEND", "SMILE", "PLANET", "GARDEN", "ORANGE", "PUZZLE")

private fun scrambledWord(word: String): String {
    val shuffled = word.toList().shuffled().joinToString("")
    return if (shuffled.equals(word, ignoreCase = true)) word.drop(1) + word.first() else shuffled
}

@Composable
private fun WordScrambleGame(language: AppLanguage) {
    var word by remember { mutableStateOf(scrambleWords.random()) }
    var scrambled by remember { mutableStateOf(scrambledWord(word)) }
    var answer by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(gameText(language, "Unscramble this word:", "শব্দটি সাজান:", "इस शब्द को सही क्रम में लगाएँ:"))
        Text(scrambled, style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it.take(24) },
            singleLine = true,
            label = { Text(gameText(language, "Your answer", "আপনার উত্তর", "आपका जवाब")) }
        )
        Button(onClick = {
            result = if (answer.trim().equals(word, ignoreCase = true)) {
                gameText(language, "Correct!", "সঠিক!", "सही!")
            } else {
                gameText(language, "Not quite. Try again.", "ঠিক হয়নি, আবার চেষ্টা করুন।", "सही नहीं, फिर कोशिश करें।")
            }
        }) {
            Text(gameText(language, "Check", "যাচাই", "जाँचें"))
        }
        if (result.isNotBlank()) Text(result)
        TextButton(onClick = {
            word = (scrambleWords - word).random()
            scrambled = scrambledWord(word)
            answer = ""
            result = ""
        }) {
            Text(gameText(language, "New word", "নতুন শব্দ", "नया शब्द"))
        }
    }
}

private data class MathQuestion(val first: Int, val second: Int, val operator: String) {
    val answer: Int
        get() = if (operator == "+") first + second else first - second
}

private fun newMathQuestion(): MathQuestion {
    val first = (2..20).random()
    val second = (1..first).random()
    return MathQuestion(first, second, if ((0..1).random() == 0) "+" else "-")
}

@Composable
private fun QuickMathGame(language: AppLanguage) {
    var question by remember { mutableStateOf(newMathQuestion()) }
    var answer by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(gameText(language, "Solve this:", "সমাধান করুন:", "इसे हल करें:"))
        Text("${question.first} ${question.operator} ${question.second} = ?", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it.filter(Char::isDigit).take(3) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(gameText(language, "Answer", "উত্তর", "जवाब")) }
        )
        Button(onClick = {
            result = if (answer.toIntOrNull() == question.answer) {
                gameText(language, "Correct!", "সঠিক!", "सही!")
            } else {
                gameText(language, "Try again.", "আবার চেষ্টা করুন।", "फिर कोशिश करें।")
            }
        }) {
            Text(gameText(language, "Check", "যাচাই", "जाँचें"))
        }
        if (result.isNotBlank()) Text(result)
        TextButton(onClick = { question = newMathQuestion(); answer = ""; result = "" }) {
            Text(gameText(language, "New question", "নতুন প্রশ্ন", "नया सवाल"))
        }
    }
}

@Composable
private fun DiceDuelGame(language: AppLanguage) {
    var player by remember { mutableIntStateOf(0) }
    var dost by remember { mutableIntStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(gameText(language, "Roll against Dost.", "Dost-এর সঙ্গে পাশা খেলুন।", "Dost के साथ पासा खेलें।"))
        if (player > 0) {
            Text(gameText(language, "You: $player · Dost: $dost", "আপনি: $player · Dost: $dost", "आप: $player · Dost: $dost"))
            Text(
                when {
                    player > dost -> gameText(language, "You win!", "আপনি জিতেছেন!", "आप जीत गए!")
                    player < dost -> gameText(language, "Dost wins.", "Dost জিতেছে।", "Dost जीत गया।")
                    else -> gameText(language, "It's a tie.", "ড্র হয়েছে।", "मैच बराबर रहा।")
                }
            )
        }
        Button(onClick = { player = (1..6).random(); dost = (1..6).random() }) {
            Text(gameText(language, "Roll dice", "পাশা ফেলুন", "पासा फेंकें"))
        }
    }
}

@Composable
private fun CoinTossGame(language: AppLanguage) {
    var choice by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf("") }
    val heads = gameText(language, "Heads", "হেড", "चित")
    val tails = gameText(language, "Tails", "টেল", "पट")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(gameText(language, "Pick a side, then flip.", "একটি দিক বেছে কয়েন ফেলুন।", "एक तरफ़ चुनें, फिर सिक्का उछालें।"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(heads, tails).forEach { side ->
                OutlinedButton(onClick = { choice = side; result = "" }) { Text(side) }
            }
        }
        Button(onClick = {
            val landed = if ((0..1).random() == 0) heads else tails
            result = if (choice == landed) {
                gameText(language, "It was $landed. You win!", "$landed উঠেছে। আপনি জিতেছেন!", "$landed आया। आप जीत गए!")
            } else {
                gameText(language, "It was $landed. Try again!", "$landed উঠেছে। আবার চেষ্টা করুন!", "$landed आया। फिर कोशिश करें!")
            }
        }, enabled = choice != null) {
            Text(gameText(language, "Flip coin", "কয়েন ফেলুন", "सिक्का उछालें"))
        }
        if (result.isNotBlank()) Text(result)
    }
}

@Composable
private fun ColorMatchGame(language: AppLanguage) {
    val colors = listOf(
        gameText(language, "Red", "লাল", "लाल") to Color(0xFFD84343),
        gameText(language, "Blue", "নীল", "नीला") to Color(0xFF3676D9),
        gameText(language, "Green", "সবুজ", "हरा") to Color(0xFF328A58),
        gameText(language, "Purple", "বেগুনি", "बैंगनी") to Color(0xFF8752B3)
    )
    var word by remember { mutableStateOf(colors.random().first) }
    var inkIndex by remember { mutableIntStateOf(colors.indices.random()) }
    var result by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(gameText(language, "Tap the ink color, not the word.", "শব্দ নয়, লেখার রং বেছে নিন।", "शब्द नहीं, अक्षरों का रंग चुनें।"))
        Text(word, style = MaterialTheme.typography.headlineLarge, color = colors[inkIndex].second)
        colors.forEach { (name, _) ->
            OutlinedButton(
                onClick = {
                    result = if (name == colors[inkIndex].first) {
                        gameText(language, "Correct!", "সঠিক!", "सही!")
                    } else {
                        gameText(language, "Try another color.", "অন্য রং চেষ্টা করুন।", "दूसरा रंग आज़माएँ।")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(name)
            }
        }
        if (result.isNotBlank()) Text(result)
        TextButton(onClick = {
            word = colors.random().first
            inkIndex = colors.indices.random()
            result = ""
        }) {
            Text(gameText(language, "New round", "নতুন রাউন্ড", "नया दौर"))
        }
    }
}

@Composable
private fun WrestlingWebViewScreen(language: AppLanguage, onBack: () -> Unit) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        if (webView?.canGoBack() == true) webView?.goBack() else onBack()
    }
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(gameTitle(MiniGame.EPIC_FURY, language)) },
                navigationIcon = { DostBackButton(language, onBack) }
            )
        }
    ) { padding ->
        AndroidView(
            factory = {
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            return request.isForMainFrame &&
                                (request.url.scheme != "https" || request.url.host != "epicfurywrestling.web.app")
                        }
                    }
                    webView = this
                    loadUrl(WRESTLING_GAME_URL)
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }
}

private fun gameText(language: AppLanguage, english: String, bangla: String, hindi: String): String = when (language) {
    AppLanguage.ENGLISH -> english
    AppLanguage.BANGLA -> bangla
    AppLanguage.HINDI -> hindi
}

private fun gameTitle(game: MiniGame, language: AppLanguage): String = when (game) {
    MiniGame.NUMBER_GUESS -> gameText(language, "Guess the Number", "সংখ্যা অনুমান", "संख्या का अनुमान")
    MiniGame.ROCK_PAPER_SCISSORS -> gameText(language, "Rock Paper Scissors", "পাথর কাগজ কাঁচি", "पत्थर कागज़ कैंची")
    MiniGame.TIC_TAC_TOE -> gameText(language, "Tic-Tac-Toe", "টিক-ট্যাক-টো", "टिक-टैक-टो")
    MiniGame.MEMORY_MATCH -> gameText(language, "Memory Match", "স্মৃতি মেলান", "मेमोरी मैच")
    MiniGame.WORD_SCRAMBLE -> gameText(language, "Word Scramble", "শব্দ সাজান", "शब्द पहेली")
    MiniGame.QUICK_MATH -> gameText(language, "Quick Math", "দ্রুত গণিত", "तेज़ गणित")
    MiniGame.DICE_DUEL -> gameText(language, "Dice Duel", "পাশার লড়াই", "पासा मुकाबला")
    MiniGame.COIN_TOSS -> gameText(language, "Coin Toss", "কয়েন টস", "सिक्का उछाल")
    MiniGame.COLOR_MATCH -> gameText(language, "Color Match", "রং মেলান", "रंग मिलाएँ")
    MiniGame.EPIC_FURY -> "Epic Fury Wrestling"
}
