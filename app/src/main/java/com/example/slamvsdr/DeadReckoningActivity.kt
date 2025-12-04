package com.example.slamvsdr

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import kotlin.math.*

class DeadReckoningActivity : AppCompatActivity(), SensorEventListener, SensorController.SensorDataCallback {

    // Sensor related
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // Controllers
    private lateinit var sensorController: SensorController
    private lateinit var fusionController: FusionController
    private lateinit var plotController: PlotController
    private lateinit var plotView: PathCanvasView

    // UI Elements from your layout
    private lateinit var positionXText: TextView
    private lateinit var positionYText: TextView
    private lateinit var positionZText: TextView
    private lateinit var velocityText: TextView
    private lateinit var resetButton: Button
    private lateinit var backButton: Button

    // SLAM UI elements
    private lateinit var slamToggleButton: Button
    private lateinit var drToggleButton: Button
    private lateinit var slamPositionText: TextView
    private lateinit var errorText: TextView
    private lateinit var landmarksText: TextView
    private lateinit var modeText: TextView

    private var isSlamComparisonMode = false
    private var isSlamEnabled = false
    private var isDrEnabled = true

    // Timing
    private var lastUpdateTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dead_reckoning)

        // Check which mode we're in
        isSlamComparisonMode = intent.getStringExtra("mode") == "slam_comparison"

        // Initialize UI elements
        initializeUI()

        // Initialize controllers
        fusionController = FusionController()
        plotController = PlotController()
        sensorController = SensorController(getSystemService(SENSOR_SERVICE) as SensorManager)

        // Setup plot view
        plotView = findViewById(R.id.path_canvas)
        plotView.setPlotController(plotController)
        plotView.setFusionController(fusionController)

        // Initialize sensor controller
        sensorController.initialize(fusionController, plotController)
        sensorController.setCallback(this)

        // Initialize sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Setup mode
        setupMode()

        // Setup button listeners
        setupButtonListeners()

        // Add SLAM UI elements programmatically WITH DIRECT CLICK LISTENERS
        addSLAMUIElements()

        // Start sensor updates
        lastUpdateTime = System.currentTimeMillis()

        // Test: Add a debug button
        addDebugTestButton()
    }

    private fun initializeUI() {
        positionXText = findViewById(R.id.position_x_text)
        positionYText = findViewById(R.id.position_y_text)
        positionZText = findViewById(R.id.position_z_text)
        velocityText = findViewById(R.id.velocity_text)
        resetButton = findViewById(R.id.reset_button)
        backButton = findViewById(R.id.back_button)
    }

    private fun setupMode() {
        if (isSlamComparisonMode) {
            // SLAM vs DR Comparison Mode
            isDrEnabled = true
            isSlamEnabled = true
            sensorController.enableDr(true)
            sensorController.enableSlam(true)
            plotController.showDr(true)
            plotController.showSlam(true)
            Log.d("SLAM-MODE", "Comparison mode: Both enabled")
        } else {
            // Dead Reckoning Only Mode
            isDrEnabled = true
            isSlamEnabled = false
            sensorController.enableDr(true)
            sensorController.enableSlam(false)
            plotController.showDr(true)
            plotController.showSlam(false)
            Log.d("SLAM-MODE", "DR only mode: SLAM disabled")
        }
    }

    private fun addSLAMUIElements() {
        // Find position card
        val positionCardContainer = findViewById<LinearLayout>(R.id.position_card_container)
        val positionCard = positionCardContainer ?:
        (findViewById<CardView>(R.id.position_card)?.getChildAt(0) as? LinearLayout)

        positionCard?.let {
            // Add mode indicator
            modeText = TextView(this).apply {
                text = if (isSlamComparisonMode) "Mode: SLAM vs DR Comparison" else "Mode: Dead Reckoning"
                textSize = 14f
                setTextColor(resources.getColor(android.R.color.holo_blue_dark))
                setPadding(0, 8, 0, 8)
            }
            it.addView(modeText, 1) // Add after the title

            // Add SLAM position text
            slamPositionText = TextView(this).apply {
                text = "SLAM: (0.00, 0.00)"
                textSize = 14f
                setTextColor(resources.getColor(android.R.color.holo_red_dark))
                setPadding(0, 4, 0, 4)
                visibility = if (isSlamEnabled) View.VISIBLE else View.GONE
            }
            it.addView(slamPositionText)

            // Add error text
            errorText = TextView(this).apply {
                text = "DR-SLAM Error: 0.00 m"
                textSize = 14f
                setTextColor(resources.getColor(android.R.color.holo_green_dark))
                setPadding(0, 4, 0, 4)
                visibility = if (isSlamEnabled) View.VISIBLE else View.GONE
            }
            it.addView(errorText)

            // Add landmarks text
            landmarksText = TextView(this).apply {
                text = "Landmarks: 0"
                textSize = 14f
                setTextColor(resources.getColor(android.R.color.holo_purple))
                setPadding(0, 4, 0, 4)
                visibility = if (isSlamEnabled) View.VISIBLE else View.GONE
            }
            it.addView(landmarksText)

            // Add SLAM control buttons WITH DIRECT CLICK LISTENERS
            val buttonContainer = findViewById<LinearLayout>(R.id.button_container)
                ?: LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 16
                    }
                    gravity = android.view.Gravity.CENTER

                    // Add to the main layout
                    val mainLayout = findViewById<LinearLayout>(R.id.main_layout)
                    mainLayout?.addView(this, mainLayout.childCount - 1)
                }

            // Add SLAM toggle button WITH CLICK LISTENER
            slamToggleButton = Button(this).apply {
                text = if (isSlamEnabled) "Disable SLAM" else "Enable SLAM"
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    weight = 1f
                    marginEnd = 8
                }
                setBackgroundColor(resources.getColor(
                    if (isSlamEnabled) android.R.color.holo_red_dark
                    else android.R.color.holo_red_light
                ))
                setTextColor(resources.getColor(android.R.color.white))
                textSize = 14f

                // DIRECT CLICK LISTENER - MOST IMPORTANT FIX!
                setOnClickListener {
                    Log.d("SLAM-BUTTON", "Clicked! Current state: $isSlamEnabled")

                    isSlamEnabled = !isSlamEnabled
                    sensorController.enableSlam(isSlamEnabled)

                    // Update button text and color
                    text = if (isSlamEnabled) "Disable SLAM" else "Enable SLAM"
                    setBackgroundColor(resources.getColor(
                        if (isSlamEnabled) android.R.color.holo_red_dark
                        else android.R.color.holo_red_light
                    ))

                    // Update UI visibility
                    if (::slamPositionText.isInitialized) {
                        slamPositionText.visibility = if (isSlamEnabled) View.VISIBLE else View.GONE
                        errorText.visibility = if (isSlamEnabled) View.VISIBLE else View.GONE
                        landmarksText.visibility = if (isSlamEnabled) View.VISIBLE else View.GONE
                    }

                    plotController.showSlam(isSlamEnabled)
                    plotView.invalidate()

                    // Show feedback
                    Toast.makeText(
                        this@DeadReckoningActivity,
                        "SLAM ${if (isSlamEnabled) "enabled" else "disabled"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            buttonContainer.addView(slamToggleButton)

            // Add DR toggle button WITH CLICK LISTENER
            drToggleButton = Button(this).apply {
                text = if (isDrEnabled) "Disable DR" else "Enable DR"
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    weight = 1f
                    marginStart = 8
                }
                setBackgroundColor(resources.getColor(
                    if (isDrEnabled) android.R.color.holo_blue_dark
                    else android.R.color.holo_blue_light
                ))
                setTextColor(resources.getColor(android.R.color.white))
                textSize = 14f

                // DIRECT CLICK LISTENER
                setOnClickListener {
                    Log.d("DR-BUTTON", "Clicked! Current state: $isDrEnabled")

                    isDrEnabled = !isDrEnabled
                    sensorController.enableDr(isDrEnabled)

                    // Update button text and color
                    text = if (isDrEnabled) "Disable DR" else "Enable DR"
                    setBackgroundColor(resources.getColor(
                        if (isDrEnabled) android.R.color.holo_blue_dark
                        else android.R.color.holo_blue_light
                    ))

                    plotController.showDr(isDrEnabled)
                    plotView.invalidate()

                    // Show feedback
                    Toast.makeText(
                        this@DeadReckoningActivity,
                        "DR ${if (isDrEnabled) "enabled" else "disabled"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            buttonContainer.addView(drToggleButton)

            Log.d("SLAM-UI", "Buttons added to container: ${buttonContainer.childCount}")
        } ?: run {
            Log.e("SLAM-UI", "Failed to find position card container!")
        }
    }

    private fun setupButtonListeners() {
        resetButton.setOnClickListener {
            // Reset all controllers
            sensorController.reset()
            plotController.reset()
            plotView.invalidate()

            // Reset UI
            positionXText.text = "X: 0.00 m"
            positionYText.text = "Y: 0.00 m"
            positionZText.text = "Z: 0.00 m"
            velocityText.text = "Velocity: 0.00 m/s"

            if (::slamPositionText.isInitialized) {
                slamPositionText.text = "SLAM: (0.00, 0.00)"
                errorText.text = "DR-SLAM Error: 0.00 m"
                landmarksText.text = "Landmarks: 0"
            }

            // Reset button states
            isSlamEnabled = isSlamComparisonMode
            isDrEnabled = true

            if (::slamToggleButton.isInitialized) {
                slamToggleButton.text = if (isSlamEnabled) "Disable SLAM" else "Enable SLAM"
                slamToggleButton.setBackgroundColor(
                    resources.getColor(
                        if (isSlamEnabled) android.R.color.holo_red_dark
                        else android.R.color.holo_red_light
                    )
                )
            }

            if (::drToggleButton.isInitialized) {
                drToggleButton.text = if (isDrEnabled) "Disable DR" else "Enable DR"
                drToggleButton.setBackgroundColor(
                    resources.getColor(
                        if (isDrEnabled) android.R.color.holo_blue_dark
                        else android.R.color.holo_blue_light
                    )
                )
            }
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    // DEBUG FUNCTION: Add a test button to verify clicks work
    private fun addDebugTestButton() {
        handler.postDelayed({
            val testButton = Button(this).apply {
                text = "DEBUG: Test Click"
                setBackgroundColor(android.graphics.Color.YELLOW)
                setTextColor(android.graphics.Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    topMargin = 10
                }

                setOnClickListener {
                    Toast.makeText(
                        this@DeadReckoningActivity,
                        "Debug button works!\nSLAM: $isSlamEnabled, DR: $isDrEnabled",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // Add to button container if it exists
            val buttonContainer = findViewById<LinearLayout>(R.id.button_container)
            buttonContainer?.addView(testButton)

        }, 1000) // 1 second delay
    }

    override fun onResume() {
        super.onResume()
        // Register sensor listeners
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorController.startSensors()
        Log.d("SLAM-ACTIVITY", "Activity resumed")
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        sensorController.stopSensors()
        Log.d("SLAM-ACTIVITY", "Activity paused")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    val currentTime = System.currentTimeMillis()
                    val dt = (currentTime - lastUpdateTime) / 1000.0

                    if (dt > 0) {
                        // Process through fusion controller
                        val result = fusionController.updateIMU(
                            event.values[0].toDouble(),
                            event.values[1].toDouble(),
                            event.values[2].toDouble(),
                            0.0, 0.0, 0.0, // Gyro values would come from gyroscope
                            dt
                        )

                        // Update plot
                        plotController.updatePose(result)

                        // Update UI on main thread
                        handler.post {
                            updateUI(result)
                        }

                        lastUpdateTime = currentTime
                    }
                }
                // Handle gyroscope separately if needed
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for now
    }

    // From SensorController callback
    override fun onSensorDataUpdated(result: FusionController.FusionResult) {
        handler.post {
            updateUI(result)
        }
    }

    override fun onPlotUpdateNeeded() {
        handler.post {
            plotView.invalidate()
        }
    }

    private fun updateUI(result: FusionController.FusionResult) {
        // Update DR position
        positionXText.text = String.format("X: %.2f m", result.drX)
        positionYText.text = String.format("Y: %.2f m", result.drY)
        positionZText.text = String.format("Z: %.1f°", Math.toDegrees(result.drTheta))

        // Calculate velocity (simplified)
        val velocity = sqrt(result.drX * result.drX + result.drY * result.drY)
        velocityText.text = String.format("Velocity: %.2f m/s", velocity)

        // Update SLAM data if enabled
        if (result.slamEnabled && ::slamPositionText.isInitialized) {
            slamPositionText.text = String.format("SLAM: (%.2f, %.2f)", result.slamX, result.slamY)

            // Calculate error between DR and SLAM
            val errorX = result.slamX - result.drX
            val errorY = result.slamY - result.drY
            val distanceError = sqrt(errorX * errorX + errorY * errorY)
            errorText.text = String.format("DR-SLAM Error: %.2f m", distanceError)

            // Update landmarks
            landmarksText.text = String.format("Landmarks: %d", result.landmarks.size)
        }
    }
}