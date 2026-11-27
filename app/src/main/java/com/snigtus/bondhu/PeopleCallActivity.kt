package com.snigtus.dost

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.snigtus.dost.ui.theme.DostTheme
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer

class PeopleCallActivity : ComponentActivity() {
    private val engine by lazy { PeopleCallEngineProvider.get(applicationContext) }
    private val repository by lazy { PeopleRepository(applicationContext) }
    private var requestedAction = ""
    private var chatId = ""
    private var callId = ""
    private var callerUid = ""
    private var calleeUid = ""
    private var peerUid = ""
    private var peerName = ""
    private var requestedMediaType = "audio"
    private var uid = ""
    private var language = AppLanguage.ENGLISH

    private val callPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (granted) {
            runRequestedAction()
        } else {
            lifecycleScope.launch {
                if (requestedAction == PeopleCallNotifications.ACTION_ANSWER) {
                    try {
                        val currentUid = FirebaseAuthRepository(applicationContext).currentAccount?.uid
                            ?: throw IllegalStateException("Sign in again to answer this call.")
                        repository.updateCallStatus(currentUid, chatId, callId, "rejected")
                    } catch (error: Exception) {
                        Log.e(TAG, "Could not reject a call after microphone permission was denied.", error)
                    }
                }
                engine.reportPermissionDenied(
                    if (requestedMediaType == "video") {
                        "Allow microphone and camera access to make a video call."
                    } else {
                        "Allow microphone access to make an audio call."
                    }
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedAction = intent.action.orEmpty()
        chatId = intent.getStringExtra(EXTRA_CHAT_ID).orEmpty()
        callId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        callerUid = intent.getStringExtra(EXTRA_CALLER_UID).orEmpty()
        calleeUid = intent.getStringExtra(EXTRA_CALLEE_UID).orEmpty()
        peerName = intent.getStringExtra(EXTRA_PEER_NAME).orEmpty().ifBlank { "Dost user" }.take(60)
        requestedMediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE).orEmpty().ifBlank { "audio" }
        language = DostStore(this).language()
        uid = FirebaseAuthRepository(applicationContext).currentAccount?.uid.orEmpty()
        peerUid = if (uid == callerUid) calleeUid else callerUid

        setContent {
            DostTheme {
                val state by engine.state.collectAsState()
                PeopleCallScreen(
                    language = language,
                    state = state,
                    engine = engine,
                    onAnswer = { requestCallPermissions(PeopleCallNotifications.ACTION_ANSWER, state.mediaType) },
                    onDecline = { finishIncomingCall() },
                    onEnd = { endCall() },
                    onToggleMute = { engine.setMuted(!state.muted) },
                    onToggleSpeaker = { engine.setSpeakerphone(!state.speakerOn) },
                    onToggleCamera = { engine.setVideoEnabled(!state.cameraEnabled) },
                    onDone = { finish() }
                )
            }
        }
        when (requestedAction) {
            ACTION_START_OUTGOING, PeopleCallNotifications.ACTION_ANSWER ->
                requestCallPermissions(requestedAction, requestedMediaType)
            PeopleCallNotifications.ACTION_OPEN_INCOMING -> openIncomingCall()
        }
    }

    private fun requestCallPermissions(action: String, mediaType: String) {
        requestedAction = action
        requestedMediaType = mediaType
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (mediaType == "video") add(Manifest.permission.CAMERA)
        }
        if (permissions.all { permission ->
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            runRequestedAction()
        } else {
            callPermissions.launch(permissions.toTypedArray())
        }
    }

    private fun runRequestedAction() {
        lifecycleScope.launch {
            if (uid.isBlank()) {
                engine.reportPermissionDenied("Sign in again to use People calls.")
                return@launch
            }
            when (requestedAction) {
                ACTION_START_OUTGOING ->
                    engine.startOutgoing(uid, chatId, peerUid, peerName, requestedMediaType)
                PeopleCallNotifications.ACTION_ANSWER -> {
                    engine.openIncoming(uid, chatId, callId, peerName)
                    if (engine.state.value.phase == PeopleCallPhase.INCOMING) {
                        if (engine.state.value.mediaType == "video" &&
                            ContextCompat.checkSelfPermission(this@PeopleCallActivity, Manifest.permission.CAMERA) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            requestCallPermissions(PeopleCallNotifications.ACTION_ANSWER, "video")
                        } else {
                            engine.answerIncoming()
                        }
                    }
                }
            }
        }
    }

    private fun openIncomingCall() {
        lifecycleScope.launch {
            if (uid.isBlank()) {
                engine.reportPermissionDenied("Sign in again to answer this call.")
                return@launch
            }
            engine.openIncoming(uid, chatId, callId, peerName)
        }
    }

    private fun finishIncomingCall() {
        lifecycleScope.launch {
            try {
                engine.rejectIncoming()
                finish()
            } catch (error: Exception) {
                Log.e(TAG, "Could not decline the incoming People call.", error)
                engine.reportPermissionDenied(error.message ?: "Could not decline this call.")
            }
        }
    }

    private fun endCall() {
        lifecycleScope.launch {
            try {
                engine.endCall()
                finish()
            } catch (error: Exception) {
                Log.e(TAG, "Could not end the People call.", error)
                engine.reportPermissionDenied(error.message ?: "Could not end this call.")
            }
        }
    }

    companion object {
        const val EXTRA_CHAT_ID = "people_call_chat_id"
        const val EXTRA_CALL_ID = "people_call_call_id"
        const val EXTRA_CALLER_UID = "people_call_caller_uid"
        const val EXTRA_CALLEE_UID = "people_call_callee_uid"
        const val EXTRA_PEER_NAME = "people_call_peer_name"
        const val EXTRA_MEDIA_TYPE = "people_call_media_type"
        const val ACTION_START_OUTGOING = "com.snigtus.dost.action.PEOPLE_CALL_OUTGOING"

        private const val TAG = "PeopleCall"
    }
}

@Composable
private fun PeopleCallScreen(
    language: AppLanguage,
    state: PeopleCallUiState,
    engine: PeopleCallEngine,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onDone: () -> Unit
) {
    val title = if (state.mediaType == "video") {
        callText(language, "People video call", "পিপল ভিডিও কল", "पीपल वीडियो कॉल")
    } else {
        callText(language, "People audio call", "পিপল অডিও কল", "पीपल ऑडियो कॉल")
    }
    val status = when (state.phase) {
        PeopleCallPhase.LOADING -> callText(language, "Loading call…", "কল লোড হচ্ছে…", "कॉल लोड हो रही है…")
        PeopleCallPhase.INCOMING -> callText(language, "Incoming call", "ইনকামিং কল", "इनकमिंग कॉल")
        PeopleCallPhase.STARTING -> callText(language, "Starting call…", "কল শুরু হচ্ছে…", "कॉल शुरू हो रही है…")
        PeopleCallPhase.RINGING -> callText(language, "Ringing…", "রিং হচ্ছে…", "रिंग हो रही है…")
        PeopleCallPhase.CONNECTING -> callText(language, "Connecting…", "সংযোগ হচ্ছে…", "कनेक्ट हो रहा है…")
        PeopleCallPhase.CONNECTED -> formatCallDuration(state.durationSeconds)
        PeopleCallPhase.FINISHED -> state.message.ifBlank {
            callText(language, "Call ended", "কল শেষ হয়েছে", "कॉल समाप्त हुई")
        }
        PeopleCallPhase.FAILED -> state.message
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val showVideo = state.mediaType == "video" &&
                state.phase !in setOf(PeopleCallPhase.LOADING, PeopleCallPhase.FINISHED, PeopleCallPhase.FAILED)
            if (showVideo) {
                val context = LocalContext.current
                val localRenderer = remember(state.chatId) { SurfaceViewRenderer(context) }
                val remoteRenderer = remember(state.chatId) { SurfaceViewRenderer(context) }
                DisposableEffect(state.chatId, localRenderer, remoteRenderer) {
                    engine.attachVideoRenderers(localRenderer, remoteRenderer)
                    onDispose { engine.detachVideoRenderers() }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(factory = { remoteRenderer }, modifier = Modifier.fillMaxSize())
                    AndroidView(
                        factory = { localRenderer },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(112.dp)
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.size(112.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(
                            state.peerName.take(1).uppercase(),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                state.peerName.ifBlank { callText(language, "Dost user", "দোস্ত ব্যবহারকারী", "दोस्त उपयोगकर्ता") },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(status, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (state.phase == PeopleCallPhase.STARTING || state.phase == PeopleCallPhase.CONNECTING ||
                state.phase == PeopleCallPhase.LOADING
            ) {
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator()
            }
            Spacer(Modifier.height(36.dp))
            when (state.phase) {
                PeopleCallPhase.INCOMING -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = onDecline) {
                        Text(callText(language, "Decline", "প্রত্যাখ্যান", "अस्वीकार"))
                    }
                    Button(onClick = onAnswer) {
                        Text(callText(language, "Answer", "উত্তর দিন", "उत्तर दें"))
                    }
                }
                PeopleCallPhase.STARTING, PeopleCallPhase.RINGING,
                PeopleCallPhase.CONNECTING, PeopleCallPhase.CONNECTED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onToggleMute) {
                            Text(if (state.muted) callText(language, "Unmute", "আনমিউট", "अनम्यूट") else callText(language, "Mute", "মিউট", "म्यूट"))
                        }
                        OutlinedButton(onClick = onToggleSpeaker) {
                            Text(if (state.speakerOn) callText(language, "Earpiece", "ইয়ারপিস", "ईयरपीस") else callText(language, "Speaker", "স্পিকার", "स्पीकर"))
                        }
                        if (state.mediaType == "video") {
                            OutlinedButton(onClick = onToggleCamera) {
                                Text(
                                    if (state.cameraEnabled) {
                                        callText(language, "Camera off", "ক্যামেরা বন্ধ", "कैमरा बंद")
                                    } else {
                                        callText(language, "Camera on", "ক্যামেরা চালু", "कैमरा चालू")
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onEnd) {
                        Text(callText(language, "End call", "কল শেষ করুন", "कॉल समाप्त करें"))
                    }
                }
                PeopleCallPhase.FINISHED, PeopleCallPhase.FAILED -> {
                    Button(onClick = onDone) {
                        Text(callText(language, "Done", "সম্পন্ন", "हो गया"))
                    }
                }
                PeopleCallPhase.LOADING -> Unit
            }
        }
    }
}

private fun callText(language: AppLanguage, english: String, bangla: String, hindi: String): String =
    when (language) {
        AppLanguage.ENGLISH -> english
        AppLanguage.BANGLA -> bangla
        AppLanguage.HINDI -> hindi
    }

private fun formatCallDuration(seconds: Long): String =
    "%02d:%02d".format(seconds / 60, seconds % 60)
