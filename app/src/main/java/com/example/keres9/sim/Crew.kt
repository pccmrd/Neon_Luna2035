package com.example.keres9.sim

import com.example.keres9.audio.AU
import com.example.keres9.core.BeamInfo
import com.example.keres9.core.Body
import com.example.keres9.core.C
import com.example.keres9.core.CrewMateSuit
import com.example.keres9.core.CrewState
import com.example.keres9.core.GD
import com.example.keres9.core.GS
import com.example.keres9.core.Pose
import com.example.keres9.core.RideState
import com.example.keres9.core.Ship
import com.example.keres9.core.TAU
import com.example.keres9.core.Vec2
import com.example.keres9.core.fm
import com.example.keres9.core.gacc
import com.example.keres9.core.hy
import com.example.keres9.core.parseHexColor
import com.example.keres9.core.rand
import com.example.keres9.core.rr
import com.example.keres9.world.SITES
import com.example.keres9.world.collideCircle
import com.example.keres9.world.cut
import com.example.keres9.world.cw
import com.example.keres9.world.portW
import com.example.keres9.world.ray
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class CrewMateSpec(val n: String, val c: Int)

val MATES: List<CrewMateSpec> = listOf(
    CrewMateSpec("Dagny Okafor", parseHexColor("#ffb347")),
    CrewMateSpec("Tomas Reyes", parseHexColor("#8cff9e")),
    CrewMateSpec("Ines Varga", parseHexColor("#c77dff"))
)

private val GA = Vec2()

fun crewInit() {
    val st = SITES.sel
    val p = st.ports[2]
    val s = mkShip("mule", "crew", "MULE-3", st.x - 200.0, st.y)
    p.crew = true
    dockPort(s, st, p, silent = true)
    s.crewShuttle = true
    val oldShifts = GS.G?.crew?.shifts ?: 0
    GS.G?.crew = CrewState(ship = s, shifts = oldShifts)
    GS.W?.suits?.clear()
}

fun shuttle(): Ship? = GS.G?.crew?.ship

private fun pickSite(): Body? {
    val W = GS.W ?: return null
    val st = SITES.sel
    val L = W.rocks.filter { b ->
        !b.dead && !b.orphan && !b.inbound && !b.kin && !b.hidden &&
            b.rVal >= 16.0 && hy(b.x - st.x, b.y - st.y) < 1500.0 && b.cells > 300
    }.toMutableList()
    if (L.isEmpty()) return null
    fun ore(b: Body): Double {
        val c = b.comp
        return ((c["ice"] ?: 0) + (c["fe"] ?: 0) * 1.3 + (c["vol"] ?: 0) * 2.0 +
            (c["pgm"] ?: 0) * 6.0 + (c["ree"] ?: 0) * 8.0) / max(1, b.cells).toDouble()
    }
    fun sc(b: Body): Double =
        hy(b.x - st.x, b.y - st.y) + (if (b.cat != 0) 900.0 else 0.0) + b.worked * 400.0 - ore(b) * 1600.0
    L.sortBy { sc(it) }
    val idx = floor(rand() * min(4, L.size)).toInt().coerceIn(0, L.lastIndex)
    return L[idx]
}

fun boardShift(): Boolean {
    val G = GS.G ?: return false
    val c = G.crew ?: return false
    val ev = GS.ev ?: return false
    if (!G.paperworkDone || !G.bunkClaimed) {
        Hooks.toast("Manager Liu: \"Clear Consortium Intake and claim your Room 14B bunk before stepping onto MULE-3.\"", "warn")
        return false
    }
    if (c.state != "idle") return false
    val site = pickSite() ?: return false
    c.state = "out"
    c.site = site
    site.worked++
    c.haul.clear()
    c.mates.clear()
    c.mine = 0
    c.t = C.SHIFT
    c.horn = false
    c.warned = false
    c.late = false
    c.legT = 0.0
    ev.hide = true
    ev.home = c.ship
    G.ride = RideState(kind = "shuttle", ship = c.ship)
    Hooks.closeStation()
    AU.thunk()
    val st = SITES.sel
    val d = c.ship.dock
    val dp = d?.pPort
    c.exit = if (dp != null) {
        val w = portW(st, dp)
        Vec2(w.x + cos(w.dir) * 70.0, w.y + sin(w.dir) * 70.0)
    } else null
    undock(c.ship)
    Hooks.toast(
        "MULE-3 · Manager Liu: \"Strap in. Today's sector is ${site.name}. Shift is ${(C.SHIFT / 60.0).roundToInt()} minutes; headlamp on inside dark fissures.\"",
        "good"
    )
    return true
}

