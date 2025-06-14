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
import android.view.WindowManager
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handler: Handler? = null
    private var isCapturing = false
    private var methodChannel: MethodChannel? = null

    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "START_CAPTURE") {
            val resultCode = intent.getIntExtra("resultCode", -1)
            val data = intent.getParcelableExtra<Intent>("data")
            val channel = intent.getStringExtra("channel")
            
            if (resultCode != -1 && data != null && channel != null) {
                startCapture(resultCode, data, channel)
            }
        } else if (intent?.action == "STOP_CAPTURE") {
            stopCapture()
        }
        return START_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent, channelName: String) {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

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
        startCaptureLoop(channelName)
    }

    private fun startCaptureLoop(channelName: String) {
        Thread {
            while (isCapturing) {
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

                        val stream = ByteArrayOutputStream()
                        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                        val byteArray = stream.toByteArray()

                        handler?.post {
                            methodChannel?.invokeMethod("onScreenshot", byteArray)
                        }

                        bitmap.recycle()
                        croppedBitmap.recycle()
                        image.close()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Thread.sleep(1000) // Capture every second
            }
        }.start()
    }

    private fun stopCapture() {
        isCapturing = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }
} 