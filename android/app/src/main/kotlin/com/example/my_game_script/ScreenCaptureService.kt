package com.example.my_game_script

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.ByteArrayOutputStream

class ScreenCaptureService : Service() {
    private val TAG = "ScreenCaptureService"
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
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START_CAPTURE") {
            val resultCode = intent.getIntExtra("resultCode", -1)
            val data = intent.getParcelableExtra<Intent>("data")
            
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
                captureAndDisplay()
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

    private fun captureAndDisplay() {
        try {
            val image = imageReader?.acquireLatestImage()
            if (image != null) {
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

                handler?.post {
                    previewImageView?.setImageBitmap(croppedBitmap)
                }

                val stream = ByteArrayOutputStream()
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                val byteArray = stream.toByteArray()
                Log.d(TAG, "Screenshot size: ${byteArray.size} bytes")

                // Send the screenshot as a local broadcast message
                val intent = Intent("onScreenshot")
                intent.putExtra("screenshot", byteArray)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                Log.d(TAG, "Broadcast sent with screenshot")

                bitmap.recycle()
                croppedBitmap.recycle()
                image.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing and displaying screenshot", e)
        }
    }

    private fun startCaptureLoop() {
        Thread {
            while (isCapturing) {
                try {
                    captureAndDisplay()
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
        Log.d(TAG, "Screen capture stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }
} 