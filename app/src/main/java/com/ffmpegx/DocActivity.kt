package com.ffmpegx

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ffmpegx.databinding.ActivityAboutBinding

class DocActivity : AppCompatActivity() {

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
        binding.toolbar.title = getString(R.string.menu_info)
        
        binding.webView.loadUrl("file:///android_asset/doc.html")
        binding.webView.setOnLongClickListener { true }
    }
}
