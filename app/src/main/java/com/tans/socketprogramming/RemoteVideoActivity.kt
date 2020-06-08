package com.tans.socketprogramming

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioDeviceCallback
import android.media.MediaCodec
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
import androidx.camera.core.*
import androidx.camera.core.impl.VideoCaptureConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.google.common.util.concurrent.ListenableFuture
import com.tans.socketprogramming.video.*
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_remote_video.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.Serializable
import java.lang.Runnable
import java.net.InetAddress
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.round

class RemoteVideoActivity : BaseActivity() {

    val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> by lazy { ProcessCameraProvider.getInstance(this) }

    val cameraXAnalysisResult: Channel<ByteArray> = Channel(Channel.BUFFERED)

    val encoder: MediaCodec by lazy { createDefaultEncodeMediaCodec() }
    val decoder: MediaCodec by lazy {
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

        matrix.postRotate(270f, cx, cy)
        matrix.preScale(xScale, yScale, cx, cy)
        remote_preview_view.setTransform(matrix)
        createDefaultDecodeMediaCodec(Surface(remote_preview_view.surfaceTexture))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_video)
        launch {
            val hasPermission = RxPermissions(this@RemoteVideoActivity)
                .request(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                .firstOrError()
                .toSuspend()

            if (hasPermission) {
                whenRemotePreviewSurfaceReady()
                // Init camera
                val cameraProvider = whenCameraProviderReady()
                val preview = createPreview()
                val imageAnalysis = createAnalysis()
                val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this@RemoteVideoActivity, cameraSelector, preview, imageAnalysis)
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
        decoder.start()
        withContext(Dispatchers.IO) {
            val bufferInfo = MediaCodec.BufferInfo()
            cameraXAnalysisResult.consumeEach { result ->
                // Decode the result.
                println("Result: $result")
                val inputIndex = decoder.dequeueInputBuffer(-1)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        inputBuffer.put(result)
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            result.size,
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
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_BLOCK_PRODUCER)
            .setMaxResolution(Size(VIDEO_WITH, VIDEO_HEIGHT))
            .build()
        val encoderAnalyzer = EncoderAnalyzer(encoder) { result -> runBlocking(Dispatchers.IO) { cameraXAnalysisResult.send(result) } }
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

class EncoderAnalyzer(val encoder: MediaCodec, val callback: (result: ByteArray) -> Unit) : ImageAnalysis.Analyzer {

    init { encoder.start() }

    override fun analyze(image: ImageProxy) {
        val inputIndex = encoder.dequeueInputBuffer(-1)
        if (inputIndex >= 0) {
            val inputBuffer = encoder.getInputBuffer(inputIndex)
            inputBuffer?.clear()
            // NV12
            val arrayByte = image.planes[0].buffer.toByteArray() + image.planes[1].buffer.toByteArray()
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
                    callback(result)
                }
            }
        }
        image.close()
    }

}
