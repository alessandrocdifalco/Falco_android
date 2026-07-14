package com.alessandro.falco.data

data class TaxonomyItem(val id: String, val label: String)
object FalcoTaxonomy {
    val statuses = listOf(TaxonomyItem("KEEP", "Keep"), TaxonomyItem("MAYBE", "Maybe"), TaxonomyItem("REJECT", "Reject"))
    val genres = listOf("HOUSE" to "House", "DISCO_NU_DISCO" to "Disco / Nu-Disco", "DEEP_LOUNGE" to "Deep / Lounge", "AFRO_LATIN" to "Afro / Latin", "TECH_CLUB" to "Tech / Club", "REVIVAL" to "Revival", "POP_PARTY" to "Pop / Party", "TRASH_UTILE" to "Trash Utile", "ALTRO" to "Altro").map { TaxonomyItem(it.first, it.second) }
    val energy = listOf("Sottofondo", "Muove la testa", "Groove presente", "Si balla", "Bomba / peak").mapIndexed { i, v -> TaxonomyItem("E${i + 1}", v) }
    val usage = listOf("APERITIVO" to "Aperitivo", "CENA" to "Cena", "WARMUP" to "Warm-up", "DOPOCENA" to "Dopocena", "BALLO" to "Ballo", "PEAK" to "Peak", "CLOSING" to "Closing", "EVENTO_PRIVATO" to "Evento privato", "CL4UDJ" to "CL4UDJ", "ALEX" to "Alex").map { TaxonomyItem(it.first, it.second) }
    val voice = listOf("STRUMENTALE" to "Strumentale", "VOCAL_HOOK" to "Vocal Hook", "VOCAL_FULL" to "Vocal Full", "CHANT" to "Chant", "SPOKEN" to "Spoken").map { TaxonomyItem(it.first, it.second) }
    val tags = listOf("GROOVY","ELEGANTE","SOLARE","SCURO","SEXY","NOTTURNO","PIANO","SAX","FIATI","CHITARRA","BASSO_FORTE","DUB","PERCUSSIONI","CLASSIC_FEEL","FAMOUS_SAMPLE","FAMOUS_VOCAL","EDIT_UTILE","REMIX","INTRO_BUONA","BREAK_LUNGO","SAFE","BOMBA","TROPPO_VELOCE","DA_RIASCOLTARE","RADIO_FRIENDLY","STRUMENTALE","CANTATO").map { TaxonomyItem(it, it.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)) }
    const val maxTags = 8
}
