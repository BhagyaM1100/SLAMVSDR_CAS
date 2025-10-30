package com.example.slamvsdr

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // TextViews for displaying sensor data
    private lateinit var accelXText: TextView
    private lateinit var accelYText: TextView
    private lateinit var accelZText: TextView
    private lateinit var gyroXText: TextView
    private lateinit var gyroYText: TextView
    private lateinit var gyroZText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize TextViews
        accelXText = findViewById(R.id.accel_x_text)
        accelYText = findViewById(R.id.accel_y_text)
        accelZText = findViewById(R.id.accel_z_text)
        gyroXText = findViewById(R.id.gyro_x_text)
        gyroYText = findViewById(R.id.gyro_y_text)
        gyroZText = findViewById(R.id.gyro_z_text)

        // Initialize Sensor Manager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Get accelerometer and gyroscope sensors
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    override fun onResume() {
        super.onResume()
        // Register sensor listeners when app resumes
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister sensor listeners when app pauses to save battery
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    // Update accelerometer data
                    accelXText.text = "X: ${"%.2f".format(event.values[0])}"
                    accelYText.text = "Y: ${"%.2f".format(event.values[1])}"
                    accelZText.text = "Z: ${"%.2f".format(event.values[2])}"
                }
                Sensor.TYPE_GYROSCOPE -> {
                    // Update gyroscope data
                    gyroXText.text = "X: ${"%.2f".format(event.values[0])}"
                    gyroYText.text = "Y: ${"%.2f".format(event.values[1])}"
                    gyroZText.text = "Z: ${"%.2f".format(event.values[2])}"
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // We don't need to do anything here for this basic implementation
    }
}