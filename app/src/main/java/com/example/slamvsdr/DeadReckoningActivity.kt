package com.example.slamvsdr

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.sqrt

class DeadReckoningActivity : AppCompatActivity(), SensorEventListener {

    // --- Tuning Constants for VIRTUAL PHONE ---
    companion object {
        private const val ACCELERATION_THRESHOLD = 0.0
        private const val VELOCITY_DAMPING = 0.95

        /**
         * Multiplies the position for the canvas to make movement larger.
         */
        private const val VISUAL_SCALE = 50.0 // Try 50.0 or 100.0 if still too slow
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // UI Elements
    private lateinit var positionXText: TextView
    private lateinit var positionYText: TextView
    private lateinit var positionZText: TextView
    private lateinit var velocityText: TextView
    private lateinit var backButton: Button

    // Path Visualization
    private lateinit var pathCanvas: PathCanvasView
    private lateinit var resetButton: Button

    // ---
    // --- ⚠️ THESE VARIABLES WERE LIKELY MISSING ⚠️ ---
    // ---
    // Dead Reckoning Variables
    private var lastTimestamp: Long = 0
    private var velocityX = 0.0
    private var velocityY = 0.0
    private var velocityZ = 0.0
    private var positionX = 0.0
    private var positionY = 0.0
    private var positionZ = 0.0

    // Calibration Variables
    private var currentAccelerometerReading = FloatArray(3) { 0.0f }
    private var initialGravity = FloatArray(3) { 0.0f }
    private var isCalibrated = false
    // ---
    // --- ⚠️ END OF MISSING VARIABLES ⚠️ ---
    // ---


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dead_reckoning)

        // Initialize TextViews
        positionXText = findViewById(R.id.position_x_text)
        positionYText = findViewById(R.id.position_y_text)
        positionZText = findViewById(R.id.position_z_text)
        velocityText = findViewById(R.id.velocity_text)
        backButton = findViewById(R.id.back_button)

        // Initialize Path Visualization
        pathCanvas = findViewById(R.id.path_canvas)
        resetButton = findViewById(R.id.reset_button)

        // Back button
        backButton.setOnClickListener {
            finish() // Go back to main menu
        }

        // Reset button
        resetButton.setOnClickListener {
            resetAndCalibrate()
        }

        // Initialize Sensor Manager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Prompt user to calibrate
        velocityText.text = "Press RESET to calibrate (in emulator)"
        resetButton.text = "Calibrate & Reset"
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val currentTimestamp = System.nanoTime()
            if (lastTimestamp == 0L) {
                lastTimestamp = currentTimestamp
                return
            }
            val dt = (currentTimestamp - lastTimestamp) / 1_000_000_000.0
            lastTimestamp = currentTimestamp

            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    currentAccelerometerReading = event.values.clone()
                    if (isCalibrated) {
                        processAccelerometerData(currentAccelerometerReading, dt)
                    }
                }
            }
        }
    }

    private fun processAccelerometerData(values: FloatArray, dt: Double) {
        // 1. Calculate linear acceleration
        val ax = values[0].toDouble() - initialGravity[0].toDouble()
        val ay = values[1].toDouble() - initialGravity[1].toDouble()
        val az = values[2].toDouble() - initialGravity[2].toDouble()

        // 2. Apply Threshold (Dead Zone)
        val filteredAx = if (abs(ax) < ACCELERATION_THRESHOLD) 0.0 else ax
        val filteredAy = if (abs(ay) < ACCELERATION_THRESHOLD) 0.0 else ay
        val filteredAz = if (abs(az) < ACCELERATION_THRESHOLD) 0.0 else az

        // 3. Update Velocity with Damping (Friction)
        velocityX = (velocityX + filteredAx * dt) * VELOCITY_DAMPING
        velocityY = (velocityY + filteredAy * dt) * VELOCITY_DAMPING
        velocityZ = (velocityZ + filteredAz * dt) * VELOCITY_DAMPING

        // 4. Update position (real-world meters)
        positionX += velocityX * dt
        positionY += velocityY * dt
        positionZ += velocityZ * dt

        // 5. Update UI (shows the real meters)
        updatePositionDisplay()

        // 6. Update path visualization with scaling
        pathCanvas.updatePosition(positionX * VISUAL_SCALE, positionY * VISUAL_SCALE)
    }

    private fun updatePositionDisplay() {
        val totalVelocity = sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ)
        // This UI still shows the TRUE physical position in meters
        positionXText.text = "X: ${"%.2f".format(positionX)} m"
        positionYText.text = "Y: ${"%.2f".format(positionY)} m"
        positionZText.text = "Z: ${"%.2f".format(positionZ)} m"
        velocityText.text = "Velocity: ${"%.2f".format(totalVelocity)} m/s"
    }

    private fun resetAndCalibrate() {
        // Reset all state variables
        velocityX = 0.0
        velocityY = 0.0
        velocityZ = 0.0
        positionX = 0.0
        positionY = 0.0
        positionZ = 0.0

        // CALIBRATE: Set the current reading as the new "zero" (gravity) point
        initialGravity = currentAccelerometerReading.clone()
        isCalibrated = true

        // Reset the UI and path
        updatePositionDisplay()
        pathCanvas.resetPath()

        // Reset timestamp
        lastTimestamp = System.nanoTime()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }
}