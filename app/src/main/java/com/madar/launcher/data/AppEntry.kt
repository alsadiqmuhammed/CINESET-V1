package com.madar.launcher.data

import android.content.ComponentName
import android.content.pm.ApplicationInfo

/** How an app relates to attention; drives mode visibility and the intent gate. */
enum class AppKind(val title: String) {
    WORK("عمل وإنتاجية"),
    COMMS("تواصل"),
    TOOLS("أدوات"),
    CALM("قراءة وصوتيات"),
    SOCIAL("سوشيال ميديا"),
    ENTERTAINMENT("ترفيه"),
    ;

    /** Apps that steal attention go through the intent gate before opening. */
    val gated: Boolean get() = this == SOCIAL || this == ENTERTAINMENT

    fun visibleIn(mode: Mode): Boolean = when (mode) {
        Mode.FOCUS -> this == WORK || this == COMMS || this == TOOLS
        Mode.FLOW -> true
        Mode.SANCTUARY -> this == CALM || this == COMMS || this == TOOLS
    }

    companion object {
        fun classify(packageName: String, category: Int): AppKind {
            KNOWN[packageName]?.let { return it }
            return when (category) {
                ApplicationInfo.CATEGORY_GAME, ApplicationInfo.CATEGORY_VIDEO -> ENTERTAINMENT
                ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_NEWS -> SOCIAL
                ApplicationInfo.CATEGORY_AUDIO -> CALM
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> WORK
                else -> TOOLS
            }
        }

        private val KNOWN = mapOf(
            "com.instagram.android" to SOCIAL,
            "com.facebook.katana" to SOCIAL,
            "com.facebook.lite" to SOCIAL,
            "com.twitter.android" to SOCIAL,
            "com.snapchat.android" to SOCIAL,
            "com.zhiliaoapp.musically" to SOCIAL,
            "com.ss.android.ugc.trill" to SOCIAL,
            "com.reddit.frontpage" to SOCIAL,
            "com.instagram.barcelona" to SOCIAL,
            "com.google.android.youtube" to ENTERTAINMENT,
            "com.netflix.mediaclient" to ENTERTAINMENT,
            "com.shahid.mobile" to ENTERTAINMENT,
            "net.mbc.shahid" to ENTERTAINMENT,
            "com.pubg.imobile" to ENTERTAINMENT,
            "com.tencent.ig" to ENTERTAINMENT,
            "com.whatsapp" to COMMS,
            "com.whatsapp.w4b" to COMMS,
            "org.telegram.messenger" to COMMS,
            "org.thunderdog.challegram" to COMMS,
            "com.viber.voip" to COMMS,
            "com.google.android.gm" to COMMS,
            "com.google.android.apps.messaging" to COMMS,
            "com.google.android.dialer" to COMMS,
            "com.android.contacts" to COMMS,
            "com.android.mms" to COMMS,
            "com.spotify.music" to CALM,
            "com.anghami" to CALM,
            "com.google.android.apps.youtube.music" to CALM,
            "com.google.android.apps.books" to CALM,
            "com.amazon.kindle" to CALM,
            "com.audible.application" to CALM,
            "com.google.android.apps.podcasts" to CALM,
            "com.quran.labs.androidquran" to CALM,
            "com.notion.id" to WORK,
            "com.microsoft.office.outlook" to WORK,
            "com.microsoft.teams" to WORK,
            "com.slack" to WORK,
            "com.google.android.apps.docs" to WORK,
            "com.google.android.keep" to WORK,
            "com.google.android.calendar" to WORK,
            "com.anthropic.claude" to WORK,
        )
    }
}

data class AppEntry(
    val component: ComponentName,
    val label: String,
    val kind: AppKind,
    /** Label normalised for Arabic/Latin fuzzy matching. */
    val searchKey: String,
) {
    val key: String get() = component.flattenToString()
    val packageName: String get() = component.packageName
}
