package com.madar.launcher.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Process
import androidx.core.content.ContextCompat
import com.madar.launcher.command.normalize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Collator

/** Every launchable activity on the device, kept current as packages change. */
class AppsRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = reload()
            },
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        reload()
    }

    fun reload() {
        scope.launch(Dispatchers.Default) {
            val overrides = settings.value.kindOverrides
            val collator = Collator.getInstance()
            _apps.value = launcherApps.getActivityList(null, Process.myUserHandle())
                .asSequence()
                .filter { it.componentName.packageName != context.packageName }
                .map {
                    val pkg = it.componentName.packageName
                    val label = it.label.toString().trim()
                    AppEntry(
                        component = it.componentName,
                        label = label,
                        kind = overrides[pkg] ?: AppKind.classify(pkg, it.applicationInfo.category),
                        searchKey = normalize(label),
                    )
                }
                .distinctBy { it.key }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
                .toList()
        }
    }

    @androidx.annotation.VisibleForTesting
    fun replaceForTest(list: List<AppEntry>) {
        _apps.value = list
    }

    fun find(component: ComponentName): AppEntry? = _apps.value.firstOrNull { it.component == component }

    fun findPackage(packageName: String): AppEntry? = _apps.value.firstOrNull { it.packageName == packageName }

    fun setKind(app: AppEntry, kind: AppKind) {
        settings.edit { it.copy(kindOverrides = it.kindOverrides + (app.packageName to kind)) }
        reload()
    }
}
