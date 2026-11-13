package com.homeai.homecamtv.data

import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Probes a camera endpoint and reports whether it is reachable.
 *
 * RTSP cameras get an `OPTIONS` request on a raw socket (a 401 still counts as
 * reachable — the camera answered). MJPEG/HTTP cameras get a GET; the response
 * headers are enough, the body is not read.
 */
object CameraStatusChecker {

    data class Status(
        val online: Boolean,
        val latencyMs: Long,
        val detail: String
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun check(camera: Camera): Status {
        val start = SystemClock.elapsedRealtime()
        return try {
            if (camera.isRtsp) probeRtsp(camera, start) else probeHttp(camera, start)
        } catch (e: Exception) {
            Status(false, SystemClock.elapsedRealtime() - start, e.message ?: "unreachable")
        }
    }

    private fun probeRtsp(camera: Camera, start: Long): Status {
        val uri = Uri.parse(camera.streamUrl())
        val host = uri.host ?: throw IllegalArgumentException("no host")
        val port = if (uri.port > 0) uri.port else 554
        val path = uri.encodedPath ?: "/"

        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 5000)
            socket.soTimeout = 5000

            val request = StringBuilder()
                .append("OPTIONS rtsp://").append(host).append(':').append(port).append(path).append(" RTSP/1.0\r\n")
                .append("CSeq: 1\r\n")
                .append("User-Agent: HomeCamTV/1.0\r\n")
            if (camera.username.isNotBlank()) {
                val token = Base64.encodeToString(
                    "${camera.username}:${camera.password}".toByteArray(),
                    Base64.NO_WRAP
                )
                request.append("Authorization: Basic ").append(token).append("\r\n")
            }
            request.append("\r\n")

            socket.getOutputStream().apply {
                write(request.toString().toByteArray())
                flush()
            }

            val statusLine = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                ?: throw IllegalStateException("no response")

            val latency = SystemClock.elapsedRealtime() - start
            return if (statusLine.startsWith("RTSP/1.0")) {
                Status(true, latency, "RTSP " + statusLine.removePrefix("RTSP/1.0").trim())
            } else {
                Status(false, latency, statusLine)
            }
        }
    }

    private fun probeHttp(camera: Camera, start: Long): Status {
        val builder = Request.Builder().url(camera.streamUrl()).get()
        if (camera.username.isNotBlank()) {
            builder.header("Authorization", Credentials.basic(camera.username, camera.password))
        }
        http.newCall(builder.build()).execute().use { response ->
            val latency = SystemClock.elapsedRealtime() - start
            return Status(
                response.isSuccessful,
                latency,
                "HTTP ${response.code} ${response.message}"
            )
        }
    }
}

/** In-memory cache of the last status probe, shown on the browse grid. */
object StatusStore {
    private val statuses = java.util.concurrent.ConcurrentHashMap<String, CameraStatusChecker.Status>()

    fun get(cameraId: String): CameraStatusChecker.Status? = statuses[cameraId]

    fun put(cameraId: String, status: CameraStatusChecker.Status) {
        statuses[cameraId] = status
    }

    fun clear() = statuses.clear()
}
