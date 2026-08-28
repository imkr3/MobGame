package com.neonvoid.game

/**
 * Everything that belongs to one pilot: the ship, its hull, its own augment
 * loadout and its own ability systems. Single-player uses slot 0 only; co-op
 * adds slot 1 for the partner.
 */
class PlayerSlot(val index: Int, fx: Fx) {
    val player = Player()
    val loadout = Loadout()
    val arsenal = Arsenal(fx)
    var ship: Ship = ShipDex.byId(ShipDex.STARTER)
    var meta: Meta = Meta()

    /** True while this pilot is part of the current run. */
    var joined = false

    var name: String = ""

    /** Set while this pilot still owes an augment pick. */
    var awaitingAugment = false

    init {
        arsenal.slot = this
    }

    val alive: Boolean get() = player.lives > 0

    fun resetFor(width: Float, height: Float) {
        loadout.reset()
        arsenal.reset()
        awaitingAugment = false
        player.shipId = ship.id
        player.lives = ship.lives + meta.extraLives
        player.hitR = ship.hitR
        player.weapon = meta.startWeapon
        player.shield = ship.startShield + meta.startShield
        player.overdrive = meta.startOverdrive
        player.odTime = 0f
        player.invuln = 2f
        player.alive = true
        player.respawnT = 0f
        player.bank = 0f
        player.thrust = 0f
        player.fireT = 0f
        player.revenge = 0f
        player.regenT = 0f
        loadout.bonusSlots = meta.extraSlots
        if (ship.signature >= 0) loadout.lvl[ship.signature] = ship.signatureLevel
        home(width, height)
    }

    /** Slot 0 starts left of centre in co-op, slot 1 right of it. */
    fun home(width: Float, height: Float) {
        val offset = if (!joined || index == 0) 0f else 0f
        player.x = width * (if (index == 0) 0.5f else 0.5f) + offset
        player.y = height - height * 0.18f
        player.tx = player.x
        player.ty = player.y
    }
}