private fun spawnMates(c: CrewState) {
    val W = GS.W ?: return
    val site = c.site ?: return
    val s = c.ship
    W.suits.clear()
    for (i in 0 until 2) {
        val m = MATES[(site.uid + i) % MATES.size]
        W.suits.add(
            CrewMateSuit(
                x = s.x + rr(::rand, -6.0, 6.0),
                y = s.y + rr(::rand, -6.0, 6.0),
                vx = 0.0,
                vy = 0.0,
                a = 0.0,
                name = m.n,
                c = m.c,
                st = "go"
            )
        )
    }
}

private fun faceSpot(b: Body, from: Ship?): Pose? {
    val a0 = if (from != null) atan2(from.y - b.y, from.x - b.x) else rand() * TAU
    for (k in 0 until 12) {
        val a = a0 + rr(::rand, -0.7, 0.7) * (if (from != null) 1.0 else 4.5)
        val ox = b.x + cos(a) * (b.rad + 30.0)
        val oy = b.y + sin(a) * (b.rad + 30.0)
        val h = ray(ox, oy, -cos(a), -sin(a), b.rad + 30.0)
        if (h != null && h.b === b) {
            return Pose(h.x + cos(a) * 7.0, h.y + sin(a) * 7.0, a + PI)
        }
    }
    return null
}

private fun stepMates(c: CrewState, dt: Double) {
    val W = GS.W ?: return
    val site = c.site ?: return
    val s = c.ship
    for (m in W.suits) {
        var tx = m.x
        var ty = m.y
        m.t -= dt
        m.beam = null
        when (m.st) {
            "go" -> {
                if (c.horn) {
                    m.st = "back"
                    m.tgt = null
                    continue
                }
                if (m.tgt == null) m.tgt = faceSpot(site, s)
                val tgt = m.tgt ?: continue
                tx = tgt.x
                ty = tgt.y
                if (hy(tx - m.x, ty - m.y) < 3.0) {
                    m.st = "cut"
                    m.t = rr(::rand, 6.0, 11.0)
                }
                m.goT += dt
                if (m.goT > 25.0) {
                    m.goT = 0.0
                    m.tgt = faceSpot(site, s)
                }
            }
            "cut" -> {
                val tgt = m.tgt
                if (tgt == null) {
                    m.st = "go"
                    continue
                }
                tx = tgt.x
                ty = tgt.y
                m.a = tgt.a
                val h = ray(m.x, m.y, cos(m.a), sin(m.a), C.CUT.rng)
                if (h != null && h.b === site) {
                    m.beam = BeamInfo(m.x, m.y, h.x, h.y)
                    val mm = cut(h.b, h.cx, h.cy, C.CUT.dps * 0.8 * dt)
                    if (mm != 0) {
                        val prevMined = W.stats.mined
                        spill(mm, h.x, h.y, cos(m.a), sin(m.a), false, 0.5)
                        W.stats.mined = prevMined
                    }
                } else {
                    m.tgt = faceSpot(site, s)
                }
                for (p in W.picks) {
                    if (!p.dead && !p.held && GD.containsKey(p.type) && hy(p.x - m.x, p.y - m.y) < 10.0 && m.n < 6) {
                        m.n += p.qty
                        p.dead = true
                        c.mates[p.type] = (c.mates[p.type] ?: 0) + p.qty
                    }
                }
                if (m.t <= 0.0 || m.n >= 6 || c.horn) {
                    m.st = "back"
                    m.tgt = null
                }
            }
            "back" -> {
                tx = s.x
                ty = s.y
                m.bt += dt
                if (hy(tx - m.x, ty - m.y) < 10.0 || m.bt > 14.0) {
                    m.bt = 0.0
                    m.n = 0
                    if (c.horn) {
                        m.st = "in"
                    } else {
                        m.st = "go"
                        m.t = 0.0
                        m.tgt = null
                    }
                }
            }
            "in" -> {
                m.x = s.x; m.y = s.y; m.vx = s.vx; m.vy = s.vy
                continue
            }
        }
        gacc(m.x, m.y, m.vx, m.vy, GA)
        val dx = tx - m.x
        val dy = ty - m.y
        val d = hy(dx, dy)
        val vs = min(9.0, d * 0.8)
        val vx = if (d > 0.1) (dx / d) * vs else 0.0
        val vy = if (d > 0.1) (dy / d) * vs else 0.0
        var ax = (vx - m.vx) * 2.0 - GA.x
        var ay = (vy - m.vy) * 2.0 - GA.y
        val am = hy(ax, ay)
        if (am > 10.0) {
            ax *= 10.0 / am
            ay *= 10.0 / am
        }
        m.vx += (ax + GA.x) * dt
        m.vy += (ay + GA.y) * dt
        m.x += m.vx * dt
        m.y += m.vy * dt
        if (m.st != "cut" && d > 1.0) m.a = atan2(dy, dx)
        if (am > 3.0 && rand() < 0.2) gas(m.x, m.y, m.vx - ax * 2.0, m.vy - ay * 2.0)
        collideCircle(
            x = m.x, y = m.y, vx = m.vx, vy = m.vy, rad = 2.4,
            update = { nx, ny, nvx, nvy, _ ->
                m.x = nx; m.y = ny; m.vx = nvx; m.vy = nvy
            }
        )
    }
}

