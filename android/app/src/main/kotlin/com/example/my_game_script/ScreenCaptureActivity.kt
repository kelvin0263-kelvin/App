package com.example.my_game_script

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log

class ScreenCaptureActivity : Activity() {
    private val TAG = "ScreenCaptureActivity"
    private val SCREEN_CAPTURE_REQUEST_CODE = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "ScreenCaptureActivity created")
        requestScreenCapture()
    }

    private fun requestScreenCapture() {
        Log.d(TAG, "Requesting screen capture permission")
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            SCREEN_CAPTURE_REQUEST_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult called with requestCode: $requestCode, resultCode: $resultCode")
        
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                Log.d(TAG, "Screen capture permission granted, starting service")
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    action = "START_CAPTURE"
                    putExtra("resultCode", resultCode)
                    putExtra("data", data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                    Log.d(TAG, "Started service as foreground service")
                } else {
                    startService(serviceIntent)
                    Log.d(TAG, "Started service as regular service")
                }
            } else {
                Log.e(TAG, "Screen capture permission denied or data is null")
            }
            finish()
        }
    }
} 