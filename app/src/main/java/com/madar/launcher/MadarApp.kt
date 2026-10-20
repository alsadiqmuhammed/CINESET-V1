package com.madar.launcher

import android.app.Application
import com.madar.launcher.data.AppsRepository
import com.madar.launcher.data.ContactsRepository
import com.madar.launcher.data.DigestRepository
import com.madar.launcher.data.RecentMedia
import com.madar.launcher.data.SettingsStore
import com.madar.launcher.data.StacksRepository
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

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        apps = AppsRepository(this, settings, scope)
        stacks = StacksRepository(this)
        digest = DigestRepository(this, settings)
        contacts = ContactsRepository(this)
        media = RecentMedia(this)
        Reminders.createChannel(this)
    }
}
