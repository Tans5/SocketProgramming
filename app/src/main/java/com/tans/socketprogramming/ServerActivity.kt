package com.tans.socketprogramming

import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.getSystemService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.*

/**
 *
 * author: pengcheng.tan
 * date: 2020/5/19
 */
class ServerActivity : BaseActivity() {

    val clientConnected: BroadcastChannel<Boolean> = BroadcastChannel(Channel.CONFLATED)

    val ip: IntArray by lazy {
        (getSystemService<WifiManager>()?.connectionInfo?.ipAddress ?: 0).toIpAddr()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)
         val waitJob = waitClientConnect()
        val sendBroadcastJob = sendBroadCast()

        launch {
            
            clientConnected.send(false)
        }

        val ipString = String.format("%d.%d.%d.%d", ip[0], ip[1], ip[2], ip[3])

        println("Server Ip: $ipString")
    }

    fun waitClientConnect(): Job {
        return launch(Dispatchers.IO) {
            ServerSocket(MAIN_PORT, MAX_CONNECT).use { serverSocket ->
                val client = serverSocket.acceptSuspend()
                if (client != null) {
                    clientConnected.send(true)
                    // TODO: Client connect.
                }
            }
        }
    }

    fun sendBroadCast(): Job {
        return launch (Dispatchers.IO) {
            val serverSocket = ServerSocket()

            while (true) {
                val j = launch {
                    repeat(254) { i ->
                        val clientNum = ip[3]
                        if (clientNum != i + 1) {
                            launch {
                                val broadcastServerIp = IntArray(4) { index ->
                                    if (index == 3) {
                                        i + 1
                                    } else {
                                        ip[index]
                                    }
                                }
                                val client = Socket()
                                val endPoint = InetSocketAddress(InetAddress
                                    .getByAddress(broadcastServerIp.toInetByteArray()), BROADCAST_PORT)
                                val result = client.connectSuspend(endPoint = endPoint)
                                if (result) {
                                    client.use {
                                        val writer = BufferedWriter(OutputStreamWriter(it.getOutputStream()))
                                        writer.write("${Build.BRAND} ${Build.MODEL}")
                                        writer.close()
                                        it.close()
                                    }
                                }
                            }
                        }
                    }
                }
                j.join()
            }
        }
    }
}