package com.snigtus.dost

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.CandidatePairChangeEvent
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.IceCandidateErrorEvent
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class PeopleCallPhase {
    LOADING,
    INCOMING,
    STARTING,
    RINGING,
    CONNECTING,
    CONNECTED,
    FINISHED,
    FAILED
}

internal data class PeopleCallUiState(
    val phase: PeopleCallPhase = PeopleCallPhase.LOADING,
    val chatId: String = "",
    val callId: String = "",
    val peerName: String = "",
    val mediaType: String = "audio",
    val localIsCaller: Boolean = false,
    val muted: Boolean = false,
    val speakerOn: Boolean = false,
    val cameraEnabled: Boolean = true,
    val durationSeconds: Long = 0L,
    val message: String = ""
)

internal object PeopleCallEngineProvider {
    @Volatile
    private var instance: PeopleCallEngine? = null

    fun get(context: Context): PeopleCallEngine = instance ?: synchronized(this) {
        instance ?: PeopleCallEngine(context.applicationContext).also { instance = it }
    }
}

internal class PeopleCallEngine(context: Context) {
    private val appContext = context.applicationContext
    private val repository = PeopleRepository(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val eglBase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { EglBase.create() }
    private val mutableState = MutableStateFlow(PeopleCallUiState())
    val state: StateFlow<PeopleCallUiState> = mutableState.asStateFlow()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var audioDeviceModule: AudioDeviceModule? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var cameraCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var frontCameraSelected = true
    private var mediaSessionId = 0L
    private var peerConnection: PeerConnection? = null
    private var callListener: ListenerRegistration? = null
    private var remoteCandidatesListener: ListenerRegistration? = null
    private var callTimeoutJob: Job? = null
    private var durationJob: Job? = null
    private var activeUid = ""
    private var activeChatId = ""
    private var activeCallId = ""
    private var activePeerName = ""
    private var activeMediaType = "audio"
    private var activeExpiresAtMillis = 0L
    private var localIsCaller = false
    private var activeCallAccepted = false
    private var remoteDescriptionSet = false
    private var settingRemoteDescription = false
    private var callStartedAtMillis = 0L
    private var audioModeActive = false
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private val seenRemoteCandidateIds = mutableSetOf<String>()

    suspend fun openIncoming(uid: String, chatId: String, callId: String, peerName: String) {
        try {
            val currentUid = requireCurrentUid()
            check(currentUid == uid) { "Sign in again to answer this call." }
            val call = repository.getCall(uid, chatId, callId)
                ?: throw IOException("This call is no longer available.")
            check(call.calleeUid == uid && call.callerUid != uid) { "This call is not addressed to this account." }
            check(call.status == "ringing" && call.ringExpiresAtMillis > System.currentTimeMillis()) {
                "This call has already ended."
            }
            prepareCall(call, peerName, localIsCaller = false)
            mutableState.value = PeopleCallUiState(
                phase = PeopleCallPhase.INCOMING,
                chatId = chatId,
                callId = callId,
                peerName = activePeerName,
                mediaType = call.mediaType,
                localIsCaller = false
            )
            bindCallListener()
            scheduleRingTimeout(call.ringExpiresAtMillis)
        } catch (error: Exception) {
            showFailure(error)
        }
    }

    suspend fun startOutgoing(
        uid: String,
        chatId: String,
        peerUid: String,
        peerName: String,
        mediaType: String = "audio"
    ) {
        var newCallId = ""
        try {
            check(requireCurrentUid() == uid) { "Sign in again to place this call." }
            check(peerUid.isNotBlank() && peerUid != uid) { "This person is not available for a call." }
            check(mediaType in PEOPLE_CALL_MEDIA_TYPES) { "This call type is not supported." }
            checkMicrophonePermission()
            if (mediaType == "video") checkCameraPermission()
            prepareNewCall(uid, chatId, peerUid, peerName, mediaType, localIsCaller = true)
            mutableState.value = PeopleCallUiState(
                phase = PeopleCallPhase.STARTING,
                chatId = chatId,
                peerName = activePeerName,
                mediaType = mediaType,
                localIsCaller = true
            )
            val connection = createPeerConnection(emptyList(), mediaType == "video")
            val offer = connection.createOfferAwait()
            val now = System.currentTimeMillis()
            val ringExpiry = now + RING_TIMEOUT_MILLIS
            val expiresAt = now + MAX_CALL_DURATION_MILLIS
            newCallId = repository.createCall(
                uid,
                chatId,
                peerUid,
                mediaType,
                offer.description,
                ringExpiry,
                expiresAt
            )
            activeCallId = newCallId
            activeExpiresAtMillis = expiresAt
            PeopleAudioCallService.start(
                appContext,
                activeUid,
                activeChatId,
                activeCallId,
                activePeerName,
                localIsCaller = true,
                mediaType = mediaType
            )
            val iceServers = repository.callIceServers(uid, chatId, newCallId)
            check(connection.setConfiguration(iceConfiguration(iceServers))) {
                "Could not apply the TURN server configuration."
            }
            connection.setLocalDescriptionAwait(offer)
            mutableState.value = mutableState.value.copy(
                phase = PeopleCallPhase.RINGING,
                callId = newCallId,
                message = "Calling…"
            )
            bindCallListener()
            bindRemoteCandidates()
            repository.notifyIncomingCall(uid, chatId, newCallId)
            scheduleRingTimeout(ringExpiry)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (newCallId.isNotBlank()) {
                try {
                    repository.updateCallStatus(uid, chatId, newCallId, "cancelled")
                } catch (cleanupError: Exception) {
                    error.addSuppressed(cleanupError)
                    Log.e(TAG, "Could not cancel an incomplete People call.", cleanupError)
                }
            }
            releaseMedia()
            showFailure(error)
        }
    }

    suspend fun answerIncoming() {
        try {
            checkMicrophonePermission()
            val uid = requireCurrentUid()
            val call = repository.getCall(uid, activeChatId, activeCallId)
                ?: throw IOException("This call is no longer available.")
            check(call.calleeUid == uid && call.status == "ringing"
                && call.ringExpiresAtMillis > System.currentTimeMillis()
            ) { "This call has already ended." }
            mutableState.value = mutableState.value.copy(
                phase = PeopleCallPhase.CONNECTING,
                message = "Connecting…"
            )
            val iceServers = repository.callIceServers(uid, activeChatId, activeCallId)
            val connection = createPeerConnection(iceServers, activeMediaType == "video")
            connection.setRemoteDescriptionAwait(SessionDescription(
                SessionDescription.Type.OFFER,
                call.offer.sdp
            ))
            remoteDescriptionSet = true
            drainRemoteCandidates()
            val answer = connection.createAnswerAwait()
            connection.setLocalDescriptionAwait(answer)
            repository.answerCall(uid, activeChatId, activeCallId, answer.description)
            activeCallAccepted = true
            bindCallListener()
            bindRemoteCandidates()
            PeopleAudioCallService.start(
                appContext,
                activeUid,
                activeChatId,
                activeCallId,
                activePeerName,
                localIsCaller = false,
                mediaType = activeMediaType
            )
            mutableState.value = mutableState.value.copy(message = "Connecting…")
            scheduleCallLimit(call.expiresAtMillis)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            failActiveCall(error)
        }
    }

    suspend fun rejectIncoming() {
        val uid = requireCurrentUid()
        repository.updateCallStatus(uid, activeChatId, activeCallId, "rejected")
        finishCall("Call declined.")
    }

    suspend fun endCall() {
        if (activeCallId.isBlank()) return
        val uid = requireCurrentUid()
        val endStatus = when {
            activeCallAccepted -> "ended"
            localIsCaller -> "cancelled"
            else -> "rejected"
        }
        repository.updateCallStatus(uid, activeChatId, activeCallId, endStatus)
        finishCall("Call ended.")
    }

    suspend fun endFromNotification(uid: String, chatId: String, callId: String) {
        if (activeCallId == callId && activeChatId == chatId) {
            endCall()
        } else {
            repository.updateCallStatus(uid, chatId, callId, "ended")
        }
    }

    fun setMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
        mutableState.value = mutableState.value.copy(muted = muted)
    }

