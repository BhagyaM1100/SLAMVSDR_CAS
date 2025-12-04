package com.example.slamvsdr

import android.util.Log
import kotlin.math.*

class SlamController {

    // EKF-SLAM State
    private var stateVector = mutableListOf<Double>() // [x, y, θ, lm1_x, lm1_y, ...]
    private var covarianceMatrix = mutableListOf<MutableList<Double>>()
    private var landmarks = mutableListOf<Landmark>()
    private var landmarkIDs = mutableListOf<Int>()

    // Parameters
    private val STATE_SIZE = 3
    private val LANDMARK_SIZE = 2
    private var currentStateSize = STATE_SIZE
    private val MOTION_NOISE = 0.01
    private val MEASUREMENT_NOISE = 0.1

    // For DR comparison
    private var drX = 0.0
    private var drY = 0.0
    private var drTheta = 0.0

    // Results container
    data class SlamResult(
        val slamX: Double,
        val slamY: Double,
        val slamTheta: Double,
        val drX: Double,
        val drY: Double,
        val drTheta: Double,
        val landmarks: List<Pair<Double, Double>>,
        val covariance: Double
    )

    data class Landmark(
        val id: Int,
        val x: Double,
        val y: Double,
        var observedCount: Int = 0
    )

    init {
        // Initialize at origin
        stateVector.add(0.0) // x
        stateVector.add(0.0) // y
        stateVector.add(0.0) // theta

        // Initialize covariance
        covarianceMatrix = MutableList(STATE_SIZE) { MutableList(STATE_SIZE) { 0.0 } }
        for (i in 0 until STATE_SIZE) {
            covarianceMatrix[i][i] = 0.01
        }

        Log.d("EKF-SLAM", "EKF-SLAM initialized")
    }

    // Update DR for comparison
    fun updateDRPose(x: Double, y: Double, theta: Double) {
        drX = x
        drY = y
        drTheta = theta
    }

    // EKF Prediction Step
    fun predictionStep(dx: Double, dy: Double, dtheta: Double): SlamResult {
        val theta = stateVector[2]

        // Predict pose
        val predX = stateVector[0] + dx * cos(theta) - dy * sin(theta)
        val predY = stateVector[1] + dx * sin(theta) + dy * cos(theta)
        val predTheta = normalizeAngle(theta + dtheta)

        // Update state
        stateVector[0] = predX
        stateVector[1] = predY
        stateVector[2] = predTheta

        // Build Jacobian F (3x3 for robot pose)
        val F = buildJacobianF(dx, dy, theta)

        // Update covariance for robot pose only
        val P_robot = covarianceMatrix.subMatrix(0, 2, 0, 2)
        val F_P = matrixMultiply(F, P_robot)
        val F_P_Ft = matrixMultiply(F_P, transposeMatrix(F))

        // Add motion noise
        val Q = buildMotionNoise(dx, dy, dtheta)
        val newP_robot = matrixAdd(F_P_Ft, Q)

        // Update covariance matrix
        for (i in 0..2) {
            for (j in 0..2) {
                covarianceMatrix[i][j] = newP_robot[i][j]
            }
        }

        // Add process noise to landmark covariances
        for (i in 3 until currentStateSize) {
            covarianceMatrix[i][i] *= 1.01 // Slight increase in uncertainty
        }

        return getCurrentResult()
    }

    // EKF Update Step
    fun updateStep(measurements: List<Triple<Double, Double, Int>>): SlamResult {
        if (measurements.isEmpty()) return getCurrentResult()

        for ((range, bearing, signature) in measurements) {
            val lmIndex = landmarkIDs.indexOf(signature)

            if (lmIndex == -1) {
                // Initialize new landmark
                initializeLandmark(range, bearing, signature)
            } else {
                // Update existing landmark
                updateLandmark(range, bearing, lmIndex)
            }
        }

        return getCurrentResult()
    }

    private fun initializeLandmark(range: Double, bearing: Double, signature: Int) {
        val robotX = stateVector[0]
        val robotY = stateVector[1]
        val robotTheta = stateVector[2]

        // Global landmark position
        val lmX = robotX + range * cos(robotTheta + bearing)
        val lmY = robotY + range * sin(robotTheta + bearing)

        // Add to state
        stateVector.add(lmX)
        stateVector.add(lmY)
        landmarkIDs.add(signature)
        landmarks.add(Landmark(signature, lmX, lmY, 1))

        // Extend covariance
        extendCovariance()

        Log.d("EKF-SLAM", "New landmark $signature at ($lmX, $lmY)")
    }

