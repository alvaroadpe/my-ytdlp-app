package com.example.mydownloader

import android.content.Intent
import android.os.Bundle
import android.os.Environment
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
    private var lastDownloadedFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        YoutubeDL.getInstance().init(this)
        FFmpeg.getInstance().init(this)

        input = EditText(this).apply { hint = "Paste link here" }
        status = TextView(this).apply { text = "Ready" }
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

    private fun startDownload(url: String) {
        btn.isEnabled = false
        openBtn.visibility = View.GONE
        shareBtn.visibility = View.GONE
        status.text = "Downloading..."

        thread {
            try {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val request = YoutubeDLRequest(url).apply {
                    addOption("-f", "b[ext=mp4]/b")
                    addOption("-o", "${dir.absolutePath}/%(title).50s.%(ext)s")
                    addOption("--print", "after_move:filepath")
                }
                val response = YoutubeDL.getInstance().execute(request)
                val path = response.out.trim().lines().lastOrNull()?.trim().orEmpty()
                val file = File(path)

                runOnUiThread {
                    btn.isEnabled = true
                    if (file.exists()) {
                        lastDownloadedFile = file
                        status.text = "Saved: ${file.name}"
                        openBtn.visibility = View.VISIBLE
                        shareBtn.visibility = View.VISIBLE
                    } else {
                        status.text = "Saved to Downloads!"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Error: ${e.message}"
                    btn.isEnabled = true
                }
            }
        }
    }

    private fun openOrShareFile(share: Boolean) {
        val file = lastDownloadedFile ?: return
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val mime = contentResolver.getType(uri) ?: "video/*"

        val intent = if (share) {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.let { Intent.createChooser(it, "Share video") }
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        startActivity(intent)
    }
}
