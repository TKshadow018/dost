package com.snigtus.dost

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Base64
import java.util.Date

@Composable
internal fun CipherScreen(
    language: AppLanguage,
    uid: String,
    conversation: SocialConversation,
    onBack: () -> Unit,
    onOpenPlans: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.cipherActivity()
    val repository = remember(context) { CipherRepository(context) }
    val scope = rememberCoroutineScope()
    var chat by remember(conversation.id) { mutableStateOf<CipherChat?>(null) }
    var loading by remember(conversation.id) { mutableStateOf(true) }
    var passphrase by remember(conversation.id) { mutableStateOf("") }
    var messageKey by remember(conversation.id) { mutableStateOf<ByteArray?>(null) }
    var messages by remember(conversation.id) { mutableStateOf(emptyList<Pair<StoredCipherMessage, String>>()) }
    var draft by remember(conversation.id) { mutableStateOf("") }
    var error by remember(conversation.id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showDisclosure by remember { mutableStateOf(false) }
    var shareConfirmed by remember { mutableStateOf(false) }

    fun lock() {
        messageKey?.fill(0)
        messageKey = null
        messages = emptyList()
        draft = ""
    }

    DisposableEffect(activity, conversation.id) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) lock()
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose {
            lock()
            activity?.lifecycle?.removeObserver(observer)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(uid, conversation.id) {
        loading = true
        error = ""
        try {
            chat = withContext(Dispatchers.IO) { repository.getChat(uid, conversation.id) }
            if (chat == null) passphrase = generatedCipherPassphrase()
        } catch (exception: Exception) {
            error = exception.message ?: cipherText(language, "Cipher could not be opened.")
        } finally {
            loading = false
        }
    }

    LaunchedEffect(messageKey) {
        val sessionKey = messageKey ?: return@LaunchedEffect
        delay(CIPHER_AUTO_LOCK_MILLIS)
        if (messageKey === sessionKey) lock()
    }

    DisposableEffect(uid, conversation.id, messageKey) {
        val key = messageKey
        if (key == null || chat == null) {
            onDispose { }
        } else {
            val registration = repository.listenMessages(
                uid,
                conversation.id,
                onChange = { encryptedMessages ->
                    scope.launch(Dispatchers.IO) {
                        val decrypted = encryptedMessages.mapNotNull { item ->
                            runCatching {
                                item to CipherCrypto.decryptMessage(
                                    conversation.id,
                                    item.senderUid,
                                    item.id,
                                    item.encrypted,
                                    key
                                )
                            }.getOrNull()
                        }
                        withContext(Dispatchers.Main) {
                            if (messageKey === key) {
                                messages = decrypted
                                if (decrypted.size != encryptedMessages.size) {
                                    error = cipherText(language, "A message could not be decrypted.")
                                }
                            }
                        }
                    }
                },
                onError = { error = it.message ?: cipherText(language, "Could not load Cipher messages.") }
            )
            onDispose { registration.remove() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cipher · ${conversation.peer.displayName}") },
                navigationIcon = { DostBackButton(language, onBack) },
                actions = {
                    if (chat != null && messageKey != null) {
                        TextButton(onClick = { lock() }) {
                            Icon(Icons.Filled.Lock, contentDescription = null)
                            Text(cipherText(language, "Lock"))
                        }
                    }
                }
            )
        }
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CipherDisclosure(language, onReadMore = { showDisclosure = true })
            if (loading) {
                CircularProgressIndicator()
            } else if (chat == null) {
                Text(
                    cipherText(language, "Start a passphrase-encrypted chat with this existing People contact."),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (BuildConfig.DOST_PREMIUM_ENABLED) {
                    TextButton(onClick = onOpenPlans) {
                        Text(cipherText(language, "View Cipher plans"))
                    }
                }
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it.take(160) },
                    label = { Text(cipherText(language, "Six-word passphrase")) },
                    supportingText = {
                        Text(
                            cipherText(language, "Share it in person or through another channel. Never send it through Dost. If you lose it, the messages are gone.")
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (cipherPassphraseStrength(passphrase)) {
                            0 -> cipherText(language, "Strength: weak")
                            1 -> cipherText(language, "Strength: fair")
                            else -> cipherText(language, "Strength: strong")
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { passphrase = generatedCipherPassphrase() }) {
                        Text(cipherText(language, "Generate"))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = shareConfirmed,
                        onCheckedChange = { shareConfirmed = it }
                    )
                    Text(cipherText(language, "We have agreed to share this passphrase outside Dost."))
                }
                Button(
                    enabled = !busy && isValidCipherPassphrase(passphrase) && shareConfirmed,
                    onClick = {
                        busy = true
                        error = ""
                        val phrase = passphrase.toCharArray()
                        scope.launch {
                            try {
                                val created = withContext(Dispatchers.IO) {
                                    val salt = CipherCrypto.newSalt()
                                    val keys = CipherCrypto.deriveKeys(phrase, salt)
                                    try {
                                        val check = CipherCrypto.encryptKeyCheck(conversation.id, keys.checkKey)
                                        repository.createChat(uid, conversation.peer.uid, conversation.id, salt, check)
                                    } finally {
                                        keys.wipe()
                                        salt.fill(0)
                                    }
                                }
                                chat = created
                                passphrase = ""
                                shareConfirmed = false
                            } catch (exception: Exception) {
                                error = exception.message ?: cipherText(language, "Cipher chat could not be created. The creator needs an active Cipher entitlement.")
                            } finally {
                                phrase.fill('\u0000')
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(cipherText(language, "Create Cipher chat"))
                }
                Text(
                    cipherText(language, "Only the creator needs an active Cipher subscription. Invited participants can unlock and reply for free.")
                )
            } else if (messageKey == null) {
                Text(cipherText(language, "Enter the passphrase shared by the chat creator. Dost cannot recover it."))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it.take(160) },
                    label = { Text(cipherText(language, "Passphrase")) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = !busy && isValidCipherPassphrase(passphrase),
                    onClick = {
                        busy = true
                        error = ""
                        val currentChat = chat ?: return@Button
                        val phrase = passphrase.toCharArray()
                        scope.launch {
                            try {
                                val key = withContext(Dispatchers.IO) {
                                    val salt = Base64.getDecoder().decode(currentChat.salt)
                                    val keys = CipherCrypto.deriveKeys(phrase, salt)
                                    try {
                                        CipherCrypto.verifyKeyCheck(currentChat.id, currentChat.keyCheck, keys.checkKey)
                                        keys.messageKey.copyOf()
                                    } finally {
                                        keys.wipe()
                                        salt.fill(0)
                                    }
                                }
                                messageKey = key
                                passphrase = ""
                            } catch (_: WrongCipherPassphraseException) {
                                error = cipherText(language, "Wrong passphrase.")
                            } catch (exception: Exception) {
                                error = exception.message ?: cipherText(language, "Cipher chat could not be unlocked.")
                            } finally {
                                phrase.fill('\u0000')
                                busy = false
                            }
                        }
                    }
                ) { Text(cipherText(language, "Unlock chat")) }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.first.id }) { (message, text) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (message.senderUid == uid) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(text)
                                Text(
                                    Date(message.createdAtMillis).toString(),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it.take(2_000) },
                        modifier = Modifier.weight(1f).padding(12.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        maxLines = 4,
                        decorationBox = { inner ->
                            if (draft.isBlank()) Text(cipherText(language, "Write a text message…"))
                            inner()
                        }
                    )
                    TextButton(
                        enabled = !busy && isValidCipherMessageText(draft.trim()),
                        onClick = {
                            val draftToSend = draft
                            val text = draftToSend.trim()
                            val key = messageKey ?: return@TextButton
                            val id = CipherCrypto.newMessageId()
                            busy = true
                            scope.launch {
                                try {
                                    val encrypted = withContext(Dispatchers.IO) {
                                        CipherCrypto.encryptMessage(conversation.id, uid, id, text, key)
                                    }
                                    repository.sendMessage(
                                        uid,
                                        conversation.id,
                                        StoredCipherMessage(id, uid, encrypted, System.currentTimeMillis())
                                    )
                                    if (draft == draftToSend) draft = ""
                                    runCatching {
                                        PeopleMessageNotificationWork.enqueue(
                                            context,
                                            DostStore(context).profileNamespace,
                                            uid,
                                            conversation.id,
                                            id,
                                            cipher = true
                                        )
                                    }
                                } catch (exception: Exception) {
                                    error = exception.message ?: cipherText(language, "Cipher message could not be sent.")
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp))
                        else Icon(Icons.Filled.Send, contentDescription = cipherText(language, "Send"))
                    }
                }
                Text(cipherText(language, "Cipher notifications are generic and never include message content."))
            }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(2.dp))
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(cipherText(language, "About Cipher")) },
            text = { CipherDisclosure(language, detailed = true) },
            confirmButton = {
                TextButton(onClick = { showDisclosure = false }) {
                    Text(cipherText(language, "Close"))
                }
            }
        )
    }
}

