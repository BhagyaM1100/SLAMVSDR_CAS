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

    companion object {
        // --- EMULATOR SETTINGS ---

        // Keep at 0.0 for Emulator
        private const val ACCELERATION_THRESHOLD = 0.0

        // Keep friction strong so it stops when you let go
        private const val VELOCITY_DAMPING = 0.80

        // --- NEW EXTREME SCALE ---
        // 3000x Magnification.
        // This will make the path move extremely fast on the map.
        private const val VISUAL_SCALE = 3000.0
    }

    private lateinit var sensorManager: SensorManager
    private var linearSensor: Sensor? = null

    // UI Elements
    private lateinit var positionXText: TextView
    private lateinit var positionYText: TextView
    private lateinit var positionZText: TextView
    private lateinit var velocityText: TextView
    private lateinit var backButton: Button
    private lateinit var resetButton: Button

    // Custom View
    private lateinit var pathCanvas: PathCanvasView

    // Variables
    private var lastTimestamp: Long = 0
    private var velocityX = 0.0
    private var velocityY = 0.0
    private var positionX = 0.0
    private var positionY = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dead_reckoning)

        // 1. Link UI
        positionXText = findViewById(R.id.position_x_text)
        positionYText = findViewById(R.id.position_y_text)
        positionZText = findViewById(R.id.position_z_text)
        velocityText = findViewById(R.id.velocity_text)
        backButton = findViewById(R.id.back_button)
        resetButton = findViewById(R.id.reset_button)
        pathCanvas = findViewById(R.id.path_canvas)

        backButton.setOnClickListener { finish() }

        resetButton.setOnClickListener {
            resetData()
        }

        // 2. Setup Sensor
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // USE LINEAR ACCELERATION
        linearSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        if (linearSensor == null) {
            velocityText.text = "Error: Linear Accel Sensor not supported!"
        } else {
            velocityText.text = "Mode: Extreme Speed (3000x)"
        }
    }

    override fun onResume() {
        super.onResume()
        linearSensor?.let {
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

            if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {

                // Read X and Y
                val ax = event.values[0].toDouble()
                val ay = event.values[1].toDouble()

                // 1. Threshold
                val cleanAx = if (abs(ax) < ACCELERATION_THRESHOLD) 0.0 else ax
                val cleanAy = if (abs(ay) < ACCELERATION_THRESHOLD) 0.0 else ay

                // 2. Velocity + Damping
                velocityX = (velocityX + cleanAx * dt) * VELOCITY_DAMPING
                velocityY = (velocityY + cleanAy * dt) * VELOCITY_DAMPING

                // 3. Position Integration
                positionX += velocityX * dt
                positionY += velocityY * dt

                // 4. Update UI
                updateUI()

                // 5. Update Canvas (Multiplying by 3000 now!)
                pathCanvas.updatePosition(positionX * VISUAL_SCALE, positionY * VISUAL_SCALE)
            }
        }
    }

    private fun updateUI() {
        val speed = sqrt(velocityX * velocityX + velocityY * velocityY)
        positionXText.text = "X: ${"%.2f".format(positionX)} m"
        positionYText.text = "Y: ${"%.2f".format(positionY)} m"
        positionZText.text = "Z: Ignored"
        velocityText.text = "Vel: ${"%.2f".format(speed)} m/s"
    }

    private fun resetData() {
        velocityX = 0.0
        velocityY = 0.0
        positionX = 0.0
        positionY = 0.0
        pathCanvas.resetPath()
        lastTimestamp = System.nanoTime()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}