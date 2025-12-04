package com.example.slamvsdr

import android.util.Log
import kotlin.math.*

class FusionController {

    private lateinit var slamController: SlamController
    private var isSlamEnabled = false
    private var isDrEnabled = true

    // DR State
    private var drX = 0.0
    private var drY = 0.0
    private var drTheta = 0.0

    // SLAM State
    private var slamX = 0.0
    private var slamY = 0.0
    private var slamTheta = 0.0

    // Timestamps
    private var lastUpdateTime = 0L

    // Landmarks (simulated for testing)
    private val simulatedLandmarks = listOf(
        Pair(2.0, 0.0),
        Pair(3.0, 1.0),
        Pair(1.0, 2.0),
        Pair(4.0, 3.0),
        Pair(-2.0, 1.0),
        Pair(-1.0, 3.0)
    )

    init {
        slamController = SlamController()
        Log.d("Fusion", "Fusion Controller initialized")
    }

    fun enableSlam(enable: Boolean) {
        isSlamEnabled = enable
        if (enable) {
            slamController.reset()
            Log.d("Fusion", "EKF-SLAM enabled")
        } else {
            Log.d("Fusion", "EKF-SLAM disabled")
        }
    }

    fun enableDr(enable: Boolean) {
        isDrEnabled = enable
        Log.d("Fusion", "DR ${if (enable) "enabled" else "disabled"}")
    }

    fun updateIMU(ax: Double, ay: Double, az: Double,
                  gx: Double, gy: Double, gz: Double,
                  dt: Double): FusionResult {

        val currentTime = System.currentTimeMillis()

        // Dead Reckoning update (simplified)
        if (isDrEnabled && dt > 0) {
            // Simple dead reckoning with accelerometer integration
            val speed = sqrt(ax * ax + ay * ay) * dt * 0.5
            val dtheta = gz * dt

            drX += speed * cos(drTheta)
            drY += speed * sin(drTheta)
            drTheta += dtheta

            // Normalize angle
            drTheta = normalizeAngle(drTheta)
        }

        // Update DR in SLAM controller
        slamController.updateDRPose(drX, drY, drTheta)

        // EKF-SLAM prediction step
        var slamResult: SlamController.SlamResult? = null
        if (isSlamEnabled && dt > 0) {
            val speed = sqrt(ax * ax + ay * ay) * dt * 0.5
            val dtheta = gz * dt

            // EKF-SLAM prediction
            slamResult = slamController.predictionStep(speed, 0.0, dtheta)

            // Generate simulated measurements (in real app, this would come from camera/LiDAR)
            val measurements = generateMeasurements(slamResult.slamX, slamResult.slamY, slamResult.slamTheta)

            // EKF-SLAM update step
            slamResult = slamController.updateStep(measurements)

            slamX = slamResult.slamX
            slamY = slamResult.slamY
            slamTheta = slamResult.slamTheta
        }

        lastUpdateTime = currentTime

        return FusionResult(
            drX = drX,
            drY = drY,
            drTheta = drTheta,
            slamX = slamX,
            slamY = slamY,
            slamTheta = slamTheta,
            landmarks = slamResult?.landmarks ?: emptyList(),
            slamEnabled = isSlamEnabled,
            drEnabled = isDrEnabled
        )
    }

    private fun generateMeasurements(robotX: Double, robotY: Double, robotTheta: Double):
            List<Triple<Double, Double, Int>> {
        val measurements = mutableListOf<Triple<Double, Double, Int>>()

        for ((index, landmark) in simulatedLandmarks.withIndex()) {
            val (lmX, lmY) = landmark

            // Calculate range and bearing
            val dx = lmX - robotX
            val dy = lmY - robotY
            val range = sqrt(dx * dx + dy * dy)
            val bearing = normalizeAngle(atan2(dy, dx) - robotTheta)

            // Add noise (simulating sensor noise)
            val rangeNoise = 0.05  // 5cm noise
            val bearingNoise = 0.01 // ~0.57 degree noise

            val noisyRange = range + (Math.random() - 0.5) * rangeNoise
            val noisyBearing = bearing + (Math.random() - 0.5) * bearingNoise

            // Only add if within sensor range (5 meters)
            if (range < 5.0) {
                measurements.add(Triple(noisyRange, noisyBearing, index))
            }
        }

        return measurements
    }

    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle
        while (normalized > PI) normalized -= 2 * PI
        while (normalized <= -PI) normalized += 2 * PI
        return normalized
    }

    fun reset() {
        drX = 0.0
        drY = 0.0
        drTheta = 0.0
        slamX = 0.0
        slamY = 0.0
        slamTheta = 0.0
        slamController.reset()
        lastUpdateTime = System.currentTimeMillis()
        Log.d("Fusion", "All states reset")
    }

    fun getLastUpdateTime(): Long = lastUpdateTime

    data class FusionResult(
        val drX: Double,
        val drY: Double,
        val drTheta: Double,
        val slamX: Double,
        val slamY: Double,
        val slamTheta: Double,
        val landmarks: List<Pair<Double, Double>>,
        val slamEnabled: Boolean,
        val drEnabled: Boolean
    )
}