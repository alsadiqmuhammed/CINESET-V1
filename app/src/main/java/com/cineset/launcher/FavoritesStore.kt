package com.cineset.launcher

import android.content.Context

/** Persists the ordered list of favorite app components. */
class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun get(): List<String> =
        prefs.getString(KEY, null)?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()

    fun contains(key: String) = key in get()

    fun toggle(key: String) {
        val current = get().toMutableList()
        if (!current.remove(key)) current.add(key)
        prefs.edit().putString(KEY, current.joinToString(SEPARATOR)).apply()
    }

    private companion object {
        const val KEY = "favorites"
        const val SEPARATOR = "|"
    }
}
