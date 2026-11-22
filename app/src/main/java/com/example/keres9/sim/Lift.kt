package com.example.keres9.sim

import com.example.keres9.audio.AU
import com.example.keres9.core.C
import com.example.keres9.core.GS
import com.example.keres9.core.LiftCar
import com.example.keres9.core.LiftEnds
import com.example.keres9.core.LiftState
import com.example.keres9.core.RideState
import com.example.keres9.core.cl
import com.example.keres9.core.hy
import com.example.keres9.core.rand
import com.example.keres9.world.SITES
import com.example.keres9.world.lw
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private fun ends(): LiftEnds? {
    val L = GS.W?.lift ?: return null
    val gDock = L.term.liftDock ?: return null
    val tDock = L.top.liftDock ?: return null
    return LiftEnds(
        lw(L.term, gDock.x, gDock.y),
        lw(L.top, tDock.x, tDock.y)
    )
}

fun liftInit() {
    val W = GS.W ?: return
    val cars = listOf(LiftCar("A", 0.0), LiftCar("B", 1.0))
    W.lift = LiftState(
        term = SITES.lift,
        top = SITES.sel,
        cars = cars,
        idle = 45.0,
        chat = 0.0
    )
    liftPos()
}

fun liftPos() {
    val L = GS.W?.lift ?: return
    val E = ends() ?: return
    L.len = hy(E.t.x - E.g.x, E.t.y - E.g.y)
    L.E = E
    for (c in L.cars) {
        c.x = E.g.x + (E.t.x - E.g.x) * c.s
        c.y = E.g.y + (E.t.y - E.g.y) * c.s
    }
}

fun carHere(end: String): LiftCar? {
    val L = GS.W?.lift ?: return null
    return L.cars.firstOrNull { c ->
        c.dir == 0 && (if (end == "ground") c.s < 0.001 else c.s > 0.999)
    }
}

fun liftBusy(): Boolean = GS.W?.lift?.cars?.any { it.dir != 0 } == true

fun ride(end: String, withPlayer: Boolean): Boolean {
    val L = GS.W?.lift ?: return false
    val G = GS.G ?: return false
    val W = GS.W ?: return false
    val ev = GS.ev
    if (liftBusy()) return false
    for (c in L.cars) {
        c.dir = if (c.s < 0.5) 1 else -1
        c.v = 0.0
        c.rider = false
    }
    val car = L.cars.firstOrNull { c -> if (end == "ground") c.s < 0.5 else c.s >= 0.5 }
    if (withPlayer && ev != null && car != null) {
        car.rider = true
        ev.hide = true
        ev.vx = 0.0
        ev.vy = 0.0
        G.ride = RideState(kind = "lift", car = car, t0 = W.time, dir = car.dir)
        W.stats.lifts++
        AU.thunk()
        Hooks.closeStation()
        Hooks.toast(
            if (car.dir > 0) "SELENE LIFT ▲ — ${L.len.roundToInt()} m to the band. Sit back."
            else "SELENE LIFT ▼ — down the well to Shackleton Rim.",
            "good"
        )
    }
    L.idle = 70.0 + rand() * 40.0
    return true
}

private val CHAT_UP = listOf(
    "LIFT PA: Eastern Cable Consortium welcomes cleared hands to the only straight line in cislunar space.",
    "LIFT PA: Ninety tonnes of basalt counterweight are passing you on the opposite strand. Please wave.",
    "RADIO · Catalog Office: still no telemetry from KERES-9. Uncharted rocks remain outside both blocs' claim zones.",
    "RADIO · Manager Liu: MULE-3 lifts every few minutes. Bunked hands get priority berths; late arrivals dock 25%.",
    "LIFT PA: After the 2029 Accords partitioned Earth-lunar orbits, South Pole cable power became the last unionized foothold.",
    "RADIO · Traffic: ARES TRANSIT is buying ice for Mars; Helion drones keep skimming unregistered platinum off outer rocks."
)

private val CHAT_DOWN = listOf(
    "LIFT PA: Descending to Shackleton Rim. Lunar gravity returns at 1.6 m/s². Mind your knees.",
    "RADIO · Rim Colony: Consortium water rations stay tight while autonomous haulers monopolize comet streams.",
    "LIFT PA: Sunlight +110°C, crater shadow −170°C. Keep suit visors sealed and headlamps ready inside unlit works."
)

fun stepLift(dt: Double) {
    val L = GS.W?.lift ?: return
    val G = GS.G ?: return
    val ev = GS.ev
    val moving = L.cars.any { it.dir != 0 }
    if (!moving) {
        L.idle -= dt
        if (L.idle <= 0.0) ride("ground", false)
    }
    for (c in L.cars) {
        if (c.dir == 0) continue
        val dist = if (c.dir > 0) (1.0 - c.s) * L.len else c.s * L.len
        val vmax = C.LIFT.v
        val a = C.LIFT.a
        val want = min(vmax, sqrt(2.0 * a * max(0.0, dist)) + 0.6)
        c.v = if (c.v < want) min(want, c.v + a * dt) else want
        c.s = cl(c.s + (c.dir * c.v * dt) / max(1.0, L.len), 0.0, 1.0)
        if ((c.dir > 0 && c.s >= 1.0) || (c.dir < 0 && c.s <= 0.0)) {
            c.dir = 0
            c.v = 0.0
            if (c.rider) arrive(c)
        }
    }
    liftPos()
    val r = G.ride
    if (r != null && r.kind == "lift" && ev != null && r.car != null) {
        ev.x = r.car.x
        ev.y = r.car.y
        ev.suit = min(100.0, ev.suit + dt * 20.0)
        L.chat -= dt
        if (L.chat <= 0.0) {
            L.chat = 7.5
            val arr = if (r.dir > 0) CHAT_UP else CHAT_DOWN
            L.ci = (L.ci + 1) % arr.size
            Hooks.toast(arr[L.ci], null)
        }
    }
}

private fun arrive(c: LiftCar) {
    val G = GS.G ?: return
    val L = GS.W?.lift ?: return
    c.rider = false
    G.ride = null
    val ev = GS.ev ?: return
    val top = c.s > 0.5
    val E = L.E ?: return
    val p = if (top) E.t else E.g
    ev.hide = false
    ev.x = p.x
    ev.y = p.y
    ev.vx = 0.0
    ev.vy = 0.0
    AU.chime()
    Hooks.liftArrive(top)
}
