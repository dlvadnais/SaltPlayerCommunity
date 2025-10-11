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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * # SaltPlayer
 *
 * An abstract base class for a media player, designed with a command-driven architecture
 * using Kotlin Coroutines and Channels. It manages the player's state and lifecycle.
 *
 * Subclasses must implement the `process...` methods to provide the actual playback logic.
 * The player distinguishes between two types of commands:
 * - [OutContextCommand]: Manages the lifecycle of a media item (e.g., `init`, `load`, `release`).
 * Processing such a command will cancel any ongoing playback task and start a new one.
 * - [InContextCommand]: Operates within the context of a loaded media item (e.g., `play`, `pause`,
 * `seek`).
 * These are processed sequentially within the active playback task.
 *
 * ---
 *
 * ### ⚠️ Special Note for Native Code Integration
 *
 * This class is designed to safely interact with native libraries (e.g., BASS, FMOD)
 * which often require careful state management and manual resource cleanup. If your
 * implementation calls native code, you **must** be aware of the following principles:
 *
 * #### 1. State Management and Thread Safety
 * Native libraries are often not thread-safe and maintain their own internal state.
 * To solve this, `SaltPlayer` guarantees that all `process...` methods are executed
 * sequentially on a **single, dedicated background thread**.
 *
 * **Your Responsibility:** Perform **all** interactions with the native library strictly
 * within the provided `process...` methods. This ensures all calls to the native code
 * are serialized, preventing race conditions and ensuring state consistency without
 * the need for manual locks (`Mutex`).
 *
 * #### 2. Resource Cleanup and Coroutine Cancellation
 * The most critical challenge is that native resources are not managed by the JVM Garbage
 * Collector and **must** be manually released. This is complicated by the fact that the
 * coroutines executing `processLoad`, `processNext`, etc., can be cancelled at any moment.
 *
 * **Your Responsibility:** Your implementation **must** be robust against cancellation.
 * - **For In-Progress Resources:** Inside any cancellable `process` function (like `processLoad`),
 * you **must** use a `try-finally` block to ensure that any resources you are currently
 * in the process of creating are properly cleaned up if the operation is cancelled halfway through.
 */
@UnstableSpPlayerApi
abstract class SaltPlayer {
    /**
     * The [CoroutineScope] for all player operations. It uses a [SupervisorJob] to ensure that
     * the failure of one child coroutine does not cancel the entire scope.
     */
    protected val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * A channel to receive all external commands from the user.
     */
    private val commandChannel = Channel<Command>(Channel.UNLIMITED)

    /**
     * A channel for commands that operate within the context of a loaded media item
     * (e.g., play, pause, seek).
     */
    private val inContextCommandChannel = Channel<InContextCommand>(Channel.UNLIMITED)

    /**
     * Represents the core lifecycle of a single media item. It is created when an
     * [OutContextCommand] (like `load`) is processed and can be cancelled when a new one arrives.
     * This job is responsible for consuming commands from [inContextCommandChannel].
     */
    private var activeJob: Job? = null

    /**
     * A list of [Callback]s to be notified of player events like state changes.
     */
    protected val callbacks = mutableListOf<Callback>()

    /**
     * The current media item being played or loaded. Can be any object,
     * to be interpreted by the subclass implementation.
     */
    var mediaItem: Any? = null
        protected set

    /**
     * The current [State] of the player (e.g., Idle, Ready, Playing).
     * Setting this property will notify all registered [Callback]s of the state change.
     */
    var state: State = State.Idle
        protected set(value) {
            field = value
            when (value) {
                State.Ready -> {
                    callbacks.forEach { it.onIsPlayingChanged(getIsPlaying()) }
                }

                else -> {
                    // Do nothing for other states in here
                }
            }
            callbacks.forEach { it.onStateChanged(value) }
        }

    init {
        // Launches a coroutine to process commands from the main command channel
        scope.launch {
            for (command in commandChannel) {
                when (command) {
                    is InContextCommand -> processInContextCommand(command)
                    is OutContextCommand -> processOutContextCommand(command)
                }
            }
        }
    }

    /**
     * After initializing the player, this function needs to be called to load libraries and
     * configurations. Some callbacks that require initialization can be added before this method.
     *
     * The software maintains initialization and requests to connect to the audio output device,
     * similar to how AU and other software handle it.
     */
    fun init() = commandChannel.trySend(OutContextCommand.Init)

    /**
     * Loads a media resource. If [mediaItem] is `null`, it unloads the current resource and
     * resets the player's context.
     *
     * After a successful `load`, [prepare] must be called before playback can start.
     * Do not call [prepare] or [play] if loading with `null`.
     *
     * @param mediaItem The media resource to load, or `null` to clear the player.
     */
    fun load(mediaItem: Any?) = commandChannel.trySend(OutContextCommand.Load(mediaItem))

