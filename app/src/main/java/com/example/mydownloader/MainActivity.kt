package com.example.mydownloader

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        YoutubeDL.getInstance().init(this)
        FFmpeg.getInstance().init(this)

        val input = EditText(this).apply { hint = "Paste link here" }
        val status = TextView(this).apply { text = "Ready" }
        val btn = Button(this).apply { text = "Download" }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            addView(input)
            addView(btn)
            addView(status)
        })

        btn.setOnClickListener {
            val url = input.text.toString().trim()
            if (url.isEmpty()) return@setOnClickListener
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
}
