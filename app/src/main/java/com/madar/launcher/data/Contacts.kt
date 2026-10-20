package com.madar.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import com.madar.launcher.command.normalize

data class ContactEntry(val name: String, val phone: String, val searchKey: String)

/** Phone contacts, loaded once on demand and cached for the command palette. */
class ContactsRepository(private val context: Context) {

    @Volatile private var cache: List<ContactEntry>? = null

    val hasPermission: Boolean
        get() = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun invalidate() {
        cache = null
    }

    fun search(query: String, limit: Int = 4): List<ContactEntry> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return all()
            .filter { it.searchKey.contains(q) }
            .sortedBy { if (it.searchKey.startsWith(q)) 0 else 1 }
            .take(limit)
    }

    private fun all(): List<ContactEntry> {
        cache?.let { return it }
        if (!hasPermission) return emptyList()
        val result = mutableListOf<ContactEntry>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val phone = c.getString(1) ?: c.getString(2) ?: continue
                result += ContactEntry(name, phone, normalize(name))
            }
        }
        return result.distinctBy { it.name to it.phone.filter(Char::isDigit).takeLast(9) }.also { cache = it }
    }
}
