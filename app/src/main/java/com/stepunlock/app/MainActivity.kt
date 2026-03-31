package com.stepunlock.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("stepunlock", Context.MODE_PRIVATE)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, StepUnlockDeviceAdminReceiver::class.java)

        val stepInput    = findViewById<EditText>(R.id.stepInput)
        val timeInput    = findViewById<EditText>(R.id.timeInput)
        val saveButton   = findViewById<Button>(R.id.saveButton)
        val enableButton = findViewById<Button>(R.id.enableButton)

        stepInput.setText(prefs.getInt("step_goal", 20).toString())
        timeInput.setText(prefs.getInt("time_limit_minutes", 30).toString())

        saveButton.setOnClickListener {
            val steps   = stepInput.text.toString().toIntOrNull()?.coerceIn(1, 500) ?: 20
            val minutes = timeInput.text.toString().toIntOrNull()?.coerceIn(1, 480) ?: 30
            prefs.edit().putInt("step_goal", steps).putInt("time_limit_minutes", minutes).apply()
            Toast.makeText(this, "Saved — $steps steps, ${minutes}min limit", Toast.LENGTH_SHORT).show()
            if (dpm.isAdminActive(adminComponent)) startScreenTimeService()
        }

        enableButton.setOnClickListener {
            if (dpm.isAdminActive(adminComponent)) {
                dpm.removeActiveAdmin(adminComponent)
                stopService(Intent(this, ScreenTimeService::class.java))
                updateStatus()
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "StepUnlock needs Device Admin to lock your screen when your time limit is reached.")
                }
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        if (dpm.isAdminActive(adminComponent)) startScreenTimeService()
    }

    private fun updateStatus() {
        val isAdmin      = dpm.isAdminActive(adminComponent)
        val statusText   = findViewById<TextView>(R.id.statusText)
        val enableButton = findViewById<Button>(R.id.enableButton)
        if (isAdmin) {
            statusText.text = "Active — monitoring screen time"
            statusText.setTextColor(0xFF1D9E75.toInt())
            enableButton.text = "Disable Admin"
        } else {
            statusText.text = "Inactive — tap Enable Admin to start"
            statusText.setTextColor(0xFFE24B4A.toInt())
            enableButton.text = "Enable Admin"
        }
    }

    private fun startScreenTimeService() =
        startForegroundService(Intent(this, ScreenTimeService::class.java))
}
