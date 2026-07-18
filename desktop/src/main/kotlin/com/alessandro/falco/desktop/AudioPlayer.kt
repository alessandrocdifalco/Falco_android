package com.alessandro.falco.desktop

import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import java.nio.file.Files
import java.nio.file.Path

/** Audio-only LibVLC player. VLC and its codecs are bundled in the Windows installer. */
class AudioPlayer {
    private var factory: MediaPlayerFactory? = null
    private var player: MediaPlayer? = null

    @Synchronized private fun ensurePlayer(): MediaPlayer {
        player?.let { return it }
        val resources = System.getProperty("compose.application.resources.dir")
            ?: error("Cartella risorse del player non disponibile")
        val vlc = Path.of(resources, "vlc").toAbsolutePath()
        check(Files.exists(vlc.resolve("libvlc.dll"))) { "Motore VLC non incluso nell'installazione" }
        NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), vlc.toString())
        System.setProperty("VLC_PLUGIN_PATH", vlc.resolve("plugins").toString())
        factory = MediaPlayerFactory("--no-video", "--quiet")
        return factory!!.mediaPlayers().newMediaPlayer().also { player = it }
    }

    @Synchronized fun play(path: Path) {
        val media = ensurePlayer()
        media.controls().stop()
        check(media.media().play(path.toAbsolutePath().toString())) { "VLC non riesce ad aprire il file" }
    }

    @Synchronized fun pause() { player?.controls()?.pause() }
    @Synchronized fun stop() { player?.controls()?.stop() }
}
