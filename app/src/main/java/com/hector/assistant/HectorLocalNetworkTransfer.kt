package com.hector.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Explicit one-payload local-network transfer.
 * Accepts only encrypted Hector sync envelopes; no remote commands or automatic import.
 * The encrypted envelope protects the payload. This TCP transport is not peer authentication.
 */
internal class HectorLocalNetworkTransfer(private val context: Context) {
    private val running = AtomicBoolean(false)
    @Volatile private var server: ServerSocket? = null
    @Volatile private var receiverThread: Thread? = null

    private fun wifiIpv4Address(): String? = try {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return null
        val capabilities = manager.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        manager.getLinkProperties(network)?.linkAddresses
            ?.mapNotNull { it.address as? Inet4Address }
            ?.firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    } catch (_: Exception) {
        null
    }

    fun localIpv4Addresses(): List<String> = listOfNotNull(wifiIpv4Address())

    @Synchronized
    fun startReceiver(onStatus: (String) -> Unit, onEnvelope: (String) -> Unit): Boolean {
        if (running.get()) {
            onStatus("Receiver is already running on port $PORT.")
            return true
        }
        return try {
            val wifiIp = wifiIpv4Address()
            if (wifiIp == null) {
                onStatus("Receiver requires an active Wi-Fi connection. Mobile data is not supported.")
                return false
            }
            val socket = ServerSocket(PORT, 1, InetAddress.getByName(wifiIp))
            socket.soTimeout = 1000
            server = socket
            running.set(true)
            receiverThread = Thread({
                onStatus("Receiver listening on ${localIpv4Addresses().joinToString()} port $PORT. Keep both devices on trusted private Wi-Fi.")
                while (running.get()) {
                    try {
                        socket.accept().use { client ->
                            client.soTimeout = 8000
                            val input = DataInputStream(BufferedInputStream(client.getInputStream()))
                            val length = input.readInt()
                            if (length !in 2..MAX_ENVELOPE_BYTES) {
                                onStatus("Rejected transfer: invalid payload size.")
                                return@use
                            }
                            val bytes = ByteArray(length)
                            input.readFully(bytes)
                            val envelope = bytes.toString(Charsets.UTF_8)
                            val valid = try {
                                val json = JSONObject(envelope)
                                json.optString("version") == "hector-sync-v1" &&
                                    json.optString("cipher") == "AES-256-GCM" &&
                                    json.optString("kdf") == "PBKDF2WithHmacSHA256"
                            } catch (_: Exception) { false }
                            if (!valid) {
                                onStatus("Rejected transfer: not a supported encrypted Hector envelope.")
                                return@use
                            }
                            DataOutputStream(BufferedOutputStream(client.getOutputStream())).use { out ->
                                out.writeUTF("OK")
                                out.flush()
                            }
                            onEnvelope(envelope)
                            onStatus("Encrypted envelope received. Not imported automatically; decrypt only if you trust the sender.")
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Periodically check the explicit stop flag.
                    } catch (e: SocketException) {
                        if (running.get()) onStatus("Receiver socket error: ${e.message ?: "unknown"}")
                    } catch (e: Exception) {
                        if (running.get()) onStatus("Transfer rejected or interrupted: ${e.message ?: "unknown"}")
                    }
                }
            }, "Hector-LAN-receiver").apply { isDaemon = true; start() }
            true
        } catch (e: Exception) {
            running.set(false)
            server = null
            onStatus("Could not start receiver: ${e.message ?: "unknown error"}")
            false
        }
    }

    fun sendEnvelope(host: String, envelope: String, onStatus: (String) -> Unit) {
        val cleanHost = host.trim()
        val bytes = envelope.toByteArray(Charsets.UTF_8)
        if (cleanHost.isBlank() || cleanHost.contains("/") || cleanHost.contains(":")) {
            onStatus("Enter the receiver's IPv4 address only, without a port or URL.")
            return
        }
        if (bytes.size !in 2..MAX_ENVELOPE_BYTES) {
            onStatus("No valid encrypted envelope is available, or it is too large.")
            return
        }
        val valid = try {
            val json = JSONObject(envelope)
            json.optString("version") == "hector-sync-v1" &&
                json.optString("cipher") == "AES-256-GCM" &&
                json.optString("kdf") == "PBKDF2WithHmacSHA256"
        } catch (_: Exception) { false }
        if (!valid) {
            onStatus("Only an encrypted Hector sync envelope can be sent.")
            return
        }
        Thread({
            try {
                Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(cleanHost, PORT), 5000)
                    socket.soTimeout = 8000
                    DataOutputStream(BufferedOutputStream(socket.getOutputStream())).use { out ->
                        out.writeInt(bytes.size)
                        out.write(bytes)
                        out.flush()
                        val response = DataInputStream(BufferedInputStream(socket.getInputStream())).readUTF()
                        onStatus(if (response == "OK") "Encrypted envelope delivered. It has not been imported." else "Receiver returned an unexpected response.")
                    }
                }
            } catch (e: Exception) {
                onStatus("Transfer failed: ${e.message ?: "check Wi-Fi, receiver IP, and port $PORT"}.")
            }
        }, "Hector-LAN-sender").apply { isDaemon = true; start() }
    }

    @Synchronized
    fun stop() {
        running.set(false)
        try { server?.close() } catch (_: Exception) { }
        server = null
        receiverThread = null
    }

    companion object {
        const val PORT = 43821
        private const val MAX_ENVELOPE_BYTES = 400 * 1024
    }
}
