package com.alessandro.falco.desktop

data class Track(
    val id: Long = 0,
    val source: String,
    val uri: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val year: Int? = null,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val modifiedAt: Long = 0,
    val format: String = "",
    val rating: Int = 0,
    val status: String = "DA_VALUTARE",
    val tags: String = ""
)

data class WebDavConfig(val url: String = "", val username: String = "", val password: String = "") {
    val ready get() = url.isNotBlank() && username.isNotBlank()
}

