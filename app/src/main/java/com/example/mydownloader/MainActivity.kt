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
import android.widget.TextView
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
    private lateinit var openBtn: Button
    private lateinit var shareBtn: Button
    private var lastDownloadedUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        YoutubeDL.getInstance().init(this)
        FFmpeg.getInstance().init(this)

        input = EditText(this).apply { hint = "Paste link here" }
        status = TextView(this).apply { text = "Ready (saves to Download/MyDownloader)" }
        btn = Button(this).apply { text = "Download" }
        openBtn = Button(this).apply { text = "Open File"; visibility = View.GONE }
        shareBtn = Button(this).apply { text = "Share File"; visibility = View.GONE }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            addView(input)
            addView(btn)
            addView(status)
            addView(openBtn)
            addView(shareBtn)
        })

        btn.setOnClickListener {
            val url = input.text.toString().trim()
            if (url.isNotEmpty()) startDownload(url)
        }

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Open Folder")
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
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
        status.text = "Downloading..."

        thread {
            try {
                val tempDir = File(cacheDir, "ytdl").apply { deleteRecursively(); mkdirs() }
                val request = YoutubeDLRequest(url).apply {
                    addOption("-f", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b") 
                    addOption("--merge-output-format", "mp4")
                    addOption("-o", "${tempDir.absolutePath}/%(title).50s.%(ext)s")
                    addOption("--print", "after_move:filepath")
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
                    lastDownloadedUri = savedUri
                    status.text = "Saved to Download/MyDownloader:\n${tempFile.name}"
                    openBtn.visibility = View.VISIBLE
                    shareBtn.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Error: ${e.message}"
                    btn.isEnabled = true
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
