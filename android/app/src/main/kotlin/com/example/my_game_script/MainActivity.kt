package com.example.my_game_script

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val TAG = "MainActivity"
    private val CHANNEL = "com.example.my_game_script/overlay"
    private val OVERLAY_PERMISSION_REQ_CODE = 1234
    private lateinit var methodChannel: MethodChannel
    private var screenshotReceiver: BroadcastReceiver? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        setupScreenshotReceiver()
        methodChannel.setMethodCallHandler { call, result ->
            Log.d(TAG, "Received method call: ${call.method}")
            when (call.method) {
                "checkOverlayPermission" -> {
                    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Settings.canDrawOverlays(this)
                    } else {
                        true
                    }
                    Log.d(TAG, "Overlay permission check result: $hasPermission")
                    result.success(hasPermission)
                }
                "requestOverlayPermission" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
                        result.success(null)
                    } else {
                        result.success(true)
                    }
                }
                "startFloatingWindow" -> {
                    try {
                        val intent = Intent(this, FloatingWindowService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        Log.d(TAG, "Started floating window service")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting service", e)
                        result.error("SERVICE_ERROR", "Failed to start service: ${e.message}", null)
                    }
                }
                "stopFloatingWindow" -> {
                    try {
                        val intent = Intent(this, FloatingWindowService::class.java)
                        stopService(intent)
                        Log.d(TAG, "Stopped floating window service")
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping service", e)
                        result.error("SERVICE_ERROR", "Failed to stop service: ${e.message}", null)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun setupScreenshotReceiver() {
        screenshotReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // When a broadcast is received, get the image data
                val byteArray = intent.getByteArrayExtra("screenshot")
                if (byteArray != null) {
                    Log.d(TAG, "Received screenshot broadcast, size: ${byteArray.size} bytes")
                    // And send it over the MethodChannel to Dart
                    methodChannel.invokeMethod("onScreenshot", byteArray)
                    Log.d(TAG, "Forwarded screenshot to Flutter")
                } else {
                    Log.e(TAG, "Received screenshot broadcast but byteArray is null")
                }
            }
        }
        // Register the receiver to listen for messages named "onScreenshot"
        LocalBroadcastManager.getInstance(this).registerReceiver(
            screenshotReceiver!!, IntentFilter("onScreenshot")
        )
        Log.d(TAG, "Screenshot receiver registered")
    }

    override fun onDestroy() {
        // Unregister the receiver when the app is destroyed to prevent memory leaks
        screenshotReceiver?.let {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
        }
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(this)
            } else {
                true
            }
            Log.d(TAG, "Overlay permission result: $hasPermission")
            methodChannel.invokeMethod("onOverlayPermissionResult", hasPermission)
        }
    }

    override fun onResume() {
        super.onResume()
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        Log.d(TAG, "Overlay permission status in onResume: $hasPermission")
        methodChannel.invokeMethod("onOverlayPermissionResult", hasPermission)
    }
}

