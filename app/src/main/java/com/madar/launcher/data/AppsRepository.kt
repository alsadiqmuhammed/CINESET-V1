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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator

/** Every launchable activity on the device, kept current as packages change. */
class AppsRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    private val _all = MutableStateFlow<List<AppEntry>>(emptyList())

    /** Every launchable app, vault included. */
    val all: StateFlow<List<AppEntry>> = _all.asStateFlow()

    /** What the rest of the launcher may show: everything outside the ghost space. */
    val apps: StateFlow<List<AppEntry>> = combine(_all, settings.state.map { it.hiddenPackages }.distinctUntilChanged()) { list, hidden ->
        if (hidden.isEmpty()) list else list.filterNot { it.packageName in hidden }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val hidden: StateFlow<List<AppEntry>> = combine(_all, settings.state.map { it.hiddenPackages }.distinctUntilChanged()) { list, hidden ->
        list.filter { it.packageName in hidden }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

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
            _all.value = launcherApps.getActivityList(null, Process.myUserHandle())
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
        _all.value = list
    }

    fun find(component: ComponentName): AppEntry? = _all.value.firstOrNull { it.component == component }

    fun findPackage(packageName: String): AppEntry? = _all.value.firstOrNull { it.packageName == packageName }

    fun isHidden(packageName: String) = packageName in settings.value.hiddenPackages

    fun setHidden(app: AppEntry, hidden: Boolean) = settings.edit {
        it.copy(
            hiddenPackages = if (hidden) it.hiddenPackages + app.packageName else it.hiddenPackages - app.packageName,
            pinned = if (hidden) it.pinned - app.key else it.pinned,
        )
    }

    fun setKind(app: AppEntry, kind: AppKind) {
        settings.edit { it.copy(kindOverrides = it.kindOverrides + (app.packageName to kind)) }
        reload()
    }
}
