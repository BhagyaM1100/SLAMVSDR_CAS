package com.example.slamvsdr

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.TextView

class SensorDataActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private lateinit var accelXText: TextView
    private lateinit var accelYText: TextView
    private lateinit var accelZText: TextView
    private lateinit var gyroXText: TextView
    private lateinit var gyroYText: TextView
    private lateinit var gyroZText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor_data)

        // Initialize TextViews
        accelXText = findViewById(R.id.sensor_accel_x_text)
        accelYText = findViewById(R.id.sensor_accel_y_text)
        accelZText = findViewById(R.id.sensor_accel_z_text)
        gyroXText = findViewById(R.id.sensor_gyro_x_text)
        gyroYText = findViewById(R.id.sensor_gyro_y_text)
        gyroZText = findViewById(R.id.sensor_gyro_z_text)

        // Initialize sensor manager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
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
                    accelXText.text = "X-Axis: ${"%.2f".format(event.values[0])} m/s²"
                    accelYText.text = "Y-Axis: ${"%.2f".format(event.values[1])} m/s²"
                    accelZText.text = "Z-Axis: ${"%.2f".format(event.values[2])} m/s²"
                }

                Sensor.TYPE_GYROSCOPE -> {
                    gyroXText.text = "X-Rotation: ${"%.2f".format(event.values[0])} rad/s"
                    gyroYText.text = "Y-Rotation: ${"%.2f".format(event.values[1])} rad/s"
                    gyroZText.text = "Z-Rotation: ${"%.2f".format(event.values[2])} rad/s"
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    fun onBackButtonClick(view: View) {
        finish()
    }
}