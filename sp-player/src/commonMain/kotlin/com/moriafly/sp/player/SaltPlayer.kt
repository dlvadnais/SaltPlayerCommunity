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
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
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
 *
 * ## Core Design Principles
 * 1.  **Command-Driven**: All player operations (e.g., play, pause, seek) are encapsulated as
 * [Command] objects and sent to an internal queue (Channel).
 * 2.  **Sequential Execution**: A dedicated coroutine processes commands from the queue one by one,
 * in the order they were sent. This design fundamentally eliminates race conditions and state
 * conflicts that can arise from concurrent operations.
 * 3.  **Thread Confinement**: The `commandDispatcher` defaults to `Dispatchers.Default.limitedParallelism(1)`.
 * This ensures that all core logic for command processing **always executes on the same, single thread**.
 * This is crucial for safely interacting with non-thread-safe underlying player engines (like C++ libraries)
 * and greatly simplifies state management.
 *
 * @param commandDispatcher The [CoroutineDispatcher] used for processing commands. Defaults to a
 * single-threaded dispatcher to ensure thread safety and sequential execution.
 * @param ioDispatcher The [CoroutineDispatcher] used for expensive I/O operations, such as preparing
 * a media file.
 */
@UnstableSpPlayerApi
abstract class SaltPlayer(
    commandDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
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
    private val callbacks = atomic(emptyList<Callback>())

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
     * Adds a [Callback] to receive player events. This is a thread-safe and lock-free operation.
     */
    fun addCallback(callback: Callback) {
        callbacks.update { currentList ->
            // Create a new list with the new callback added
            currentList + callback
        }
    }

    /**
     * Removes a previously added [Callback]. This is a thread-safe and lock-free operation.
     */
    fun removeCallback(callback: Callback) {
        callbacks.update { currentList ->
            // Create a new list with the specified callback removed
            currentList - callback
        }
    }

    protected abstract suspend fun processInit()

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

        // Clear the callbacks by setting the reference to a new empty list
        callbacks.value = emptyList()
    }

    protected abstract suspend fun processSeekTo(position: Long)

    protected abstract suspend fun processPrevious()

    protected abstract suspend fun processNext()

    protected abstract suspend fun processSetConfig(config: Config)

    protected open suspend fun processWhenReady() {
        state = State.Ready
    }

    protected open suspend fun processCustomCommand(command: Command) {}

    protected fun triggerCallbacks(block: (Callback) -> Unit) {
        // Get an immutable snapshot of the current callback list
        // This is safe even if other threads are calling addCallback/removeCallback concurrently
        val currentCallbacks = callbacks.value
        currentCallbacks.forEach { callback ->
            block(callback)
        }
    }

    override suspend fun processCommand(command: Command) {
        if (command is InternalCommand) {
            when (command) {
                is InternalCommand.Init -> processInit()
                is InternalCommand.Load -> processLoad(command.mediaSource)
                is InternalCommand.Prepare -> processPrepare()
                is InternalCommand.Play -> processPlay()
                is InternalCommand.Pause -> processPause()
                is InternalCommand.SeekTo -> processSeekTo(command.position)
                is InternalCommand.Previous -> processPrevious()
                is InternalCommand.Next -> processNext()
                is InternalCommand.Stop -> processStop()
                is InternalCommand.Release -> processRelease()
                is InternalCommand.SetConfig -> processSetConfig(command.config)
                is InternalCommand.WhenReady -> processWhenReady()
            }
        } else {
            processCustomCommand(command)
        }
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
