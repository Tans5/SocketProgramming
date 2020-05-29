package com.tans.socketprogramming

import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import com.tans.socketprogramming.databinding.ServerItemLayoutBinding
import com.tans.tadapter.adapter.DifferHandler
import com.tans.tadapter.adapter.SimpleAdapter
import com.tans.tadapter.spec.SimpleAdapterSpec
import com.tans.tadapter.spec.toAdapter
import kotlinx.android.synthetic.main.activity_client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
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
                differHandler = DifferHandler(itemsTheSame = { a, b ->  a.serverAddress.hostAddress == b.serverAddress.hostAddress})
            ).toAdapter()
        }
    }

    fun receiveBroadcast(): Job {
        return launch(Dispatchers.IO) {
            ServerSocket(BROADCAST_PORT, MAX_CONNECT).use { serverSocket ->
                while (true) {
                    val client = serverSocket.acceptSuspend()
                    if (client != null) {
                        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                        val serverName = reader.readLine()
                        val serverInfoModel = ServerInfoModel(
                            serverName = serverName,
                            serverAddress = client.inetAddress,
                            updateTime = System.currentTimeMillis()
                        )
                        updateServerChannel(serverInfoModel)
                        reader.close()
                        client.close()
                    }
                    delay(200)
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
        }.filter {
            (System.currentTimeMillis() - it.updateTime) < SERVER_KEEP_ALIVE_TIME
        }
        serversChannel.send(newServers)
    }
}

// 10 seconds.
const val SERVER_KEEP_ALIVE_TIME = 10000

data class ServerInfoModel(val serverName: String,
                           val serverAddress: InetAddress,
                           val updateTime: Long)