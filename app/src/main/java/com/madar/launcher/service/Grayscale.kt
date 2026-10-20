package com.madar.launcher.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings as SystemSettings
import com.madar.launcher.MadarApp

/**
 * True system grayscale through Android's colour-correction (daltonizer) setting.
 * Writing it needs WRITE_SECURE_SETTINGS, granted once over adb:
 *   adb shell pm grant com.madar.launcher android.permission.WRITE_SECURE_SETTINGS
 * Madar never overrides a colour correction the user turned on themselves, and remembers what it
 * changed so it can always restore it — even after a crash.
 */
object Grayscale {

    const val ADB_GRANT = "adb shell pm grant com.madar.launcher android.permission.WRITE_SECURE_SETTINGS"

    private const val ENABLED = "accessibility_display_daltonizer_enabled"
    private const val MODE = "accessibility_display_daltonizer"
    private const val MONOCHROMACY = 0

    fun canControl(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Returns true if the screen is now grayscale because of Madar. */
    fun hold(context: Context): Boolean {
        if (!canControl(context)) return false
        val app = context.applicationContext as MadarApp
        if (app.settings.value.grayscaleHeld) return true
        val cr = context.contentResolver
        if (SystemSettings.Secure.getInt(cr, ENABLED, 0) == 1) return false // the user's own setting; leave it
        val previous = SystemSettings.Secure.getInt(cr, MODE, -1)
        return runCatching {
            app.settings.edit { it.copy(grayscaleHeld = true, previousDaltonizerMode = previous) }
            SystemSettings.Secure.putInt(cr, MODE, MONOCHROMACY)
            SystemSettings.Secure.putInt(cr, ENABLED, 1)
        }.getOrElse {
            app.settings.edit { it.copy(grayscaleHeld = false) }
            false
        }
    }

    fun release(context: Context) {
        val app = context.applicationContext as MadarApp
        val s = app.settings.value
        if (!s.grayscaleHeld || !canControl(context)) return
        val cr = context.contentResolver
        runCatching {
            SystemSettings.Secure.putInt(cr, ENABLED, 0)
            if (s.previousDaltonizerMode >= 0) SystemSettings.Secure.putInt(cr, MODE, s.previousDaltonizerMode)
        }
        app.settings.edit { it.copy(grayscaleHeld = false) }
    }
}
