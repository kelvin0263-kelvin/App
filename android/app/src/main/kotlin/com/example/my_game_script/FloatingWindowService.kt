package com.example.my_game_script

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.app.Activity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import android.graphics.BitmapFactory

class FloatingWindowService : Service() {
    private val TAG = "FloatingWindowService"
    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: Button
    private lateinit var floatingWindow: LinearLayout
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private val CHANNEL_ID = "FloatingWindowServiceChannel"
    private val NOTIFICATION_ID = 1
    private var isWindowVisible = false
    private lateinit var ocrResultTextView: TextView

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            Log.d(TAG, "WindowManager initialized successfully")
            setupFloatingButton()
            setupFloatingWindow()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Error initializing service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Window Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for the floating window service"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating Window Service")
            .setContentText("Service is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun setupFloatingButton() {
        try {
            Log.d(TAG, "Setting up floating button")
            floatingButton = Button(this).apply {
                text = "Floating Button"
                setBackgroundColor(0xFF0000FF.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                width = 200
                height = 100
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 100
                alpha = 1.0f
            }

            floatingButton.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        Log.d(TAG, "Touch down at ($initialX, $initialY)")
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(view, params)
                        Log.d(TAG, "Moving to (${params.x}, ${params.y})")
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (Math.abs(event.rawX - initialTouchX) < 5 && 
                            Math.abs(event.rawY - initialTouchY) < 5) {
                            Log.d(TAG, "Button clicked!")
                            toggleFloatingWindow()
                        }
                        true
                    }
                    else -> false
                }
            }

            Log.d(TAG, "Adding button to window manager")
            windowManager.addView(floatingButton, params)
            Log.d(TAG, "Button added successfully")
            Toast.makeText(this, "Floating button added", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding view to window manager", e)
            Toast.makeText(this, "Error adding floating button: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupFloatingWindow() {
        try {
            Log.d(TAG, "Setting up floating window")
            floatingWindow = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(20, 20, 20, 20)

                // Add a title
                addView(TextView(this@FloatingWindowService).apply {
                    text = "Floating Window"
                    textSize = 20f
                    setTextColor(Color.BLACK)
                })

                // Add OCR result TextView
                ocrResultTextView = TextView(this@FloatingWindowService).apply {
                    text = "OCR Result will appear here."
                    textSize = 16f
                    setTextColor(Color.BLUE)
                    setPadding(0, 20, 0, 0)
                }
                addView(ocrResultTextView)

                // Add Start OCR button
                addView(Button(this@FloatingWindowService).apply {
                    text = "Start OCR"
                    setOnClickListener {
                        startOcrProcess()
                    }
                })

                // Add a close button
                addView(Button(this@FloatingWindowService).apply {
                    text = "Close"
                    setOnClickListener {
                        toggleFloatingWindow()
                    }
                })
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                alpha = 1.0f
            }

            // Initially hide the window
            floatingWindow.visibility = View.GONE
            windowManager.addView(floatingWindow, params)
            Log.d(TAG, "Floating window setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up floating window", e)
            Toast.makeText(this, "Error setting up floating window: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleFloatingWindow() {
        try {
            if (isWindowVisible) {
                floatingWindow.visibility = View.GONE
                Log.d(TAG, "Floating window hidden")
            } else {
                floatingWindow.visibility = View.VISIBLE
                Log.d(TAG, "Floating window shown")
            }
            isWindowVisible = !isWindowVisible
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling floating window", e)
            Toast.makeText(this, "Error toggling window: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startOcrProcess() {
        try {
            // Read the image from Android assets
            val inputStream = applicationContext.assets.open("img2.jpg")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                ocrResultTextView.text = "Error: Could not load image"
                return
            }

            // Crop the specific region (1344,393)-(1504,658)
            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                1344, 393,
                1504 - 1344,
                658 - 393
            )

            // --- START PRE-PROCESSING ---
            // Create a new, mutable bitmap for our black & white output
            val processedBitmap = croppedBitmap.copy(Bitmap.Config.ARGB_8888, true)

            val width = processedBitmap.width
            val height = processedBitmap.height

            // Loop through every pixel
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val pixel = processedBitmap.getPixel(x, y)

                    // Get the Red, Green, and Blue values of the pixel
                    val red = android.graphics.Color.red(pixel)
                    val green = android.graphics.Color.green(pixel)
                    val blue = android.graphics.Color.blue(pixel)

                    // Apply threshold for yellow text
                    // Yellow text has high Red and Green values, and a low Blue value
                    if (red > 150 && green > 150 && blue < 100) {
                        // This pixel is likely part of the yellow text, so make it pure white
                        processedBitmap.setPixel(x, y, android.graphics.Color.WHITE)
                    } else {
                        // Otherwise, it's background noise, so make it pure black
                        processedBitmap.setPixel(x, y, android.graphics.Color.BLACK)
                    }
                }
            }
            // --- END PRE-PROCESSING ---

            // Run OCR on the pre-processed image
            val inputImage = InputImage.fromBitmap(processedBitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    // Update the UI with the OCR result
                    ocrResultTextView.text = visionText.text
                }
                .addOnFailureListener { e ->
                    ocrResultTextView.text = "OCR failed: ${e.message}"
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
            ocrResultTextView.text = "Error processing image: ${e.message}"
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        try {
            if (::floatingButton.isInitialized && ::windowManager.isInitialized) {
                windowManager.removeView(floatingButton)
                Log.d(TAG, "Button removed successfully")
            }
            if (::floatingWindow.isInitialized && ::windowManager.isInitialized) {
                windowManager.removeView(floatingWindow)
                Log.d(TAG, "Window removed successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying service", e)
        }
    }
} 