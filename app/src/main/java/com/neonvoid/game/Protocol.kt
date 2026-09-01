package com.neonvoid.game

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Wire format for LAN co-op. The host owns the simulation; the client sends
 * input and renders the snapshots it is sent. Deliberately free of Android
 * imports so the whole protocol can be exercised headlessly.
 */
object Proto {
    const val VERSION = 2
    const val PORT = 47653
    const val SERVICE_TYPE = "_neonvoid._tcp"

    const val HELLO = 1
    const val WELCOME = 2
    const val START = 3
    const val INPUT = 4
    const val SNAPSHOT = 5
    const val OFFER = 6
    const val PICK = 7
    const val OVER = 8
    const val BYE = 9
    const val PING = 10

    /** Snapshots are sent at this rate; input a little faster. */
    const val SNAPSHOT_HZ = 30f
    const val INPUT_HZ = 30f

    const val MAX_FRAME = 1 shl 20

    // fixed-point helpers: positions are sent as 1/16th units
    fun packPos(v: Float): Short = (v * 16f).toInt().coerceIn(-32768, 32767).toShort()
    fun unpackPos(v: Short): Float = v / 16f
}

/** One decoded message: a type plus its raw payload. */
class NetMessage(val type: Int, val data: ByteArray)

class MessageWriter(val type: Int) {
    private val buf = ByteArrayOutputStream(256)
    val out = DataOutputStream(buf)

    /** The framed message: a type byte followed by the payload. */
    fun bytes(): ByteArray {
        val payload = buf.toByteArray()
        val framed = ByteArray(payload.size + 1)
        framed[0] = type.toByte()
        System.arraycopy(payload, 0, framed, 1, payload.size)
        return framed
    }
}

/** A pilot's state as it appears in a snapshot. */
class NetPlayerState {
    var joined = false
    var alive = false
    var x = 0f
    var y = 0f
    var lives = 0
    var shield = 0
    var maxShield = 1
    var weapon = 1
    var overdrive = 0f
    var odTime = 0f
    var shipId = 0
    var bank = 0f
    var invuln = 0f
    var tx = 0f
    var ty = 0f
    /** The host's true movement responsiveness, so prediction matches exactly. */
    var handling = 0.42f
}

/** Everything the client needs to draw a frame. */
class Snapshot {
    var tick = 0
    var score = 0
    var combo = 0
    var wave = 0
    var levelsCleared = 0
    var bossHpRatio = 0f
    var bossPresent = false
    var multiplier = 1f
    var banner = ""
    var bannerSub = ""
    var bannerT = 0f
    var gameOver = false
    val players = arrayOf(NetPlayerState(), NetPlayerState())

    var bulletCount = 0
    val bulletX = FloatArray(World.BULLET_CAP)
    val bulletY = FloatArray(World.BULLET_CAP)
    val bulletR = FloatArray(World.BULLET_CAP)
    val bulletStyle = IntArray(World.BULLET_CAP)
    val bulletHostile = BooleanArray(World.BULLET_CAP)
    val bulletColor = IntArray(World.BULLET_CAP)

    var enemyCount = 0
    val enemyX = FloatArray(World.ENEMY_CAP)
    val enemyY = FloatArray(World.ENEMY_CAP)
    val enemyR = FloatArray(World.ENEMY_CAP)
    val enemyKind = IntArray(World.ENEMY_CAP)
    val enemyAngle = FloatArray(World.ENEMY_CAP)
    val enemyFlash = FloatArray(World.ENEMY_CAP)
    val enemyElite = BooleanArray(World.ENEMY_CAP)
    val enemyColor = IntArray(World.ENEMY_CAP)
    /** Wind-up timer, so the client can draw the same tells the host does. */
    val enemyTelegraph = FloatArray(World.ENEMY_CAP)
    /** Index into this snapshot's own enemy list, or -1. Mender beams. */
    val enemyLink = IntArray(World.ENEMY_CAP)

    var pickupCount = 0
    val pickupX = FloatArray(32)
    val pickupY = FloatArray(32)
    val pickupKind = IntArray(32)
    val pickupT = FloatArray(32)

    var eventCount = 0
    val eventType = IntArray(24)
    val eventX = FloatArray(24)
    val eventY = FloatArray(24)
}

object Codec {

    /** Scratch used to renumber enemy links; the host writes snapshots alone. */
    private val compact = IntArray(World.ENEMY_CAP)

    // ------------------------------------------------------------- handshake

    fun hello(shipId: Int, name: String): ByteArray {
        val m = MessageWriter(Proto.HELLO)
        m.out.writeInt(Proto.VERSION)
        m.out.writeInt(shipId)
        m.out.writeUTF(name.take(16))
        return m.bytes()
    }

