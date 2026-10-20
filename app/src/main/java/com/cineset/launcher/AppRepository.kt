package com.cineset.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.text.Collator

/** Loads every launchable activity on the device, sorted by label. */
class AppRepository(private val context: Context) {

    fun loadApps(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance()
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                val info = it.activityInfo
                AppEntry(
                    component = ComponentName(info.packageName, info.name),
                    label = it.loadLabel(pm).toString().trim(),
                    icon = it.loadIcon(pm),
                )
            }
            .distinctBy { it.key }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
            .toList()
    }
}
