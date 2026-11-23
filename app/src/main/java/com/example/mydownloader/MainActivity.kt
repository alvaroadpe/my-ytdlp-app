package com.example.mydownloader

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var btn: Button
    private lateinit var cancelBtn: Button
    private lateinit var updateBtn: Button
    private lateinit var cookiesBtn: Button
    private lateinit var openBtn: Button
    private lateinit var shareBtn: Button
    private lateinit var progressBar: ProgressBar
    private var lastDownloadedUri: Uri? = null
    private var currentProcessId: String? = null
    @Volatile private var engineReady = false

    private val cookieFile: File
        get() = File(filesDir, "cookies.txt")

    private val pickCookiesLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                cookieFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            updateCookiesButtonText()
            status.text = "Cookies saved! Try downloading again."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        input = EditText(this).apply { hint = "Paste link here" }
        btn = Button(this).apply { text = "Download"; isEnabled = false }
        cancelBtn = Button(this).apply { text = "Cancel"; visibility = View.GONE }
        updateBtn = Button(this).apply { text = "Update yt-dlp"; isEnabled = false }
        cookiesBtn = Button(this)
        updateCookiesButtonText()
        openBtn = Button(this).apply { text = "Open File"; visibility = View.GONE }
        shareBtn = Button(this).apply { text = "Share File"; visibility = View.GONE }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
        }

        status = TextView(this).apply { text = "Initializing engine..." }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(input)
            addView(btn)
            addView(cancelBtn)
            addView(progressBar)
            addView(updateBtn)
            addView(cookiesBtn)
            addView(openBtn)
            addView(shareBtn)
            addView(status)
        }
        setContentView(ScrollView(this).apply { addView(layout) })

        btn.setOnClickListener {
            val url = input.text.toString().trim()
            if (url.isNotEmpty()) startDownload(url)
        }

        cancelBtn.setOnClickListener {
            currentProcessId?.let { id ->
                try {
                    YoutubeDL.getInstance().destroyProcessById(id)
                } catch (_: Exception) {}
            }
        }

        updateBtn.setOnClickListener { updateYtDlp() }
        cookiesBtn.setOnClickListener { pickCookiesLauncher.launch(arrayOf("text/*", "*/*")) }
        openBtn.setOnClickListener { openOrShareFile(share = false) }
        shareBtn.setOnClickListener { openOrShareFile(share = true) }

        val sharedUrl = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            Regex("https?://\\S+").find(sharedText)?.value
        } else null

        if (sharedUrl != null) {
            input.setText(sharedUrl)
        }

        thread {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                engineReady = true
                runOnUiThread {
                    btn.isEnabled = true
                    updateBtn.isEnabled = true
                    status.text = "Ready (saves to Download/MyDownloader)"
                    if (sharedUrl != null) startDownload(sharedUrl)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Init error: ${e.message}"
                }
            }
        }
    }

    private fun updateCookiesButtonText() {
        cookiesBtn.text = if (cookieFile.exists()) "Cookies Loaded (Tap to Replace)" else "Load cookies.txt (For Instagram)"
    }

    private fun updateYtDlp() {
        if (!engineReady) return
        updateBtn.isEnabled = false
        btn.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        status.text = "Updating yt-dlp..."
        thread {
            try {
                val updateStatus = YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel.NIGHTLY)
                runOnUiThread {
                    status.text = "yt-dlp update: $updateStatus"
                    updateBtn.isEnabled = true
                    btn.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Update failed: ${e.message}"
                    updateBtn.isEnabled = true
                    btn.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            }
        }
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
        val folderUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FMyDownloader")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(folderUri, "vnd.android.document/directory")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        }
    }

    private fun startDownload(url: String) {
        if (!engineReady) return
        val processId = UUID.randomUUID().toString()
        currentProcessId = processId

        btn.isEnabled = false
        updateBtn.isEnabled = false
        cancelBtn.visibility = View.VISIBLE
        openBtn.visibility = View.GONE
        shareBtn.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        progressBar.progress = 0
        status.text = "Connecting..."

        thread {
            try {
                val tempDir = File(cacheDir, "ytdl").apply { deleteRecursively(); mkdirs() }
                val request = YoutubeDLRequest(url).apply {
                    addOption("-f", "bv*[height<=1080][ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b")
                    addOption("--merge-output-format", "mp4")
                    addOption("--socket-timeout", "15")
                    addOption("--retries", "3")
                    addOption("-o", "${tempDir.absolutePath}/%(title).50s.%(ext)s")
                    addOption("--print", "after_move:filepath")
                    if (cookieFile.exists()) {
                        addOption("--cookies", cookieFile.absolutePath)
                    }
                }

                val response = YoutubeDL.getInstance().execute(request, processId) { progress, _, line ->
                    runOnUiThread {
                        if (progress >= 0f) {
                            progressBar.isIndeterminate = false
                            progressBar.progress = progress.toInt()
                        }
                        if (line.isNotBlank()) {
                            status.text = line.trim()
                        }
                    }
                }

                val path = response.out.trim().lines().lastOrNull()?.trim().orEmpty()
                val tempFile = File(path).takeIf { it.exists() } ?: tempDir.listFiles()?.firstOrNull()

                if (tempFile == null || !tempFile.exists()) {
                    throw Exception("No file produced by yt-dlp")
                }

                val savedUri = saveToMyDownloaderFolder(tempFile)
                tempFile.delete()

                runOnUiThread {
                    currentProcessId = null
                    btn.isEnabled = true
                    updateBtn.isEnabled = true
                    cancelBtn.visibility = View.GONE
                    progressBar.progress = 100
                    progressBar.visibility = View.GONE
                    lastDownloadedUri = savedUri
                    status.text = "Saved to Download/MyDownloader:\n${tempFile.name}"
                    openBtn.visibility = View.VISIBLE
                    shareBtn.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    currentProcessId = null
                    status.text = "Stopped/Error: ${e.message}"
                    btn.isEnabled = true
                    updateBtn.isEnabled = true
                    cancelBtn.visibility = View.GONE
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun saveToMyDownloaderFolder(file: File): Uri {
        val relativeFolder = "${Environment.DIRECTORY_DOWNLOADS}/MyDownloader"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeFolder)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("Failed to create MediaStore entry")
            contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
            return uri
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MyDownloader").apply { mkdirs() }
            val dest = File(dir, file.name)
            file.copyTo(dest, overwrite = true)
            return FileProvider.getUriForFile(this, "${packageName}.provider", dest)
        }
    }

    private fun openOrShareFile(share: Boolean) {
        val uri = lastDownloadedUri ?: return
        val intent = if (share) {
            Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.let { Intent.createChooser(it, "Share video") }
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        startActivity(intent)
    }
}