    fun welcome(slotIndex: Int, hostShipId: Int, hostName: String): ByteArray {
        val m = MessageWriter(Proto.WELCOME)
        m.out.writeInt(Proto.VERSION)
        m.out.writeInt(slotIndex)
        m.out.writeInt(hostShipId)
        m.out.writeUTF(hostName.take(16))
        return m.bytes()
    }

    fun input(seq: Int, dx: Float, dy: Float, overdrive: Boolean): ByteArray {
        val m = MessageWriter(Proto.INPUT)
        m.out.writeInt(seq)
        m.out.writeShort(Proto.packPos(dx).toInt())
        m.out.writeShort(Proto.packPos(dy).toInt())
        m.out.writeByte(if (overdrive) 1 else 0)
        return m.bytes()
    }

    fun pick(index: Int): ByteArray {
        val m = MessageWriter(Proto.PICK)
        m.out.writeInt(index)
        return m.bytes()
    }

    // -------------------------------------------------------------- snapshot

    fun snapshot(w: World, tick: Int, events: List<Triple<Int, Float, Float>>): ByteArray {
        val m = MessageWriter(Proto.SNAPSHOT)
        val o = m.out
        o.writeInt(tick)
        o.writeInt(w.score)
        o.writeInt(w.combo)
        o.writeInt(w.wave)
        o.writeInt(w.levelsCleared)
        o.writeFloat(w.bossHpRatio)
        o.writeBoolean(w.bossPresent())
        o.writeFloat(w.multiplier)
        o.writeUTF(w.banner.take(32))
        o.writeUTF(w.bannerSub.take(48))
        o.writeFloat(w.bannerT)
        o.writeBoolean(w.gameOver)

        for (s in w.slots) {
            val p = s.player
            o.writeBoolean(s.joined)
            o.writeBoolean(p.alive)
            o.writeShort(Proto.packPos(p.x).toInt())
            o.writeShort(Proto.packPos(p.y).toInt())
            o.writeByte(p.lives.coerceIn(0, 127))
            o.writeByte(p.shield.coerceIn(0, 127))
            o.writeByte(s.loadout.maxShield().coerceIn(0, 127))
            o.writeByte(p.weapon.coerceIn(0, 127))
            o.writeByte((p.overdrive * 100f).toInt().coerceIn(0, 127))
            o.writeByte((p.odTime * 20f).toInt().coerceIn(0, 127))
            o.writeByte(p.shipId.coerceIn(0, 127))
            o.writeByte((p.bank * 100f).toInt().coerceIn(-128, 127))
            o.writeByte((p.invuln * 20f).toInt().coerceIn(0, 127))
            o.writeShort(Proto.packPos(p.tx).toInt())
            o.writeShort(Proto.packPos(p.ty).toInt())
            o.writeByte((w.handlingOf(s) * 200f).toInt().coerceIn(0, 255))
        }

        var n = 0
        for (b in w.bullets) if (b.active) n++
        o.writeShort(n)
        for (b in w.bullets) {
            if (!b.active) continue
            o.writeShort(Proto.packPos(b.x).toInt())
            o.writeShort(Proto.packPos(b.y).toInt())
            o.writeByte((b.r * 4f).toInt().coerceIn(0, 127))
            o.writeByte(b.style)
            o.writeBoolean(b.hostile)
            o.writeInt(b.color)
        }

        // Links are host-array indices, but only live enemies go on the wire,
        // so they have to be renumbered into the compacted list the client sees.
        n = 0
        for (i in w.enemies.indices) {
            compact[i] = if (w.enemies[i].active) n++ else -1
        }
        o.writeShort(n)
        for (e in w.enemies) {
            if (!e.active) continue
            o.writeShort(Proto.packPos(e.x).toInt())
            o.writeShort(Proto.packPos(e.y).toInt())
            o.writeByte((e.r).toInt().coerceIn(0, 127))
            o.writeByte(e.kind)
            o.writeShort(e.angle.toInt().coerceIn(-32768, 32767))
            o.writeByte((e.hitFlash * 100f).toInt().coerceIn(0, 127))
            o.writeBoolean(e.elite)
            o.writeInt(e.color)
            o.writeByte((e.telegraph * 250f).toInt().coerceIn(0, 250))
            val link = if (e.link in w.enemies.indices) compact[e.link] else -1
            o.writeByte(link.coerceIn(-1, 127))
        }

        n = w.pickupSnapshotCount()
        o.writeShort(n)
        w.writePickups(o)

        o.writeByte(events.size.coerceAtMost(24))
        for (i in 0 until events.size.coerceAtMost(24)) {
            val (t, x, y) = events[i]
            o.writeByte(t)
            o.writeShort(Proto.packPos(x).toInt())
            o.writeShort(Proto.packPos(y).toInt())
        }
        return m.bytes()
    }

