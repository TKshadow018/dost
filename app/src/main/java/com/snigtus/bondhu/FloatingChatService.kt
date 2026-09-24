package com.snigtus.dost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect

class FloatingChatService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var store: DostStore
    private lateinit var friend: Friend
    private var bubbleView: View? = null
    private var unreadBadge: TextView? = null
    private var panelView: LinearLayout? = null
    private var panelMessages: LinearLayout? = null
    private var panelScroll: ScrollView? = null
    private var input: EditText? = null
    private var closeTarget: TextView? = null
    private var closeTargetParams: WindowManager.LayoutParams? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var expanded = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        store = DostStore(this)
        ConversationManager.initialize(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::friend.isInitialized) {
            val friendId = intent?.getStringExtra(EXTRA_FRIEND_ID)
            friend = store.loadFriends().firstOrNull { it.id == friendId }
                ?: store.loadFriends().firstOrNull() ?: run {
                    Toast.makeText(this, "Add a friend in Dost first", Toast.LENGTH_SHORT).show()
                    stopSelf()
                    return START_NOT_STICKY
                }
            initializeOverlay()
        }
        return START_NOT_STICKY
    }

    private fun initializeOverlay() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        serviceScope.launch {
            ConversationManager.messages.collect { messageMap ->
                messageMap[friend.id]?.let { renderMessages(it) }
            }
        }
        serviceScope.launch {
            ConversationManager.unread.collect { counts ->
                updateUnreadBadge(counts[friend.id] ?: 0)
            }
        }
        showBubble()
    }

    private fun showBubble() {
        val size = dp(60)
        val avatar = ImageView(this).apply {
            contentDescription = "Chat with ${friend.name}"
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(37, 99, 235))
            }
            if (friend.photoUri.isNotBlank()) {
                runCatching {
                    contentResolver.openInputStream(android.net.Uri.parse(friend.photoUri))?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()?.let { setImageBitmap(it) }
            } else {
                setImageBitmap(fallbackAvatar(friend.name, size))
            }
        }
        val container = FrameLayout(this).apply {
            addView(avatar, FrameLayout.LayoutParams(size, size))
            unreadBadge = TextView(this@FloatingChatService).apply {
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(220, 38, 38))
                }
                visibility = View.GONE
            }
            addView(unreadBadge, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.END))
            setOnTouchListener(DragListener())
        }
        bubbleView = container
        bubbleParams = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(180)
        }
        windowManager.addView(bubbleView, bubbleParams)
        updateUnreadBadge(ConversationManager.unread.value[friend.id] ?: 0)
    }

    private fun updateUnreadBadge(count: Int) {
        unreadBadge?.apply {
            text = count.coerceAtMost(99).toString()
            visibility = if (count > 0 && !expanded) View.VISIBLE else View.GONE
        }
    }

    private fun fallbackAvatar(name: String, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(37, 99, 235))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = size * 0.38f
                textAlign = Paint.Align.CENTER
            }
            drawText(name.take(1).uppercase(), size / 2f, size * 0.64f, paint)
        }
        return bitmap
    }

    private fun showCloseTarget() {
        if (closeTarget != null) return
        closeTarget = TextView(this).apply {
            text = "Drag here to close"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(220, 38, 38), dp(24))
        }
        closeTargetParams = WindowManager.LayoutParams(
            dp(190), dp(52), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - dp(190)) / 2
            y = resources.displayMetrics.heightPixels - dp(100)
        }
        windowManager.addView(closeTarget, closeTargetParams)
    }

    private fun hideCloseTarget() {
        closeTarget?.let { windowManager.removeView(it) }
        closeTarget = null
        closeTargetParams = null
    }

    private fun closeFromDrop() {
        hideCloseTarget()
        ConversationManager.closeChat(friend.id)
        bubbleView?.let { windowManager.removeView(it) }
        bubbleView = null
        unreadBadge = null
        stopSelf()
    }

    private fun showPanel() {
        if (panelView != null) return
        ConversationManager.openChat(friend.id)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            background = rounded(Color.WHITE, dp(18))
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "Dost · ${friend.name}"
            textSize = 18f
            setTextColor(Color.rgb(17, 24, 39))
        }
        val close = Button(this).apply {
            text = "×"
            setOnClickListener { collapsePanel() }
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(close, LinearLayout.LayoutParams(dp(52), dp(48)))
        root.addView(header)

        val status = TextView(this).apply {
            text = "${friendshipLevel(friend.friendshipScore)} · ${friend.friendshipScore}/100"
            textSize = 12f
            setTextColor(Color.DKGRAY)
        }
        root.addView(status)

        panelScroll = ScrollView(this)
        panelMessages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        panelScroll!!.addView(panelMessages, LinearLayout.LayoutParams(-1, -2))
        root.addView(panelScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val composer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply {
            hint = "Message"
            maxLines = 3
            setTextColor(Color.DKGRAY)
        }
        val send = Button(this).apply {
            text = "Send"
            setOnClickListener { sendMessage() }
        }
        composer.addView(input, LinearLayout.LayoutParams(0, dp(56), 1f))
        composer.addView(send, LinearLayout.LayoutParams(dp(82), dp(56)))
        root.addView(composer)

        panelView = root
        panelParams = WindowManager.LayoutParams(
            dp(340), dp(500), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(120)
        }
        windowManager.addView(root, panelParams)
        bubbleView?.visibility = View.GONE
        loadMessages()
    }

    private fun loadMessages() {
        renderMessages(store.loadMessages(friend.id))
        panelScroll?.post { panelScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun renderMessages(messages: List<ChatMessage>) {
        panelMessages?.let { container ->
            container.removeAllViews()
            messages.takeLast(30).forEach { addMessageView(it) }
            panelScroll?.post { panelScroll?.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun addMessageView(message: ChatMessage) {
        val text = TextView(this).apply {
            text = message.content
            textSize = 15f
            setTextColor(if (message.role == "user") Color.WHITE else Color.rgb(31, 41, 55))
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(if (message.role == "user") Color.rgb(37, 99, 235) else Color.rgb(229, 231, 235), dp(16))
        }
        val row = LinearLayout(this).apply {
            gravity = if (message.role == "user") Gravity.END else Gravity.START
            setPadding(0, dp(4), 0, dp(4))
        }
        row.addView(text, LinearLayout.LayoutParams(dp(265), -2))
        panelMessages?.addView(row)
    }

    private fun sendMessage() {
        val text = input?.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        input?.setText("")
        ConversationManager.send(friend, text, store.language())
    }

    private fun collapsePanel() {
        panelView?.let { windowManager.removeView(it) }
        panelView = null
        panelMessages = null
        panelScroll = null
        input = null
        ConversationManager.closeChat(friend.id)
        bubbleView?.visibility = View.VISIBLE
        expanded = false
        updateUnreadBadge(ConversationManager.unread.value[friend.id] ?: 0)
    }

    private fun togglePanel() {
        if (expanded) collapsePanel() else {
            expanded = true
            showPanel()
        }
    }

    private inner class DragListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = bubbleParams ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; moved = false
                    showCloseTarget()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt(); val dy = (event.rawY - downY).toInt()
                    moved = moved || kotlin.math.abs(dx) > dp(5) || kotlin.math.abs(dy) > dp(5)
                    params.x = startX + dx; params.y = startY + dy
                    windowManager.updateViewLayout(view, params)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val target = closeTarget
                    val targetLocation = IntArray(2)
                    target?.getLocationOnScreen(targetLocation)
                    val targetWidth = target?.width ?: 0
                    val targetHeight = target?.height ?: 0
                    val droppedOnClose = target != null &&
                        event.rawX >= targetLocation[0] - dp(24) && event.rawX <= targetLocation[0] + targetWidth + dp(24) &&
                        event.rawY >= targetLocation[1] - dp(24) && event.rawY <= targetLocation[1] + targetHeight + dp(24)
                    hideCloseTarget()
                    if (droppedOnClose) closeFromDrop()
                    else if (!moved) togglePanel()
                    return true
                }
            }
            return false
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID).setContentTitle("Dost floating chat").setContentText("Chat with ${friend.name}").setSmallIcon(R.mipmap.ic_launcher).setContentIntent(pending).setOngoing(true).build()
        } else {
            Notification.Builder(this).setContentTitle("Dost floating chat").setContentText("Chat with ${friend.name}").setSmallIcon(R.mipmap.ic_launcher).setContentIntent(pending).setOngoing(true).build()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Dost floating chat", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun overlayType() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }

    override fun onDestroy() {
        if (::friend.isInitialized) ConversationManager.closeChat(friend.id)
        hideCloseTarget()
        panelView?.let { windowManager.removeView(it) }
        bubbleView?.let { windowManager.removeView(it) }
        unreadBadge = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_FRIEND_ID = "friend_id"
        private const val CHANNEL_ID = "dost_floating_chat"
        private const val NOTIFICATION_ID = 92
    }
}
