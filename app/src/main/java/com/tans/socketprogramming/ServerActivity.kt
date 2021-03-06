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
                val bindResult = serverSocket.bindSuspend(InetSocketAddress(null as InetAddress?, CONFIRM_PORT), MAX_CONNECT)
                val acceptResult = serverSocket.acceptSuspend()
                if (bindResult.isFailure() || acceptResult.isFailure()) {
                    println("Wait client connect error: ${bindResult.errorOrNull()?.toString().orEmpty()}${acceptResult.errorOrNull()?.toString().orEmpty()}")
                    waitClientConnect().join()
                } else {
                    acceptResult.resultOrNull()!!.use {
                        val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                        val readWriteResult = runCatching {
                            val clientName = reader.readLineSuspend()
                            val clientAddress = it.inetAddress
                            val result = withContext(Dispatchers.Main) {
                                alertDialog(
                                    "Connect Request",
                                    "Accept $clientName(${clientAddress.hostAddress})"
                                )
                            }
                            val writer = BufferedWriter(OutputStreamWriter(it.getOutputStream()))
                            writer.writeSuspend(result.toString() + '\n')
                            if (!result) {
                                waitClientConnect()
                            } else {
                                withContext(Dispatchers.Main) {
                                    startActivity(
                                        RemoteVideoActivity.createServerIntent(
                                            this@ServerActivity,
                                            RemoteVideoActivity.Companion.ClientInfo(
                                                clientName, clientAddress
                                            )
                                        )
                                    )
                                    overridePendingTransition(0, 0)
                                    finish()
                                }
                            }
                        }
                        if (readWriteResult.isFailure()) {
                            println("Read Write client data error: ${readWriteResult.errorOrNull()?.message}")
                            readWriteResult.errorOrNull()?.printStackTrace()
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
            val bindResult = socket.bindSuspend(InetSocketAddress(BROADCAST_SEND_PORT))
            if (bindResult.isSuccess()) {
                socket.use {
                    val data = "${Build.BRAND} ${Build.MODEL}".toByteArray(Charsets.UTF_8)
                    val dataLen = data.size.toByteArray()
                    val lenAndData = dataLen + data
                    while (true) {
                        val job = async(Dispatchers.IO) {
                            val packet = DatagramPacket(
                                lenAndData,
                                0,
                                lenAndData.size,
                                InetSocketAddress(
                                    broadcastIpAddress(),
                                    BROADCAST_RECEIVE_PORT
                                )
                            )
                            socket.sendSuspend(packet).isSuccess()
                        }
                        if (!job.await() && !isActive) {
                            break
                        } else {
                            continue
                        }
                    }
                }
            } else {
                socket.close()
                sendBroadCast()
            }

        }
    }

    fun broadcastIpAddress(): InetAddress {
        val wifiManager: WifiManager? = getSystemService()
        return wifiManager?.dhcpInfo?.let {
            val ip = InetAddress.getByAddress(it.ipAddress.toIpAddr().toInetByteArray())
            val networkInterface = NetworkInterface.getByInetAddress(ip)
            networkInterface.interfaceAddresses.map { interfaceAddress ->  interfaceAddress.broadcast }.findLast { broadcast -> broadcast != null }
        } ?: InetAddress.getByAddress((-1).toIpAddr().toInetByteArray())
    }
}