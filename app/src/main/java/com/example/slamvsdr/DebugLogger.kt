package com.example.slamvsdr

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class DebugLogger(private val context: Context) {

    companion object {
        private const val LOG_FILE_NAME = "visual_slam_debug.log"
    }

    private val logFile: File
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    init {
        logFile = File(context.getExternalFilesDir(null), LOG_FILE_NAME)
        Log.d("DebugLogger", "Log file: ${logFile.absolutePath}")

        // Clear previous log on initialization
        clearLog()
        log("=== Visual SLAM Debug Log Started ===")
    }

    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val logMessage = "[$timestamp] $message\n"

        Log.d("SLAM-Debug", message)

        try {
            FileOutputStream(logFile, true).use { fos ->
                fos.write(logMessage.toByteArray())
            }
        } catch (e: Exception) {
            Log.e("DebugLogger", "Failed to write log: ${e.message}")
        }
    }

    fun logSlamData(x: Double, y: Double, landmarks: Int, trackingQuality: Float) {
        log(String.format("SLAM: pos=(%.2f, %.2f) landmarks=%d quality=%.2f",
            x, y, landmarks, trackingQuality))
    }

    fun logCameraEvent(event: String) {
        log("CAMERA: $event")
    }

    fun logError(error: String) {
        log("ERROR: $error")
    }

    fun getLogContent(): String {
        return try {
            if (logFile.exists()) {
                logFile.readText()
            } else {
                "No log file found"
            }
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }

    fun clearLog() {
        try {
            if (logFile.exists()) {
                logFile.delete()
            }
        } catch (e: Exception) {
            Log.e("DebugLogger", "Error clearing log: ${e.message}")
        }
    }
}