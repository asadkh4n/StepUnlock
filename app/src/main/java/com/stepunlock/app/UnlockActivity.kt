package com.stepunlock.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class UnlockActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private var stepsTaken = 0
    private var stepGoal   = 20
    private var unlocked   = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContentView(R.layout.activity_unlock)

        val prefs = getSharedPreferences("stepunlock", Context.MODE_PRIVATE)
        stepGoal  = prefs.getInt("step_goal", 20)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor    = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

        if (stepSensor == null) {
            // Fallback for devices without a step sensor — tap to unlock
            findViewById<TextView>(R.id.instructionText).text =
                "No step sensor found on this device.\nTap the number to unlock."
            findViewById<TextView>(R.id.stepsText).setOnClickListener { unlock() }
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        stepSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
    }

    override fun onPause() {
        super.onPause()
        if (!unlocked) sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (unlocked || event?.sensor?.type != Sensor.TYPE_STEP_DETECTOR) return
        stepsTaken++
        updateUI()
        if (stepsTaken % 5 == 0) vibrate(40)
        if (stepsTaken >= stepGoal) unlock()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateUI() {
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val stepsText   = findViewById<TextView>(R.id.stepsText)
        val goalText    = findViewById<TextView>(R.id.goalText)
        val instruction = findViewById<TextView>(R.id.instructionText)

        progressBar.max      = stepGoal
        progressBar.progress = stepsTaken
        stepsText.text       = stepsTaken.toString()
        goalText.text        = "/ $stepGoal steps"

        val remaining = stepGoal - stepsTaken
        instruction.text = when {
            remaining <= 0 -> "Unlocking..."
            remaining == 1 -> "1 more step!"
            else           -> "$remaining steps to go — keep walking"
        }
    }

    private fun unlock() {
        if (unlocked) return
        unlocked = true
        sensorManager.unregisterListener(this)
        vibrate(150)
        getSharedPreferences("stepunlock", Context.MODE_PRIVATE).edit()
            .putBoolean("locked_by_app", false)
            .putLong("screen_start_time", System.currentTimeMillis())
            .apply()
        finish()
    }

    private fun vibrate(ms: Long) {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* blocked — user must walk */ }
}
