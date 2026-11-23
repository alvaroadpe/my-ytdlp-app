package com.example.mydownloader

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var btn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        YoutubeDL.getInstance().init(this)
        FFmpeg.getInstance().init(this)

        input = EditText(this).apply { hint = "Paste link here" }
        status = TextView(this).apply { text = "Ready" }
        btn = Button(this).apply { text = "Download" }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            addView(input)
            addView(btn)
            addView(status)
        })

        btn.setOnClickListener {
            val url = input.text.toString().trim()
            if (url.isNotEmpty()) startDownload(url)
        }

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
        status.text = "Downloading..."

        thread {
            try {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val request = YoutubeDLRequest(url).apply {
                    addOption("-f", "b[ext=mp4]/b")
                    addOption("-o", "${dir.absolutePath}/%(title).50s.%(ext)s")
                }
                YoutubeDL.getInstance().execute(request)
                runOnUiThread {
                    status.text = "Saved to Downloads!"
                    btn.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Error: ${e.message}"
                    btn.isEnabled = true
                }
            }
        }
    }
}
