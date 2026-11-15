package com.elsewhere.app.record

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** What the platform has let us do, behind a seam so the view model can be tested with a fake. */
interface PermissionGate {
    val sdk: Int
    fun granted(permission: String): Boolean
    fun ignoringBatteryOptimizations(): Boolean
}

class AndroidPermissionGate @Inject constructor(@ApplicationContext private val ctx: Context) : PermissionGate {
    override val sdk: Int get() = Build.VERSION.SDK_INT
    override fun granted(permission: String) = ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED
    override fun ignoringBatteryOptimizations() = ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) ?: true
}
