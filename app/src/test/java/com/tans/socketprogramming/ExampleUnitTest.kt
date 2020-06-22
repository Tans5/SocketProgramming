package com.tans.socketprogramming

import kotlinx.coroutines.*
import org.junit.Test

import org.junit.Assert.*
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.math.BigInteger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

    val inputStream: PipedInputStream = PipedInputStream()
    val outputStream: PipedOutputStream = PipedOutputStream(inputStream)

    @Test
    fun readWriteTest() = runBlocking {
        val writeJob = launch(Dispatchers.IO) {
            val sizeByteArray = ByteArray(4)
            inputStream.readWithoutRemainSuspend(sizeByteArray)
            var count = sizeByteArray.toInt()
            println("Count: $count")
            var dataArray = ByteArray(count)
            inputStream.readWithoutRemainSuspend(dataArray)
            while (true) {
                inputStream.readWithoutRemainSuspend(sizeByteArray)
                count = sizeByteArray.toInt()
                println("Count: $count")
                dataArray = ByteArray(count)
                inputStream.readWithoutRemainSuspend(dataArray)
                // println("Result: ${dataArray.toString(Charsets.UTF_8)}")
            }
        }

        val readJob = launch(Dispatchers.IO) {
            while (true) {
                delay(200)
                val testData = ByteArray(2000)
                val size = testData.size
                outputStream.write(size.toByteArray() + testData)
            }
        }

        writeJob.join()
        readJob.join()
    }

    @Test
    fun asyncTest(): Unit = runBlocking {
        val job = async {
            launch {
                delay(Long.MAX_VALUE)
            }
            true
        }
        job.await()
        Unit
    }

    @Test
    fun shortTest(): Unit {
        val s: Short = 0
        val b = s.toByte()
        val s1 = b.toShort()
        println(s1)
    }

    @Test
    fun coroutineTest() = runBlocking {

        val job = launch {
            try {
                val job = launch {
                    val job1 = async {
                        runCatching {
                            blockToSuspend {
                                Thread.sleep(Long.MAX_VALUE)
                            }
                        }
                    }
                    val job2 = async {
                        runCatching {
                            blockToSuspend {
                                Thread.sleep(5000)
                            }
                        }
                    }
                    job1.await()
                    job2.await()
                }
                job.join()
            } finally {
                println("Do finally..")
            }
        }
        delay(2000)
        job.cancel()
    }
}
