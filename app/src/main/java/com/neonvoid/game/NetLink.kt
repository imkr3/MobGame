package com.neonvoid.game

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * One length-prefixed message stream over a socket, with a reader thread and a
 * writer thread so the render loop never blocks on the network. Every failure
 * path just closes the link - co-op dropping out must never take the game down.
 */
class NetLink(private val socket: Socket) {

    private val inbox = ArrayDeque<NetMessage>()
    private val outbox = ArrayBlockingQueue<ByteArray>(64)

    @Volatile var closed = false
        private set

    private var reader: Thread? = null
    private var writer: Thread? = null

    /** Set when the link dies on its own, so the game can report it. */
    @Volatile var failure: String? = null
        private set

    fun start() {
        try {
            socket.tcpNoDelay = true
        } catch (_: Exception) {
        }
        reader = Thread({ readLoop() }, "NeonVoidNetRead").also { it.isDaemon = true; it.start() }
        writer = Thread({ writeLoop() }, "NeonVoidNetWrite").also { it.isDaemon = true; it.start() }
    }

    private fun readLoop() {
        try {
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
            while (!closed) {
                val len = input.readInt()
                if (len <= 0 || len > Proto.MAX_FRAME) throw IllegalStateException("bad frame $len")
                val frame = ByteArray(len)
                input.readFully(frame)
                val type = frame[0].toInt() and 0xFF
                val payload = ByteArray(len - 1)
                System.arraycopy(frame, 1, payload, 0, len - 1)
                synchronized(inbox) {
                    if (inbox.size < 256) inbox.addLast(NetMessage(type, payload))
                }
            }
        } catch (e: Exception) {
            if (!closed) failure = e.message ?: "connection lost"
            close()
        }
    }

    private fun writeLoop() {
        try {
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            while (!closed) {
                val frame = outbox.poll(200, TimeUnit.MILLISECONDS) ?: continue
                output.writeInt(frame.size)
                output.write(frame)
                // drain anything else already queued before flushing
                while (true) {
                    val more = outbox.poll() ?: break
                    output.writeInt(more.size)
                    output.write(more)
                }
                output.flush()
            }
        } catch (e: Exception) {
            if (!closed) failure = e.message ?: "connection lost"
            close()
        }
    }

    /** Queues a frame. Returns false if the link is saturated or closed. */
    fun send(frame: ByteArray): Boolean {
        if (closed) return false
        return outbox.offer(frame)
    }

    /**
     * Queues a frame that is safe to drop when the link is backed up - used for
     * snapshots, where a stale one is worse than a missing one.
     */
    fun sendLossy(frame: ByteArray) {
        if (closed) return
        if (outbox.remainingCapacity() < 8) outbox.poll()
        outbox.offer(frame)
    }

    fun poll(): NetMessage? = synchronized(inbox) { inbox.removeFirstOrNull() }

    fun close() {
        if (closed) return
        closed = true
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }
}
