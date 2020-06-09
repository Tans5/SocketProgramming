package com.tans.socketprogramming

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.os.Bundle
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.google.common.util.concurrent.ListenableFuture
import com.tans.socketprogramming.video.*
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.activity_remote_video.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Serializable
import java.lang.Runnable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.round

class RemoteVideoActivity : BaseActivity() {

    val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> by lazy { ProcessCameraProvider.getInstance(this) }

    val cameraXAnalysisResult: BroadcastChannel<ByteArray> = BroadcastChannel(Channel.BUFFERED)

    val remoteData: BroadcastChannel<ByteArray> = BroadcastChannel(Channel.BUFFERED)

    val encoder: MediaCodec by lazy { createDefaultEncodeMediaCodec() }
    val decoder: MediaCodec by lazy { createDefaultDecodeMediaCodec(Surface(remote_preview_view.surfaceTexture)) }

    var degrees: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_video)
        launch {
            val hasPermission = RxPermissions(this@RemoteVideoActivity)
                .request(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                .firstOrError()
                .toSuspend()

            if (hasPermission) {
                startCamera()
                workAsServer()
                workAsClient()
                decodeRemoteData()
            }
        }
    }

    suspend fun startCamera() {
        whenRemotePreviewSurfaceReady()
        // Init camera
        val cameraProvider = whenCameraProviderReady()
        val preview = createPreview()
        val imageAnalysis = createAnalysis()
        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(this@RemoteVideoActivity, cameraSelector, preview, imageAnalysis)
    }

    fun workAsServer(): Job = launch {
        val clientInfo = getClientInfo(intent)
        if (clientInfo != null) {
            title_toolbar.title = clientInfo.clientName
            serverListen()
        }
    }

    fun serverListen(): Job = launch(Dispatchers.IO) {
        ServerSocket().use { serverSocket ->
            val hasBind = serverSocket.bindSuspend(InetSocketAddress(null as InetAddress?, VIDEO_PORT), MAX_CONNECT)
            if (hasBind) {
                val client = serverSocket.acceptSuspend()
                client?.use {
                    // Read
                    val readJob = launch(Dispatchers.IO) {
                        try {
                            val bis = BufferedInputStream(client.getInputStream(), BUFFER_SIZE)
                            val sizeByteArray = ByteArray(4)
                            bis.read(sizeByteArray)
                            var size = sizeByteArray.toInt()
                            var remoteResult = ByteArray(size)
                            var resultCount = bis.read(remoteResult)
                            while (resultCount > 0) {
                                remoteData.send(remoteResult)
                                bis.read(sizeByteArray)
                                size = sizeByteArray.toInt()
                                remoteResult = ByteArray(size)
                                resultCount = bis.read(remoteResult)
                            }
                        } catch (e: Throwable) {
                            println(e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@RemoteVideoActivity, "Client has close connect.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    // Write
                    val writeJob = launch(Dispatchers.IO) {
                        try {
                            val bos = client.getOutputStream()
                            cameraXAnalysisResult.asFlow().collect { bytes: ByteArray -> bos.write(bytes) }
                        } catch (e: Throwable) {
                            println(e)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@RemoteVideoActivity, "Client has close connect.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    readJob.join()
                    writeJob.join()
                } ?: serverListen()
            } else {
                serverListen()
            }
        }
    }

    fun workAsClient(): Job = launch {
        val serverInfo = getServerInfo(intent)
        if (serverInfo != null) {
            title_toolbar.title = serverInfo.serverName
            connectServer(serverInfo.serverAddress)
        }
    }

    fun connectServer(serverAddr: InetAddress): Job = launch(Dispatchers.IO) {
        val client = Socket()
        val endPoint = InetSocketAddress(serverAddr, VIDEO_PORT)
        val connectResult = client.connectSuspend(endPoint = endPoint)
        if (connectResult) {
            client.use {
                // Read
                val readJob = launch(Dispatchers.IO) {
                    try {
                        val bis = BufferedInputStream(client.getInputStream(), BUFFER_SIZE)
                        val sizeByteArray = ByteArray(4)
                        bis.read(sizeByteArray)
                        var size = sizeByteArray.toInt()
                        var remoteResult = ByteArray(size)
                        var resultCount = bis.read(remoteResult)
                        while (resultCount > 0) {
                            remoteData.send(remoteResult)
                            bis.read(sizeByteArray)
                            size = sizeByteArray.toInt()
                            remoteResult = ByteArray(size)
                            resultCount = bis.read(remoteResult)
                        }
                    } catch (e: Throwable) {
                        println(e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@RemoteVideoActivity, "Server has close connect.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                // Write
                val writeJob = launch(Dispatchers.IO) {
                    try {
                        val bos = client.getOutputStream()
                        cameraXAnalysisResult.asFlow().collect { bytes: ByteArray -> bos.write(bytes) }
                    } catch (e: Throwable) {
                        println(e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@RemoteVideoActivity, "Server has close connect.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                readJob.join()
                writeJob.join()
            }
        } else {
            connectServer(serverAddr)
        }
    }

    suspend fun decodeRemoteData() = withContext(Dispatchers.IO) {
        decoder.start()
        val bufferInfo = MediaCodec.BufferInfo()
        remoteData.asFlow().collect { bytes ->
            val inputIndex = try {
                decoder.dequeueInputBuffer(-1)
            } catch (e: Throwable) {
                println(e)
                return@collect
            }
            if (inputIndex >= 0) {
                val inputBuffer = decoder.getInputBuffer(inputIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    inputBuffer.put(bytes)
                    decoder.queueInputBuffer(
                        inputIndex,
                        0,
                        bytes.size,
                        0,
                        0
                    )
                }
            }
            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
            if (outputIndex >= 0) {
                decoder.releaseOutputBuffer(outputIndex, true)
            }
        }
    }

    fun getClientInfo(intent: Intent): ClientInfo? = intent.getSerializableExtra(CLIENT_INFO_EXTRA) as? ClientInfo

    fun getServerInfo(intent: Intent): ServerInfo? = intent.getSerializableExtra(SERVER_INFO_EXTRA) as? ServerInfo

    suspend fun whenCameraProviderReady(): ProcessCameraProvider = suspendCoroutine { cont ->
        cameraProviderFuture.addListener(Runnable { cont.resume(cameraProviderFuture.get()) }, ContextCompat.getMainExecutor(this))
    }

    suspend fun whenRemotePreviewSurfaceReady() = suspendCoroutine<Unit> { cont ->
        remote_preview_view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture?,
                width: Int,
                height: Int
            ) {

            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {

            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
                return true
            }

            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture?,
                width: Int,
                height: Int
            ) {
                cont.resume(Unit)
            }

        }
    }

    fun createPreview(): Preview {
        val preview = Preview.Builder()
            .setTargetRotation(Surface.ROTATION_90)
            .build()
        preview.setSurfaceProvider { it.provideSurface(me_preview_view.holder.surface, Dispatchers.IO.asExecutor(), Consumer {  }) }
        return preview
    }

    @SuppressLint("RestrictedApi")
    fun createAnalysis(): ImageAnalysis {
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setMaxResolution(Size(VIDEO_WITH, VIDEO_HEIGHT))
            .setTargetRotation(Surface.ROTATION_0)
            .build()
        val encoderAnalyzer = EncoderAnalyzer(encoder) { result, degrees -> runBlocking(Dispatchers.IO) {
            if (this@RemoteVideoActivity.degrees == -1) {
                this@RemoteVideoActivity.degrees = degrees
                withContext(Dispatchers.Main) { rotationRemoteView(degrees) }
            }
            cameraXAnalysisResult.send(result)
        } }
        imageAnalysis.setAnalyzer(Dispatchers.IO.asExecutor(), encoderAnalyzer)
        return imageAnalysis
    }

    override fun onDestroy() {
        super.onDestroy()
        encoder.stop()
        encoder.release()
        decoder.stop()
        decoder.release()
    }

    fun rotationRemoteView(degrees: Int) {
        val matrix = Matrix()
        val viewWidth = remote_preview_view.measuredWidth
        val viewHeight = remote_preview_view.measuredHeight
        val cx = viewWidth.toFloat() / 2
        val cy = viewHeight.toFloat() / 2
        val ratio = VIDEO_WITH.toFloat() / VIDEO_HEIGHT.toFloat()
        var scaledWidth: Int = 0
        var scaleHeight: Int = 0
        if (viewWidth > viewHeight) {
            scaleHeight = viewWidth
            scaledWidth = round(viewWidth * ratio).toInt()
        } else {
            scaleHeight = viewHeight
            scaledWidth = round(viewHeight * ratio).toInt()
        }
        val xScale = scaledWidth.toFloat() / viewWidth.toFloat()
        val yScale = scaleHeight.toFloat() / viewHeight.toFloat()

        matrix.postRotate(degrees.toFloat(), cx, cy)
        matrix.preScale(xScale, yScale, cx, cy)
        remote_preview_view.setTransform(matrix)
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

class EncoderAnalyzer(val encoder: MediaCodec, val callback: (result: ByteArray, degrees: Int) -> Unit) : ImageAnalysis.Analyzer {

    init { encoder.start() }

    override fun analyze(image: ImageProxy) {
        val inputIndex = encoder.dequeueInputBuffer(-1)
        if (inputIndex >= 0) {
            val inputBuffer = encoder.getInputBuffer(inputIndex)
            inputBuffer?.clear()
            // NV12
            val arrayByte = if (image.planes[1].rowStride == VIDEO_WITH) {
                image.planes[0].buffer.toByteArray() + image.planes[1].buffer.toByteArray()
            } else {
                image.planes[0].buffer.toByteArray() + image.planes[1].buffer.toByteArray() + image.planes[2].buffer.toByteArray()
            }
            inputBuffer?.put(arrayByte)
            encoder.queueInputBuffer(inputIndex, 0, arrayByte.size, System.currentTimeMillis(), 0)

            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
            while (outputIndex >= 0) {
                val outputBuffer = encoder.getOutputBuffer(outputIndex)
                val result = outputBuffer?.toByteArray()
                encoder.releaseOutputBuffer(outputIndex, false)
                outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                if (result != null) {
                    callback(result.size.toByteArray() + result, image.imageInfo.rotationDegrees)
                }
            }
        }
        image.close()
    }

}
