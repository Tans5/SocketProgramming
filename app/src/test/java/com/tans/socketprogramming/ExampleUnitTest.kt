package com.tans.socketprogramming

import kotlinx.coroutines.*
import org.junit.Test

import org.junit.Assert.*
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.math.BigInteger
import java.net.*
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

    @Test
    fun udpSocketTest() = runBlocking {
        val localAddr = InetAddress.getLocalHost()
        val datagramSocket = DatagramSocket(null)
        val bytes = ByteArray(1024)
        val dataPackage = DatagramPacket(bytes, 0, bytes.size, InetSocketAddress(1997))
        val result = datagramSocket.bindSuspend(InetSocketAddress(localAddr,1999))
            .map(this) { datagramSocket.receiveSuspend(dataPackage) }
        if (result.isSuccess()) {
            println("Remote Address: ${dataPackage.address.hostAddress}")
        }
        datagramSocket.close()
    }

    @Test
    fun udpMulticast() = runBlocking {
        val socket = MulticastSocket(null)
        val result = socket.bindSuspend(InetSocketAddress(InetAddress.getLocalHost(), 9999))
            .map(this) {
                // socket.joinGroup(InetSocketAddress(InetAddress.getByName("239.0.0.1"), 0), NetworkInterface.getByName("192.168.104.242"))
                socket.`interface` = InetAddress.getLocalHost()
                socket.joinGroup(InetAddress.getByName("239.0.1.1"))
                val bytes = "Hello, World!!!".toByteArray(Charsets.UTF_8)
                val dataPackage = DatagramPacket(bytes, 0, bytes.size, InetSocketAddress(9998))
                socket.sendSuspend(dataPackage)
            }

        if (result.isSuccess()) {
            println("Success")
        } else {
            println("Error: ${result.errorOrNull()}")
        }

    }
}
