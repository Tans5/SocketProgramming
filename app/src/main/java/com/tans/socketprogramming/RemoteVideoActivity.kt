package com.tans.socketprogramming

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.core.impl.VideoCaptureConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.google.common.util.concurrent.ListenableFuture
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_remote_video.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import java.io.File
import java.io.Serializable
import java.net.InetAddress
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class RemoteVideoActivity : BaseActivity() {

    val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> by lazy { ProcessCameraProvider.getInstance(this) }

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_video)
        launch {
            val hasPermission = RxPermissions(this@RemoteVideoActivity)
                .request(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                .firstOrError()
                .toSuspend()

            if (hasPermission) {
                val cameraProvider = whenCameraProviderReady()
                val preview = createPreview()

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)
                    .build()

                imageAnalyzer.setAnalyzer(Executors.newSingleThreadExecutor(), ImageAnalysis.Analyzer { image ->

                    println("Size: ${image.width} * ${image.height}")

                    if (image.format == ImageFormat.YUV_420_888) {
                        println("Format: YUV_420_888")
                        image.planes.forEach { plane ->
                            val buffer = plane.buffer
                            buffer.rewind()
                            val br = ByteArray(buffer.limit())
                            buffer.get(br)
                            println("PixelStride: ${plane.pixelStride}, RowStride: ${plane.rowStride}, Size: ${plane.buffer.limit()}")
                        }
                    }
                    image.close()
                })

                val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(this@RemoteVideoActivity, cameraSelector, preview, imageAnalyzer)

//                val videoCapture = VideoCaptureConfig.Builder().apply {
//                    setCameraSelector(cameraSelector)
//                    setBackgroundExecutor(Dispatchers.IO.asExecutor())
//                }.build()
//                val videoFile = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "${System.currentTimeMillis()}.mp4")
//
//
//                start_recording_bt.setOnClickListener {
//                    videoCapture.startRecording(videoFile,
//                        Dispatchers.IO.asExecutor(),
//                        object : VideoCapture.OnVideoSavedCallback {
//                            override fun onVideoSaved(file: File) {
//                                println("Video Saved.")
//                            }
//
//                            override fun onError(
//                                videoCaptureError: Int,
//                                message: String,
//                                cause: Throwable?
//                            ) {
//                                println("Start Recording error: $cause")
//                            }
//
//                        })
//                }
//                stop_recording_bt.setOnClickListener {
//                    videoCapture.stopRecording()
//                }

                workAsServer()
                workAsClient()
            }
        }
    }

    fun workAsServer(): Job = launch {
        val clientInfo = getClientInfo(intent)
        if (clientInfo != null) {
            title_toolbar.title = clientInfo.clientName
        }
    }

    fun workAsClient(): Job = launch {
        val serverInfo = getServerInfo(intent)
        if (serverInfo != null) {
            title_toolbar.title = serverInfo.serverName
        }
    }

    fun getClientInfo(intent: Intent): ClientInfo? = intent.getSerializableExtra(CLIENT_INFO_EXTRA) as? ClientInfo

    fun getServerInfo(intent: Intent): ServerInfo? = intent.getSerializableExtra(SERVER_INFO_EXTRA) as? ServerInfo

    suspend fun whenCameraProviderReady(): ProcessCameraProvider = suspendCoroutine { cont ->
        cameraProviderFuture.addListener(Runnable { cont.resume(cameraProviderFuture.get()) }, ContextCompat.getMainExecutor(this))
    }

    fun createPreview(): Preview {
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider { it.provideSurface(me_preview_view.holder.surface, Dispatchers.IO.asExecutor(), Consumer {  }) }
        return preview
    }

    companion object {

        private const val CLIENT_INFO_EXTRA = "client info extra"
        private const val SERVER_INFO_EXTRA = "server info extra"

        fun createServerIntent(context: Context,
                               clientInfo: ClientInfo): Intent {
            val i = Intent(context, RemoteVideoActivity::class.java)
            i.putExtra(CLIENT_INFO_EXTRA, clientInfo)
            return i
        }

        fun createClientIntent(context: Context,
                               serverInfo: ServerInfo): Intent {
            val i = Intent(context, RemoteVideoActivity::class.java)
            i.putExtra(SERVER_INFO_EXTRA, serverInfo)
            return i
        }

        data class ClientInfo(
            val clientName: String,
            val clientAddress: InetAddress
        ) : Serializable

        data class ServerInfo(
            val serverName: String,
            val serverAddress: InetAddress
        ) : Serializable
    }
}
