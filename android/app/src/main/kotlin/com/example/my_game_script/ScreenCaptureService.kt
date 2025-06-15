package com.example.my_game_script

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenCaptureService : Service() {
    private val TAG = "ScreenCaptureService"
    private val CHANNEL_ID = "ScreenCaptureServiceChannel"
    private val NOTIFICATION_ID = 2
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handler: Handler? = null
    private var isCapturing = false
    private var windowManager: WindowManager? = null
    private var capturePreviewView: View? = null
    private var previewImageView: ImageView? = null

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Service created and started in foreground")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Capture Active")
            .setContentText("Capture service is running.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")
        if (intent?.action == "START_CAPTURE") {
            val resultCode = intent.getIntExtra("resultCode", -1)
            val data = intent.getParcelableExtra<Intent>("data")
            
            Log.d(TAG, "Received START_CAPTURE with resultCode: $resultCode, data: $data")
            
            if (resultCode != -1 && data != null) {
                Log.d(TAG, "Starting screen capture")
                startCapture(resultCode, data)
            } else {
                Log.e(TAG, "Invalid capture parameters: resultCode=$resultCode, data=$data")
            }
        } else if (intent?.action == "STOP_CAPTURE") {
            Log.d(TAG, "Stopping screen capture")
            stopCapture()
        }
        return START_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        try {
            Log.d(TAG, "startCapture called with resultCode: $resultCode")
            val metrics = DisplayMetrics()
            windowManager?.defaultDisplay?.getMetrics(metrics)

            Log.d(TAG, "Creating ImageReader with dimensions: ${metrics.widthPixels}x${metrics.heightPixels}")
            imageReader = ImageReader.newInstance(
                metrics.widthPixels,
                metrics.heightPixels,
                PixelFormat.RGBA_8888,
                2
            )

            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            Log.d(TAG, "MediaProjection created successfully")

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                handler
            )
            Log.d(TAG, "VirtualDisplay created successfully")

            isCapturing = true
            Log.d(TAG, "Screen capture started successfully")
            setupCapturePreview()
            startCaptureLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen capture", e)
        }
    }

    private fun setupCapturePreview() {
        try {
            val inflater = LayoutInflater.from(this)
            capturePreviewView = inflater.inflate(R.layout.capture_preview, null)
            
            previewImageView = capturePreviewView?.findViewById(R.id.previewImageView)
            
            val displayButton = capturePreviewView?.findViewById<Button>(R.id.displayButton)
            displayButton?.setOnClickListener {
                captureAndSave()
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 100
            }

            windowManager?.addView(capturePreviewView, params)
            Log.d(TAG, "Capture preview window added")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up capture preview", e)
        }
    }

    private fun captureAndSave() {
        try {
            val image = imageReader?.acquireLatestImage()
            if (image != null) {
                Log.d("DEBUG_BOT", "Image captured from ImageReader")
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width

                val bitmap = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)

                val croppedBitmap = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    image.width,
                    image.height
                )

                // Save the bitmap to a file
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val filename = "Screenshot_$timestamp.jpg"
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val screenshotDir = File(picturesDir, "Screenshots")
                if (!screenshotDir.exists()) {
                    screenshotDir.mkdirs()
                }
                val file = File(screenshotDir, filename)
                
                FileOutputStream(file).use { out ->
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }
                
                Log.d("DEBUG_BOT", "Screenshot saved to: ${file.absolutePath}")

                bitmap.recycle()
                croppedBitmap.recycle()
                image.close()
            } else {
                Log.d("DEBUG_BOT", "No image available from ImageReader")
            }
        } catch (e: Exception) {
            Log.e("DEBUG_BOT", "Error capturing and saving screenshot", e)
        }
    }

    private fun startCaptureLoop() {
        Thread {
            while (isCapturing) {
                try {
                    captureAndSave()
                    Thread.sleep(1000) // Capture every second
                } catch (e: Exception) {
                    Log.e(TAG, "Error in capture loop", e)
                }
            }
        }.start()
    }

    private fun stopCapture() {
        isCapturing = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        capturePreviewView?.let { windowManager?.removeView(it) }
        stopSelf()
        Log.d(TAG, "Screen capture stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }
} 