package com.alessandro.falco.desktop

import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

class AudioPlayer {
    private var player: MediaPlayer? = null

    init { if (started.compareAndSet(false, true)) runCatching { Platform.startup {} } }

    fun play(path: Path) = Platform.runLater {
        player?.stop(); player?.dispose()
        player = MediaPlayer(Media(path.toUri().toString())).apply { play() }
    }

    fun pause() = Platform.runLater { player?.pause() }
    fun stop() = Platform.runLater { player?.stop() }

    companion object { private val started = AtomicBoolean(false) }
}

