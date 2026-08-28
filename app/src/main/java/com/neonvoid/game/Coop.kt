package com.neonvoid.game

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

enum class NetRole { NONE, HOST, CLIENT }

enum class NetStage { IDLE, LISTENING, CONNECTING, LOBBY, RUNNING, ENDED, FAILED }

/**
 * Host side of LAN co-op: owns the simulation, accepts one partner, applies
 * their input and broadcasts snapshots.
 */
class CoopHost {

    @Volatile var stage = NetStage.IDLE
        private set
    @Volatile var message = ""
        private set
    @Volatile var partnerName = ""
        private set
    @Volatile var partnerShipId = ShipDex.STARTER
        private set

    private var server: ServerSocket? = null
    private var accept: Thread? = null
    private var link: NetLink? = null

    private var tick = 0
    private var sendT = 0f
    private val events = ArrayList<Triple<Int, Float, Float>>(24)

    /** Set when the partner picks a card, consumed by the game loop. */
    @Volatile var pendingPick = -1

    val connected: Boolean get() = link?.closed == false

    fun start(): Boolean {
        return try {
            val s = ServerSocket()
            s.reuseAddress = true
            s.bind(InetSocketAddress(Proto.PORT))
            server = s
            stage = NetStage.LISTENING
            message = "WAITING FOR PARTNER"
            accept = Thread({ acceptLoop(s) }, "NeonVoidAccept").also { it.isDaemon = true; it.start() }
            true
        } catch (e: Exception) {
            stage = NetStage.FAILED
            message = e.message ?: "could not open port"
            false
        }
    }

    private fun acceptLoop(s: ServerSocket) {
        try {
            val socket = s.accept()
            val l = NetLink(socket)
            l.start()
            link = l
            stage = NetStage.LOBBY
            message = "PARTNER CONNECTED"
        } catch (e: Exception) {
            if (stage == NetStage.LISTENING) {
                stage = NetStage.FAILED
                message = e.message ?: "listen failed"
            }
        }
    }

    fun localAddress(): String = NetUtil.localIpv4() ?: "?"

    /** Handshake and lobby traffic. Returns true when the partner is ready. */
    fun pumpLobby(hostShipId: Int, hostName: String): Boolean {
        val l = link ?: return false
        if (l.closed) { fail(l.failure ?: "partner disconnected"); return false }
        while (true) {
            val m = l.poll() ?: break
            if (m.type == Proto.HELLO) {
                val i = java.io.DataInputStream(m.data.inputStream())
                val version = i.readInt()
                if (version != Proto.VERSION) {
                    fail("version mismatch")
                    return false
                }
                partnerShipId = i.readInt()
                partnerName = i.readUTF()
                l.send(Codec.welcome(1, hostShipId, hostName))
                stage = NetStage.LOBBY
                message = "$partnerName READY"
            }
        }
        return stage == NetStage.LOBBY && partnerName.isNotEmpty()
    }

    fun beginRun() {
        val m = MessageWriter(Proto.START)
        link?.send(m.bytes())
        stage = NetStage.RUNNING
        tick = 0
        sendT = 0f
    }

    fun note(type: Int, x: Float, y: Float) {
        if (events.size < 24) events.add(Triple(type, x, y))
    }

    /** Applies partner input and ships a snapshot when one is due. */
    fun pumpRun(world: World, dt: Float) {
        val l = link ?: return
        if (l.closed) {
            world.dropPartner()
            fail(l.failure ?: "partner disconnected")
            return
        }
        while (true) {
            val m = l.poll() ?: break
            when (m.type) {
                Proto.INPUT -> {
                    val i = java.io.DataInputStream(m.data.inputStream())
                    i.readInt()                                   // sequence, unused for now
                    val dx = Proto.unpackPos(i.readShort())
                    val dy = Proto.unpackPos(i.readShort())
                    val od = i.readByte().toInt() != 0
                    world.moveBy(1, dx, dy)
                    if (od) world.triggerOverdrive(1)
                }
                Proto.PICK -> {
                    val i = java.io.DataInputStream(m.data.inputStream())
                    pendingPick = i.readInt()
                }
                Proto.BYE -> {
                    world.dropPartner()
                    fail("partner left")
                    return
                }
            }
        }
        sendT -= dt
        if (sendT <= 0f) {
            sendT = 1f / Proto.SNAPSHOT_HZ
            tick++
            l.sendLossy(Codec.snapshot(world, tick, events))
            events.clear()
        }
    }

