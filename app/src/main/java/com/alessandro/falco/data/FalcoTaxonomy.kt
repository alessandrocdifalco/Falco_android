package com.alessandro.falco.data

data class TaxonomyItem(val id: String, val label: String)
object FalcoTaxonomy {
    val statuses = listOf(TaxonomyItem("KEEP", "Keep"), TaxonomyItem("MAYBE", "Maybe"), TaxonomyItem("REJECT", "Reject"))
    val genres = listOf("HOUSE" to "House", "DISCO_NU_DISCO" to "Disco / Nu-Disco", "DEEP_LOUNGE" to "Deep / Lounge", "AFRO_LATIN" to "Afro / Latin", "TECH_CLUB" to "Tech / Club", "REVIVAL" to "Revival", "POP_PARTY" to "Pop / Party", "TRASH_UTILE" to "Trash Utile", "ALTRO" to "Altro").map { TaxonomyItem(it.first, it.second) }
    val subgenres = mapOf(
        "HOUSE" to listOf("HOUSE","CLUB_HOUSE","CLASSIC_HOUSE","PIANO_HOUSE","VOCAL_HOUSE","SOULFUL_HOUSE","FUNKY_HOUSE","JACKIN_HOUSE","GARAGE_HOUSE","DISCO_HOUSE"),
        "DISCO_NU_DISCO" to listOf("DISCO_NU_DISCO","CLASSIC_DISCO","NU_DISCO","DISCO_HOUSE","ITALO_DISCO","BOOGIE","FUNK_DISCO","DISCO_EDIT","DISCO_REWORK","FILTER_DISCO"),
        "DEEP_LOUNGE" to listOf("DEEP_LOUNGE","DEEP_HOUSE","LOUNGE_HOUSE","CHILL_HOUSE","JAZZY_HOUSE","ORGANIC_HOUSE","BALEARIC","DOWNTEMPO","AMBIENT_HOUSE","DUB_HOUSE"),
        "AFRO_LATIN" to listOf("AFRO_LATIN","AFRO_HOUSE","AFRO_TECH","LATIN_HOUSE","TRIBAL_HOUSE","PERCUSSIVE_HOUSE","ORGANIC_AFRO","AFRO_DEEP","LATIN_DISCO","AMAPIANO_ISH"),
        "TECH_CLUB" to listOf("TECH_CLUB","TECH_HOUSE","AFRO_TECH","MINIMAL","DEEP_TECH","MELODIC_HOUSE","PROGRESSIVE_HOUSE","INDIE_DANCE","PEAK_TIME_HOUSE","TECHNO"),
        "REVIVAL" to listOf("REVIVAL","SEVENTIES","EIGHTIES","NINETIES","TWO_THOUSANDS","ORIGINAL","EDIT","REMIX","REWORK","DANCE_CLASSIC"),
        "POP_PARTY" to listOf("POP_PARTY","POP_DANCE","COMMERCIAL_DANCE","RADIO_HIT","ITALIAN_POP","LATIN_POP","REGGAETON","DANCEHALL","PARTY_CLASSIC","SINGALONG"),
        "TRASH_UTILE" to listOf("TRASH_UTILE","BALLI_DI_GRUPPO","WEDDING_TRASH","KARAOKE_MOMENT","GUILTY_PLEASURE","MEME_HIT","FESTA_ITALIANA","NINETIES_TRASH","LATIN_TRASH","EMERGENCY_BOMB"),
        "ALTRO" to listOf("ALTRO","HIP_HOP","RNB","FUNK","SOUL","JAZZ_FUNK","ROCK_INDIE","ELECTRONIC","WORLD","EXPERIMENTAL")
    ).mapValues { (_, values) -> values.map { TaxonomyItem(it, it.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)) } }
    val energy = listOf("Sottofondo", "Muove la testa", "Groove presente", "Si balla", "Bomba / peak").mapIndexed { i, v -> TaxonomyItem("E${i + 1}", v) }
    val usage = listOf("APERITIVO" to "Aperitivo", "CENA" to "Cena", "WARMUP" to "Warm-up", "DOPOCENA" to "Dopocena", "BALLO" to "Ballo", "PEAK" to "Peak", "CLOSING" to "Closing", "EVENTO_PRIVATO" to "Evento privato", "CL4UDJ" to "CL4UDJ", "ALEX" to "Alex").map { TaxonomyItem(it.first, it.second) }
    val voice = listOf("STRUMENTALE" to "Strumentale", "VOCAL_HOOK" to "Vocal Hook", "VOCAL_FULL" to "Vocal Full", "CHANT" to "Chant", "SPOKEN" to "Spoken").map { TaxonomyItem(it.first, it.second) }
    val tags = listOf("GROOVY","ELEGANTE","SOLARE","SCURO","SEXY","NOTTURNO","PIANO","SAX","FIATI","CHITARRA","BASSO_FORTE","DUB","PERCUSSIONI","CLASSIC_FEEL","FAMOUS_SAMPLE","FAMOUS_VOCAL","EDIT_UTILE","REMIX","INTRO_BUONA","BREAK_LUNGO","SAFE","BOMBA","TROPPO_VELOCE","DA_RIASCOLTARE","RADIO_FRIENDLY","STRUMENTALE","CANTATO").map { TaxonomyItem(it, it.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)) }
    const val maxTags = 8
}
