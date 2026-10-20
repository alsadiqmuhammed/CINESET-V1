package com.cineset.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var repository: AppRepository
    private lateinit var favoritesStore: FavoritesStore

    private lateinit var search: EditText
    private lateinit var battery: TextView
    private lateinit var appsList: RecyclerView
    private lateinit var favoritesSection: View
    private lateinit var favoritesEmpty: View
    private lateinit var appsAdapter: AppAdapter
    private lateinit var favoritesAdapter: AppAdapter

    private val loader = Executors.newSingleThreadExecutor()
    private var allApps: List<AppEntry> = emptyList()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reloadApps()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level < 0) return
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val percent = level * 100 / scale
            battery.text = getString(if (charging) R.string.battery_charging_format else R.string.battery_format, percent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        repository = AppRepository(this)
        favoritesStore = FavoritesStore(this)

        search = findViewById(R.id.search)
        battery = findViewById(R.id.battery)
        appsList = findViewById(R.id.apps)
        favoritesSection = findViewById(R.id.favoritesSection)
        favoritesEmpty = findViewById(R.id.favoritesEmpty)
        findViewById<View>(R.id.favoritesTitle).findViewById<TextView>(R.id.title).setText(R.string.favorites)
        findViewById<View>(R.id.appsTitle).findViewById<TextView>(R.id.title).setText(R.string.all_apps)

        applyInsets()
        setUpLists()
        setUpSearch()
        reloadApps()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        unregisterReceiver(batteryReceiver)
        super.onStop()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        loader.shutdownNow()
        super.onDestroy()
    }

    /** Pressing Home while already on the launcher resets it to its initial state. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        resetView()
    }

    @Deprecated("Launchers swallow Back; it only clears an active search.")
    override fun onBackPressed() {
        resetView()
    }

    private fun resetView() {
        search.text.clear()
        search.clearFocus()
        hideKeyboard()
        appsList.scrollToPosition(0)
    }

    private fun applyInsets() {
        val root = findViewById<View>(R.id.root)
        val baseStart = root.paddingStart
        val baseEnd = root.paddingEnd
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPaddingRelative(baseStart + bars.left, bars.top, baseEnd + bars.right, 0)
            appsList.setPadding(0, 0, 0, bars.bottom)
            insets
        }
    }

    private fun setUpLists() {
        val density = resources.displayMetrics.density
        val columns = (resources.configuration.screenWidthDp / 90).coerceIn(4, 8)

        appsAdapter = AppAdapter(onClick = ::launch, onLongClick = ::showAppMenu)
        appsList.layoutManager = GridLayoutManager(this, columns)
        appsList.adapter = appsAdapter

        favoritesAdapter = AppAdapter(itemWidthPx = (78 * density).toInt(), onClick = ::launch, onLongClick = ::showAppMenu)
        findViewById<RecyclerView>(R.id.favorites).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = favoritesAdapter
        }
    }

    private fun setUpSearch() {
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = render()
        })
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                appsAdapter.currentList.firstOrNull()?.let { launch(it, search) }
                true
            } else {
                false
            }
        }
    }

    private fun reloadApps() {
        loader.execute {
            val apps = repository.loadApps()
            runOnUiThread {
                allApps = apps
                render()
            }
        }
    }

    private fun render() {
        val query = search.text.toString().trim()
        val searching = query.isNotEmpty()

        appsAdapter.submitList(
            if (searching) allApps.filter { it.label.contains(query, ignoreCase = true) } else allApps,
        )

        val byKey = allApps.associateBy { it.key }
        val favorites = favoritesStore.get().mapNotNull { byKey[it] }
        favoritesAdapter.submitList(favorites)
        favoritesEmpty.visibility = if (favorites.isEmpty()) View.VISIBLE else View.GONE
        favoritesSection.visibility = if (searching) View.GONE else View.VISIBLE
    }

    private fun launch(app: AppEntry, from: View) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val options = ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.width, from.height)
        try {
            startActivity(intent, options.toBundle())
            hideKeyboard()
        } catch (e: ActivityNotFoundException) {
            reloadApps()
        } catch (e: SecurityException) {
            Toast.makeText(this, app.label, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppMenu(app: AppEntry, anchor: View) {
        val isFavorite = favoritesStore.contains(app.key)
        PopupMenu(this, anchor, Gravity.CENTER).apply {
            menu.add(0, MENU_FAVORITE, 0, if (isFavorite) R.string.remove_favorite else R.string.add_favorite)
            menu.add(0, MENU_INFO, 1, R.string.app_info)
            menu.add(0, MENU_UNINSTALL, 2, R.string.uninstall)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_FAVORITE -> {
                        favoritesStore.toggle(app.key)
                        render()
                    }
                    MENU_INFO -> startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(app)),
                    )
                    MENU_UNINSTALL -> startActivity(
                        @Suppress("DEPRECATION")
                        Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri(app)),
                    )
                }
                true
            }
        }.show()
    }

    private fun packageUri(app: AppEntry) = Uri.fromParts("package", app.component.packageName, null)

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(search.windowToken, 0)
    }

    private companion object {
        const val MENU_FAVORITE = 1
        const val MENU_INFO = 2
        const val MENU_UNINSTALL = 3
    }
}
