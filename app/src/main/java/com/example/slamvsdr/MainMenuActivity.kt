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

    // TextViews
    private lateinit var accelXText: TextView
    private lateinit var accelYText: TextView
    private lateinit var accelZText: TextView
    private lateinit var gyroXText: TextView
    private lateinit var gyroYText: TextView
    private lateinit var gyroZText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        // Initialize TextViews
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

        // Camera SLAM Button
        findViewById<Button>(R.id.camera_slam_btn).setOnClickListener {
            val intent = Intent(this, CameraSlamActivity::class.java)
            startActivity(intent)
        }

        // SLAM vs DR Comparison Button
        findViewById<Button>(R.id.slam_comparison_btn).setOnClickListener {
            val intent = Intent(this, DeadReckoningActivity::class.java)
            intent.putExtra("mode", "slam_comparison")
            startActivity(intent)
        }

        // Sensor Data Button
        findViewById<Button>(R.id.sensor_data_btn).setOnClickListener {
            val intent = Intent(this, SensorDataActivity::class.java)
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
        // Register sensor listeners
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
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    // Update accelerometer data
                    accelXText.text = "Accel X: ${"%.2f".format(event.values[0])}"
                    accelYText.text = "Accel Y: ${"%.2f".format(event.values[1])}"
                    accelZText.text = "Accel Z: ${"%.2f".format(event.values[2])}"
                }
                Sensor.TYPE_GYROSCOPE -> {
                    // Update gyroscope data
                    gyroXText.text = "Gyro X: ${"%.2f".format(event.values[0])}"
                    gyroYText.text = "Gyro Y: ${"%.2f".format(event.values[1])}"
                    gyroZText.text = "Gyro Z: ${"%.2f".format(event.values[2])}"
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }
}