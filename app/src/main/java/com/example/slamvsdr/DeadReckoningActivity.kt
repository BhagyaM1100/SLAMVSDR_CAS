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
        // Tuning: adjust these constants to make the dot reach edges faster/slower.
        private const val ACCELERATION_THRESHOLD = 0.01      // dead-zone for tiny noise
        private const val VELOCITY_DAMPING = 0.99           // closer to 1 => more carry (more drift)
        private const val ACCELERATION_GAIN = 10.0          // amplifies sensor accel (emulator often tiny)
        private const val WORLD_SCALE = 1.0                 // multiplies integrated meters -> world units (-7..7 / -4..4)
    }

    // Sensor
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // UI
    private lateinit var positionXText: TextView
    private lateinit var positionYText: TextView
    private lateinit var positionZText: TextView
    private lateinit var velocityText: TextView
    private lateinit var backButton: Button
    private lateinit var resetButton: Button

    private lateinit var pathCanvas: PathCanvasView

    // Integration state
    private var lastTimestamp: Long = 0L
    private var velocityX = 0.0
    private var velocityY = 0.0
    private var velocityZ = 0.0
    private var positionX = 0.0   // meters (simulated integrated position)
    private var positionY = 0.0
    private var positionZ = 0.0

    // Calibration
    private var currentAccelerometerReading = FloatArray(3) { 0.0f }
    private var initialGravity = FloatArray(3) { 0.0f }
    private var isCalibrated = false

    // Canvas world bounds (must match PathCanvasView)
    private val worldXMin = -7.0
    private val worldXMax = 7.0
    private val worldYMin = -4.0
    private val worldYMax = 4.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dead_reckoning)

        // Bind UI (these IDs must exist in your layout)
        positionXText = findViewById(R.id.position_x_text)
        positionYText = findViewById(R.id.position_y_text)
        positionZText = findViewById(R.id.position_z_text)
        velocityText = findViewById(R.id.velocity_text)
        backButton = findViewById(R.id.back_button)
        resetButton = findViewById(R.id.reset_button)

        pathCanvas = findViewById(R.id.path_canvas)

        // Sensor manager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Buttons
        backButton.setOnClickListener { finish() }
        resetButton.setOnClickListener { resetAndCalibrate() }

        // UI initial state
        velocityText.text = "Press RESET to calibrate (emulator)"
        resetButton.text = "Calibrate & Reset"
        pathCanvas.resetPath()
        updatePositionDisplay()
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
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                // cache raw reading for calibration
                currentAccelerometerReading = event.values.clone()
            }

            val currentTimestamp = System.nanoTime()
            if (lastTimestamp == 0L) {
                lastTimestamp = currentTimestamp
                return
            }
            val dt = (currentTimestamp - lastTimestamp) / 1_000_000_000.0
            lastTimestamp = currentTimestamp

            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    if (isCalibrated) processAccelerometerData(event.values, dt)
                }
            }
        }
    }

    private fun processAccelerometerData(values: FloatArray, dt: Double) {
        // 1) Subtract calibrated gravity
        var ax = values[0].toDouble() - initialGravity[0].toDouble()
        var ay = values[1].toDouble() - initialGravity[1].toDouble()
        var az = values[2].toDouble() - initialGravity[2].toDouble()

        // 2) Dead-zone
        ax = if (abs(ax) < ACCELERATION_THRESHOLD) 0.0 else ax
        ay = if (abs(ay) < ACCELERATION_THRESHOLD) 0.0 else ay
        az = if (abs(az) < ACCELERATION_THRESHOLD) 0.0 else az

        // 3) Amplify (useful on emulator / small sensor values)
        val amplifiedAx = ax * ACCELERATION_GAIN
        val amplifiedAy = ay * ACCELERATION_GAIN
        val amplifiedAz = az * ACCELERATION_GAIN

        // 4) Integrate -> velocity (apply damping)
        velocityX = (velocityX + amplifiedAx * dt) * VELOCITY_DAMPING
        velocityY = (velocityY + amplifiedAy * dt) * VELOCITY_DAMPING
        velocityZ = (velocityZ + amplifiedAz * dt) * VELOCITY_DAMPING

        // 5) Integrate -> position (meters)
        positionX += velocityX * dt
        positionY += velocityY * dt
        positionZ += velocityZ * dt

        // 6) Map to world coordinates and scale
        var worldX = positionX * WORLD_SCALE
        var worldY = positionY * WORLD_SCALE

        // 7) Clamp to bounds so dot reaches near edges
        worldX = worldX.coerceIn(worldXMin, worldXMax)
        worldY = worldY.coerceIn(worldYMin, worldYMax)

        // Optional: if you want to stop pushing once clamped, uncomment to zero velocity on clamp
        // if (worldX == worldXMin || worldX == worldXMax) velocityX = 0.0
        // if (worldY == worldYMin || worldY == worldYMax) velocityY = 0.0

        // 8) Update UI and canvas with world coords (-7..7, -4..4)
        updatePositionDisplay()
        pathCanvas.updatePosition(worldX, worldY)
    }

    private fun updatePositionDisplay() {
        val totalVelocity = sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ)
        positionXText.text = "X: ${"%.2f".format(positionX)} m"
        positionYText.text = "Y: ${"%.2f".format(positionY)} m"
        positionZText.text = "Z: ${"%.2f".format(positionZ)} m"
        velocityText.text = "Velocity: ${"%.2f".format(totalVelocity)} m/s"
    }

    private fun resetAndCalibrate() {
        // Reset numeric state
        velocityX = 0.0; velocityY = 0.0; velocityZ = 0.0
        positionX = 0.0; positionY = 0.0; positionZ = 0.0

        // Set current raw reading as gravity baseline
        initialGravity = currentAccelerometerReading.clone()
        isCalibrated = true

        // Reset canvas & timestamp
        pathCanvas.resetPath()
        lastTimestamp = System.nanoTime()
        updatePositionDisplay()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // not used
    }
}