    fun sendOffer(slotIndex: Int, cards: List<AugCard>) {
        link?.send(Codec.offer(slotIndex, cards))
    }

    fun sendOver(world: World) {
        link?.send(Codec.over(world.score, world.wave, world.kills, world.maxCombo, world.levelsCleared))
        stage = NetStage.ENDED
    }

    private fun fail(reason: String) {
        stage = NetStage.FAILED
        message = reason
    }

    fun stop() {
        try {
            link?.send(MessageWriter(Proto.BYE).bytes())
        } catch (_: Exception) {
        }
        link?.close()
        link = null
        try {
            server?.close()
        } catch (_: Exception) {
        }
        server = null
        stage = NetStage.IDLE
        partnerName = ""
        message = ""
    }
}

/**
 * Client side: sends input, renders what the host sends back. It never
 * simulates, so the two screens cannot disagree.
 */
class CoopClient {

    @Volatile var stage = NetStage.IDLE
        private set
    @Volatile var message = ""
        private set
    @Volatile var hostName = ""
        private set
    @Volatile var hostShipId = ShipDex.STARTER
        private set

    /** Slot this client occupies on the host, normally 1. */
    @Volatile var mySlot = 1
        private set

    private var link: NetLink? = null
    private var connectThread: Thread? = null
    private var seq = 0
    private var sendT = 0f
    private var pendingDx = 0f
    private var pendingDy = 0f
    private var pendingOd = false

    // Client-side prediction: the local ship answers the thumb immediately and
    // is nudged back towards the host's authoritative position as snapshots land.
    var localX = 0f
        private set
    var localY = 0f
        private set
    private var localTx = 0f
    private var localTy = 0f
    private var predicting = false
    private var bounds = 540f to 1170f
    // input sent since the last snapshot landed - replayed on top of the
    // authoritative target so prediction cannot drift away from the host
    private var unackedDx = 0f
    private var unackedDy = 0f

    val snapshot = Snapshot()

    @Volatile var haveSnapshot = false
        private set

    /** Augment offer waiting to be shown, if it is this client's turn. */
    var offer: Codec.Offer? = null

    var finalScore = 0
        private set
    var finalWave = 0
        private set

