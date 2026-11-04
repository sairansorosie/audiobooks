package com.lightphone.audiobooks.screens

import android.net.Uri
import com.lightphone.audiobooks.PlayerSession
import com.lightphone.audiobooks.library.Audiobook
import com.thelightphone.sdk.audio.LightAudio
import com.thelightphone.sdk.audio.LightAudioPlayback
import com.thelightphone.sdk.audio.LightAudioPlayer
import com.thelightphone.sdk.audio.LightAudioUsage
import com.thelightphone.sdk.audio.LightPlayerSpec
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.File

/** Reset only a queue that the audiobook tool knows belongs to the selected book. */
internal suspend fun pauseAndRewindLoadedQueue(player: LightAudioPlayer, book: Audiobook) {
    if (!withTimeout(4_000L) { player.awaitReady() } ||
        player.currentMediaItemIndex.value < 0) {
        throw IOException("Audiobook queue is not available for reset")
    }
    val expected = book.parts.map { it.playbackReference }
        .filter(String::isNotBlank)
        .ifEmpty { listOf(book.playbackReference) }
        .map { Uri.fromFile(File(it)).toString() }
    if (player.queue.value.map { it.id } != expected) {
        throw IOException("Loaded audio queue does not belong to the selected audiobook")
    }
    player.pause()
    player.seekToDefaultPosition(0)
    player.seekTo(0L)
    // Confirm the actual detached-session state before reporting success.
    withTimeout(4_000L) {
        combine(player.isPlaying, player.currentMediaItemIndex, player.positionMs) {
            playing, index, position -> !playing && index == 0 && position <= 250L
        }.first { it }
    }
    PlayerSession.isPlaying = false
}

/** Library may own a screen but not the live detached playback handle. */
internal suspend fun pauseAndRewindFromLibrary(audio: LightAudio, book: Audiobook) {
    if (PlayerSession.loadedBookId != book.id) return
    val handle = audio.newPlayer(
        LightPlayerSpec(usage = LightAudioUsage.Speech, playback = LightAudioPlayback.Detached),
    )
    try {
        withTimeout(4_000L) { pauseAndRewindLoadedQueue(handle, book) }
    } finally {
        handle.release()
    }
}
