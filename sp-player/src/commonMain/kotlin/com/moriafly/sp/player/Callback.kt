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
 * Callback interface for player.
 *
 * Implement or extend this interface to handle custom player events.
 */
@UnstableSpPlayerApi
interface Callback {
    /**
     * Called with loaded [mediaItem] when ready, or null when cleared/failed.
     */
    fun onLoaded(mediaItem: Any?) {}

    /**
     * Called when [mediaItem]'s internal data updates during playback.
     */
    fun onCurrentMediaItemDataRefreshed(mediaItem: Any) {}

    /**
     * Called when playback [state] changes.
     */
    fun onStateChanged(state: State) {}

    /**
     * Called when playback starts/stops ([isPlaying] changes).
     */
    fun onIsPlayingChanged(isPlaying: Boolean) {}

    /**
     * Called after seeking to [position] (in ms).
     */
    fun onSeekTo(position: Long) {}

    /**
     * Called when receive **Recoverable Operational Errors**.
     *
     * Receive errors that need to be handled externally by a library or errors that need to be
     * feedback to the user. Internal errors that can be handled through the process should not
     * callback this method.
     */
    fun onRecoverableError(e: Throwable) {}
}
