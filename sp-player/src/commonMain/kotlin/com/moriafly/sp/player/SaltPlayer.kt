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

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 *
 * @param dispatcher The [CoroutineDispatcher] to use for all player operations.
 */
@UnstableSpPlayerApi
abstract class SaltPlayer(
    dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
) {
    /**
     * The [CoroutineScope] for all player operations. It uses a [SupervisorJob] to ensure that
     * the failure of one child coroutine does not cancel the entire scope.
     */
    protected val scope = CoroutineScope(dispatcher + SupervisorJob())

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
    @Suppress("ktlint:standard:backing-property-naming")
    private val _callbacks = atomic<List<Callback>>(emptyList())

    private val _mediaItem = atomic<Any?>(null)

    /**
     * The current media item being played or loaded. Can be any object,
     * to be interpreted by the subclass implementation.
     */
    var mediaItem: Any?
        get() = _mediaItem.value
        protected set(value) {
            _mediaItem.value = value
        }

    private val _state = atomic(State.Idle)

    /**
     * The current [State] of the player.
     */
    var state: State
        get() = _state.value
        protected set(value) {
            _state.value = value

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

    init {
        // Launches a coroutine to process commands from the main command channel
        // In this case, when sending Out -> In -> Out in sequence, the In in the middle may likely
        // not be executed
        scope.launch {
            for (command in commandChannel) {
                when (command) {
                    is NonContextCommand -> processNonContextCommand(command)
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

    fun setConfig(config: Config) = commandChannel.trySend(NonContextCommand.SetConfig(config))

    fun customCommand(command: CustomCommand) =
        commandChannel.trySend(NonContextCommand.Custom(command))

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
        scope.launch {
            _callbacks.getAndUpdate { oldList ->
                oldList + callback
            }
        }
    }

    /**
     * Removes a previously added [Callback].
     */
    fun removeCallback(callback: Callback) {
        scope.launch {
            _callbacks.getAndUpdate { oldList ->
                oldList - callback
            }
        }
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
    protected open suspend fun processRelease() {
        scope.cancel()
        commandChannel.close()
        inContextCommandChannel.close()
        activeJob?.cancel()
        _callbacks.getAndSet(emptyList())
    }

    protected abstract suspend fun processSeekTo(position: Long)

    /**
     * **⚠️ Implementation Note: Coroutine Cancellation**
     */
    protected abstract suspend fun processPrevious()

    /**
     * **⚠️ Implementation Note: Coroutine Cancellation**
     */
    protected abstract suspend fun processNext()

    protected open suspend fun processSetConfig(config: Config) {}

    protected open suspend fun processCustomCommand(command: CustomCommand) {}

    // 触发回调：直接读取当前不可变列表（快照）
    protected fun triggerCallbacks(block: (Callback) -> Unit) {
        // 1. 获取回调快照（非 suspend，可在任何地方调用）
        val currentCallbacks = _callbacks.value

        // 2. 提交到 scope 执行，自动处理线程切换和异常
        scope.launch {
            // 切换到主线程
            withContext(Dispatchers.Main) {
                // 遍历回调并执行
                currentCallbacks.forEach { callback ->
                    block(callback)
                }
            }
        }
    }

    private suspend fun processNonContextCommand(nonContextCommand: NonContextCommand) {
        when (nonContextCommand) {
            is NonContextCommand.SetConfig -> processSetConfig(nonContextCommand.config)
            is NonContextCommand.Custom -> processCustomCommand(nonContextCommand.command)
        }
    }

    /**
     * Forwards an [InContextCommand] to the dedicated channel for processing by the active job.
     */
    private fun processInContextCommand(inContextCommand: InContextCommand) {
        // If there is an active job, forward the command to the in-context channel
        if (activeJob?.isActive == true) {
            // Use trySend for forwarding to avoid blocking, allowing the in-context method to be
            // interrupted by out-of-context operations
            // That is, when an out-of-context command is triggered, it can enter
            // processOutContextCommand to clear the in-context command
            inContextCommandChannel.trySend(inContextCommand)
        }
    }

    /**
     * Processes an [OutContextCommand], which defines a new media lifecycle.
     * This function cancels any existing [activeJob], clears the queue of pending in-context
     * commands, and starts a new [activeJob] to handle the new context.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun processOutContextCommand(outContextCommand: OutContextCommand) {
        // Atomically drain any leftover in-context commands from the previous job's queue
        // Using a non-suspending `tryReceive` loop prevents a race condition where a new
        // command could be posted after a check for emptiness but before a suspending `receive`
        while (inContextCommandChannel.tryReceive().isSuccess) {
            // Loop until the channel is empty
        }

        // Cancel the previous job without waiting for its completion to ensure responsiveness
        activeJob?.cancel()

        when (outContextCommand) {
            is OutContextCommand.Init -> processInit()
            is OutContextCommand.Load -> processLoad(outContextCommand.mediaItem)
            is OutContextCommand.Stop -> processStop()
            is OutContextCommand.Release -> processRelease()
            is OutContextCommand.Previous -> processPrevious()
            is OutContextCommand.Next -> processNext()
        }

        // Start a new job for the new context
        activeJob =
            scope.launch {
                // If the job is still active after the initial processing
                // (e.g., load was successful), start consuming in-context commands
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
                        }
                    }
                }
            }
    }

    companion object {
        private val TAG = SaltPlayer::class.simpleName!!
    }
}