@Composable
private fun CipherDisclosure(language: AppLanguage, onReadMore: (() -> Unit)? = null, detailed: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(cipherText(language, "Messages are encrypted on your device. Dost does not receive the passphrase and cannot recover it."))
        if (detailed) {
            Text(cipherText(language, "Metadata is not hidden: members, message times, frequency, and ciphertext sizes remain visible to Dost/Firebase. FCM tokens may be used for delivery."))
            Text(cipherText(language, "A weak passphrase can be brute-forced offline. Argon2id slows guessing but cannot prevent it."))
            Text(cipherText(language, "There is one shared key and no forward secrecy. Anyone who learns it can read past and future messages, and a key holder could impersonate another member."))
            Text(cipherText(language, "A compromised or rooted phone, or someone looking over your shoulder, can expose messages."))
            Text(cipherText(language, "Report and block remain available in the linked Circle chat. Reports contain metadata and the reason you type. Messages are never attached automatically; you can decrypt and paste only messages you choose to include."))
            Text(cipherText(language, "Share the passphrase in person or through another channel. Never send it through Dost. If you lose it, the messages are gone."))
        }
        if (onReadMore != null) {
            TextButton(onClick = onReadMore) { Text(cipherText(language, "Read security limits")) }
        }
    }
}

private fun Context.cipherActivity(): Activity? {
    var current: Context? = this
    while (current != null) {
        if (current is Activity) return current
        val wrapper = current as? ContextWrapper ?: return null
        if (wrapper.baseContext === current) return null
        current = wrapper.baseContext
    }
    return null
}

private fun cipherText(language: AppLanguage, english: String): String = when (language) {
    AppLanguage.ENGLISH -> english
    AppLanguage.BANGLA -> english
    AppLanguage.HINDI -> english
}

private const val CIPHER_AUTO_LOCK_MILLIS = 60_000L
