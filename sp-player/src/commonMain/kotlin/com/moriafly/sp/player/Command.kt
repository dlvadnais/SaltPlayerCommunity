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

/**
 * Marker interface for all player commands.
 * Commands are used to control the player's behavior.
 */
@UnstableSpPlayerApi
interface Command

/**
 * Custom command interface for custom commands.
 */
@UnstableSpPlayerApi
interface CustomCommand

/**
 * Non-context commands should not be affected by the context flow and must be triggered correctly.
 */
@UnstableSpPlayerApi
sealed interface NonContextCommand : Command {
    /**
     * Command to update player configuration.
     *
     * @param config The new configuration settings.
     */
    data class SetConfig(
        val config: Config
    ) : NonContextCommand

    /**
     * Command to execute a custom in-context command.
     */
    data class Custom(
        val command: CustomCommand
    ) : NonContextCommand
}

/**
 * Internal context commands that require synchronous execution.
 *
 * Rules:
 * - Internal command -> Internal command: Executed synchronously
 * - External command -> Internal command: Executed synchronously
 *
 * Conclusion: New external commands will wait for previous commands to complete.
 */
@UnstableSpPlayerApi
internal sealed interface InContextCommand : Command {
    /**
     * Command to prepare playback at a specific position.
     *
     * @param position The position in milliseconds to prepare playback.
     * @param playWhenReady Whether to start playback when ready.
     */
    data class Prepare(
        val position: Long,
        val playWhenReady: Boolean
    ) : InContextCommand

    /**
     * Command to start or resume playback.
     */
    object Play : InContextCommand

    /**
     * Command to pause playback.
     */
    object Pause : InContextCommand

    /**
     * Command to seek to a specific position.
     *
     * @param position The position in milliseconds to seek to.
     */
    data class SeekTo(
        val position: Long
    ) : InContextCommand
}

/**
 * External context commands that can interrupt current playback flow.
 *
 * Rules:
 * - External command -> External command: Interrupts previous command
 * - Internal command -> External command: Interrupts previous command
 *
 * Conclusion: New external commands will interrupt any previous commands.
 */
@UnstableSpPlayerApi
internal sealed interface OutContextCommand : Command {
    /**
     * Command to init player.
     */
    object Init : OutContextCommand

    /**
     * Command to load a new media item.
     *
     * @param mediaItem The media item to load.
     */
    data class Load(
        val mediaItem: Any?,
    ) : OutContextCommand

    /**
     * Command to play the previous item.
     */
    object Previous : OutContextCommand

    /**
     * Command to play the next item.
     */
    object Next : OutContextCommand

    /**
     * Command to stop playback and release media item resource.
     */
    object Stop : OutContextCommand

    /**
     * Command to release all player resources.
     */
    object Release : OutContextCommand
}
