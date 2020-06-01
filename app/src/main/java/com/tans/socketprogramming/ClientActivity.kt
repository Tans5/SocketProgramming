package com.tans.socketprogramming

import android.net.wifi.WifiManager
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.*

/**
 *
 * author: pengcheng.tan
 * date: 2020/5/19
 */
class ClientActivity : BaseActivity() {

    val serversChannel: BroadcastChannel<List<ServerInfoModel>> = BroadcastChannel(Channel.CONFLATED)

    val ip: IntArray by lazy {
        (getSystemService<WifiManager>()?.connectionInfo?.ipAddress ?: 0).toIpAddr()
    }

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
                            Toast.makeText(this@ClientActivity, data.serverAddress.hostAddress, Toast.LENGTH_SHORT).show()
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
            val serverSocket = ServerSocket()
            serverSocket.use { serverSocket ->
                val result = serverSocket.bindSuspend(InetSocketAddress(null as InetAddress?, BROADCAST_PORT), Int.MAX_VALUE)
                if (result) {
                    while (true) {
                        val client = serverSocket.acceptSuspend()
                        if (client != null) {
                            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                            val serverName = reader.readLine()
                            val serverInfoModel = ServerInfoModel(
                                serverName = serverName ?: "",
                                serverAddress = client.inetAddress,
                                updateTime = System.currentTimeMillis()
                            )
                            updateServerChannel(serverInfoModel)
                            reader.close()
                            client.close()
                        }
                        delay(200)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ClientActivity, "Bind Broadcast server error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    suspend fun updateServerChannel(serverInfoModel: ServerInfoModel) {
        val lastServers = serversChannel.asFlow().firstOrNull() ?: emptyList()
        val needUpdateServer = lastServers.find { it.serverAddress.hostAddress == serverInfoModel.serverAddress.hostAddress }
        val newServers = if (needUpdateServer == null) {
            lastServers + serverInfoModel
        } else {
            lastServers - needUpdateServer + serverInfoModel
        }
        serversChannel.send(newServers)
    }

    suspend fun removeOutOfDateServers() {
        val lastServers = serversChannel.asFlow().firstOrNull() ?: emptyList()
        val newServers = lastServers.filter { (System.currentTimeMillis() - it.updateTime) < SERVER_KEEP_ALIVE_TIME }
        serversChannel.send(newServers)
    }

}

// 10 seconds.
const val SERVER_KEEP_ALIVE_TIME: Long = 10000

// 5 seconds
const val CHECK_SERVERS_DURATION: Long = 5000

data class ServerInfoModel(val serverName: String,
                           val serverAddress: InetAddress,
                           val updateTime: Long)