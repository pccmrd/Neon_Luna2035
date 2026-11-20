package com.example.keres9.sim

import com.example.keres9.core.Agent
import com.example.keres9.core.Body
import com.example.keres9.core.DockPick
import com.example.keres9.core.GD
import com.example.keres9.core.GS
import com.example.keres9.core.Ship
import com.example.keres9.core.hy
import com.example.keres9.core.pk
import com.example.keres9.core.rand
import com.example.keres9.core.ri
import com.example.keres9.core.rr
import com.example.keres9.world.SITES
import com.example.keres9.world.portW
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val FIRST = listOf(
    "Mara", "Juno", "Okon", "Signe", "Ravi", "Lena", "Abel", "Tove", "Kenji", "Ayla",
    "Bram", "Noor", "Ilse", "Dmitri", "Esme", "Femi", "Hollis", "Yara", "Pell", "Rune"
)
private val LAST = listOf(
    "Achterberg", "Nakamura", "Osei", "Lindqvist", "Haddad", "Moreau", "Quispe",
    "Brandt", "Sato", "Okafor", "Ruiz", "Halvorsen", "Kowal", "Mbeki", "Ferreira"
)
private val SHIPN = mapOf(
    "hauler" to listOf("ORE HAUL 2", "ORE HAUL 5", "BULK SELENE", "THE PATIENT"),
    "pros" to listOf("DUST DEVIL", "LITTLE ROCK", "MAGPIE", "SECOND WIND", "GLINT"),
    "lander" to listOf("RIM SHUTTLE 1", "RIM SHUTTLE 4", "TYCHO EXPRESS"),
    "drone" to listOf("CAT-DRONE 3", "CAT-DRONE 7")
)
private val HAULER_GOODS = listOf("fuel", "parts", "food", "ice")

fun mkAgent(cls: String, fac: String, x: Double, y: Double, id: Int, name: String? = null): Agent {
    val rf = ::rand
    val shipTitle = pk(rf, SHIPN[cls] ?: listOf("VESSEL")) + if (rf() < 0.5) "" else " ${ri(rf, 2, 9)}"
    val s = mkShip(cls, fac, shipTitle, x, y)
    val a = Agent(
        id = id,
        name = name ?: "${pk(rf, FIRST)} ${pk(rf, LAST)}",
        role = cls,
        fac = fac,
        ship = s,
        state = "idle",
        t = rr(rf, 2.0, 20.0),
        credits = ri(rf, 200, 3000),
        mood = rr(rf, -1.0, 1.0),
        alive = true
    )
    s.ag = a
    GS.AG.add(a)
    return a
}

fun initAgents() {
    GS.AG.clear()
    val rf = ::rand
    var nextId = 1
    fun nid() = nextId++
    val orb = listOf(SITES.sel, SITES.vul, SITES.ares, SITES.gaia)
    for (k in 0 until 3) {
        val h = orb[k % 4]
        val a = mkAgent("hauler", "selene", h.x + rr(rf, -300.0, 300.0), h.y - 260.0, nid())
        a.state = "go"
        a.dest = pk(rf, orb)
    }
    for (k in 0 until 3) {
        val h = SITES.sel
        val a = mkAgent("pros", "free", h.x + rr(rf, -600.0, 600.0), h.y + rr(rf, -200.0, 200.0), nid())
        a.state = "idle"
    }
    for (k in 0 until 2) {
        val b = if (k != 0) SITES.tycho else SITES.depot
        val p = b.pads.getOrNull(1) ?: b.pads.firstOrNull()
        val a = mkAgent("lander", "rim", b.x, b.y - 30.0, nid())
        if (p != null && p.occ == null) dockPad(a.ship, b, p, silent = true)
        a.state = "wait"
        a.t = rr(rf, 20.0, 90.0)
        a.base = b
    }
    for (k in 0 until 2) {
        val a = mkAgent("drone", "selene", SITES.sel.x + rr(rf, -400.0, 400.0), SITES.sel.y + rr(rf, -300.0, 300.0), nid())
        a.state = "idle"
    }
}

private fun freeDock(st: Body, s: Ship): DockPick? {
    if (s.tailLand) {
        val p = st.pads.firstOrNull { it.occ == null }
        return if (p != null) DockPick(null, p, "pad") else null
    }
    val ports = st.ports.filterIndexed { idx, q ->
        q.occ == null && !q.berth && !(st === SITES.sel && idx == 2)
    }
    return if (ports.isNotEmpty()) DockPick(pk(::rand, ports), null, "port") else null
}

