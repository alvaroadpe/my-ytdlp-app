package com.example.mydownloader

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var input: EditText
    private lateinit var clearBtn: Button
    private lateinit var qualitySwitch: SwitchCompat
    private lateinit var qualitySubtext: TextView
    private lateinit var audioOnlySwitch: SwitchCompat
    private lateinit var audioSubtext: TextView
    private lateinit var downloadBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var progressCard: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var statusText: TextView
    private lateinit var actionsRow: LinearLayout
    private lateinit var openBtn: Button
    private lateinit var shareBtn: Button
    private lateinit var drawerUpdateBtn: Button
    private lateinit var drawerCookiesBtn: Button

    private var lastDownloadedUri: Uri? = null
    private var lastDownloadedMime: String = "video/mp4"
    private var currentProcessId: String? = null
    @Volatile private var engineReady = false
    @Volatile private var isDownloading = false

    private val channelId = "mydownloader_progress"
    private val notificationId = 1001
    private val cookieFile: File get() = File(filesDir, "cookies.txt")

    private val requestNotifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val pickCookiesLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            contentResolver.openInputStream(uri)?.use { inp ->
                cookieFile.outputStream().use { out -> inp.copyTo(out) }
            }
            updateCookiesLabel()
            statusText.text = "Cookies loaded. Ready to download."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val bgMain = Color.parseColor("#FAFAFA")
        val surface = Color.WHITE
        val border = Color.parseColor("#E5E7EB")
        val ink = Color.parseColor("#111827")
        val muted = Color.parseColor("#6B7280")
        val softFill = Color.parseColor("#F3F4F6")

        fun cardBg(fill: Int = surface, stroke: Int = border, radius: Float = 16f) = GradientDrawable().apply {
            setColor(fill)
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
            cornerRadius = dp(radius).toFloat()
        }

        drawerLayout = DrawerLayout(this).apply { setBackgroundColor(bgMain) }

        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = DrawerLayout.LayoutParams(DrawerLayout.LayoutParams.MATCH_PARENT, DrawerLayout.LayoutParams.MATCH_PARENT)
        }

        val toolbar = Toolbar(this).apply {
            setBackgroundColor(surface)
            title = "MyDownloader"
            setTitleTextColor(ink)
            elevation = 0f
        }
        setSupportActionBar(toolbar)
        val toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, android.R.string.ok, android.R.string.cancel)
        toggle.drawerArrowDrawable.color = ink
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        val divider = View(this).apply {
            setBackgroundColor(border)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }

        // 1. Link Input + Clear Card
        val inputCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBg()
            setPadding(dp(14), dp(6), dp(8), dp(6))
        }
        input = EditText(this).apply {
            hint = "Paste video link..."
            setHintTextColor(muted)
            setTextColor(ink)
            textSize = 15f
            background = null
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        clearBtn = Button(this).apply {
            text = "Clear"
            textSize = 12f
            isAllCaps = false
            setTextColor(muted)
            background = cardBg(softFill, Color.TRANSPARENT, 10f)
            minHeight = 0; minimumHeight = 0
            minWidth = 0; minimumWidth = 0
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        inputCard.addView(input)
        inputCard.addView(clearBtn)

        // 2. Format & Quality Options Card
        val optionsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
        }
        qualitySwitch = SwitchCompat(this).apply {
            text = "Fast download"
            setTextColor(ink)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            isChecked = false
        }
        qualitySubtext = TextView(this).apply {
            text = "Single-stream MP4 for speed"
            setTextColor(muted)
            textSize = 12f
            setPadding(0, dp(2), 0, dp(12))
        }

        val innerDivider = View(this).apply {
            setBackgroundColor(border)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        }

        audioOnlySwitch = SwitchCompat(this).apply {
            text = "Audio only (MP3)"
            setTextColor(ink)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            isChecked = false
            setPadding(0, dp(12), 0, 0)
        }
        audioSubtext = TextView(this).apply {
            text = "Download full video with audio"
            setTextColor(muted)
            textSize = 12f
            setPadding(0, dp(2), 0, 0)
        }

        qualitySwitch.setOnCheckedChangeListener { _, isChecked ->
            qualitySwitch.text = if (isChecked) "Max quality" else "Fast download"
            qualitySubtext.text = if (isChecked) {
                "Best video + audio merged via FFmpeg"
            } else {
                "Single-stream MP4 for speed"
            }
            resetDownloadedButtonLabel()
        }

        audioOnlySwitch.setOnCheckedChangeListener { _, isChecked ->
            qualitySwitch.isEnabled = !isChecked && !isDownloading
            audioSubtext.text = if (isChecked) {
                "Extract audio track only as .mp3"
            } else {
                "Download full video with audio"
            }
            resetDownloadedButtonLabel()
        }

        optionsCard.addView(qualitySwitch)
        optionsCard.addView(qualitySubtext)
        optionsCard.addView(innerDivider)
        optionsCard.
