package com.homeai.homecamtv.data

import java.io.Serializable
import java.util.UUID

/**
 * A camera endpoint the user can view.
 *
 * RTSP cameras are played with ExoPlayer's RtspMediaSource; MJPEG cameras are
 * rendered by [com.homeai.homecamtv.player.MjpegView].
 *
 * [serialNumber] and [host] are the device's identifiers: the serial as printed
 * on the label, and the IP address or domain name it is reachable at.
 */
data class Camera(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val serialNumber: String? = null,
    val protocol: String = PROTOCOL_RTSP,
    val host: String = "",
    val port: Int = 554,
    val path: String = "",
    val username: String = "",
    val password: String = "",
    val group: String = GROUP_DEFAULT,
    val channel: Int = 1,
    val enableAudio: Boolean = true
) : Serializable {

    val isRtsp: Boolean get() = protocol.equals(PROTOCOL_RTSP, ignoreCase = true)

    /** Full stream URL, including basic-auth credentials when set. */
    fun streamUrl(): String {
        val scheme = if (isRtsp) "rtsp" else "http"
        val auth = if (username.isNotBlank()) {
            val user = java.net.URLEncoder.encode(username, "UTF-8")
            val pass = if (password.isNotBlank()) ":" + java.net.URLEncoder.encode(password, "UTF-8") else ""
            "$user$pass@"
        } else {
            ""
        }
        val portPart = if (port > 0) ":$port" else ""
        val pathPart = if (path.startsWith("/")) path else "/$path"
        return "$scheme://$auth$host$portPart$pathPart"
    }

    /** Short human-readable endpoint, used in the details screen. */
    fun endpoint(): String {
        val portPart = if (port > 0) ":$port" else ""
        return "$protocol://$host$portPart$path"
    }

    /** Identifier used when crawling the web: name (brand) first, then serial. */
    fun lookupQuery(): String =
        listOfNotNull(
            name.takeIf { it.isNotBlank() },
            serialNumber?.takeIf { it.isNotBlank() }
        ).joinToString(" ")

    companion object {
        const val PROTOCOL_RTSP = "rtsp"
        const val PROTOCOL_MJPEG = "mjpeg"
        const val GROUP_DEFAULT = "Default"

        val PROTOCOLS = listOf(PROTOCOL_RTSP, PROTOCOL_MJPEG)
    }
}
