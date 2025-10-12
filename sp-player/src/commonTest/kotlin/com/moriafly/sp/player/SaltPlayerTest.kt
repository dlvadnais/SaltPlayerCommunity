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

package com.moriafly.sp.player

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, UnstableSpPlayerApi::class)
class SaltPlayerTest {
    @Test
    fun `send in context command before send out context command`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = TestSaltPlayer(this.coroutineContext[CoroutineDispatcher]!!)

            player.play()
            advanceUntilIdle()
            assertTrue(player.inContextCommandsExecuted.isEmpty())
        }

    @Test
    fun `send out context command before send in context command`() =
        runTest(UnconfinedTestDispatcher()) {
            val player = TestSaltPlayer(this.coroutineContext[CoroutineDispatcher]!!)

            player.init()
            assertTrue(player.inContextCommandsExecuted.isEmpty())

            player.play()
            assertEquals(player.inContextCommandsExecuted.size, 1)

            player.play()
            assertEquals(player.inContextCommandsExecuted.size, 2)
        }
}

@OptIn(UnstableSpPlayerApi::class)
private class TestSaltPlayer(
    dispatcher: CoroutineDispatcher
) : SaltPlayer(dispatcher) {
    val inContextCommandsExecuted = mutableListOf<InContextCommand>()

    override fun getIsPlaying(): Boolean = false

    override fun getDuration(): Long = -1

    override fun getPosition(): Long = -1

    override suspend fun processInit() {}

    override suspend fun processLoad(mediaItem: Any?) {}

    override suspend fun processPrepare(position: Long, playWhenReady: Boolean) {}

    override suspend fun processPlay() {
        inContextCommandsExecuted.add(InContextCommand.Play)
    }

    override suspend fun processPause() {}

    override suspend fun processStop() {}

    override suspend fun processRelease() {}

    override suspend fun processSeekTo(position: Long) {}

    override suspend fun processPrevious() {}

    override suspend fun processNext() {}
}
