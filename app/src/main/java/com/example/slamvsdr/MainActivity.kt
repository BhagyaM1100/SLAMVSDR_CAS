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

class MainActivity : AppCompatActivity(), SensorEventListener {

    // Sensor Management
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // UI Elements - Sensor Data
    private lateinit var accelXText: TextView
    private lateinit var accelYText: TextView
    private lateinit var accelZText: TextView
    private lateinit var gyroXText: TextView
    private lateinit var gyroYText: TextView
    private lateinit var gyroZText: TextView

    // UI Elements - Dead Reckoning
    private lateinit var positionXText: TextView
    private lateinit var positionYText: TextView
    private lateinit var positionZText: TextView
    private lateinit var velocityText: TextView

    // UI Elements - Path Visualization
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
        setContentView(R.layout.activity_main)

        initializeSensorTextViews()
        initializeDeadReckoningTextViews()
        initializePathVisualization()
        initializeSensors()

        lastTimestamp = System.nanoTime()
    }

    private fun initializeSensorTextViews() {
        accelXText = findViewById(R.id.accel_x_text)
        accelYText = findViewById(R.id.accel_y_text)
        accelZText = findViewById(R.id.accel_z_text)
        gyroXText = findViewById(R.id.gyro_x_text)
        gyroYText = findViewById(R.id.gyro_y_text)
        gyroZText = findViewById(R.id.gyro_z_text)
    }

    private fun initializeDeadReckoningTextViews() {
        positionXText = findViewById(R.id.position_x_text)
        positionYText = findViewById(R.id.position_y_text)
        positionZText = findViewById(R.id.position_z_text)
        velocityText = findViewById(R.id.velocity_text)
    }

    private fun initializePathVisualization() {
        pathCanvas = findViewById(R.id.path_canvas)
        resetButton = findViewById(R.id.reset_button)
        resetButton.setOnClickListener {
            resetDeadReckoning()
        }
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.let {
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
            val dt = (currentTimestamp - lastTimestamp) / 1_000_000_000.0 // Convert to seconds
            lastTimestamp = currentTimestamp

            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    processAccelerometerData(event.values, dt)
                }
                Sensor.TYPE_GYROSCOPE -> {
                    processGyroscopeData(event.values)
                }
            }
        }
    }

    private fun processAccelerometerData(values: FloatArray, dt: Double) {
        val ax = values[0].toDouble()
        val ay = values[1].toDouble()
        val az = values[2].toDouble() - 9.81 // Remove gravity

        // Update velocity with damping
        val damping = 0.98
        velocityX = velocityX * damping + ax * dt
        velocityY = velocityY * damping + ay * dt
        velocityZ = velocityZ * damping + az * dt

        // Update position using velocity
        positionX += velocityX * dt
        positionY += velocityY * dt
        positionZ += velocityZ * dt

        // Update UI
        updateAccelerometerUI(ax, ay, az)
        updatePositionDisplay()
        pathCanvas.updatePosition(positionX, positionY)
    }

    private fun processGyroscopeData(values: FloatArray) {
        gyroXText.text = "X: ${"%.2f".format(values[0])} rad/s"
        gyroYText.text = "Y: ${"%.2f".format(values[1])} rad/s"
        gyroZText.text = "Z: ${"%.2f".format(values[2])} rad/s"
    }

    private fun updateAccelerometerUI(ax: Double, ay: Double, az: Double) {
        accelXText.text = "X: ${"%.2f".format(ax)} m/s²"
        accelYText.text = "Y: ${"%.2f".format(ay)} m/s²"
        accelZText.text = "Z: ${"%.2f".format(az)} m/s²"
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
        pathCanvas.resetPath()
        updatePositionDisplay()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for basic implementation
    }
}