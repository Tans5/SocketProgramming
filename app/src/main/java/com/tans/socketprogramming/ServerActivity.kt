package com.tans.socketprogramming

import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.getSystemService
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.*

/**
 *
 * author: pengcheng.tan
 * date: 2020/5/19
 */
class ServerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server)
        waitClientConnect()
        sendBroadCast()
    }

    fun waitClientConnect(): Job {
        return launch(Dispatchers.IO) {
            ServerSocket().use { serverSocket ->
                serverSocket.bindSuspend(InetSocketAddress(null as InetAddress?, CONFIRM_PORT), MAX_CONNECT)
                val client = serverSocket.acceptSuspend()
                client?.use {
                    val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                    val clientName = reader.readLine()
                    val clientAddress = it.inetAddress
                    val result = withContext(Dispatchers.Main) {
                        alertDialog("Connect Request", "Accept $clientName(${clientAddress.hostAddress})")
                    }
                    val writer = BufferedWriter(OutputStreamWriter(it.getOutputStream()))
                    writer.write(result.toString() + '\n')
                    writer.flush()
                    if (!result) {
                        waitClientConnect()
                    } else {
                        withContext(Dispatchers.Main) {
                            startActivity(RemoteVideoActivity.createServerIntent(this@ServerActivity, RemoteVideoActivity.Companion.ClientInfo(
                                clientName, clientAddress)))
                            overridePendingTransition(0, 0)
                            finish()
                        }
                    }
                }
            }
        }
    }

    fun sendBroadCast(): Job {
        return launch (Dispatchers.IO) {
//            while (true) {
//                launch {
//                    repeat(254) { i ->
//                        val clientNum = ip[3]
//                        if (clientNum != i + 1) {
//                            launch {
//                                val broadcastServerIp = IntArray(4) { index ->
//                                    if (index == 3) {
//                                        i + 1
//                                    } else {
//                                        ip[index]
//                                    }
//                                }
//                                val client = Socket()
//                                val endPoint = InetSocketAddress(InetAddress.getByAddress(broadcastServerIp.toInetByteArray()), BROADCAST_PORT)
//                                val result = client.connectSuspend(endPoint = endPoint)
//                                if (result) {
//                                    client.use {
//                                        val writer = BufferedWriter(OutputStreamWriter(it.getOutputStream()))
//                                        writer.write("${Build.BRAND} ${Build.MODEL}\n")
//                                        writer.flush()
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }.join()
//            }
            val socket = DatagramSocket(null)
            val hasBind = socket.bindSuspend(InetSocketAddress(BROADCAST_SEND_PORT))
            if (hasBind) {
                socket.use {
                    val data = "${Build.BRAND} ${Build.MODEL}".toByteArray(Charsets.UTF_8)
                    val dataLen = data.size.toByteArray()
                    val lenAndData = dataLen + data
                    while (true) {
                        val job = launch(Dispatchers.IO) {
                            val packet = DatagramPacket(
                                lenAndData,
                                0,
                                lenAndData.size,
                                InetSocketAddress(
                                    InetAddress.getByAddress((-1).toIpAddr().toInetByteArray()),
                                    BROADCAST_RECEIVE_PORT
                                )
                            )
                            socket.sendSuspend(packet)
                        }
                        job.join()
                    }
                }
            } else {
                socket.close()
                sendBroadCast()
            }

        }
    }
}