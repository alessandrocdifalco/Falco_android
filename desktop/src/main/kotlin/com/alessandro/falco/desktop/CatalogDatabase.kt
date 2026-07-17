package com.alessandro.falco.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class CatalogDatabase {
    private val connection: Connection

    init {
        val dir = Path.of(System.getProperty("user.home"), ".falco")
        Files.createDirectories(dir)
        connection = DriverManager.getConnection("jdbc:sqlite:${dir.resolve("falco.db")}")
        connection.createStatement().use { it.executeUpdate("""
            CREATE TABLE IF NOT EXISTS tracks(
              id INTEGER PRIMARY KEY AUTOINCREMENT, source TEXT NOT NULL, uri TEXT NOT NULL UNIQUE,
              title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, genre TEXT NOT NULL,
              year INTEGER, duration_ms INTEGER NOT NULL, size_bytes INTEGER NOT NULL,
              modified_at INTEGER NOT NULL, format TEXT NOT NULL, rating INTEGER NOT NULL DEFAULT 0,
              status TEXT NOT NULL DEFAULT 'DA_VALUTARE', tags TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent()) }
    }

    @Synchronized fun upsert(track: Track) {
        connection.prepareStatement("""
            INSERT INTO tracks(source,uri,title,artist,album,genre,year,duration_ms,size_bytes,modified_at,format,rating,status,tags)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(uri) DO UPDATE SET source=excluded.source,title=excluded.title,artist=excluded.artist,
            album=excluded.album,genre=excluded.genre,year=excluded.year,duration_ms=excluded.duration_ms,
            size_bytes=excluded.size_bytes,modified_at=excluded.modified_at,format=excluded.format
        """.trimIndent()).use { ps ->
            listOf(track.source, track.uri, track.title, track.artist, track.album, track.genre).forEachIndexed { i, v -> ps.setString(i + 1, v as String) }
            if (track.year == null) ps.setNull(7, java.sql.Types.INTEGER) else ps.setInt(7, track.year)
            ps.setLong(8, track.durationMs); ps.setLong(9, track.sizeBytes); ps.setLong(10, track.modifiedAt)
            ps.setString(11, track.format); ps.setInt(12, track.rating); ps.setString(13, track.status); ps.setString(14, track.tags)
            ps.executeUpdate()
        }
    }

    @Synchronized fun all(query: String = ""): List<Track> {
        val sql = if (query.isBlank()) "SELECT * FROM tracks ORDER BY title COLLATE NOCASE" else
            "SELECT * FROM tracks WHERE title LIKE ? OR artist LIKE ? OR album LIKE ? OR genre LIKE ? OR tags LIKE ? ORDER BY title COLLATE NOCASE"
        connection.prepareStatement(sql).use { ps ->
            if (query.isNotBlank()) repeat(5) { ps.setString(it + 1, "%$query%") }
            ps.executeQuery().use { rs ->
                val out = mutableListOf<Track>()
                while (rs.next()) out += Track(rs.getLong("id"), rs.getString("source"), rs.getString("uri"), rs.getString("title"), rs.getString("artist"), rs.getString("album"), rs.getString("genre"), rs.getInt("year").takeIf { !rs.wasNull() }, rs.getLong("duration_ms"), rs.getLong("size_bytes"), rs.getLong("modified_at"), rs.getString("format"), rs.getInt("rating"), rs.getString("status"), rs.getString("tags"))
                return out
            }
        }
    }

    @Synchronized fun setStatus(id: Long, status: String) = connection.prepareStatement("UPDATE tracks SET status=? WHERE id=?").use { it.setString(1, status); it.setLong(2, id); it.executeUpdate() }
}

