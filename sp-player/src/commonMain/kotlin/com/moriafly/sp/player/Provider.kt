package com.moriafly.sp.player

/**
 * The player will call the method to fetch the latest data when needed, acting as a provider.
 */
@UnstableSpPlayerApi
interface Provider {
    suspend fun onGetPrevious(): Any?

    /**
     * Called when the player needs the next song to play.
     *
     * When used in Gapless.
     * 1. [onGetNext]
     * 2. Gapless.
     * 3. End previous song [Callback.onStateChanged] state [State.Ended].
     */
    suspend fun onGetNext(): Any?

    suspend fun onEnded(): EndedType

    suspend fun onGetFrontCover(mediaItem: Any): ByteArray?

    enum class EndedType {
        Loop,
        Gapless,
        Stop
    }
}
