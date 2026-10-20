package com.madar.launcher.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

enum class MediaKind(val title: String) {
    PHOTO("صورة"), VIDEO("فيديو"), SCREENSHOT("سكرين شوت"), PDF("ملف PDF"), AUDIO("مقطع صوتي"), ANY("ملف"),
}

data class RecentFile(val uri: Uri, val name: String, val mime: String)

/** Finds the newest file of a kind in shared storage, for commands like "ارسل آخر صورة". */
class RecentMedia(private val context: Context) {

    fun latest(kind: MediaKind): RecentFile? {
        val collection = when (kind) {
            MediaKind.PHOTO, MediaKind.SCREENSHOT -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            MediaKind.PDF, MediaKind.ANY -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        val (selection, args) = when (kind) {
            MediaKind.SCREENSHOT -> "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" to arrayOf("%Screenshot%")
            MediaKind.PDF -> "${MediaStore.MediaColumns.MIME_TYPE} = ?" to arrayOf("application/pdf")
            MediaKind.ANY -> "${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL AND ${MediaStore.MediaColumns.SIZE} > 0" to emptyArray()
            else -> null to null
        }
        return runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE),
                selection, args,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                RecentFile(
                    uri = ContentUris.withAppendedId(collection, c.getLong(0)),
                    name = c.getString(1) ?: kind.title,
                    mime = c.getString(2) ?: "*/*",
                )
            }
        }.getOrNull()
    }
}
