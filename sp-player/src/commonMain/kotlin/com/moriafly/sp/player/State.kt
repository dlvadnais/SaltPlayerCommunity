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
 * Player state.
 */
@UnstableSpPlayerApi
enum class State {
    /**
     * 1. The player is initialized but no song has been loaded, or a song has been loaded but
     * is not playing.
     *
     * 2. The player has been stopped/released.
     */
    Idle,

    /**
     * The player is buffering the current song.
     */
    Buffering,

    /**
     * 1. [Buffering] end, like Android Media3 STATE_READY.
     * 2. The player is playing the current song.
     * 3. The player is paused.
     */
    Ready,

    /**
     * The player has ended playback of the current song.
     */
    Ended
}
