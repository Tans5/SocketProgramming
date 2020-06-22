package com.tans.socketprogramming

import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.getSystemService
import com.tans.socketprogramming.databinding.ServerItemLayoutBinding
import com.tans.tadapter.adapter.DifferHandler
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.toAdapter
import kotlinx.android.synthetic.main.activity_client.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.firstOrNull
import java.io.*
import java.net.*

/**
 *
 * author: pengcheng.tan
 * date: 2020/5/19
 */
class ClientActivity : BaseActivity() {

    val serversChannel: BroadcastChannel<List<ServerInfoModel>> = BroadcastChannel(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)
        receiveBroadcast()

        launch {
            serversChannel.send(emptyList())
            services_rv.adapter = SimpleAdapterSpec<ServerInfoModel, ServerItemLayoutBinding>(
                layoutId = R.layout.server_item_layout,
                bindData = { _, data, binding -> binding.data = data},
                dataUpdater = serversChannel.asFlow().toObservable(this),
                differHandler = DifferHandler(itemsTheSame = { a, b ->  a.serverAddress.hostAddress == b.serverAddress.hostAddress},
                    contentTheSame = { a, b -> a.serverName == b.serverName }),
                itemClicks = listOf { binding, _ ->
                    binding.root to { _, data ->
                        asyncAsSingle {
                            val loadingDialog = createLoadingDialog()
                            loadingDialog.show()
                            val result = tryToConnectServer(data)
                            if (result) {
                                startActivity(RemoteVideoActivity.createClientIntent(this@ClientActivity, RemoteVideoActivity.Companion.ServerInfo(
                                    serverName = data.serverName,
                                    serverAddress = data.serverAddress
                                )))
                                overridePendingTransition(0, 0)
                                finish()
                            } else {
                                Toast.makeText(this@ClientActivity, "Connect to ${data.serverAddress.hostAddress} fail, try later.", Toast.LENGTH_SHORT).show()
                            }
                            loadingDialog.cancel()
                        }
                    }
                }
            ).toAdapter()
            launch {
                while (true) {
                    delay(CHECK_SERVERS_DURATION)
                    removeOutOfDateServers()
                }
            }
        }
    }

    fun receiveBroadcast(): Job {
        return launch(Dispatchers.IO) {
//            ServerSocket().use { serverSocket ->
//                val result = serverSocket.bindSuspend(InetSocketAddress(null as InetAddress?, BROADCAST_PORT), Int.MAX_VALUE)
//                if (result) {
//                    while (true) {
//                        val client = serverSocket.acceptSuspend()
//                        client?.use {
//                            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
//                            val serverName = reader.readLine()
//                            val serverInfoModel = ServerInfoModel(
//                                serverName = serverName ?: "",
//                                serverAddress = client.inetAddress,
//                                updateTime = System.currentTimeMillis()
//                            )
//                            updateServerChannel(serverInfoModel)
//                        }
//                        delay(200)
//                    }
//                } else {
//                    withContext(Dispatchers.Main) {
//                        Toast.makeText(this@ClientActivity, "Bind Broadcast server error", Toast.LENGTH_SHORT).show()
//                    }
//                }
//            }
            val socket = DatagramSocket(null)
            val bindResult = socket.bindSuspend(InetSocketAddress(BROADCAST_RECEIVE_PORT))
            if (bindResult.isSuccess()) {
                socket.use {
                    while (true) {
                        val job = async(Dispatchers.IO) {
                            val result = ByteArray(BROADCAST_BUFFER_SIZE)
                            val packet = DatagramPacket(
                                result,
                                0,
                                BROADCAST_BUFFER_SIZE,
                                InetSocketAddress(BROADCAST_SEND_PORT)
                            )
                            if (socket.receiveSuspend(packet).isSuccess()) {
                                val len = result.slice(0 until 4).toByteArray().toInt()
                                val serverName =
                                    result.slice(4 until len + 4).toByteArray()
                                        .toString(Charsets.UTF_8)
                                val serverAddress = packet.address
                                println("Find Server: $serverName, addr: ${serverAddress.hostAddress}")
                                updateServerChannel(
                                    ServerInfoModel(
                                        serverName = serverName,
                                        serverAddress = serverAddress,
                                        updateTime = System.currentTimeMillis()
                                    )
                                )
                                true
                            } else {
                                false
                            }
                        }
                        if (job.await()) {
                            continue
                        } else {
                            break
                        }
                    }
                }
            } else {
                socket.disconnect()
                socket.close()
                receiveBroadcast()
            }
        }
    }

    suspend fun updateServerChannel(serverInfoModel: ServerInfoModel) {
        val lastServers = serversChannel.asFlow().firstOrNull() ?: emptyList()
        val needUpdateServer = lastServers.find { it.serverAddress.hostAddress == serverInfoModel.serverAddress.hostAddress }
        val newServers = if (needUpdateServer == null) {
            lastServers + serverInfoModel
        } else {
            lastServers.map { if (it.serverAddress == serverInfoModel.serverAddress) serverInfoModel else it }
        }
        serversChannel.send(newServers)
    }

    suspend fun removeOutOfDateServers() {
        val lastServers = serversChannel.asFlow().firstOrNull() ?: emptyList()
        val newServers = lastServers.filter { (System.currentTimeMillis() - it.updateTime) < SERVER_KEEP_ALIVE_TIME }
        serversChannel.send(newServers)
    }

    suspend fun tryToConnectServer(server: ServerInfoModel): Boolean {
        val deferred = async(Dispatchers.IO) {
            val socket = Socket()
            val connectResult = socket.connectSuspend(endPoint = InetSocketAddress(server.serverAddress, CONFIRM_PORT))
            if (connectResult.isSuccess()) {
                socket.use {
                    val readWriteResult = runCatching {
                        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        writer.writeSuspend("${Build.BRAND} ${Build.MODEL}\n")
                        val accept = reader.readLineSuspend()
                        accept == true.toString()
                    }
                    if (readWriteResult.isFailure()) {
                        println("Try connect to server ReadWrite error: ${readWriteResult.errorOrNull()}")
                        readWriteResult.errorOrNull()?.printStackTrace()
                        false
                    } else {
                        true
                    }
                }
            } else {
                false
            }
        }
        return deferred.await()
    }

    companion object {
        // 4 seconds.
        const val SERVER_KEEP_ALIVE_TIME: Long = 4000

        // 1 seconds
        const val CHECK_SERVERS_DURATION: Long = 1000

        data class ServerInfoModel(val serverName: String,
                                   val serverAddress: InetAddress,
                                   val updateTime: Long)
    }

}