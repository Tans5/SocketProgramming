package com.tans.socketprogramming

import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.*
import java.net.*
import kotlin.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import java.lang.Runnable

/**
 *
 * author: pengcheng.tan
 * date: 2020/5/19
 */

suspend fun ServerSocket.acceptSuspend(workDispatcher: CoroutineDispatcher = Dispatchers.IO): Socket? {
    return try {
        blockToSuspend(workDispatcher) { accept() }
    } catch (e: SocketException) {
        println("ServerSocket accept error: $e")
        null
    }
}

suspend fun ServerSocket.bindSuspend(endPoint: InetSocketAddress, backlog: Int, workDispatcher: CoroutineDispatcher = Dispatchers.IO): Boolean = try {
    blockToSuspend(workDispatcher) { bind(endPoint, backlog) }
    true
} catch (e: Throwable) {
    println("ServerSocket bind error: $e")
    false
}

suspend fun Socket.connectSuspend(workDispatcher: CoroutineDispatcher = Dispatchers.IO,
                                  endPoint: InetSocketAddress,
                                  timeout: Int = CONNECT_TIMEOUT): Boolean = try {
    blockToSuspend(workDispatcher) { connect(endPoint, timeout) }
    true
} catch (t: Throwable) {
    println("Socket connect error: $t")
    false
}

suspend fun <T> blockToSuspend(workDispatcher: CoroutineDispatcher = Dispatchers.IO,
                               block: () -> T): T = suspendCancellableCoroutine { cont ->
    val interceptor = cont.context[ContinuationInterceptor]
    if (interceptor is CoroutineDispatcher) {
        workDispatcher.dispatch(cont.context, Runnable {
            try {
                val result = block()
                interceptor.dispatch(cont.context, Runnable { cont.resume(result) })
            } catch (e: Throwable) {
                interceptor.dispatch(cont.context, Runnable { cont.resumeWithException(e) })
            }
        })
    } else {
        cont.resumeWithException(Throwable("Can't find ContinuaDispatcher"))
    }
}

@UseExperimental(ExperimentalUnsignedTypes::class)
fun Int.toIpAddr(isRevert: Boolean = true): IntArray = IntArray(4) { i ->
    if (isRevert) {
        (this shr 8 * i and 0xff).toUByte().toInt()
    } else {
        (this shr 8 * (3 - i) and 0xff).toUByte().toInt()
    }
}

fun IntArray.toInetByteArray(isRevert: Boolean = false): ByteArray = ByteArray(4) { i ->
    if (isRevert) {
        (this[3 - i] and 0xff).toByte()
    } else {
        (this[i] and 0xff).toByte()
    }
}

fun <T> Flow<T>.toObservable(coroutineScope: CoroutineScope,
                             context: CoroutineContext = EmptyCoroutineContext): Observable<T> {
    var job: Job? = null
    var dispose: Disposable? = null
    return Observable.create<T> { emitter ->
        job = coroutineScope.launch(context) {
            try {
                collect { emitter.onNext(it) }
            } finally {
                dispose?.dispose()
                job = null
            }
        }
    }.doOnDispose { job?.cancel(); dispose = null }
        .doOnSubscribe { dispose = dispose }
}

fun <T> CoroutineScope.asyncAsSingle(context: CoroutineContext = EmptyCoroutineContext,
                      block: suspend CoroutineScope.() -> T): Single<T> {
    var job: Job? = null
    var disposable: Disposable? = null
    return Single.create<T> { emitter ->
        job = this.launch(context) {
            try {
                val deferred = async(block = block)
                val result = deferred.await()
                if (result != null) {
                    emitter.onSuccess(result)
                }
            } finally {
                disposable?.dispose()
                job = null
            }
        }
    }.doOnDispose { job?.cancel(); disposable = null}
        .doOnSubscribe { disposable = it }
}