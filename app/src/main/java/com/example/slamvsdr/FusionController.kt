package com.example.slamvsdr

import android.content.Context
import android.util.Log
import kotlin.math.*

class FusionController(context: Context? = null) {

    private lateinit var slamController: SlamController
    private lateinit var visualSlamController: VisualSlamController
    private var isSlamEnabled = false
    private var isVisualSlamEnabled = false
    private var isDrEnabled = true

    // DR State
    private var drX = 0.0
    private var drY = 0.0
    private var drTheta = 0.0

    // SLAM State
    private var slamX = 0.0
    private var slamY = 0.0
    private var slamTheta = 0.0

    // Visual SLAM State
    private var visualSlamX = 0.0
    private var visualSlamY = 0.0
    private var visualSlamZ = 0.0
    private var visualSlamYaw = 0.0

    // Fused State
    private var fusedX = 0.0
    private var fusedY = 0.0
    private var fusedTheta = 0.0

    // Timestamps
    private var lastUpdateTime = 0L


    init {
        slamController = SlamController()
        context?.let {
            visualSlamController = VisualSlamController(it)
            Log.d("Fusion", "Fusion Controller with Visual SLAM")
        } ?: run {
            Log.d("Fusion", "Fusion Controller without Visual SLAM")
        }
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

    fun enableVisualSlam(enable: Boolean) {
        isVisualSlamEnabled = enable
        if (enable) {
            visualSlamController.reset()
            Log.d("Fusion", "Visual SLAM enabled")
        } else {
            Log.d("Fusion", "Visual SLAM disabled")
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

        // Dead Reckoning update
        if (isDrEnabled && dt > 0) {
            val speed = sqrt(ax * ax + ay * ay) * dt * 0.5
            val dtheta = gz * dt

            drX += speed * cos(drTheta)
            drY += speed * sin(drTheta)
            drTheta += dtheta

            drTheta = normalizeAngle(drTheta)
        }

        // EKF-SLAM update
        if (isSlamEnabled && dt > 0) {
            val speed = sqrt(ax * ax + ay * ay) * dt * 0.5
            val dtheta = gz * dt

            slamController.updateDRPose(drX, drY, drTheta)
            var slamResult = slamController.predictionStep(speed, 0.0, dtheta)

            val measurements = generateMeasurements(slamResult.slamX, slamResult.slamY, slamResult.slamTheta)
            slamResult = slamController.updateStep(measurements)

            slamX = slamResult.slamX
            slamY = slamResult.slamY
            slamTheta = slamResult.slamTheta
        }

        // Get Visual SLAM data
        if (isVisualSlamEnabled) {
            val visualPose = visualSlamController.getCurrentPose()
            visualSlamX = visualPose[0]
            visualSlamY = visualPose[1]
            visualSlamZ = visualPose[2]
            visualSlamYaw = visualPose[3]
        }

        // Fuse all available estimates
        fuseEstimates()

        lastUpdateTime = currentTime

        return FusionResult(
            drX = drX,
            drY = drY,
            drTheta = drTheta,
            slamX = slamX,
            slamY = slamY,
            slamTheta = slamTheta,
            visualSlamX = visualSlamX,
            visualSlamY = visualSlamY,
            visualSlamZ = visualSlamZ,
            fusedX = fusedX,
            fusedY = fusedY,
            fusedTheta = fusedTheta,
            landmarks = if (isSlamEnabled) slamController.getLandmarks() else emptyList(),
            visualLandmarks = if (isVisualSlamEnabled) visualSlamController.getMapPoints().map {
                Pair(it.x, it.y)
            } else emptyList(),
            slamEnabled = isSlamEnabled,
            visualSlamEnabled = isVisualSlamEnabled,
            drEnabled = isDrEnabled
        )
    }

    private fun fuseEstimates() {
        var weightSum = 0.0
        var tempX = 0.0
        var tempY = 0.0
        var tempTheta = 0.0

        if (isDrEnabled) {
            val weight = 0.3
            tempX += drX * weight
            tempY += drY * weight
            tempTheta += drTheta * weight
            weightSum += weight
        }

        if (isSlamEnabled) {
            val weight = 0.4
            tempX += slamX * weight
            tempY += slamY * weight
            tempTheta += slamTheta * weight
            weightSum += weight
        }

        if (isVisualSlamEnabled) {
            val weight = 0.5
            tempX += visualSlamX * weight
            tempY += visualSlamY * weight
            tempTheta += visualSlamYaw * weight
            weightSum += weight
        }

        if (weightSum > 0) {
            fusedX = tempX / weightSum
            fusedY = tempY / weightSum
            fusedTheta = normalizeAngle(tempTheta / weightSum)
        }
    }

    fun updateVisualSlam(pose: DoubleArray, landmarks: List<Pair<Double, Double>>) {
        if (isVisualSlamEnabled) {
            visualSlamX = pose[0]
            visualSlamY = pose[1]
            visualSlamZ = pose[2]
            visualSlamYaw = pose[3]
        }
    }

    private fun generateMeasurements(robotX: Double, robotY: Double, robotTheta: Double):
            List<Triple<Double, Double, Int>> {
        val measurements = mutableListOf<Triple<Double, Double, Int>>()
        val simulatedLandmarks = listOf(
            Pair(2.0, 0.0),
            Pair(3.0, 1.0),
            Pair(1.0, 2.0),
            Pair(4.0, 3.0),
            Pair(-2.0, 1.0),
            Pair(-1.0, 3.0)
        )

        for ((index, landmark) in simulatedLandmarks.withIndex()) {
            val (lmX, lmY) = landmark
            val dx = lmX - robotX
            val dy = lmY - robotY
            val range = sqrt(dx * dx + dy * dy)
            val bearing = normalizeAngle(atan2(dy, dx) - robotTheta)

            val rangeNoise = 0.05
            val bearingNoise = 0.01

            val noisyRange = range + (Math.random() - 0.5) * rangeNoise
            val noisyBearing = bearing + (Math.random() - 0.5) * bearingNoise

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
        visualSlamX = 0.0
        visualSlamY = 0.0
        visualSlamZ = 0.0
        visualSlamYaw = 0.0
        fusedX = 0.0
        fusedY = 0.0
        fusedTheta = 0.0
        slamController.reset()
        visualSlamController.reset()
        lastUpdateTime = System.currentTimeMillis()
        Log.d("Fusion", "All states reset")
    }

    data class FusionResult(
        val drX: Double,
        val drY: Double,
        val drTheta: Double,
        val slamX: Double,
        val slamY: Double,
        val slamTheta: Double,
        val visualSlamX: Double,
        val visualSlamY: Double,
        val visualSlamZ: Double,
        val fusedX: Double,
        val fusedY: Double,
        val fusedTheta: Double,
        val landmarks: List<Pair<Double, Double>>,
        val visualLandmarks: List<Pair<Double, Double>>,
        val slamEnabled: Boolean,
        val visualSlamEnabled: Boolean,
        val drEnabled: Boolean
    )
}