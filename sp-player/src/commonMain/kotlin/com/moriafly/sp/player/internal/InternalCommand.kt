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

package com.moriafly.sp.player.internal

import com.moriafly.sp.player.Command
import com.moriafly.sp.player.Config
import com.moriafly.sp.player.MediaSource
import com.moriafly.sp.player.UnstableSpPlayerApi

/**
 * Internal commands that require asynchronous execution.
 */
@UnstableSpPlayerApi
internal sealed interface InternalCommand : Command {
    /**
     * Command to init player.
     */
    object Init : InternalCommand

    /**
     * Command to load a new media item.
     *
     * @param mediaSource The media item to load.
     */
    data class Load(
        val mediaSource: MediaSource?,
    ) : InternalCommand

    /**
     * Command to prepare playback.
     */
    object Prepare : InternalCommand

    /**
     * Command to start or resume playback.
     */
    object Play : InternalCommand

    /**
     * Command to pause playback.
     */
    object Pause : InternalCommand

    /**
     * Command to seek to a specific position.
     *
     * @param position The position in milliseconds to seek to.
     */
    data class SeekTo(
        val position: Long
    ) : InternalCommand

    /**
     * Command to play the previous item.
     */
    object Previous : InternalCommand

    /**
     * Command to play the next item.
     */
    object Next : InternalCommand

    /**
     * Command to stop playback and release media item resource.
     */
    object Stop : InternalCommand

    /**
     * Command to release all player resources.
     */
    object Release : InternalCommand

    /**
     * Command to update player configuration.
     *
     * @param config The new configuration settings.
     */
    data class SetConfig(
        val config: Config
    ) : InternalCommand

    object WhenReady : InternalCommand
}
