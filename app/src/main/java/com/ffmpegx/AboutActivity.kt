package com.ffmpegx

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ffmpegx.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        binding.webView.loadUrl("file:///android_asset/about.html")
        binding.webView.setOnLongClickListener { true }
    }
}