private fun approach(a: Agent, st: Body, pick: DockPick): Boolean {
    val s = a.ship
    val pad = pick.ty == "pad"
    val w = when {
        pick.pPad != null -> portW(st, pick.pPad)
        pick.pPort != null -> portW(st, pick.pPort)
        else -> return false
    }
    val occShip = pick.pPad?.occ ?: pick.pPort?.occ
    val off = if (pad) (if (s.tailLand) s.nose + 1.2 else s.belly + 1.2) else s.nose + 1.5
    val face = if (pad) (if (s.tailLand) w.dir else w.dir + PI / 2.0) else w.dir + PI
    val fx = w.x + cos(w.dir) * off
    val fy = w.y + sin(w.dir) * off
    val far = hy(s.x - fx, s.y - fy)
    val ax = w.x + cos(w.dir) * (off + 34.0)
    val ay = w.y + sin(w.dir) * (off + 34.0)
    if (far > 60.0) {
        val wp = detour(s.x, s.y, ax, ay, s.rad + 4.0, st)
        val tx = wp?.x ?: ax
        val ty = wp?.y ?: ay
        steer(s, tx, ty, SteerOpts(vmax = if (s.tailLand) 60.0 else 45.0, out = a.ap))
    } else if (!a.final) {
        val t = steer(s, ax, ay, SteerOpts(vmax = 10.0, out = a.ap, face = face, tvx = w.vx, tvy = w.vy))
        if (t.d < 6.0 && t.rv < 1.5) a.final = true
    } else {
        steer(s, fx, fy, SteerOpts(vmax = 3.0, out = a.ap, face = face, tvx = w.vx, tvy = w.vy, kv = 1.6))
    }
    applyOut(s, a.ap)
    if (a.final && far < 5.0 && hy(s.vx - w.vx, s.vy - w.vy) < 2.0 && occShip == null) {
        if (pick.pPad != null) dockPad(s, st, pick.pPad, silent = true)
        else if (pick.pPort != null) dockPort(s, st, pick.pPort, silent = true)
        a.final = false
        return true
    }
    if (occShip != null && occShip !== s) {
        a.final = false
        a.dockChoice = null
    }
    return false
}

private fun agentTrade(a: Agent, st: Body) {
    val s = a.ship
    val econ = st.econ ?: return
    val keys = s.cargo.keys.toList()
    for (g in keys) {
        val q = s.cargo[g] ?: continue
        val e = econ[g] ?: continue
        val def = GD[g] ?: continue
        e.stock += q
        a.credits += (q * def.p).toInt()
        s.cargo.remove(g)
    }
    if (a.role == "hauler") {
        val g = pk(::rand, HAULER_GOODS)
        val e = econ[g]
        if (e != null && e.stock > 15.0) {
            val q = kotlin.math.min(18, floor(e.stock * 0.2).toInt())
            e.stock -= q
            if (q > 0) s.cargo[g] = q
        }
    }
    s.fuel = s.fuelMax
}

fun agents(dt: Double) {
    val snapshot = GS.AG.toList()
    for (a in snapshot) {
        if (!a.alive) continue
        val s = a.ship
        if (s.dead) {
            a.alive = false
            feed("${a.name} lost the ${s.name}.", "bad")
            continue
        }
        a.t -= dt
        when (a.role) {
            "hauler" -> hauler(a, s)
            "pros" -> prospector(a, s)
            "lander" -> lander(a, s)
            "drone" -> drone(a, s)
        }
    }
}

private fun hauler(a: Agent, s: Ship) {
    val rf = ::rand
    when (a.state) {
        "go" -> {
            if (s.dock != null) undock(s)
            val dest = a.dest ?: SITES.sel.also { a.dest = it }
            val pick = a.dockChoice ?: freeDock(dest, s).also { a.dockChoice = it }
            if (pick == null) {
                steer(s, dest.x, dest.y - dest.rad - 80.0, SteerOpts(vmax = 30.0, out = a.ap))
                applyOut(s, a.ap)
                return
            }
            if (approach(a, dest, pick)) {
                a.state = "dock"
                a.t = rr(rf, 18.0, 40.0)
                agentTrade(a, dest)
                a.dockChoice = null
            }
        }
        "dock" -> {
            if (a.t <= 0.0) {
                val orb = listOf(SITES.sel, SITES.vul, SITES.ares, SITES.gaia).filter { it !== a.dest && !it.gone }
                a.dest = if (orb.isNotEmpty()) pk(rf, orb) else SITES.sel
                a.state = "go"
                undock(s)
            }
        }
        else -> {
            a.state = "go"
            a.dest = SITES.sel
        }
    }
}

