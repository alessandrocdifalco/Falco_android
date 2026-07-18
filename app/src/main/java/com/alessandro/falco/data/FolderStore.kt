package com.alessandro.falco.data

import android.content.Context
import android.net.Uri

class FolderStore(context: Context) {
    private val prefs = context.getSharedPreferences("falco_folders", Context.MODE_PRIVATE)
    fun all(): Set<String> = prefs.getStringSet("uris", emptySet())?.toSet().orEmpty()
    fun add(uri: Uri) = prefs.edit().putStringSet("uris", all() + uri.toString()).apply()
    fun remove(uri: String) = prefs.edit().putStringSet("uris", all() - uri).apply()
}