    private fun updateLandmark(range: Double, bearing: Double, lmIndex: Int) {
        val robotX = stateVector[0]
        val robotY = stateVector[1]
        val robotTheta = stateVector[2]

        val lmX = stateVector[STATE_SIZE + lmIndex * 2]
        val lmY = stateVector[STATE_SIZE + lmIndex * 2 + 1]

        // Expected measurement
        val dx = lmX - robotX
        val dy = lmY - robotY
        val q = dx * dx + dy * dy
        val expectedRange = sqrt(q)
        val expectedBearing = normalizeAngle(atan2(dy, dx) - robotTheta)

        // Innovation
        val zRange = range - expectedRange
        val zBearing = normalizeAngle(bearing - expectedBearing)

        // Jacobian H
        val H = buildMeasurementJacobian(dx, dy, q, lmIndex)

        // Innovation covariance S = H*P*H' + R
        val P = covarianceMatrix
        val Ht = transposeMatrix(H)
        val PHt = matrixMultiply(P, Ht)
        val HPHt = matrixMultiply(H, PHt)
        val R = buildMeasurementNoise()
        val S = matrixAdd(HPHt, R)

        // Kalman gain K = P*H'*S^-1
        val S_inv = if (S.size == 2 && S[0].size == 2) {
            invert2x2(S)
        } else {
            S // Fallback
        }
        val K = matrixMultiply(PHt, S_inv)

        // Update state
        for (i in stateVector.indices) {
            stateVector[i] += K[i][0] * zRange + K[i][1] * zBearing
        }

        // Update covariance P = (I - K*H)*P
        val KH = matrixMultiply(K, H)
        val I = identityMatrix(currentStateSize)
        val I_KH = matrixSubtract(I, KH)
        covarianceMatrix = matrixMultiply(I_KH, P).toMutableList()
        // Keep covariance symmetric
        makeSymmetric()

        // Update landmark observation count
        landmarks[lmIndex].observedCount++
    }

    private fun buildJacobianF(dx: Double, dy: Double, theta: Double): List<List<Double>> {
        return listOf(
            listOf(1.0, 0.0, -dx * sin(theta) - dy * cos(theta)),
            listOf(0.0, 1.0, dx * cos(theta) - dy * sin(theta)),
            listOf(0.0, 0.0, 1.0)
        )
    }

    private fun buildMeasurementJacobian(dx: Double, dy: Double, q: Double, lmIndex: Int): List<List<Double>> {
        val H = MutableList(2) { MutableList(currentStateSize) { 0.0 } }
        val sqrtQ = sqrt(q)

        // Robot pose derivatives
        H[0][0] = -dx / sqrtQ
        H[0][1] = -dy / sqrtQ
        H[0][2] = 0.0

        H[1][0] = dy / q
        H[1][1] = -dx / q
        H[1][2] = -1.0

        // Landmark derivatives
        val lmStart = STATE_SIZE + lmIndex * 2
        H[0][lmStart] = dx / sqrtQ
        H[0][lmStart + 1] = dy / sqrtQ

        H[1][lmStart] = -dy / q
        H[1][lmStart + 1] = dx / q

        return H
    }

    private fun buildMotionNoise(dx: Double, dy: Double, dtheta: Double): List<List<Double>> {
        val noiseScale = 0.01
        return listOf(
            listOf(noiseScale * abs(dx), 0.0, 0.0),
            listOf(0.0, noiseScale * abs(dy), 0.0),
            listOf(0.0, 0.0, noiseScale * abs(dtheta))
        )
    }

    private fun buildMeasurementNoise(): List<List<Double>> {
        return listOf(
            listOf(MEASUREMENT_NOISE, 0.0),
            listOf(0.0, MEASUREMENT_NOISE * 0.5)
        )
    }

    private fun extendCovariance() {
        val oldSize = covarianceMatrix.size
        currentStateSize += LANDMARK_SIZE

        // Create new matrix
        val newCov = MutableList(currentStateSize) { MutableList(currentStateSize) { 0.0 } }

        // Copy old values
        for (i in 0 until oldSize) {
            for (j in 0 until oldSize) {
                newCov[i][j] = covarianceMatrix[i][j]
            }
        }

        // Initialize new landmark covariance (large uncertainty)
        for (i in oldSize until currentStateSize) {
            newCov[i][i] = 100.0
        }

        covarianceMatrix = newCov
    }

