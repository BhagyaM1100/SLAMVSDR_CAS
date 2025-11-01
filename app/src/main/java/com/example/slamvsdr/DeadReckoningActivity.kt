package com.example.slamvsdr

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sqrt

class DeadReckoningActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // UI Elements - Dead Reckoning
    private lateinit var positionXText: TextView
    private lateinit var positionYText: TextView
    private lateinit var positionZText: TextView
    private lateinit var velocityText: TextView
    private lateinit var backButton: Button

    // Path Visualization
    private lateinit var pathCanvas: PathCanvasView
    private lateinit var resetButton: Button

    // Dead Reckoning Variables
    private var lastTimestamp: Long = 0
    private var velocityX = 0.0
    private var velocityY = 0.0
    private var velocityZ = 0.0
    private var positionX = 0.0
    private var positionY = 0.0
    private var positionZ = 0.0

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
            resetDeadReckoning()
        }

        // Initialize Sensor Manager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        lastTimestamp = System.nanoTime()
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val currentTimestamp = System.nanoTime()
            val dt = (currentTimestamp - lastTimestamp) / 1_000_000_000.0
            lastTimestamp = currentTimestamp

            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    processAccelerometerData(event.values, dt)
                }
            }
        }
    }

    private fun processAccelerometerData(values: FloatArray, dt: Double) {
        val ax = values[0].toDouble()
        val ay = values[1].toDouble()
        val az = values[2].toDouble() - 9.81

        // REDUCED SENSITIVITY - Added damping and lower multipliers
        val damping = 0.95 // 5% velocity reduction per frame

        // Update velocity with damping and lower sensitivity
        velocityX = velocityX * damping + ax * dt * 0.5  // Reduced from 3.0 to 0.5
        velocityY = velocityY * damping + ay * dt * 0.5  // Reduced from 3.0 to 0.5
        velocityZ = velocityZ * damping + az * dt * 0.5  // Reduced from 3.0 to 0.5

        // Update position
        positionX += velocityX * dt
        positionY += velocityY * dt
        positionZ += velocityZ * dt

        // Update UI
        updatePositionDisplay()

        // Update path visualization
        pathCanvas.updatePosition(positionX, positionY)
    }

    private fun updatePositionDisplay() {
        val totalVelocity = sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ)
        positionXText.text = "X: ${"%.2f".format(positionX)} m"
        positionYText.text = "Y: ${"%.2f".format(positionY)} m"
        positionZText.text = "Z: ${"%.2f".format(positionZ)} m"
        velocityText.text = "Velocity: ${"%.2f".format(totalVelocity)} m/s"
    }

    private fun resetDeadReckoning() {
        velocityX = 0.0
        velocityY = 0.0
        velocityZ = 0.0
        positionX = 0.0
        positionY = 0.0
        positionZ = 0.0
        updatePositionDisplay()
        pathCanvas.resetPath()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }
}