package com.example.audio_equalizer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {

    private val TAG = "EqualizerApp"
    private val PERMISSION_REQUEST_CODE = 123
    private var equalizer: Equalizer? = null
    private val sliders = arrayOfNulls<Slider>(5)
    private val valueTextViews = arrayOfNulls<TextView>(5)
    private var statusUpdateTimer: Timer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(TAG, "onCreate started")

        // Set up UI
        try {
            setupUI()

            // Request permissions if needed
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Requesting permissions")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.MODIFY_AUDIO_SETTINGS, Manifest.permission.RECORD_AUDIO),
                    PERMISSION_REQUEST_CODE
                )
            } else {
                Log.d(TAG, "Permissions already granted, initializing equalizer")
                initializeEqualizer()
                setupAudioFocus()
                updateEqualizerStatus()

                // Start periodic status updates
                statusUpdateTimer = Timer()
                statusUpdateTimer?.scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        runOnUiThread {
                            updateEqualizerStatus()
                        }
                    }
                }, 0, 2000) // Update every 2 seconds
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Error setting up app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupUI() {
        Log.d(TAG, "Setting up UI components")

        // Find all UI elements
        sliders[0] = findViewById(R.id.band1Slider)
        sliders[1] = findViewById(R.id.band2Slider)
        sliders[2] = findViewById(R.id.band3Slider)
        sliders[3] = findViewById(R.id.band4Slider)
        sliders[4] = findViewById(R.id.band5Slider)

        valueTextViews[0] = findViewById(R.id.band1Value)
        valueTextViews[1] = findViewById(R.id.band2Value)
        valueTextViews[2] = findViewById(R.id.band3Value)
        valueTextViews[3] = findViewById(R.id.band4Value)
        valueTextViews[4] = findViewById(R.id.band5Value)

        val resetButton = findViewById<Button>(R.id.resetButton)
        val equalizerSwitch = findViewById<Switch>(R.id.equalizerSwitch)

        // Initialize text views with default values
        for (i in 0 until 5) {
            valueTextViews[i]?.text = "0 dB"
        }

        // Set up reset button
        resetButton.setOnClickListener {
            Log.d(TAG, "Reset button clicked")
            resetEqualizer()
        }

        // Set up equalizer switch
        equalizerSwitch.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "Equalizer switch toggled: $isChecked")
            toggleEqualizer(isChecked)
        }

        // Disable sliders initially until equalizer is initialized
        for (i in 0 until 5) {
            sliders[i]?.isEnabled = false
        }
    }

    private fun initializeEqualizer() {
        try {
            Log.d(TAG, "Initializing equalizer")

            // Check if device supports equalizer before creating
            val supported = hasEqualizerSupport()
            if (!supported) {
                Log.e(TAG, "Device does not support Equalizer")
                Toast.makeText(this, "This device does not support audio equalization", Toast.LENGTH_LONG).show()
                return
            }

            // Create and enable equalizer with defensive check
            try {
                equalizer = Equalizer(1000, 0) // Higher priority (1000) for system-wide effect
                Log.d(TAG, "Equalizer created successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create equalizer: ${e.message}", e)
                Toast.makeText(this, "Unable to create equalizer: ${e.message}", Toast.LENGTH_LONG).show()
                return
            }

            // Enable the equalizer
            try {
                equalizer?.enabled = true
                Log.d(TAG, "Equalizer enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enable equalizer: ${e.message}", e)
                Toast.makeText(this, "Unable to enable equalizer: ${e.message}", Toast.LENGTH_LONG).show()
                return
            }

            // Get equalizer properties with defensive checks
            val numBands = try {
                equalizer?.numberOfBands?.toInt() ?: 0
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get number of bands: ${e.message}", e)
                0
            }

            Log.d(TAG, "Number of equalizer bands: $numBands")

            if (numBands == 0) {
                Toast.makeText(this, "No equalizer bands available on this device", Toast.LENGTH_LONG).show()
                return
            }

            // Get equalizer level range
            val levelRange = try {
                equalizer?.bandLevelRange
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get band level range: ${e.message}", e)
                null
            }

            val minLevel = (levelRange?.get(0)?.div(100)) ?: -15
            val maxLevel = (levelRange?.get(1)?.div(100)) ?: 15

            Log.d(TAG, "Level range: $minLevel dB to $maxLevel dB")

            // Configure sliders for available bands
            for (i in 0 until minOf(numBands, 5)) {
                configureSlider(i, minLevel, maxLevel)
            }

            // Enable UI now that equalizer is working
            findViewById<Switch>(R.id.equalizerSwitch)?.isChecked = true
            for (i in 0 until minOf(numBands, 5)) {
                sliders[i]?.isEnabled = true
            }

            Log.d(TAG, "Equalizer initialization complete")
            Toast.makeText(this, "Equalizer ready", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "Error in initializeEqualizer: ${e.message}", e)
            Toast.makeText(this, "Error initializing equalizer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasEqualizerSupport(): Boolean {
        // Simple test to see if equalizer is supported
        return try {
            val testEq = Equalizer(0, 0)
            val numBands = testEq.numberOfBands.toInt() // Convert to Int first
            testEq.release()
            numBands > 0
        } catch (e: Exception) {
            Log.e(TAG, "Equalizer not supported: ${e.message}", e)
            false
        }
    }

    private fun configureSlider(bandIndex: Int, minLevel: Int, maxLevel: Int) {
        try {
            // Get current band level
            val bandLevel = try {
                equalizer?.getBandLevel(bandIndex.toShort())?.div(100) ?: 0
            } catch (e: Exception) {
                Log.e(TAG, "Error getting band $bandIndex level: ${e.message}", e)
                0
            }

            Log.d(TAG, "Band $bandIndex current level: $bandLevel dB")

            // Configure slider
            sliders[bandIndex]?.apply {
                valueFrom = minLevel.toFloat()
                valueTo = maxLevel.toFloat()
                value = bandLevel.toFloat()

                // Update listener
                addOnChangeListener { _, value, fromUser ->
                    if (fromUser) {
                        try {
                            val millibel = (value * 100).toInt().toShort()
                            equalizer?.setBandLevel(bandIndex.toShort(), millibel)
                            valueTextViews[bandIndex]?.text = "${value.toInt()} dB"
                            Log.d(TAG, "Band $bandIndex set to ${value.toInt()} dB")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error setting band $bandIndex level: ${e.message}", e)
                        }
                    }
                }
            }

            // Set initial text display
            valueTextViews[bandIndex]?.text = "$bandLevel dB"

        } catch (e: Exception) {
            Log.e(TAG, "Error configuring slider for band $bandIndex: ${e.message}", e)
        }
    }

    private fun resetEqualizer() {
        try {
            Log.d(TAG, "Resetting equalizer bands")
            val numBands = equalizer?.numberOfBands?.toInt() ?: 0

            for (i in 0 until minOf(numBands, 5)) {
                try {
                    equalizer?.setBandLevel(i.toShort(), 0)
                    sliders[i]?.value = 0f
                    valueTextViews[i]?.text = "0 dB"
                } catch (e: Exception) {
                    Log.e(TAG, "Error resetting band $i: ${e.message}", e)
                }
            }

            Toast.makeText(this, "Equalizer reset", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in resetEqualizer: ${e.message}", e)
            Toast.makeText(this, "Error resetting equalizer: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleEqualizer(enabled: Boolean) {
        try {
            Log.d(TAG, "Toggling equalizer: $enabled")
            equalizer?.enabled = enabled

            for (i in 0 until 5) {
                sliders[i]?.isEnabled = enabled
            }

            Toast.makeText(
                this,
                if (enabled) "Equalizer enabled" else "Equalizer disabled",
                Toast.LENGTH_SHORT
            ).show()

            updateEqualizerStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling equalizer: ${e.message}", e)
            Toast.makeText(this, "Error toggling equalizer: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAudioPlaying(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.isMusicActive
    }

    private fun setupAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build()

            audioManager.requestAudioFocus(focusRequest)
        } else {
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun updateEqualizerStatus() {
        val statusTextView = findViewById<TextView>(R.id.statusTextView)

        if (equalizer?.enabled == true) {
            if (isAudioPlaying()) {
                statusTextView.text = "Equalizer active and processing audio"
                statusTextView.setTextColor(Color.GREEN)
            } else {
                statusTextView.text = "Equalizer ready (play audio to test)"
                statusTextView.setTextColor(Color.YELLOW)
            }
        } else {
            statusTextView.text = "Equalizer disabled"
            statusTextView.setTextColor(Color.RED)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "Permission result received")

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission granted, initializing equalizer")
                initializeEqualizer()
                setupAudioFocus()
                updateEqualizerStatus()

                // Start periodic status updates
                statusUpdateTimer = Timer()
                statusUpdateTimer?.scheduleAtFixedRate(object : TimerTask() {
                    override fun run() {
                        runOnUiThread {
                            updateEqualizerStatus()
                        }
                    }
                }, 0, 2000) // Update every 2 seconds
            } else {
                Log.e(TAG, "Permission denied")
                Toast.makeText(
                    this,
                    "Permission denied. Equalizer functionality will be limited.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called, releasing equalizer")

        // Stop the timer
        statusUpdateTimer?.cancel()
        statusUpdateTimer = null

        try {
            equalizer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing equalizer: ${e.message}", e)
        }
        super.onDestroy()
    }
}