package com.homeai.homecamtv.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Minimal MJPEG (multipart/x-mixed-replace) renderer for cameras that expose
 * an HTTP JPEG stream instead of RTSP.
 *
 * A background thread reads the HTTP response, splits it on JPEG SOI/EOI
 * markers, decodes each frame and draws it on the UI thread.
 */
class MjpegView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class ScaleType { FIT_CENTER, CENTER_CROP }

    /** Reports connection/decode problems so the host screen can show status. */
    var statusListener: ((String) -> Unit)? = null

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val matrix = Matrix()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var bitmap: Bitmap? = null
    private var scaleType = ScaleType.CENTER_CROP
    private var url: String? = null
    private var basicAuth: String? = null

    fun setScaleType(scaleType: ScaleType) {
        this.scaleType = scaleType
        invalidate()
    }

    fun start(url: String, username: String = "", password: String = "") {
        this.url = url
        this.basicAuth = if (username.isNotBlank()) Credentials.basic(username, password) else null
        stop()
        running = true
        thread = Thread { streamLoop() }.apply {
            isDaemon = true
            name = "mjpeg-reader"
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        if (bw <= 0f || bh <= 0f) return

        val scale = when (scaleType) {
            ScaleType.CENTER_CROP -> maxOf(vw / bw, vh / bh)
            ScaleType.FIT_CENTER -> minOf(vw / bw, vh / bh)
        }
        matrix.reset()
        matrix.setScale(scale, scale)
        matrix.postTranslate((vw - bw * scale) / 2f, (vh - bh * scale) / 2f)
        canvas.drawBitmap(bmp, matrix, paint)
    }

    private fun streamLoop() {
        while (running) {
            try {
                openStream()
            } catch (e: Exception) {
                if (running) {
                    notifyStatus(e.message ?: "connection error")
                    sleepQuietly(2000)
                }
            }
        }
    }

    private fun openStream() {
        val target = url ?: return
        val requestBuilder = Request.Builder().url(target)
        basicAuth?.let { requestBuilder.header("Authorization", it) }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("empty response")
            parseStream(BufferedInputStream(body.byteStream()))
        }
    }

    /** Splits the multipart stream on JPEG SOI (FFD8) / EOI (FFD9) markers. */
    private fun parseStream(input: InputStream) {
        var state = STATE_SCAN
        var prev = -1
        val acc = java.io.ByteArrayOutputStream()

        while (running) {
            val b = input.read()
            if (b < 0) break
            when (state) {
                STATE_SCAN -> if (prev == 0xFF && b == 0xD8) {
                    acc.reset()
                    acc.write(0xFF)
                    acc.write(0xD8)
                    state = STATE_FRAME
                }
                STATE_FRAME -> {
                    acc.write(b)
                    if (prev == 0xFF && b == 0xD9) {
                        val jpeg = acc.toByteArray()
                        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                        if (decoded != null) publishFrame(decoded)
                        acc.reset()
                        state = STATE_SCAN
                    }
                }
            }
            prev = b
            if (acc.size() > MAX_BUFFER) {
                acc.reset()
                state = STATE_SCAN
            }
        }
    }

    private fun publishFrame(frame: Bitmap) {
        post {
            if (!running) return@post
            val old = bitmap
            bitmap = frame
            old?.recycle()
            invalidate()
        }
    }

    private fun notifyStatus(message: String) {
        post { if (running) statusListener?.invoke(message) }
    }

    private fun sleepQuietly(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private const val STATE_SCAN = 0
        private const val STATE_FRAME = 1
        private const val MAX_BUFFER = 8 * 1024 * 1024
    }
}
