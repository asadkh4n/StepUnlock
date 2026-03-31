package com.stepunlock.app

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, StepUnlockDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            context.startForegroundService(Intent(context, ScreenTimeService::class.java))
        }
    }
}