fun crewTick(dt: Double) {
    val G = GS.G ?: return
    val W = GS.W ?: return
    val c = G.crew ?: return
    val s = c.ship
    if (s.dead) {
        val riding = G.ride?.kind == "shuttle"
        crewInit()
        val ev = GS.ev
        if (riding && ev != null) {
            val st = SITES.sel
            val p = st.ports[2]
            val pw = cw(st, p.cx, p.cy)
            ev.x = pw.x - 10.0
            ev.y = pw.y
            ev.vx = st.vx
            ev.vy = st.vy
            ev.hide = false
            G.ride = null
        }
        Hooks.toast("MULE-3 lost — SELENE rescue brings the crew home. A spare MULE is fuelled.", "warn")
        return
    }
    val st = SITES.sel
    val ap = c.ap
    when (c.state) {
        "out" -> {
            val site = c.site ?: return
            val so = standoff(site, st.x, st.y, 16.0)
            val ex = c.exit
            if (ex != null && (hy(ex.x - s.x, ex.y - s.y) < 12.0 || c.legT > 40.0)) c.exit = null
            val wp = c.exit ?: detour(s.x, s.y, so.x, so.y, 16.0, site)
            val t = if (wp != null) {
                steer(s, wp.x, wp.y, SteerOpts(vmax = 40.0, out = ap))
            } else {
                steer(s, so.x, so.y, SteerOpts(vmax = 40.0, out = ap, face = atan2(site.y - s.y, site.x - s.x)))
            }
            applyOut(s, ap)
            c.legT += dt
            var h = null as com.example.keres9.world.RayHit?
            var ok = false
            if ((wp == null && t.d < 6.0 && t.rv < 1.2) || (c.legT > 120.0 && t.rv < 3.0)) {
                val dd = max(1.0, hy(site.x - s.x, site.y - s.y))
                h = ray(s.x, s.y, (site.x - s.x) / dd, (site.y - s.y) / dd, 60.0)
                ok = h != null && h.b === site && hy(h.x - s.x, h.y - s.y) < 30.0
            }
            if (!ok && c.legT > 200.0) {
                val dd = max(1.0, hy(s.x - site.x, s.y - site.y))
                val ux = (s.x - site.x) / dd
                val uy = (s.y - site.y) / dd
                val r0 = site.rad + 40.0
                val g = ray(site.x + ux * r0, site.y + uy * r0, -ux, -uy, r0 + 5.0)
                if (g != null && g.b === site) {
                    h = g
                    ok = true
                    s.x = g.x + ux * (s.rad + 3.0)
                    s.y = g.y + uy * (s.rad + 3.0)
                    s.vx = site.vx
                    s.vy = site.vy
                    s.a = atan2(-uy, -ux)
                } else if (c.legT > 260.0) {
                    val ns = pickSite()
                    if (ns != null) {
                        c.site = ns
                        ns.worked++
                    }
                    c.legT = 0.0
                }
            }
            if (ok && h != null) {
                anchor(s, site, h.cx, h.cy)
                c.legT = 0.0
                c.state = "site"
                spawnMates(c)
                val ev = GS.ev
                if (ev != null) {
                    ev.hide = false
                    G.ride = null
                    val a = atan2(site.y - s.y, site.x - s.x) + PI / 2.0
                    ev.x = s.x + cos(a) * 9.0
                    ev.y = s.y + sin(a) * 9.0
                    ev.vx = 0.0
                    ev.vy = 0.0
                    ev.a = atan2(site.y - ev.y, site.x - ev.x)
                }
                AU.thunk()
                Hooks.toast(
                    "ON SITE · ${site.name} — out you go. Cut, fill the sack, bring it to the MULE. [MINE] cuts · [C] hand scanner surveys the body",
                    "good"
                )
            }
        }
        "site" -> {
            c.t -= dt
            val site = c.site ?: return
            if (s.dock == null) {
                val so = standoff(site, st.x, st.y, 16.0)
                val t = steer(
                    s, so.x, so.y,
                    SteerOpts(vmax = 15.0, out = ap, face = atan2(site.y - s.y, site.x - s.x), tvx = site.vx, tvy = site.vy)
                )
                applyOut(s, ap)
                if (t.d < 8.0 && t.rv < 1.5) {
                    val dd = max(1.0, hy(site.x - s.x, site.y - s.y))
                    val h = ray(s.x, s.y, (site.x - s.x) / dd, (site.y - s.y) / dd, 60.0)
                    if (h != null && hy(h.x - s.x, h.y - s.y) < 30.0) anchor(s, site, h.cx, h.cy)
                }
            }
            stepMates(c, dt)
            if (c.t <= 60.0 && !c.warned) {
                c.warned = true
                Hooks.toast("Manager Liu: \"One minute, people. Start bringing it home.\"", "warn")
                AU.warn()
            }
            if (c.t <= 0.0 && !c.horn) {
                c.horn = true
                c.grace = 75.0
                AU.alarm()
                Hooks.toast("SHIFT HORN · back to MULE-3 and press F at the hatch", "warn")
            }
            if (c.horn) {
                c.grace -= dt
                val matesIn = W.suits.all { it.st == "in" }
                val ev = GS.ev
                val youIn = ev == null || ev.hide
                if (c.grace <= 0.0 && !youIn && ev != null) {
                    Hooks.toast("Manager Liu reels you in on the tether. \"That comes out of your share.\"", "bad")
                    c.late = true
                    reelIn()
                }
                if (matesIn && (ev == null || ev.hide)) {
                    c.legT = 0.0
                    c.state = "back"
                    W.suits.clear()
                    undock(s)
                    Hooks.toast("MULE-3 · heading home to SELENE HIGH", "good")
                }
            }
        }
        "back" -> {
            val ch = c.home
            if (ch == null || (ch.occ != null && ch.occ !== s)) {
                val cand = (listOf(st.ports[2]) + st.ports).firstOrNull { it.occ == null || it.occ === s } ?: st.ports[2]
                c.home = cand
            }
            val p = c.home!!
            val pw = cw(st, p.cx, p.cy)
            val outDist = if (p.cx < st.gw / 2) -26.0 else 26.0
            val app = Vec2(pw.x + cos(st.a) * outDist, pw.y + sin(st.a) * outDist)
            val wp = detour(s.x, s.y, app.x, app.y, 16.0, st)
            val tx = wp?.x ?: app.x
            val ty = wp?.y ?: app.y
            val t = steer(s, tx, ty, SteerOpts(vmax = 40.0, out = ap, face = if (wp != null) null else 0.0))
            applyOut(s, ap)
            c.legT += dt
            if ((wp == null && t.d < 8.0 && t.rv < 1.0) || c.legT > 140.0) {
                c.legT = 0.0
                c.home = null
                if (p.occ == null || p.occ === s) dockPort(s, st, p, silent = true)
                payout(c)
            }
        }
        "idle" -> {
            s.thr = 0.0; s.rcx = 0.0; s.rcy = 0.0; s.tqi = 0.0
        }
    }
}