    /**
     * Prepares media for playback.
     *
     * @param position Start position in milliseconds.
     * @param playWhenReady Auto-play when ready.
     */
    fun prepare(
        position: Long = 0L,
        playWhenReady: Boolean = false
    ) = commandChannel.trySend(InContextCommand.Prepare(position, playWhenReady))

    /**
     * Start or resume.
     *
     * 1. [load]
     * 2. [prepare]
     * 3. [play]
     */
    fun play() = commandChannel.trySend(InContextCommand.Play)

    /**
     * Pause.
     */
    fun pause() = commandChannel.trySend(InContextCommand.Pause)

    /**
     * Stop resource, but remember playback position. Use [prepare] to prepare resource again.
     */
    fun stop() = commandChannel.trySend(OutContextCommand.Stop)

    /**
     * Release player. The player cannot be used again.
     */
    fun release() = commandChannel.trySend(OutContextCommand.Release)

    /**
     * Seek to [position] ms.
     */
    fun seekTo(position: Long) = commandChannel.trySend(InContextCommand.SeekTo(position))

    fun previous() = commandChannel.trySend(OutContextCommand.Previous)

    fun next() = commandChannel.trySend(OutContextCommand.Next)

    fun setConfig(config: Config) = commandChannel.trySend(InContextCommand.SetConfig(config))

    fun customInContextCommand(command: CustomCommand) =
        commandChannel.trySend(InContextCommand.CustomInContextCommand(command))

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

    protected abstract suspend fun processPrepare(position: Long, playWhenReady: Boolean)

    protected abstract suspend fun processPlay()

    protected abstract suspend fun processPause()

    /**
     * **⚠️ Implementation Note: Coroutine Cancellation**
     */
    protected abstract suspend fun processStop()

    /**
     * **⚠️ Implementation Note: Coroutine Cancellation**
     */
    protected abstract suspend fun processRelease()

    protected abstract suspend fun processSeekTo(position: Long)

    /**
     * **⚠️ Implementation Note: Coroutine Cancellation**
     */
    protected abstract suspend fun processPrevious()

    /**
     * **⚠️ Implementation Note: Coroutine Cancellation**
     */
    protected abstract suspend fun processNext()

    protected abstract suspend fun processSetConfig(config: Config)

    protected abstract suspend fun processCustomInContextCommand(command: CustomCommand)

    /**
     * Forwards an [InContextCommand] to the dedicated channel for processing by the active job.
     */
    private fun processInContextCommand(inContextCommand: InContextCommand) {
        inContextCommandChannel.trySend(inContextCommand)
    }

    /**
     * Processes an [OutContextCommand], which defines a new media lifecycle.
     * This function cancels any existing [activeJob], clears the queue of pending in-context
     * commands, and starts a new [activeJob] to handle the new context.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun processOutContextCommand(outContextCommand: OutContextCommand) {
        // Before starting a new task, clear any leftover in-context commands from the previous one.
        while (!inContextCommandChannel.isEmpty) {
            inContextCommandChannel.receive()
        }

        // Cancel the previous job without waiting for its completion to ensure responsiveness.
        activeJob?.cancel()

        // Start a new job for the new context.
        activeJob =
            scope.launch {
                when (outContextCommand) {
                    is OutContextCommand.Init -> processInit()
                    is OutContextCommand.Load -> processLoad(outContextCommand.mediaItem)
                    is OutContextCommand.Stop -> processStop()
                    is OutContextCommand.Release -> processRelease()
                    is OutContextCommand.Previous -> processPrevious()
                    is OutContextCommand.Next -> processNext()
                }

                // If the job is still active after the initial processing (e.g., load was successful),
                // start consuming in-context commands.
                if (isActive) {
                    for (inContextCommand in inContextCommandChannel) {
                        when (inContextCommand) {
                            is InContextCommand.Prepare ->
                                processPrepare(
                                    position = inContextCommand.position,
                                    playWhenReady = inContextCommand.playWhenReady
                                )
                            is InContextCommand.Play -> processPlay()
                            is InContextCommand.Pause -> processPause()
                            is InContextCommand.SeekTo -> processSeekTo(inContextCommand.position)
                            is InContextCommand.SetConfig ->
                                processSetConfig(
                                    inContextCommand.config
                                )
                            is InContextCommand.CustomInContextCommand ->
                                processCustomInContextCommand(inContextCommand.command)
                        }
                    }
                }
            }
    }

    companion object {
        private val TAG = SaltPlayer::class.simpleName!!
    }
}