    fun readSnapshot(data: ByteArray, into: Snapshot) {
        val i = DataInputStream(data.inputStream())
        into.tick = i.readInt()
        into.score = i.readInt()
        into.combo = i.readInt()
        into.wave = i.readInt()
        into.levelsCleared = i.readInt()
        into.bossHpRatio = i.readFloat()
        into.bossPresent = i.readBoolean()
        into.multiplier = i.readFloat()
        into.banner = i.readUTF()
        into.bannerSub = i.readUTF()
        into.bannerT = i.readFloat()
        into.gameOver = i.readBoolean()

        for (p in into.players) {
            p.joined = i.readBoolean()
            p.alive = i.readBoolean()
            p.x = Proto.unpackPos(i.readShort())
            p.y = Proto.unpackPos(i.readShort())
            p.lives = i.readUnsignedByte()
            p.shield = i.readUnsignedByte()
            p.maxShield = i.readUnsignedByte()
            p.weapon = i.readUnsignedByte()
            p.overdrive = i.readUnsignedByte() / 100f
            p.odTime = i.readUnsignedByte() / 20f
            p.shipId = i.readUnsignedByte()
            p.bank = i.readByte() / 100f
            p.invuln = i.readUnsignedByte() / 20f
            p.tx = Proto.unpackPos(i.readShort())
            p.ty = Proto.unpackPos(i.readShort())
            p.handling = i.readUnsignedByte() / 200f
        }

        into.bulletCount = i.readUnsignedShort().coerceAtMost(World.BULLET_CAP)
        for (k in 0 until into.bulletCount) {
            into.bulletX[k] = Proto.unpackPos(i.readShort())
            into.bulletY[k] = Proto.unpackPos(i.readShort())
            into.bulletR[k] = i.readUnsignedByte() / 4f
            into.bulletStyle[k] = i.readUnsignedByte()
            into.bulletHostile[k] = i.readBoolean()
            into.bulletColor[k] = i.readInt()
        }

        into.enemyCount = i.readUnsignedShort().coerceAtMost(World.ENEMY_CAP)
        for (k in 0 until into.enemyCount) {
            into.enemyX[k] = Proto.unpackPos(i.readShort())
            into.enemyY[k] = Proto.unpackPos(i.readShort())
            into.enemyR[k] = i.readUnsignedByte().toFloat()
            into.enemyKind[k] = i.readUnsignedByte()
            into.enemyAngle[k] = i.readShort().toFloat()
            into.enemyFlash[k] = i.readUnsignedByte() / 100f
            into.enemyElite[k] = i.readBoolean()
            into.enemyColor[k] = i.readInt()
            into.enemyTelegraph[k] = i.readUnsignedByte() / 250f
            into.enemyLink[k] = i.readByte().toInt()
        }

        into.pickupCount = i.readUnsignedShort().coerceAtMost(32)
        for (k in 0 until into.pickupCount) {
            into.pickupX[k] = Proto.unpackPos(i.readShort())
            into.pickupY[k] = Proto.unpackPos(i.readShort())
            into.pickupKind[k] = i.readUnsignedByte()
            into.pickupT[k] = i.readUnsignedByte() / 8f
        }

        into.eventCount = i.readUnsignedByte().coerceAtMost(24)
        for (k in 0 until into.eventCount) {
            into.eventType[k] = i.readUnsignedByte()
            into.eventX[k] = Proto.unpackPos(i.readShort())
            into.eventY[k] = Proto.unpackPos(i.readShort())
        }
    }

    // ----------------------------------------------------------- augments

    fun offer(slotIndex: Int, cards: List<AugCard>): ByteArray {
        val m = MessageWriter(Proto.OFFER)
        m.out.writeInt(slotIndex)
        m.out.writeByte(cards.size)
        for (c in cards) {
            m.out.writeInt(c.id)
            m.out.writeInt(c.branchPick)
            m.out.writeUTF(c.title)
            m.out.writeUTF(c.tag)
            m.out.writeUTF(c.body)
            m.out.writeInt(c.color)
        }
        return m.bytes()
    }

    class Offer(val slotIndex: Int, val cards: List<AugCard>)

    fun readOffer(data: ByteArray): Offer {
        val i = DataInputStream(data.inputStream())
        val slot = i.readInt()
        val n = i.readUnsignedByte()
        val cards = ArrayList<AugCard>(n)
        for (k in 0 until n) {
            val id = i.readInt()
            val branch = i.readInt()
            val title = i.readUTF()
            val tag = i.readUTF()
            val body = i.readUTF()
            val color = i.readInt()
            cards.add(AugCard(id, branch, title, tag, body, color))
        }
        return Offer(slot, cards)
    }

    fun over(score: Int, wave: Int, kills: Int, combo: Int, levels: Int): ByteArray {
        val m = MessageWriter(Proto.OVER)
        m.out.writeInt(score); m.out.writeInt(wave); m.out.writeInt(kills)
        m.out.writeInt(combo); m.out.writeInt(levels)
        return m.bytes()
    }
}
