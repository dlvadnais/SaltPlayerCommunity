package com.moriafly.sp.player

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test

class NewSaltPlayerTest {
    class NewSaltPlayer(
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) {
        private val scope = CoroutineScope(dispatcher)

        private val commandChannel = Channel<Int>(Channel.UNLIMITED)

        fun prepare() {
            commandChannel.trySend(0)
        }

        fun play() {
            commandChannel.trySend(1)
        }

        private suspend fun processPrepare() {
            println("processPrepare")
            withContext(ioDispatcher) {
                // 模拟本机代码加载耗时
                println("processPrepare IO start")
                delay(5000)
                println("processPrepare IO end")
            }
            println("processPrepare end")
        }

        private suspend fun processPlay() {
            println("processPlay")
        }

        init {
            scope.launch {
                for (command in commandChannel) {
                    when (command) {
                        0 -> processPrepare()
                        1 -> processPlay()
                    }
                }
            }
        }
    }

    @Test
    fun test() {
        runTest {
            val dispatcher = StandardTestDispatcher(this.testScheduler)
            val ioDispatcher = StandardTestDispatcher(this.testScheduler)
            val saltPlayer = NewSaltPlayer(dispatcher, ioDispatcher)
            saltPlayer.prepare()

            saltPlayer.play()
            saltPlayer.prepare()

            advanceUntilIdle()
        }
    }
}
