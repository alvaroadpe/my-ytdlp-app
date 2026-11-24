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
        optionsCard.addView(audioOnlySwitch)
        optionsCard.addView(audioSubtext)

        // 3. Primary Download / Cancel Row
        val primaryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(16)
            }
        }
        downloadBtn = Button(this).apply {
            text = "Download"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = cardBg(ink, Color.TRANSPARENT, 14f)
            isEnabled = false
            setPadding(0, dp(14), 0, dp(14))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        cancelBtn = Button(this).apply {
            text = "Cancel"
            textSize = 14f
            isAllCaps = false
            setTextColor(ink)
            background = cardBg(softFill, border, 14f)
            visibility = View.GONE
            setPadding(dp(18), dp(14), dp(18), dp(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(10)
            }
        }
        primaryRow.addView(downloadBtn)
        primaryRow.addView(cancelBtn)

        // 4. Progress Bar + Percentage Card
        progressCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg()
            setPadding(dp(16), dp(14), dp(16), dp(14))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            }
        }
        val barRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressDrawable = LayerDrawable(arrayOf(
                cardBg(softFill, Color.TRANSPARENT, 8f),
                ClipDrawable(cardBg(ink, Color.TRANSPARENT, 8f), Gravity.START, ClipDrawable.HORIZONTAL)
            ))
            layoutParams = LinearLayout.LayoutParams(0, dp(8), 1f)
        }
        progressText = TextView(this).apply {
            text = "0%"
            setTextColor(ink)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), 0, 0, 0)
        }
        barRow.addView(progressBar)
        barRow.addView(progressText)
        progressCard.addView(barRow)

        // 5. Post-Download Open / Share Buttons
        actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            }
        }
        openBtn = Button(this).apply {
            text = "Open File"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            setTextColor(ink)
            background = cardBg(surface, border, 12f)
            setPadding(0, dp(12), 0, dp(12))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        shareBtn = Button(this).apply {
            text = "Share File"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            setTextColor(ink)
            background = cardBg(surface, border, 12f)
            setPadding(0, dp(12), 0, dp(12))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(10)
            }
        }
        actionsRow.addView(openBtn)
        actionsRow.addView(shareBtn)

        // 6. Status Log
        statusText = TextView(this).apply {
            text = "Initializing engine..."
            setTextColor(muted)
            textSize = 12f
            setPadding(dp(4), dp(14), dp(4), 0)
        }

        contentLayout.addView(inputCard)
        contentLayout.addView(optionsCard)
        contentLayout.addView(primaryRow)
        contentLayout.addView(progressCard)
        contentLayout.addView(actionsRow)
        contentLayout.addView(statusText)

        mainContainer.addView(toolbar)
        mainContainer.addView(divider)
        mainContainer.addView(ScrollView(this).apply { addView(contentLayout) })

        // Left Deployable Menu (Drawer)
        val drawerMenu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
            setPadding(dp(20), dp(48), dp(20), dp(24))
            isClickable = true
            layoutParams = DrawerLayout.LayoutParams(dp(275), DrawerLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = GravityCompat.START
            }
        }
        val drawerHeader = TextView(this).apply {
            text = "Settings & Engine"
            setTextColor(ink)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        val drawerSub = TextView(this).apply {
            text = "Manage yt-dlp & authentication"
            setTextColor(muted)
            textSize = 12f
            setPadding(0, dp(4), 0, dp(20))
        }
        drawerUpdateBtn = Button(this).apply {
            text = "Update yt-dlp"
            textSize = 14f
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(ink)
            background = cardBg(softFill, Color.TRANSPARENT, 12f)
            isEnabled = false
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        drawerCookiesBtn = Button(this).apply {
            textSize = 14f
            isAllCaps = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(ink)
            background = cardBg(softFill, Color.TRANSPARENT, 12f)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            }
        }
        updateCookiesLabel()

        drawerMenu.addView(drawerHeader)
        drawerMenu.addView(drawerSub)
        drawerMenu.addView(drawerUpdateBtn)
        drawerMenu.addView(drawerCookiesBtn)

        drawerLayout.addView(mainContainer)
        drawerLayout.addView(drawerMenu)
        setContentView(drawerLayout)

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                resetDownloadedButtonLabel()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        clearBtn.setOnClickListener {
            input.setText("")
            actionsRow.visibility = View.GONE
            progressCard.visibility = View.GONE
            if (!isDownloading) {
                downloadBtn.isEnabled = engineReady
                downloadBtn.text = "Download"
            }
            statusText.text = "Ready"
            input.requestFocus()
        }

        downloadBtn.setOnClickListener {
            val url = input.text.toString().trim()
            if (url.isNotEmpty() && !isDownloading) startDownload(url)
        }

        cancelBtn.setOnClickListener {
            currentProcessId?.let { id ->
                try { YoutubeDL.getInstance().destroyProcessById(id) } catch (_: Exception) {}
            }
        }

        drawerUpdateBtn.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            updateYtDlp()
        }

        drawerCookiesBtn.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            pickCookiesLauncher.launch(arrayOf("text/*", "*/*"))
        }

        openBtn.setOnClickListener { openOrShareFile(share = false) }
        shareBtn.setOnClickListener { openOrShareFile(share = true) }

        if (intent?.getBooleanExtra("open_folder", false) == true) {
            openMyDownloaderFolder()
        }

        val sharedUrl = extractUrlFromIntent(intent)
        if (sharedUrl != null) {
            input.setText(sharedUrl)
        }

        thread {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                engineReady = true
                runOnUiThread {
                    drawerUpdateBtn.isEnabled = true
                    downloadBtn.isEnabled = true
                    downloadBtn.text = "Download"
                    statusText.text = if (sharedUrl != null) {
                        "Link pasted • Choose options and tap Download"
                    } else {
                        "Ready • Saves to Download/MyDownloader"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    downloadBtn.text = "Download"
                    statusText.text = "Init error: ${e.message}"
                }
            }
        }
    }

    private fun resetDownloadedButtonLabel() {
        if (!isDownloading && engineReady && downloadBtn.text == "Downloaded") {
            downloadBtn.text = "Download"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun updateCookiesLabel() {
        drawerCookiesBtn.text = if (cookieFile.exists()) "Cookies Loaded ✓ (Replace)" else "Load cookies.txt"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_folder", false)) {
            openMyDownloaderFolder()
            return
        }
        val sharedUrl = extractUrlFromIntent(intent)
        if (sharedUrl != null && !isDownloading) {
            input.setText(sharedUrl)
            actionsRow.visibility = View.GONE
            progressCard.visibility = View.GONE
            downloadBtn.isEnabled = engineReady
            downloadBtn.text = "Download"
            statusText.text = "Link pasted • Choose options and tap Download"
        }
    }

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            return Regex("https?://\\S+").find(sharedText)?.value
        }
        return null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Open Folder").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            openMyDownloaderFolder()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openMyDownloaderFolder() {
        startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
    }

    private fun updateYtDlp() {
        if (!engineReady || isDownloading) return
        drawerUpdateBtn.isEnabled = false
        downloadBtn.isEnabled = false
        progressCard.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        progressText.text = "..."
        statusText.text = "Updating yt-dlp engine..."
        thread {
            try {
                val res = YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel.NIGHTLY)
                runOnUiThread {
                    statusText.text = "yt-dlp update: $res"
                    drawerUpdateBtn.isEnabled = true
                    downloadBtn.isEnabled = true
                    progressCard.visibility = View.GONE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Update failed: ${e.message}"
                    drawerUpdateBtn.isEnabled = true
                    downloadBtn.isEnabled = true
                    progressCard.visibility = View.GONE
                }
            }
        }
    }

    private fun startDownload(url: String) {
        if (!engineReady || isDownloading) return
        isDownloading = true
        val processId = UUID.randomUUID().toString()
        currentProcessId = processId
        val maxQuality = qualitySwitch.isChecked
        val audioOnly = audioOnlySwitch.isChecked

        downloadBtn.isEnabled = false
        downloadBtn.text = "Downloading"
        qualitySwitch.isEnabled = false
        audioOnlySwitch.isEnabled = false
        drawerUpdateBtn.isEnabled = false
        cancelBtn.visibility = View.VISIBLE
        actionsRow.visibility = View.GONE
        progressCard.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        progressBar.progress = 0
        progressText.text = "0%"
        statusText.text = "Connecting..."
        showProgressNotification(-1, "Connecting...")

        thread {
            try {
                val tempDir = File(cacheDir, "ytdl").apply { deleteRecursively(); mkdirs() }
                val request = YoutubeDLRequest(url).apply {
                    if (audioOnly) {
                        addOption("-x")
                        addOption("--audio-format", "mp3")
                        addOption("--audio-quality", "0")
                    } else if (maxQuality) {
                        addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b")
                        addOption("--merge-output-format", "mp4")
                    } else {
                        addOption("-f", "b[ext=mp4]/b")
                    }
                    addOption("--socket-timeout", "15")
                    addOption("--retries", "3")
                    addOption("-o", "${tempDir.absolutePath}/%(title).50s.%(ext)s")
                    addOption("--print", "after_move:filepath")
                    if (cookieFile.exists()) addOption("--cookies", cookieFile.absolutePath)
                }

                var lastNotif = 0L
                val response = YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
                    val pct = progress.toInt()
                    val now = System.currentTimeMillis()
                    if (now - lastNotif > 700) {
                        lastNotif = now
                        showProgressNotification(pct, line.trim().ifEmpty { "Downloading..." })
                    }
                    runOnUiThread {
                        if (pct >= 0) {
                            progressBar.isIndeterminate = false
                            progressBar.progress = pct
                            progressText.text = "$pct%"
                        }
                        if (line.isNotBlank()) statusText.text = line.trim()
                    }
                }

                val path = response.out.trim().lines().lastOrNull()?.trim().orEmpty()
                val tempFile = File(path).takeIf { it.exists() } ?: tempDir.listFiles()?.firstOrNull()
                if (tempFile == null || !tempFile.exists()) throw Exception("No file produced by yt-dlp")

                val mimeType = if (tempFile.extension.equals("mp3", ignoreCase = true)) "audio/mpeg" else "video/mp4"
                val savedUri = saveToMyDownloaderFolder(tempFile, mimeType)
                val savedName = tempFile.name
                tempFile.delete()

                showCompletedNotification(savedName, savedUri, mimeType)

                runOnUiThread {
                    isDownloading = false
                    currentProcessId = null
                    downloadBtn.isEnabled = true
                    downloadBtn.text = "Downloaded"
                    audioOnlySwitch.isEnabled = true
                    qualitySwitch.isEnabled = !audioOnlySwitch.isChecked
                    drawerUpdateBtn.isEnabled = true
                    cancelBtn.visibility = View.GONE
                    progressBar.isIndeterminate = false
                    progressBar.progress = 100
                    progressText.text = "100%"
                    lastDownloadedUri = savedUri
                    lastDownloadedMime = mimeType
                    statusText.text = "Saved: $savedName"
                    actionsRow.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                showErrorNotification(e.message ?: "Download error")
                runOnUiThread {
                    isDownloading = false
                    currentProcessId = null
                    statusText.text = "Error: ${e.message}"
                    downloadBtn.isEnabled = true
                    downloadBtn.text = "Download"
                    audioOnlySwitch.isEnabled = true
                    qualitySwitch.isEnabled = !audioOnlySwitch.isChecked
                    drawerUpdateBtn.isEnabled = true
                    cancelBtn.visibility = View.GONE
                    progressCard.visibility = View.GONE
                }
            }
        }
    }

    private fun saveToMyDownloaderFolder(file: File, mimeType: String): Uri {
        val relativeFolder = "${Environment.DIRECTORY_DOWNLOADS}/MyDownloader"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeFolder)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("Failed to create MediaStore entry")
            contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            uri
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyDownloader").apply { mkdirs() }
            val dest = File(dir, file.name)
            file.copyTo(dest, overwrite = true)
            FileProvider.getUriForFile(this, "${packageName}.provider", dest)
        }
    }

    private fun openOrShareFile(share: Boolean) {
        val uri = lastDownloadedUri ?: return
        val intent = if (share) {
            Intent(Intent.ACTION_SEND).apply {
                type = lastDownloadedMime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.let { Intent.createChooser(it, "Share file") }
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, lastDownloadedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(progress: Int, detail: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val indeterminate = progress < 0
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (indeterminate) "Downloading..." else "Downloading ($progress%)")
            .setContentText(detail)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, if (indeterminate) 0 else progress, indeterminate)
            .build()
        nm.notify(notificationId, notif)
    }

    private fun showCompletedNotification(fileName: String, fileUri: Uri, mimeType: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val openFilePending = PendingIntent.getActivity(this, 1, Intent.createChooser(viewIntent, "Open file"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val openFolderPending = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java).apply { putExtra("open_folder", true) }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(fileName)
            .setContentIntent(openFilePending)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Open File", openFilePending)
            .addAction(android.R.drawable.ic_menu_agenda, "Open Folder", openFolderPending)
            .build()
        nm.notify(notificationId, notif)
    }

    private fun showErrorNotification(msg: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText(msg)
            .setAutoCancel(true)
            .build()
        nm.notify(notificationId, notif)
    }
}
