package com.example.my_game_script

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

class ScreenCaptureActivity : Activity() {
    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            REQUEST_MEDIA_PROJECTION
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            // Create an intent to restart the service with the result
            val serviceIntent = Intent(this, FloatingWindowService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            startService(serviceIntent)
            finish()
        }
    }
} 