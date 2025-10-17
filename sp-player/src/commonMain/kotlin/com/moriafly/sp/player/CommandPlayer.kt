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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * A player that receives commands from the user.
 */
@UnstableSpPlayerApi
abstract class CommandPlayer(
    commandDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
) {
    private val commandScope = CoroutineScope(commandDispatcher)

    /**
     * A channel to receive all external commands from the user.
     */
    private val commandChannel = Channel<Command>(Channel.UNLIMITED)

    /**
     * Send a command to the player.
     */
    protected fun sendCommand(command: Command) {
        commandChannel.trySend(command)
    }

    /**
     * Process a command.
     */
    abstract suspend fun processCommand(command: Command)

    /**
     * Close all commands, this player can not be used after this.
     */
    protected fun closeAllCommands() {
        commandChannel.close()
        commandScope.cancel()
    }

    init {
        commandScope.launch {
            for (command in commandChannel) {
                processCommand(command)
            }
        }
    }
}
