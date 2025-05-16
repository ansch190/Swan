package com.schwanitz.swan.ui.activity

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.schwanitz.swan.databinding.ActivityFullScreenImageBinding
import com.schwanitz.swan.domain.usecase.MetadataExtractor
import com.schwanitz.swan.ui.adapter.FullScreenImageAdapter

class FullScreenImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenImageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullScreenImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uri = intent.extras?.getParcelable<Uri>("uri", Uri::class.java)
        val artworkCount = intent.getIntExtra("artworkCount", 0)

        if (uri != null && artworkCount > 0) {
            val metadataExtractor = MetadataExtractor(this)
            val adapter = FullScreenImageAdapter(this, metadataExtractor)
            Log.d("FullScreenImageActivity", "Setting adapter data: uri=$uri, artworkCount=$artworkCount")
            binding.viewPager.adapter = adapter
            adapter.setData(uri, artworkCount)
        } else {
            Log.e("FullScreenImageActivity", "Invalid data: uri=$uri, artworkCount=$artworkCount")
            finish()
        }
    }
}