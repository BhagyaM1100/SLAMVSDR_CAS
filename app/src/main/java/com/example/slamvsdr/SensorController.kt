package com.example.slamvsdr

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class SensorController(private val sensorManager: SensorManager) : SensorEventListener {

    private var fusionController: FusionController? = null
    private var plotController: PlotController? = null

    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private var lastUpdateTime = 0L
    private var isSlamEnabled = false

    // Callback interface
    interface SensorDataCallback {
        fun onSensorDataUpdated(result: FusionController.FusionResult)
        fun onPlotUpdateNeeded()
    }

    private var callback: SensorDataCallback? = null

    fun initialize(fusion: FusionController, plot: PlotController) {
        fusionController = fusion
        plotController = plot

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        Log.d("Sensor", "Sensors initialized")
    }

    fun setCallback(cb: SensorDataCallback) {
        callback = cb
    }

    fun enableSlam(enable: Boolean) {
        isSlamEnabled = enable
        fusionController?.enableSlam(enable)
        Log.d("Sensor", "SLAM ${if (enable) "enabled" else "disabled"}")
    }

    fun enableDr(enable: Boolean) {
        fusionController?.enableDr(enable)
        Log.d("Sensor", "DR ${if (enable) "enabled" else "disabled"}")
    }

    fun startSensors() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        lastUpdateTime = System.currentTimeMillis()
        Log.d("Sensor", "Sensors started")
    }

    fun stopSensors() {
        sensorManager.unregisterListener(this)
        Log.d("Sensor", "Sensors stopped")
    }

    fun reset() {
        fusionController?.reset()
        plotController?.reset()
        lastUpdateTime = System.currentTimeMillis()
        Log.d("Sensor", "All controllers reset")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val currentTime = System.currentTimeMillis()
                val dt = (currentTime - lastUpdateTime) / 1000.0

                if (dt > 0) {
                    // Process data through fusion
                    fusionController?.let { fusion ->
                        val result = fusion.updateIMU(
                            event.values[0].toDouble(),
                            event.values[1].toDouble(),
                            event.values[2].toDouble(),
                            0.0, 0.0, 0.0, // In real app, use gyro values
                            dt
                        )

                        // Update plot
                        plotController?.updatePose(result)

                        // Notify callback
                        callback?.onSensorDataUpdated(result)
                        callback?.onPlotUpdateNeeded()
                    }

                    lastUpdateTime = currentTime
                }
            }

            // Gyroscope could be handled here for better accuracy
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Log.d("Sensor", "Accuracy changed: ${sensor.name} = $accuracy")
    }
}