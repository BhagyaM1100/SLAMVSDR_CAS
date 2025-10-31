package com.example.slamvsdr

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // TextViews for sensor data
    private lateinit var accelXText: TextView
    private lateinit var accelYText: TextView
    private lateinit var accelZText: TextView
    private lateinit var gyroXText: TextView
    private lateinit var gyroYText: TextView
    private lateinit var gyroZText: TextView

    // New TextViews for Dead Reckoning
    private lateinit var positionXText: TextView
    private lateinit var positionYText: TextView
    private lateinit var positionZText: TextView
    private lateinit var velocityText: TextView

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

        // Initialize Sensor TextViews
        accelXText = findViewById(R.id.accel_x_text)
        accelYText = findViewById(R.id.accel_y_text)
        accelZText = findViewById(R.id.accel_z_text)
        gyroXText = findViewById(R.id.gyro_x_text)
        gyroYText = findViewById(R.id.gyro_y_text)
        gyroZText = findViewById(R.id.gyro_z_text)

        // Initialize Dead Reckoning TextViews (we'll add these to layout next)
        positionXText = findViewById(R.id.position_x_text)
        positionYText = findViewById(R.id.position_y_text)
        positionZText = findViewById(R.id.position_z_text)
        velocityText = findViewById(R.id.velocity_text)

        // Initialize Sensor Manager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        lastTimestamp = System.nanoTime()
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
                    val ax = event.values[0].toDouble()
                    val ay = event.values[1].toDouble()
                    val az = event.values[2].toDouble() - 9.81 // Remove gravity

                    // Add small damping factor to simulate friction (0.98 = 2% reduction per calculation)
                    val damping = 0.98

// Update velocity using acceleration with damping
                    velocityX = velocityX * damping + ax * dt
                    velocityY = velocityY * damping + ay * dt
                    velocityZ = velocityZ * damping + az * dt

                    // Update position using velocity
                    positionX += velocityX * dt
                    positionY += velocityY * dt
                    positionZ += velocityZ * dt

                    // Calculate total velocity
                    val totalVelocity = sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ)

                    // Update UI
                    accelXText.text = "X: ${"%.2f".format(ax)} m/s²"
                    accelYText.text = "Y: ${"%.2f".format(ay)} m/s²"
                    accelZText.text = "Z: ${"%.2f".format(az)} m/s²"

                    positionXText.text = "X: ${"%.2f".format(positionX)} m"
                    positionYText.text = "Y: ${"%.2f".format(positionY)} m"
                    positionZText.text = "Z: ${"%.2f".format(positionZ)} m"
                    velocityText.text = "Velocity: ${"%.2f".format(totalVelocity)} m/s"
                }

                Sensor.TYPE_GYROSCOPE -> {
                    gyroXText.text = "X: ${"%.2f".format(event.values[0])} rad/s"
                    gyroYText.text = "Y: ${"%.2f".format(event.values[1])} rad/s"
                    gyroZText.text = "Z: ${"%.2f".format(event.values[2])} rad/s"
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for basic implementation
    }
}