fun crewBoard() {
    val ev = GS.ev ?: return
    val c = GS.G?.crew ?: return
    if (c.state != "site") return
    if (!c.horn) {
        c.horn = true
        c.t = 0.0
        c.grace = 75.0
        Hooks.toast("You call it early. Manager Liu sounds the horn.", "warn")
    }
    ev.hide = true
    GS.G?.ride = RideState(kind = "shuttle", ship = c.ship)
}

private fun reelIn() {
    val ev = GS.ev ?: return
    val c = GS.G?.crew ?: return
    ev.hide = true
    GS.G?.ride = RideState(kind = "shuttle", ship = c.ship)
}

private fun payout(c: CrewState) {
    val G = GS.G ?: return
    val W = GS.W ?: return
    c.state = "idle"
    c.shifts++
    W.stats.shifts++
    c.ship.fuel = c.ship.fuelMax
    for (m in c.ship.mods) { m.hp = m.max; m.dead = false }
    val st = SITES.sel
    var value = 0.0
    var n = 0
    for ((g, q) in c.haul) {
        val def = GD[g] ?: continue
        value += q * def.p
        n += q
    }
    var mates = 0.0
    for ((g, q) in c.mates) {
        val def = GD[g] ?: continue
        mates += q * def.p
    }
    val base = 140
    val share = (value * 0.6 + mates * 0.08).roundToInt()
    var pay = base + share
    val wasLate = c.late
    if (wasLate) pay = (pay * 0.75).roundToInt()
    c.late = false
    G.cr += pay
    W.stats.earned += pay
    c.warned = false
    val ev = GS.ev
    if (ev != null) {
        val p = airlockSpot(st, c.ship.x, c.ship.y)
        ev.hide = false
        G.ride = null
        ev.home = null
        if (p != null) { ev.x = p.x; ev.y = p.y }
        ev.vx = 0.0
        ev.vy = 0.0
    }
    AU.cash()
    Hooks.toast(
        "SHIFT PAID · ${fm(pay)}₡ — base $base, your $n units ${fm((value * 0.6).roundToInt())}, crew bonus ${(mates * 0.08).roundToInt()}${if (wasLate) " (late −25%)" else ""}",
        "good"
    )
    Hooks.shiftDone(n, pay)
}

fun crewLine(): String {
    val c = GS.G?.crew ?: return ""
    return when (c.state) {
        "idle" -> "MULE-3 docked at SELENE HIGH · next shift boards on request"
        "out" -> "MULE-3 en route to ${c.site?.name ?: "site"}"
        "site" -> {
            val timer = if (c.t > 0.0) {
                val mins = floor(c.t / 60.0).toInt()
                val secs = floor(c.t % 60.0).toInt()
                "$mins:${secs.toString().padStart(2, '0')}"
            } else {
                "HORN · get back aboard"
            }
            "SHIFT $timer"
        }
        else -> "MULE-3 returning to SELENE HIGH"
    }
}
