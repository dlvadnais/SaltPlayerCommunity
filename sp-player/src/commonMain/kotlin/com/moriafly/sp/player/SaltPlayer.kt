/*
 * Salt Player Community
 * Copyright (C) 2025 Moriafly and Contributions
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

@file:Suppress("unused")

package com.moriafly.sp.player

import com.moriafly.sp.player.internal.InternalCommand
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * # SaltPlayer
 *
 * An abstract base class for a media player, designed with a command-driven architecture
 * using Kotlin Coroutines and Channels. It manages the player's state and lifecycle.
 */
@UnstableSpPlayerApi
abstract class SaltPlayer(
    commandDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CommandPlayer(
        commandDispatcher = commandDispatcher
    ) {
    private val ioScope = CoroutineScope(ioDispatcher)

    /**
     * A job for preparing IO operations.
     */
    private var prepareIOJob: Job? = null

    /**
     * A job for seeking IO operations.
     */
    private var seekToIOJob: Job? = null

    /**
     * A list of [Callback]s to be notified of player events like state changes.
     */
    private val callbacks: MutableList<Callback> = mutableListOf()

    /**
     * The current media item being played or loaded
     */
    var mediaSource: MediaSource? = null
        protected set

    /**
     * The current [State] of the player.
     */
    var state: State = State.Idle
        protected set(value) {
            field = value

            when (value) {
                State.Ready -> {
                    triggerCallbacks { it.onIsPlayingChanged(getIsPlaying()) }
                }
                else -> {
                    // Do nothing for other states in here
                }
            }
            triggerCallbacks { it.onStateChanged(value) }
        }

    /**
     * After initializing the player, this function needs to be called to load libraries and
     * configurations. Some callbacks that require initialization can be added before this method.
     *
     * The software maintains initialization and requests to connect to the audio output device,
     * similar to how AU and other software handle it.
     */
    fun init() = sendCommand(InternalCommand.Init)

    /**
     * Loads a media resource. If [mediaItem] is `null`, it unloads the current resource and
     * resets the player's context.
     *
     * After a successful `load`, [prepare] must be called before playback can start.
     * Do not call [prepare] or [play] if loading with `null`.
     *
     * @param mediaItem The media resource to load, or `null` to clear the player.
     */
    fun load(mediaItem: MediaSource?) = sendCommand(InternalCommand.Load(mediaItem))

    /**
     * Prepares media for playback.
     */
    fun prepare() = sendCommand(InternalCommand.Prepare)

    /**
     * Start or resume.
     *
     * 1. [load]
     * 2. [prepare]
     * 3. [play]
     */
    fun play() = sendCommand(InternalCommand.Play)

    /**
     * Pause.
     */
    fun pause() = sendCommand(InternalCommand.Pause)

    /**
     * Stop resource, but remember playback position. Use [prepare] to prepare resource again.
     */
    fun stop() = sendCommand(InternalCommand.Stop)

    /**
     * Release player. The player cannot be used again.
     */
    fun release() = sendCommand(InternalCommand.Release)

    /**
     * Seek to [position] ms.
     */
    fun seekTo(position: Long) = sendCommand(InternalCommand.SeekTo(position))

    fun previous() = sendCommand(InternalCommand.Previous)

    fun next() = sendCommand(InternalCommand.Next)

    fun setConfig(config: Config) = sendCommand(InternalCommand.SetConfig(config))

    abstract fun getIsPlaying(): Boolean

    /**
     * Milliseconds. -1 means unknown.
     */
    abstract fun getDuration(): Long

    /**
     * Milliseconds. -1 means unknown.
     */
    abstract fun getPosition(): Long

    /**
     * Adds a [Callback] to receive player events.
     */
    fun addCallback(callback: Callback) {
        callbacks.add(callback)
    }

    /**
     * Removes a previously added [Callback].
     */
    fun removeCallback(callback: Callback) {
        callbacks.remove(callback)
    }

    /**
     * **⚠️ Implementation Note: Coroutine Cancellation**
     */
    protected abstract suspend fun processInit()

    /**
     * **⚠️ Implementation Note: Coroutine Cancellation**
     */
    protected abstract suspend fun processLoad(mediaItem: Any?)

    protected abstract suspend fun processPlay()

    protected abstract suspend fun processPause()

    protected abstract suspend fun processStop()

    protected open suspend fun processRelease() {
        closeAllCommands()

        prepareIOJob?.cancel()
        prepareIOJob = null

        seekToIOJob?.cancel()
        seekToIOJob = null

        callbacks.clear()
    }

    protected abstract suspend fun processSeekTo(position: Long)

    protected abstract suspend fun processPrevious()

    protected abstract suspend fun processNext()

    protected abstract suspend fun processSetConfig(config: Config)

    protected open suspend fun processWhenReady() {
        state = State.Ready
    }

    protected fun triggerCallbacks(block: (Callback) -> Unit) {
        callbacks.forEach { callback ->
            // Intentional: Don't catch exceptions here - let callers handle their own errors
            block(callback)
        }
    }

    override suspend fun processCommand(command: Command) {
        TODO("Not yet implemented")
    }

    @Suppress("RedundantSuspendModifier")
    private suspend fun processPrepare() {
        val currentMediaSource = mediaSource
        if (currentMediaSource == null) {
            // TODO Throw error
            return
        }

        state = State.Buffering

        prepareIOJob?.cancel()
        prepareIOJob =
            ioScope.launch {
                currentMediaSource.prepare()
                if (isActive) {
                    // Ready
                    sendCommand(InternalCommand.WhenReady)
                } else {
                    // Do nothing
                    currentMediaSource.release()
                }
            }
    }

    companion object {
        private val TAG = SaltPlayer::class.simpleName!!
    }
}
