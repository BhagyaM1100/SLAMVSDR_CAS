package com.example.slamvsdr

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainMenuActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // TextViews - USING CORRECT IDs FROM OUR NEW LAYOUT
    private lateinit var accelXText: TextView
    private lateinit var accelYText: TextView
    private lateinit var accelZText: TextView
    private lateinit var gyroXText: TextView
    private lateinit var gyroYText: TextView
    private lateinit var gyroZText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        // Initialize TextViews WITH CORRECT IDs
        initializeSensorTextViews()

        // Initialize Sensor Manager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Dead Reckoning Button
        findViewById<Button>(R.id.dead_reckoning_btn).setOnClickListener {
            val intent = Intent(this, DeadReckoningActivity::class.java)
            startActivity(intent)
        }
    }

    private fun initializeSensorTextViews() {
        // USING THE CORRECT IDs FROM activity_main_menu.xml
        accelXText = findViewById(R.id.main_accel_x)
        accelYText = findViewById(R.id.main_accel_y)
        accelZText = findViewById(R.id.main_accel_z)
        gyroXText = findViewById(R.id.main_gyro_x)
        gyroYText = findViewById(R.id.main_gyro_y)
        gyroZText = findViewById(R.id.main_gyro_z)
    }

    override fun onResume() {
        super.onResume()
        // Register sensor listeners - USE NORMAL UPDATE RATE (safer)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) // ← CHANGED
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) // ← CHANGED
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    // Update accelerometer data
                    accelXText.text = "%.2f".format(event.values[0])
                    accelYText.text = "%.2f".format(event.values[1])
                    accelZText.text = "%.2f".format(event.values[2])
                }
                Sensor.TYPE_GYROSCOPE -> {
                    // Update gyroscope data
                    gyroXText.text = "%.2f".format(event.values[0])
                    gyroYText.text = "%.2f".format(event.values[1])
                    gyroZText.text = "%.2f".format(event.values[2])
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }
}