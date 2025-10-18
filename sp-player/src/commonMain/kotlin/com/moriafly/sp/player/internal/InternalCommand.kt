package com.moriafly.sp.player.internal

import com.moriafly.sp.player.Command
import com.moriafly.sp.player.Config
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
        val mediaSource: Any?,
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
     * Command to notify that the player has ended playback.
     */
    object OnEnded : InternalCommand

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

    object PrepareCompleted : InternalCommand

    object SeekToCompleted : InternalCommand
}