    fun connect(host: String, shipId: Int, name: String) {
        stage = NetStage.CONNECTING
        message = "CONNECTING"
        connectThread = Thread({
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, Proto.PORT), 6000)
                val l = NetLink(socket)
                l.start()
                link = l
                l.send(Codec.hello(shipId, name))
                stage = NetStage.LOBBY
                message = "WAITING FOR HOST"
            } catch (e: Exception) {
                stage = NetStage.FAILED
                message = e.message ?: "could not reach host"
            }
        }, "NeonVoidConnect").also { it.isDaemon = true; it.start() }
    }

    fun setBounds(w: Float, h: Float) {
        bounds = w to h
    }

    fun queueInput(dx: Float, dy: Float) {
        pendingDx += dx
        pendingDy += dy
        unackedDx += dx
        unackedDy += dy
        localTx = clamp(localTx + dx, 18f, bounds.first - 18f)
        localTy = clamp(localTy + dy, bounds.second * 0.14f, bounds.second - 34f)
    }

    /** Advances the predicted local position; call once per rendered frame. */
    fun predict(dt: Float, fallbackHandling: Float = 0.42f) {
        if (!predicting) return
        val handling = snapshot.players.getOrNull(mySlot)?.handling?.takeIf { it > 0.05f } ?: fallbackHandling
        localX = approach(localX, localTx, handling, dt)
        localY = approach(localY, localTy, handling, dt)
    }

    /** Pulls the prediction back towards the authoritative position. */
    private fun reconcile() {
        val p = snapshot.players.getOrNull(mySlot) ?: return
        if (!p.joined) return
        if (!predicting) {
            localX = p.x; localY = p.y
            localTx = p.tx; localTy = p.ty
            unackedDx = 0f; unackedDy = 0f
            predicting = true
            return
        }
        // The host owns the movement target; rebuild ours from it plus whatever
        // input has not made it into a snapshot yet.
        localTx = clamp(p.tx + unackedDx, 18f, bounds.first - 18f)
        localTy = clamp(p.ty + unackedDy, bounds.second * 0.14f, bounds.second - 34f)
        unackedDx = 0f
        unackedDy = 0f
        val err = len(p.x - localX, p.y - localY)
        if (err > 60f) {
            localX = p.x; localY = p.y      // way out of step: snap rather than drift
        } else {
            localX += (p.x - localX) * 0.25f
            localY += (p.y - localY) * 0.25f
        }
    }

    fun queueOverdrive() {
        pendingOd = true
    }

    fun sendPick(index: Int) {
        link?.send(Codec.pick(index))
        offer = null
    }

    /** Drains host traffic and ships input. Returns false once the link is done. */
    fun pump(dt: Float): Boolean {
        val l = link ?: return stage != NetStage.FAILED
        if (l.closed) {
            if (stage != NetStage.ENDED) {
                stage = NetStage.FAILED
                message = l.failure ?: "host disconnected"
            }
            return false
        }
        while (true) {
            val m = l.poll() ?: break
            when (m.type) {
                Proto.WELCOME -> {
                    val i = java.io.DataInputStream(m.data.inputStream())
                    i.readInt()
                    mySlot = i.readInt()
                    hostShipId = i.readInt()
                    hostName = i.readUTF()
                    message = "READY"
                }
                Proto.START -> {
                    stage = NetStage.RUNNING
                    message = ""
                }
                Proto.SNAPSHOT -> {
                    try {
                        Codec.readSnapshot(m.data, snapshot)
                        haveSnapshot = true
                        reconcile()
                    } catch (_: Exception) {
                        // a malformed snapshot is not worth dropping the run over
                    }
                }
                Proto.OFFER -> {
                    val o = Codec.readOffer(m.data)
                    offer = if (o.slotIndex == mySlot) o else null
                }
                Proto.OVER -> {
                    val i = java.io.DataInputStream(m.data.inputStream())
                    finalScore = i.readInt()
                    finalWave = i.readInt()
                    stage = NetStage.ENDED
                }
                Proto.BYE -> {
                    stage = NetStage.ENDED
                    message = "HOST LEFT"
                    return false
                }
            }
        }
        if (stage == NetStage.RUNNING) {
            sendT -= dt
            if (sendT <= 0f) {
                sendT = 1f / Proto.INPUT_HZ
                l.send(Codec.input(seq++, pendingDx, pendingDy, pendingOd))
                pendingDx = 0f; pendingDy = 0f; pendingOd = false
            }
        }
        return true
    }

    fun stop() {
        try {
            link?.send(MessageWriter(Proto.BYE).bytes())
        } catch (_: Exception) {
        }
        link?.close()
        link = null
        stage = NetStage.IDLE
        haveSnapshot = false
        predicting = false
        unackedDx = 0f; unackedDy = 0f
        offer = null
        message = ""
    }
}

object NetUtil {
    /** Best-effort local address to show the partner in the lobby. */
    fun localIpv4(): String? {
        return try {
            val ifaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (iface in ifaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
