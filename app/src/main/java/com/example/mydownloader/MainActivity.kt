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
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var btn: Button
    private lateinit var updateBtn: Button
    private lateinit var cookiesBtn: Button
    private lateinit var openBtn: Button
    private lateinit var shareBtn: Button
    private lateinit var progressBar: ProgressBar
    private var lastDownloadedUri: Uri? = null

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
        YoutubeDL.getInstance().init(this)
        FFmpeg.getInstance().init(this)

        input = EditText(this).apply { hint = "Paste link here" }
        btn = Button(this).apply { text = "Download" }
        updateBtn = Button(this).apply { text = "Update yt-dlp" }
        cookiesBtn = Button(this)
        updateCookiesButtonText()
        openBtn = Button(this).apply { text = "Open File"; visibility = View.GONE }
        shareBtn = Button(this).apply { text = "Share File"; visibility = View.GONE }
        
        // Circular indeterminate progress loading spinner
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleLarge).apply {
            visibility = View.GONE
        }
        
        status = TextView(this).apply { text = "Ready (saves to Download/MyDownloader)" }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(input)
            addView(btn)
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

        updateBtn.setOnClickListener { updateYtDlp() }
        cookiesBtn.setOnClickListener { pickCookiesLauncher.launch(arrayOf("text/*", "*/*")) }
        openBtn.setOnClickListener { openOrShareFile(share = false) }
        shareBtn.setOnClickListener { openOrShareFile(share = true) }

        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val extractedUrl = Regex("https?://\\S+").find(sharedText)?.value
            if (extractedUrl != null) {
                input.setText(extractedUrl)
                startDownload(extractedUrl)
            }
        }
    }

    private fun updateCookiesButtonText() {
        cookiesBtn.text = if (cookieFile.exists()) "Cookies Loaded (Tap to Replace)" else "Load cookies.txt (For Instagram)"
    }

    private fun updateYtDlp() {
        updateBtn.isEnabled = false
        progressBar.visibility = View.VISIBLE
        status.text = "Updating yt-dlp..."
        thread {
            try {
                val updateStatus = YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel.NIGHTLY)
                runOnUiThread {
                    status.text = "yt-dlp update status: $updateStatus"
                    updateBtn.isEnabled = true
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Update failed: ${e.message}"
                    updateBtn.isEnabled = true
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
        btn.isEnabled = false
        openBtn.visibility = View.GONE
        shareBtn.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        status.text = "Downloading..."

        thread {
            try {
                val tempDir = File(cacheDir, "ytdl").apply { deleteRecursively(); mkdirs() }
                val request = YoutubeDLRequest(url).apply {
                    addOption("-f", "bv*[height<=1080][ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b")
                    addOption("--merge-output-format", "mp4")
                    addOption("-o", "${tempDir.absolutePath}/%(title).50s.%(ext)s")
                    addOption("--print", "after_move:filepath")
                    if (cookieFile.exists()) {
                        addOption("--cookies", cookieFile.absolutePath)
                    }
                }
                val response = YoutubeDL.getInstance().execute(request)
                val path = response.out.trim().lines().lastOrNull()?.trim().orEmpty()
                val tempFile = File(path).takeIf { it.exists() } ?: tempDir.listFiles()?.firstOrNull()

                if (tempFile == null || !tempFile.exists()) {
                    throw Exception("No file produced by yt-dlp")
                }

                val savedUri = saveToMyDownloaderFolder(tempFile)
                tempFile.delete()

                runOnUiThread {
                    btn.isEnabled = true
                    progressBar.visibility = View.GONE
                    lastDownloadedUri = savedUri
                    status.text = "Saved to Download/MyDownloader:\n${tempFile.name}"
                    openBtn.visibility = View.VISIBLE
                    shareBtn.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Error: ${e.message}"
                    btn.isEnabled = true
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
