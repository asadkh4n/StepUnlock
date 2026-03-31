package com.stepunlock.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class StepUnlockDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "StepUnlock activated", Toast.LENGTH_SHORT).show()
    }
    override fun onDisabled(context: Context, intent: Intent) {
        context.getSharedPreferences("stepunlock", Context.MODE_PRIVATE).edit()
            .putBoolean("locked_by_app", false)
            .putLong("screen_start_time", 0L)
            .apply()
        Toast.makeText(context, "StepUnlock deactivated", Toast.LENGTH_SHORT).show()
    }
}
