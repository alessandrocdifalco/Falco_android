package com.alessandro.falco

import com.alessandro.falco.data.TrackEntity
import com.alessandro.falco.ui.Filters
import com.alessandro.falco.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryAnalysisTest {
    private fun track(id: Long, title: String, artist: String, duration: Long, genre: String = "House") = TrackEntity(
        id, "content://$id", "folder", "$title.mp3", title, artist, "Album", genre, 2026,
        duration, "Music/$title.mp3", 1_000, 1, "MP3"
    )

    @Test fun duplicateDetectionNormalizesTextAndDuration() {
        val state = UiState(tracks = listOf(track(1, "Groove", "Alex", 180_000), track(2, " groove ", "ALEX", 180_900)))
        assertEquals(1, state.duplicates.size)
        assertEquals(2, state.duplicates.single().tracks.size)
    }

    @Test fun filtersCanBeCombined() {
        val state = UiState(tracks = listOf(track(1, "One", "Alex", 10), track(2, "Two", "Claudia", 10, "Disco")), filters = Filters(genre = "Disco"), query = "two")
        assertEquals(listOf(2L), state.visible.map { it.id })
    }
}
