package com.madar.launcher

import android.app.Application
import com.madar.launcher.data.AgendaRepository
import com.madar.launcher.data.AppsRepository
import com.madar.launcher.data.ContactsRepository
import com.madar.launcher.data.DigestRepository
import com.madar.launcher.data.FrictionRepository
import com.madar.launcher.data.Mode
import com.madar.launcher.data.PlacesRepository
import com.madar.launcher.data.RecentMedia
import com.madar.launcher.data.SettingsStore
import com.madar.launcher.data.ShelfRepository
import com.madar.launcher.data.StacksRepository
import com.madar.launcher.service.Grayscale
import com.madar.launcher.service.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

class MadarApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settings: SettingsStore private set
    lateinit var apps: AppsRepository private set
    lateinit var stacks: StacksRepository private set
    lateinit var digest: DigestRepository private set
    lateinit var contacts: ContactsRepository private set
    lateinit var media: RecentMedia private set
    lateinit var shelf: ShelfRepository private set
    lateinit var friction: FrictionRepository private set
    lateinit var agenda: AgendaRepository private set
    lateinit var places: PlacesRepository private set
    lateinit var ai: com.madar.launcher.ai.LocalAi private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        apps = AppsRepository(this, settings, scope)
        stacks = StacksRepository(this)
        digest = DigestRepository(this, settings)
        contacts = ContactsRepository(this)
        media = RecentMedia(this)
        shelf = ShelfRepository(this)
        friction = FrictionRepository(this)
        agenda = AgendaRepository(this)
        places = PlacesRepository(this).also { it.start() }
        ai = com.madar.launcher.ai.LocalAi(this, scope)
        // If the process died while the screen was held in grayscale, give the colour back.
        Grayscale.release(this)
        Reminders.createChannel(this)
    }

    /** The one place the effective mode is decided: override, then place, then schedule. */
    fun currentMode(nowMillis: Long = System.currentTimeMillis()): Mode = settings.value.modeAt(nowMillis, places.ssid.value)
}
