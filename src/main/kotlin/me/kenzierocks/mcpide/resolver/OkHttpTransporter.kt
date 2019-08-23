/*
 * This file is part of MCPIDE, licensed under the MIT License (MIT).
 *
 * Copyright (c) kenzierocks <https://kenzierocks.me>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package me.kenzierocks.mcpide.resolver

import com.google.common.net.HttpHeaders
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.io.ByteChannel
import kotlinx.coroutines.io.jvm.javaio.toOutputStream
import kotlinx.coroutines.io.jvm.nio.copyTo
import kotlinx.coroutines.runBlocking
import me.kenzierocks.mcpide.util.CallResult
import me.kenzierocks.mcpide.util.enqueueSuspend
import me.kenzierocks.mcpide.util.toHttpUrl
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okio.BufferedSink
import org.apache.maven.wagon.InputData
import org.apache.maven.wagon.OutputData
import org.apache.maven.wagon.ResourceDoesNotExistException
import org.apache.maven.wagon.StreamWagon
import org.apache.maven.wagon.TransferFailedException
import org.apache.maven.wagon.Wagon
import org.apache.maven.wagon.authorization.AuthorizationException
import org.apache.maven.wagon.proxy.ProxyInfo
import org.apache.maven.wagon.resource.Resource
import org.eclipse.aether.transport.wagon.WagonProvider
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketAddress
import java.util.Properties
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

class OkHttpWagon @Inject constructor(
    private var httpClient: OkHttpClient,
    coroutineExceptionHandler: CoroutineExceptionHandler
) : StreamWagon() {
    private val ioScope = CoroutineScope(Dispatchers.IO
        + CoroutineName("OkHttpWagonPuts")
        + coroutineExceptionHandler)
    private var headers: Headers = Headers.Builder().build()
    // Headers hack, to comply with expected http-Wagon behavior
    @Suppress("unused")
    var httpHeaders: Properties
        get() = headers.toMultimap().mapValuesTo(Properties(), { (_, v) -> v.firstOrNull() })
        set(value) {
            headers = Headers.Builder()
                .also {
                    value.forEach { (name, value) -> it.add(name as String, value as String) }
                }
                .build()
        }
    private lateinit var request: Request.Builder
    private lateinit var getResponse: Response
    private lateinit var putResponse: Deferred<CallResult>

    override fun openConnectionInternal() {
        request = Request.Builder()
        getProxyInfo(repository.protocol, repository.host)?.let { proxyInfo ->
            httpClient = httpClient.newBuilder()
                .proxy(proxyInfo.asProxy())
                .build()
        }
    }

    // Run a "GET" request
    override fun fillInputData(inputData: InputData) {
        val req = request.headers(headers)
            .get()
            .url(repository.url.toHttpUrl().newBuilder()
                .addPathSegments(inputData.resource.name)
                .build())
            .build()
        val response = httpClient.newCall(req).execute()
        when (response.code) {
            HTTP_FORBIDDEN, HTTP_UNAUTHORIZED ->
                AuthorizationException("Access denied to ${req.url}")
            HTTP_NOT_FOUND ->
                ResourceDoesNotExistException("Resource ${req.url} not found")
            !in 200..299 ->
                TransferFailedException("Failed to transfer ${response.request.url}: HTTP Error ${response.code}")
            else -> null
        }?.let { error ->
            try {
                response.close()
            } catch (e: Throwable) {
                error.addSuppressed(e)
            }
            throw error
        }
        inputData.inputStream = response.body?.byteStream()
        inputData.resource.lastModified = response.headers.getInstant(HttpHeaders.LAST_MODIFIED)?.toEpochMilli() ?: -1
        inputData.resource.contentLength = response.body?.contentLength() ?: -1
    }

    override fun cleanupGetTransfer(resource: Resource?) {
        if (this::getResponse.isInitialized) {
            getResponse.closeQuietly()
        }
    }

    // Run a "PUT" request
    // This is slightly complicated because we don't have a "live" output
    // OkHttp expects us to already have the source given to us.
    // So we spin up a copy-coroutine to handle the transfer
    override fun fillOutputData(outputData: OutputData) {
        val channel = ByteChannel(false)
        val body = object : RequestBody() {
            override fun contentLength() = outputData.resource.contentLength
            override fun contentType(): MediaType? = null
            override fun isOneShot() = true

            override fun writeTo(sink: BufferedSink) {
                runBlocking {
                    channel.copyTo(sink)
                }
            }
        }

        val req = request.headers(headers)
            .put(body)
            .url(repository.url.toHttpUrl().newBuilder()
                .addPathSegments(outputData.resource.name)
                .build())
            .build()

        putResponse = ioScope.async {
            httpClient.newCall(req).enqueueSuspend()
        }

        outputData.outputStream = channel.toOutputStream()
    }

    override fun finishPutTransfer(resource: Resource?, input: InputStream?, output: OutputStream?) {
        runBlocking {
            putResponse.await().throwIfFailed()
        }.use { response ->
            when (response.code) {
                HTTP_FORBIDDEN, HTTP_UNAUTHORIZED ->
                    throw AuthorizationException("Access denied to ${response.request.url}")
                HTTP_NOT_FOUND ->
                    throw ResourceDoesNotExistException("Resource ${response.request.url} not found")
                !in 200..299 ->
                    throw TransferFailedException("Failed to transfer ${response.request.url}: HTTP Error ${response.code}")
                else -> Unit
            }
        }
    }

    override fun cleanupPutTransfer(resource: Resource) {
        if (this::putResponse.isInitialized) {
            runBlocking {
                when (val result = putResponse.await()) {
                    is CallResult.Success -> result.response.closeQuietly()
                }
            }
        }
    }

    override fun closeConnection() {
    }

}

private fun ProxyInfo.asProxy() = Proxy(proxyType, socketAddress)

private val ProxyInfo.proxyType: Proxy.Type
    get() {
        return when (type) {
            ProxyInfo.PROXY_SOCKS4, ProxyInfo.PROXY_SOCKS5 -> Proxy.Type.SOCKS
            else -> Proxy.Type.HTTP
        }
    }

private val ProxyInfo.socketAddress: SocketAddress
    get() =
        InetSocketAddress.createUnresolved(host, port)

@Singleton
class OkHttpWagonProvider @Inject constructor(
    private val delegate: Provider<OkHttpWagon>
) : WagonProvider {

    override fun lookup(roleHint: String): Wagon = when (roleHint) {
        "http", "https" -> delegate.get()
        else -> throw IllegalArgumentException("Unsupported role: $roleHint")
    }

    override fun release(wagon: Wagon?) {
    }

}