private fun prospector(a: Agent, s: Ship) {
    val W = GS.W ?: return
    val rf = ::rand
    when (a.state) {
        "idle" -> {
            val d = s.dock
            if (d != null && d.ty != "anchor") {
                if (a.t > 0.0) return
                undock(s)
            }
            val L = W.rocks.filter { b -> !b.dead && b.rVal > 14.0 && !b.orphan && hy(b.x - s.x, b.y - s.y) < 2400.0 }
            if (L.isNotEmpty()) {
                a.rock = pk(rf, L)
                a.state = "go"
                a.final = false
            }
        }
        "go" -> {
            val b = a.rock
            if (b == null || b.dead) {
                a.state = "idle"
                return
            }
            val so = standoff(b, s.x, s.y, 22.0)
            val wp = detour(s.x, s.y, so.x, so.y, s.rad + 4.0, b)
            val t = steer(
                s,
                wp?.x ?: so.x,
                wp?.y ?: so.y,
                SteerOpts(vmax = 28.0, out = a.ap, face = if (wp != null) null else atan2(b.y - s.y, b.x - s.x))
            )
            applyOut(s, a.ap)
            if (wp == null && t.d < 6.0 && t.rv < 1.0) {
                a.state = "mine"
                a.t = rr(rf, 25.0, 50.0)
            }
        }
        "mine" -> {
            val b = a.rock
            if (b == null || b.dead) {
                a.state = "idle"
                s.wantM = false
                return
            }
            val so = standoff(b, s.x, s.y, 20.0)
            val t = steer(s, so.x, so.y, SteerOpts(vmax = 4.0, out = a.ap, face = atan2(b.y - s.y, b.x - s.x)))
            applyOut(s, a.ap)
            s.wantM = t.err < 0.15 && hy(s.x - GS.cam.x, s.y - GS.cam.y) < 1600.0
            if (!s.wantM && rf() < 0.02) {
                s.cargo["fe"] = (s.cargo["fe"] ?: 0) + 1
            }
            if (a.t <= 0.0 || cmass(s) >= s.cargoMax - 2) {
                s.wantM = false
                a.state = "sell"
                a.dockChoice = null
                a.dest = if (rf() < 0.6) SITES.vul else SITES.sel
            }
        }
        "sell" -> {
            val dest = a.dest ?: SITES.sel.also { a.dest = it }
            val pick = a.dockChoice ?: freeDock(dest, s).also { a.dockChoice = it }
            if (pick == null) {
                a.state = "idle"
                return
            }
            if (approach(a, dest, pick)) {
                agentTrade(a, dest)
                a.state = "idle"
                a.t = rr(rf, 15.0, 35.0)
                a.dockChoice = null
                if (rf() < 0.3) feed("${s.name} sold a hold of ore at ${dest.name}.")
            }
        }
    }
}

private fun lander(a: Agent, s: Ship) {
    val rf = ::rand
    when (a.state) {
        "wait" -> {
            if (a.t > 0.0) {
                s.thr = 0.0; s.rcx = 0.0; s.rcy = 0.0; s.tqi = 0.0
                return
            }
            val up = s.dock?.b?.surf == true
            a.dest = if (up) (if (rf() < 0.7) SITES.sel else SITES.vul) else (a.base ?: SITES.depot)
            a.state = "climb"
            a.dockChoice = null
            a.final = false
            if (s.dock != null) undock(s)
            s.thr = 0.7
        }
        "climb" -> {
            val dest = a.dest ?: SITES.sel
            val r = hy(s.x, s.y).let { if (it == 0.0) 1.0 else it }
            val ux = s.x / r
            val uy = s.y / r
            val alt = r - com.example.keres9.world.surfR(atan2(s.y, s.x))
            if (dest.surf || alt > 260.0) {
                a.state = "go"
                return
            }
            steer(s, s.x + ux * 200.0, s.y + uy * 200.0, SteerOpts(vmax = 35.0, out = a.ap, up = true))
            applyOut(s, a.ap)
        }
        "go" -> {
            val dest = a.dest ?: SITES.sel
            val pick = a.dockChoice ?: freeDock(dest, s).also { a.dockChoice = it }
            if (pick == null) {
                a.dest = if (dest === SITES.sel) SITES.vul else (a.base ?: SITES.depot)
                return
            }
            if (approach(a, dest, pick)) {
                a.state = "wait"
                a.t = rr(rf, 30.0, 80.0)
                agentTrade(a, dest)
                a.dockChoice = null
                if (rf() < 0.4) feed("${s.name}${if (dest.surf) " landed at " else " docked at "}${dest.name}.")
            }
        }
    }
}

private fun drone(a: Agent, s: Ship) {
    val W = GS.W ?: return
    val rf = ::rand
    if (a.rock == null || a.t <= 0.0) {
        val L = W.rocks.filter { b -> !b.dead && hy(b.x - SITES.sel.x, b.y - SITES.sel.y) < 2600.0 }
        if (L.isNotEmpty()) a.rock = pk(rf, L)
        a.t = rr(rf, 25.0, 50.0)
    }
    val b = a.rock ?: return
    val ang = W.time * 0.15 + a.id
    val tx = b.x + cos(ang) * (b.rad + 40.0)
    val ty = b.y + sin(ang) * (b.rad + 40.0)
    val wp = detour(s.x, s.y, tx, ty, 8.0, b)
    steer(s, wp?.x ?: tx, wp?.y ?: ty, SteerOpts(vmax = 22.0, out = a.ap, face = atan2(b.y - s.y, b.x - s.x)))
    applyOut(s, a.ap)
}