    fun setSpeakerphone(enabled: Boolean) {
        audioManager.isSpeakerphoneOn = enabled
        mutableState.value = mutableState.value.copy(speakerOn = enabled)
    }

    fun setVideoEnabled(enabled: Boolean) {
        val track = localVideoTrack ?: return
        val capturer = cameraCapturer ?: return
        if (enabled == mutableState.value.cameraEnabled) return
        try {
            if (enabled) {
                checkCameraPermission()
                capturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
                track.setEnabled(true)
            } else {
                track.setEnabled(false)
                capturer.stopCapture()
            }
            mutableState.value = mutableState.value.copy(cameraEnabled = enabled)
        } catch (error: Exception) {
            Log.e(TAG, "Could not change People call camera state.", error)
            mutableState.value = mutableState.value.copy(
                message = error.message?.take(180) ?: "Could not change camera state."
            )
        }
    }

    fun attachVideoRenderers(local: SurfaceViewRenderer, remote: SurfaceViewRenderer) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Video renderers must be attached on the main thread." }
        detachVideoRenderers()
        local.init(eglBase.eglBaseContext, null)
        local.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        local.setMirror(frontCameraSelected)
        remote.init(eglBase.eglBaseContext, null)
        remote.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        remote.setMirror(false)
        localRenderer = local
        remoteRenderer = remote
        localVideoTrack?.addSink(local)
        remoteVideoTrack?.addSink(remote)
    }

    fun detachVideoRenderers() {
        localRenderer?.let { renderer ->
            localVideoTrack?.removeSink(renderer)
            renderer.release()
        }
        remoteRenderer?.let { renderer ->
            remoteVideoTrack?.removeSink(renderer)
            renderer.release()
        }
        localRenderer = null
        remoteRenderer = null
    }

    fun reportPermissionDenied(message: String = "Allow microphone and camera access to make a video call.") {
        releaseMedia()
        mutableState.value = mutableState.value.copy(
            phase = PeopleCallPhase.FAILED,
            message = message.take(180)
        )
    }

    private fun prepareCall(call: PeopleCallRecord, peerName: String, localIsCaller: Boolean) {
        releaseMedia()
        activeUid = requireCurrentUid()
        activeChatId = call.chatId
        activeCallId = call.id
        activePeerName = peerName.ifBlank { "Dost user" }.take(60)
        activeMediaType = call.mediaType
        activeExpiresAtMillis = call.expiresAtMillis
        this.localIsCaller = localIsCaller
        activeCallAccepted = call.status == "accepted"
        if (!localIsCaller) PeopleCallNotifications.cancelIncoming(appContext, call.id)
    }

    private fun prepareNewCall(
        uid: String,
        chatId: String,
        peerUid: String,
        peerName: String,
        mediaType: String,
        localIsCaller: Boolean
    ) {
        releaseMedia()
        activeUid = uid
        activeChatId = chatId
        activeCallId = ""
        activePeerName = peerName.ifBlank { "Dost user" }.take(60)
        activeMediaType = mediaType
        this.localIsCaller = localIsCaller
        checkMicrophonePermission()
        if (mediaType == "video") checkCameraPermission()
        initializeFactory()
        enterCommunicationAudioMode()
    }

    private fun createPeerConnection(iceServers: List<PeopleIceServer>, withVideo: Boolean): PeerConnection {
        checkMicrophonePermission()
        initializeFactory()
        val configuration = iceConfiguration(iceServers)
        val connection = peerConnectionFactory?.createPeerConnection(configuration, peerObserver())
            ?: throw IOException("Could not start the call connection.")
        peerConnection = connection
        val source = peerConnectionFactory?.createAudioSource(MediaConstraints())
            ?: throw IOException("Could not open the microphone.")
        val track = peerConnectionFactory?.createAudioTrack("people-call-audio", source)
            ?: throw IOException("Could not open the microphone.")
        audioSource = source
        localAudioTrack = track
        track.setEnabled(true)
        if (connection.addTrack(track, listOf("people-call-audio-stream")) == null) {
            throw IOException("Could not add microphone audio to the call.")
        }
        if (withVideo) createLocalVideoTrack(connection)
        enterCommunicationAudioMode()
        return connection
    }

    private fun createLocalVideoTrack(connection: PeerConnection) {
        checkCameraPermission()
        val factory = peerConnectionFactory ?: throw IOException("Could not start the video connection.")
        val enumerator = Camera2Enumerator(appContext)
        val cameraName = enumerator.deviceNames.firstOrNull(enumerator::isFrontFacing)
            ?: enumerator.deviceNames.firstOrNull()
            ?: throw IOException("No camera is available for this call.")
        val capturer = enumerator.createCapturer(cameraName, null)
            ?: throw IOException("Could not open the camera for this call.")
        cameraCapturer = capturer
        val textureHelper = SurfaceTextureHelper.create("PeopleCallCapture", eglBase.eglBaseContext)
            ?: throw IOException("Could not prepare the camera.")
        surfaceTextureHelper = textureHelper
        val source = factory.createVideoSource(capturer.isScreencast)
        videoSource = source
        val track = factory.createVideoTrack("people-call-video", source)
        localVideoTrack = track
        frontCameraSelected = enumerator.isFrontFacing(cameraName)
        capturer.initialize(textureHelper, appContext, source.capturerObserver)
        capturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
        track.setEnabled(true)
        localRenderer?.let(track::addSink)
        if (connection.addTrack(track, listOf("people-call-video-stream")) == null) {
            throw IOException("Could not add camera video to the call.")
        }
    }

    private fun initializeFactory() {
        if (peerConnectionFactory != null) return
        synchronized(PeopleCallEngine::class.java) {
            if (peerConnectionFactory != null) return
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                    .createInitializationOptions()
            )
            val module = JavaAudioDeviceModule.builder(appContext)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()
            audioDeviceModule = module
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(module)
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
                .createPeerConnectionFactory()
        }
    }

    private fun iceConfiguration(servers: List<PeopleIceServer>): PeerConnection.RTCConfiguration {
        val iceServers = servers.flatMap { server ->
            server.urls.map { url ->
                PeerConnection.IceServer.builder(url)
                    .setUsername(server.username)
                    .setPassword(server.credential)
                    .createIceServer()
            }
        }
        return PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
    }

    private fun peerObserver(): PeerConnection.Observer {
        val sessionId = mediaSessionId
        return object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> markConnected()
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    mutableState.value = mutableState.value.copy(
                        phase = PeopleCallPhase.CONNECTING,
                        message = "Reconnecting…"
                    )
                }
                PeerConnection.IceConnectionState.FAILED -> scope.launch {
                    failActiveCall(IOException("The network connection was lost."))
                }
                else -> Unit
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit

        override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate == null || activeCallId.isBlank()) return
            val callId = activeCallId
            val chatId = activeChatId
            val uid = activeUid
            val expiresAt = activeExpiresAtMillis
            val isCaller = localIsCaller
            scope.launch {
                try {
                    repository.addCallCandidate(
                        uid,
                        chatId,
                        callId,
                        isCaller,
                        PeopleCallCandidate("", candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex),
                        expiresAt
                    )
                } catch (error: Exception) {
                    if (callId == activeCallId) failActiveCall(error)
                }
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
        override fun onAddStream(stream: MediaStream?) = Unit
        override fun onRemoveStream(stream: MediaStream?) = Unit
        override fun onDataChannel(channel: org.webrtc.DataChannel?) = Unit
        override fun onRenegotiationNeeded() = Unit

        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
            when (val track = receiver?.track()) {
                is AudioTrack -> track.setEnabled(true)
                is VideoTrack -> attachRemoteVideoTrack(track, sessionId)
            }
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            when (val track = transceiver?.receiver?.track()) {
                is AudioTrack -> track.setEnabled(true)
                is VideoTrack -> attachRemoteVideoTrack(track, sessionId)
            }
        }

        override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) = Unit
        override fun onIceCandidateError(event: IceCandidateErrorEvent?) = Unit
        override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent?) = Unit
        }
    }

    private fun attachRemoteVideoTrack(track: VideoTrack, sessionId: Long) {
        scope.launch {
            if (sessionId != mediaSessionId || activeMediaType != "video") return@launch
            remoteVideoTrack?.takeIf { it !== track }?.let { previous ->
                remoteRenderer?.let(previous::removeSink)
            }
            remoteVideoTrack = track
            track.setEnabled(true)
            remoteRenderer?.let(track::addSink)
        }
    }

    private fun bindCallListener() {
        if (callListener != null || activeCallId.isBlank()) return
        callListener = repository.listenCall(
            activeUid,
            activeChatId,
            activeCallId,
            onChange = { call -> if (call != null) scope.launch { handleCallUpdate(call) } },
            onError = { error -> scope.launch { failActiveCall(error) } }
        )
    }

    private fun bindRemoteCandidates() {
        if (remoteCandidatesListener != null || activeCallId.isBlank()) return
        remoteCandidatesListener = repository.listenCallCandidates(
            activeUid,
            activeChatId,
            activeCallId,
            remoteIsCallee = localIsCaller,
            onChange = { candidates ->
                candidates.filter { seenRemoteCandidateIds.add(it.id) }.forEach { candidate ->
                    val iceCandidate = IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                    if (remoteDescriptionSet) addRemoteCandidate(iceCandidate)
                    else pendingRemoteCandidates += iceCandidate
                }
            },
            onError = { error -> scope.launch { failActiveCall(error) } }
        )
    }

    private suspend fun handleCallUpdate(call: PeopleCallRecord) {
        if (call.id != activeCallId || call.chatId != activeChatId) return
        when (call.status) {
            "accepted" -> if (localIsCaller && !remoteDescriptionSet && !settingRemoteDescription) {
                val answer = call.answer ?: return
                activeCallAccepted = true
                settingRemoteDescription = true
                try {
                    peerConnection?.setRemoteDescriptionAwait(
                        SessionDescription(SessionDescription.Type.ANSWER, answer.sdp)
                    ) ?: return
                    remoteDescriptionSet = true
                    drainRemoteCandidates()
                    mutableState.value = mutableState.value.copy(
                        phase = PeopleCallPhase.CONNECTING,
                        message = "Connecting…"
                    )
                } finally {
                    settingRemoteDescription = false
                }
            }
            "rejected" -> finishCall("Call declined.")
            "cancelled" -> finishCall("Call cancelled.")
            "missed" -> finishCall("No answer.")
            "ended" -> finishCall("Call ended.")
        }
    }

    private fun addRemoteCandidate(candidate: IceCandidate) {
        val connection = peerConnection ?: return
        if (!connection.addIceCandidate(candidate)) {
            scope.launch { failActiveCall(IOException("Could not add network connection data.")) }
        }
    }

    private fun drainRemoteCandidates() {
        if (!remoteDescriptionSet) return
        pendingRemoteCandidates.toList().forEach(::addRemoteCandidate)
        pendingRemoteCandidates.clear()
    }

    private fun scheduleRingTimeout(ringExpiresAtMillis: Long) {
        callTimeoutJob?.cancel()
        callTimeoutJob = scope.launch {
            delay((ringExpiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L))
            val phase = mutableState.value.phase
            if (phase != PeopleCallPhase.RINGING && phase != PeopleCallPhase.INCOMING) return@launch
            val endStatus = if (localIsCaller) "cancelled" else "missed"
            try {
                repository.updateCallStatus(activeUid, activeChatId, activeCallId, endStatus)
                finishCall(if (localIsCaller) "Call cancelled." else "No answer.")
            } catch (error: Exception) {
                failActiveCall(error)
            }
        }
    }

    private fun scheduleCallLimit(expiresAtMillis: Long) {
        callTimeoutJob?.cancel()
        callTimeoutJob = scope.launch {
            delay((expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L))
            if (activeCallId.isNotBlank()) endCall()
        }
    }

    private fun markConnected() {
        if (activeCallId.isBlank()) return
        if (callStartedAtMillis == 0L) {
            callStartedAtMillis = System.currentTimeMillis()
            durationJob?.cancel()
            durationJob = scope.launch {
                while (true) {
                    delay(1_000)
                    mutableState.value = mutableState.value.copy(
                        phase = PeopleCallPhase.CONNECTED,
                        durationSeconds = ((System.currentTimeMillis() - callStartedAtMillis) / 1000).coerceAtLeast(0L),
                        message = "Connected"
                    )
                }
            }
        } else {
            mutableState.value = mutableState.value.copy(
                phase = PeopleCallPhase.CONNECTED,
                message = "Connected"
            )
        }
    }

    private suspend fun failActiveCall(error: Exception) {
        val callId = activeCallId
        if (callId.isNotBlank() && mutableState.value.phase !in setOf(PeopleCallPhase.FINISHED, PeopleCallPhase.FAILED)) {
            try {
                val status = if (activeCallAccepted) "ended" else if (localIsCaller) "cancelled" else "rejected"
                repository.updateCallStatus(activeUid, activeChatId, callId, status)
            } catch (cleanupError: Exception) {
                error.addSuppressed(cleanupError)
                Log.e(TAG, "Could not end a failed People call.", cleanupError)
            }
        }
        releaseMedia()
        showFailure(error)
    }

    private fun finishCall(message: String) {
        releaseMedia()
        mutableState.value = mutableState.value.copy(
            phase = PeopleCallPhase.FINISHED,
            muted = false,
            speakerOn = false,
            message = message
        )
    }

    private fun showFailure(error: Exception) {
        releaseMedia()
        Log.e(TAG, "People call failed.", error)
        mutableState.value = mutableState.value.copy(
            phase = PeopleCallPhase.FAILED,
            message = error.message?.take(180) ?: "The call could not be started."
        )
    }

    private fun releaseMedia() {
        mediaSessionId += 1
        callTimeoutJob?.cancel()
        callTimeoutJob = null
        durationJob?.cancel()
        durationJob = null
        callListener?.remove()
        callListener = null
        remoteCandidatesListener?.remove()
        remoteCandidatesListener = null
        detachVideoRenderers()
        cameraCapturer?.let { capturer ->
            try {
                capturer.stopCapture()
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.w(TAG, "Interrupted while stopping People call camera capture.", error)
            } catch (error: Exception) {
                Log.w(TAG, "Could not stop People call camera capture.", error)
            }
            capturer.dispose()
        }
        cameraCapturer = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        localAudioTrack?.setEnabled(false)
        localAudioTrack?.dispose()
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null
        localVideoTrack?.setEnabled(false)
        localVideoTrack?.dispose()
        localVideoTrack = null
        remoteVideoTrack = null
        videoSource?.dispose()
        videoSource = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        pendingRemoteCandidates.clear()
        seenRemoteCandidateIds.clear()
        activeCallId = ""
        activeMediaType = "audio"
        activeExpiresAtMillis = 0L
        activeCallAccepted = false
        callStartedAtMillis = 0L
        remoteDescriptionSet = false
        settingRemoteDescription = false
        if (audioModeActive) {
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            audioModeActive = false
        }
        appContext.stopService(PeopleAudioCallService.intent(appContext))
    }

    private fun enterCommunicationAudioMode() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = false
        audioModeActive = true
    }

    private fun checkMicrophonePermission() {
        check(ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) { "Allow microphone access to make an audio call." }
    }

    private fun checkCameraPermission() {
        check(ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) { "Allow camera access to make a video call." }
    }

    private fun requireCurrentUid(): String {
        val firebaseApp = FirebaseApp.getApps(appContext).firstOrNull()
            ?: throw IOException("Firebase is not configured for People calls.")
        return FirebaseAuth.getInstance(firebaseApp).currentUser?.uid
            ?: throw IOException("Sign in again to use People calls.")
    }

    private suspend fun PeerConnection.createOfferAwait(): SessionDescription =
        suspendCancellableCoroutine { continuation ->
            createOffer(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) {
                    if (description == null) {
                        continuation.resumeWithException(IOException("Could not create a call offer."))
                    } else if (continuation.isActive) {
                        continuation.resume(description)
                    }
                }

                override fun onSetSuccess() = Unit

                override fun onCreateFailure(error: String?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IOException(error ?: "Could not create a call offer."))
                    }
                }

                override fun onSetFailure(error: String?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IOException(error ?: "Could not set the call offer."))
                    }
                }
            }, MediaConstraints())
        }

    private suspend fun PeerConnection.createAnswerAwait(): SessionDescription =
        suspendCancellableCoroutine { continuation ->
            createAnswer(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) {
                    if (description == null) {
                        continuation.resumeWithException(IOException("Could not create a call answer."))
                    } else if (continuation.isActive) {
                        continuation.resume(description)
                    }
                }

                override fun onSetSuccess() = Unit

                override fun onCreateFailure(error: String?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IOException(error ?: "Could not create a call answer."))
                    }
                }

                override fun onSetFailure(error: String?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IOException(error ?: "Could not set the call answer."))
                    }
                }
            }, MediaConstraints())
        }

    private suspend fun PeerConnection.setLocalDescriptionAwait(description: SessionDescription) {
        setDescriptionAwait { observer -> setLocalDescription(observer, description) }
    }

    private suspend fun PeerConnection.setRemoteDescriptionAwait(description: SessionDescription) {
        setDescriptionAwait { observer -> setRemoteDescription(observer, description) }
    }

    private suspend fun setDescriptionAwait(setDescription: (SdpObserver) -> Unit) {
        suspendCancellableCoroutine { continuation ->
            setDescription(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) = Unit

                override fun onSetSuccess() {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onCreateFailure(error: String?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IOException(error ?: "Could not configure the call."))
                    }
                }

                override fun onSetFailure(error: String?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IOException(error ?: "Could not configure the call."))
                    }
                }
            })
        }
    }

    private companion object {
        const val TAG = "PeopleCall"
        const val RING_TIMEOUT_MILLIS = 55_000L
        const val MAX_CALL_DURATION_MILLIS = 55 * 60 * 1000L
        const val VIDEO_WIDTH = 640
        const val VIDEO_HEIGHT = 480
        const val VIDEO_FPS = 24
    }
}
