package com.alessandro.falco.data

data class InsightMetric(val label: String, val value: String, val detail: String = "")
data class LibraryInsight(
    val reviewProgress: Float,
    val aiCoverage: Float,
    val tagCoverage: Float,
    val metadataCoverage: Float,
    val metrics: List<InsightMetric>,
    val genres: List<Pair<String, Int>>,
    val formats: List<Pair<String, Int>>,
    val ratings: List<Pair<String, Int>>,
    val advice: List<String>
)

fun libraryInsight(tracks: List<TrackEntity>, duplicateCount: Int): LibraryInsight {
    if (tracks.isEmpty()) return LibraryInsight(0f, 0f, 0f, 0f, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    val total = tracks.size.toFloat()
    val reviewed = tracks.count { it.workStatus != "DA_VALUTARE" }
    val analyzed = tracks.count { it.maestCache.isNotBlank() }
    val tagged = tracks.count { it.customTags.isNotBlank() || it.genre.isNotBlank() }
    val metadataFields = tracks.sumOf { listOf(it.title, it.artist, it.album, it.genre).count(String::isNotBlank) }
    val unknownArtist = tracks.count { it.artist.isBlank() || it.artist.equals("Unknown", true) || it.artist.equals("Artista sconosciuto", true) }
    val missingAlbum = tracks.count { it.album.isBlank() }
    val missingGenre = tracks.count { it.genre.isBlank() }
    val noRating = tracks.count { it.rating == 0 && it.workStatus != "DA_VALUTARE" }
    val favorites = tracks.count { it.favorite }
    val ready = tracks.count { it.workStatus == "PRONTO" || it.workStatus == "KEEP" }
    val advice = buildList {
        if (missingGenre > 0) add("Completa il genere di $missingGenre brani: è il requisito più utile per le smart playlist.")
        if (noRating > 0) add("$noRating brani già lavorati non hanno rating: una passata rapida renderà migliori i filtri DJ.")
        if (analyzed < tracks.size) add("Pre-analizza ${tracks.size - analyzed} brani per avere suggerimenti AI e waveform già pronti.")
        if (unknownArtist > 0 || missingAlbum > 0) add("Ripulisci i metadati: $unknownArtist artisti sconosciuti e $missingAlbum album mancanti.")
        if (duplicateCount > 0) add("Controlla $duplicateCount file probabili duplicati: occupano spazio e sporcano le playlist.")
        if (reviewed == tracks.size && noRating == 0 && missingGenre == 0) add("Catalogo in ottima forma: ora conviene bilanciare energia, situazioni e voce.")
    }
    fun distribution(value: (TrackEntity) -> String) = tracks.groupingBy { value(it).ifBlank { "Sconosciuto" } }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key to it.value }
    return LibraryInsight(
        reviewed / total, analyzed / total, tagged / total, metadataFields / (total * 4f),
        listOf(
            InsightMetric("Pronti", ready.toString(), "${(ready / total * 100).toInt()}% della libreria"),
            InsightMetric("Preferiti", favorites.toString(), "${(favorites / total * 100).toInt()}% della libreria"),
            InsightMetric("Senza rating", noRating.toString(), "tra i brani lavorati"),
            InsightMetric("Duplicati", duplicateCount.toString(), "file da controllare")
        ),
        distribution { it.genre }, distribution { it.format.uppercase() },
        tracks.groupingBy { if (it.rating == 0) "Senza rating" else "★${it.rating}" }.eachCount().entries.sortedByDescending { it.key }.map { it.key to it.value },
        advice
    )
}
