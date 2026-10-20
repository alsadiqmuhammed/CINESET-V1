package com.cineset.launcher

import android.content.ComponentName
import android.graphics.drawable.Drawable

data class AppEntry(
    val component: ComponentName,
    val label: String,
    val icon: Drawable,
) {
    val key: String get() = component.flattenToString()
}