    private fun getCurrentResult(): SlamResult {
        val slamLandmarks = landmarks.map { Pair(it.x, it.y) }
        val avgCov = if (covarianceMatrix.isNotEmpty()) {
            covarianceMatrix.flatten().average()
        } else {
            0.0
        }

        return SlamResult(
            slamX = stateVector[0],
            slamY = stateVector[1],
            slamTheta = stateVector[2],
            drX = drX,
            drY = drY,
            drTheta = drTheta,
            landmarks = slamLandmarks,
            covariance = avgCov
        )
    }

    // Matrix operations
    private fun matrixMultiply(A: List<List<Double>>, B: List<List<Double>>): List<MutableList<Double>> {
        val rowsA = A.size
        val colsA = A[0].size
        val colsB = B[0].size

        val result = MutableList(rowsA) { MutableList(colsB) { 0.0 } }

        for (i in 0 until rowsA) {
            for (j in 0 until colsB) {
                for (k in 0 until colsA) {
                    result[i][j] += A[i][k] * B[k][j]
                }
            }
        }

        return result
    }

    private fun matrixAdd(A: List<List<Double>>, B: List<List<Double>>): List<MutableList<Double>> {
        val rows = A.size
        val cols = A[0].size
        val result = MutableList(rows) { MutableList(cols) { 0.0 } }

        for (i in 0 until rows) {
            for (j in 0 until cols) {
                result[i][j] = A[i][j] + B[i][j]
            }
        }

        return result
    }

    private fun matrixSubtract(A: List<List<Double>>, B: List<List<Double>>): List<MutableList<Double>> {
        val rows = A.size
        val cols = A[0].size
        val result = MutableList(rows) { MutableList(cols) { 0.0 } }

        for (i in 0 until rows) {
            for (j in 0 until cols) {
                result[i][j] = A[i][j] - B[i][j]
            }
        }

        return result
    }

    private fun transposeMatrix(A: List<List<Double>>): List<MutableList<Double>> {
        val rows = A.size
        val cols = A[0].size
        val result = MutableList(cols) { MutableList(rows) { 0.0 } }

        for (i in 0 until rows) {
            for (j in 0 until cols) {
                result[j][i] = A[i][j]
            }
        }

        return result
    }

    private fun identityMatrix(size: Int): List<MutableList<Double>> {
        val I = MutableList(size) { MutableList(size) { 0.0 } }
        for (i in 0 until size) {
            I[i][i] = 1.0
        }
        return I
    }

    private fun invert2x2(A: List<List<Double>>): List<MutableList<Double>> {
        val a = A[0][0]
        val b = A[0][1]
        val c = A[1][0]
        val d = A[1][1]

        val det = a * d - b * c
        if (abs(det) < 1e-10) return identityMatrix(2)

        val invDet = 1.0 / det
        return listOf(
            mutableListOf(d * invDet, -b * invDet),
            mutableListOf(-c * invDet, a * invDet)
        ).toMutableList()
    }

    private fun List<List<Double>>.subMatrix(rowStart: Int, rowEnd: Int, colStart: Int, colEnd: Int): List<MutableList<Double>> {
        val result = MutableList(rowEnd - rowStart + 1) { MutableList(colEnd - colStart + 1) { 0.0 } }
        for (i in rowStart..rowEnd) {
            for (j in colStart..colEnd) {
                result[i - rowStart][j - colStart] = this[i][j]
            }
        }
        return result
    }

    private fun makeSymmetric() {
        val n = covarianceMatrix.size
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val avg = (covarianceMatrix[i][j] + covarianceMatrix[j][i]) / 2.0
                covarianceMatrix[i][j] = avg
                covarianceMatrix[j][i] = avg
            }
        }
    }

    private fun normalizeAngle(angle: Double): Double {
        var normalized = angle
        while (normalized > PI) normalized -= 2 * PI
        while (normalized <= -PI) normalized += 2 * PI
        return normalized
    }

    fun reset() {
        stateVector.clear()
        covarianceMatrix.clear()
        landmarks.clear()
        landmarkIDs.clear()

        stateVector.add(0.0)
        stateVector.add(0.0)
        stateVector.add(0.0)

        covarianceMatrix = MutableList(STATE_SIZE) { MutableList(STATE_SIZE) { 0.0 } }
        for (i in 0 until STATE_SIZE) {
            covarianceMatrix[i][i] = 0.01
        }

        currentStateSize = STATE_SIZE
        drX = 0.0
        drY = 0.0
        drTheta = 0.0

        Log.d("EKF-SLAM", "Reset to initial state")
    }

    fun getLandmarkCount(): Int = landmarks.size
    fun getStateSize(): Int = currentStateSize
}