package com.tans.socketprogramming

import kotlinx.coroutines.*
import org.junit.Test

import org.junit.Assert.*
import java.math.BigInteger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() = runBlocking {
        val j = launch(Dispatchers.IO) {
            val s = ServerSocket(900, 1)
            try {
                println("Accept pre")
                println("LaunchThread: ${Thread.currentThread().name}")
                val client = s.acceptSuspend()
            } finally {
                s.close()
                println("Do finally")
            }
        }
        delay(1000)
        j.cancel()
    }

    suspend fun testSuspend() = suspendCoroutine<Unit> { cont ->
        Thread.sleep(500)
        cont.resume(Unit)
    }
}
