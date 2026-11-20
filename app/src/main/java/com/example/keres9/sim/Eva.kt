package com.example.keres9.sim

import com.example.keres9.audio.AU
import com.example.keres9.core.BeamInfo
import com.example.keres9.core.Body
import com.example.keres9.core.C
import com.example.keres9.core.CrewMateSuit
import com.example.keres9.core.GD
import com.example.keres9.core.GS
import com.example.keres9.core.Room
import com.example.keres9.core.Ship
import com.example.keres9.core.SuitState
import com.example.keres9.core.Vec2
import com.example.keres9.core.ad
import com.example.keres9.core.cl
import com.example.keres9.core.gacc
import com.example.keres9.core.geff
import com.example.keres9.core.hy
import com.example.keres9.core.parseHexColor
import com.example.keres9.core.rand
import com.example.keres9.world.CellHit
import com.example.keres9.world.ca
import com.example.keres9.world.collideCircle
import com.example.keres9.world.cut
import com.example.keres9.world.cw
import com.example.keres9.world.groundEntity
import com.example.keres9.world.near
import com.example.keres9.world.ray
import com.example.keres9.world.solidAt
import com.example.keres9.world.surfR
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

private val GA = Vec2()
private val COL_SUIT_SPARK = parseHexColor("#a8ecff")
private val COL_CUT_SPARK = parseHexColor("#ffb347")

data class BodyCell(val b: Body, val c: CellHit)
data class RoomHit(val b: Body, val rm: Room?)
data class CrewSpotHit(val cr: com.example.keres9.core.StationCrew, val b: Body)

fun sackN(e: SuitState?): Int {
    if (e == null) return 0
    var n = 0
    for (v in e.sack.values) n += v
    return n
}

fun sackN(sack: Map<String, Int>): Int {
    var n = 0
    for (v in sack.values) n += v
    return n
}

/** the body whose pressurised interior (floor cell) a point is inside */
fun inBody(x: Double, y: Double): BodyCell? {
    for (b in near(x, y, 8.0)) {
        val c = ca(b, x, y)
        if (c != null && b.sol[c.i] == 0 && b.flr[c.i] != 0) return BodyCell(b, c)
    }
    return null
}

fun evaIn(): Body? {
    val ev = GS.ev ?: return null
    if (ev.dead) return null
    return inBody(ev.x, ev.y)?.b
}

/** the room (with kind) the suit floats in, if any */
fun evaRoom(): RoomHit? {
    val ev = GS.ev ?: return null
    val q = inBody(ev.x, ev.y) ?: return null
    if (q.b.kind != "station") return null
    val cx = q.c.cx
    val cy = q.c.cy
    for (rm in q.b.rooms) {
        if (cx > rm.x && cy > rm.y && cx < rm.x + rm.w - 1 && cy < rm.y + rm.h - 1) {
            return RoomHit(q.b, rm)
        }
    }
    return RoomHit(q.b, null)
}

private fun pressurised(q: BodyCell?): Boolean = q != null && q.b.kind == "station" && q.b.flr[q.c.i] == 2

/** ship the suit can board right now */
fun boardable(): Ship? {
    val ev = GS.ev ?: return null
    val W = GS.W ?: return null
    var best: Ship? = null
    var bd = 15.0
    for (s in W.ships) {
        if (s.dead || !s.own) continue
        val d = hy(ev.x - s.x, ev.y - s.y) - s.rad * 0.5
        if (d < bd) {
            bd = d
            best = s
        }
    }
    return best
}

fun mkSuit(x: Double, y: Double, vx: Double = 0.0, vy: Double = 0.0, home: Ship? = null): SuitState =
    SuitState(x = x, y = y, vx = vx, vy = vy, home = home)

/** place the suit in the station interior nearest to a docked port (airlock transfer) */
fun airlockSpot(b: Body, x: Double, y: Double): Vec2? {
    var best: Vec2? = null
    var bd = 1e9
    for (cy in 0 until b.gh) {
        for (cx in 0 until b.gw) {
            val i = cy * b.gw + cx
            if (b.sol[i] != 0 || b.flr[i] != 2) continue
            val w = cw(b, cx, cy)
            val d = hy(w.x - x, w.y - y)
            if (d < bd) {
                bd = d
                best = w
            }
        }
    }
    return best
}

fun enterEVA(silent: Boolean = false) {
    if (GS.ev != null) return
    val s = GS.pl ?: return
    if (s.dead) return
    if (s.dock == null && hy(s.vx, s.vy) > 2.5 && s.grounded <= 0.0) {
        Hooks.toast("EVA denied — kill drift below 2.5 m/s", "warn")
        return
    }
    var p: Vec2? = null
    val d = s.dock
    if (d != null && (d.ty == "port" || d.ty == "pad") && d.b != null) {
        p = airlockSpot(d.b, s.x, s.y)
    }
    if (p == null) {
        val dir = s.a + PI / 2.0
        p = Vec2(s.x + cos(dir) * (s.belly + 3.0), s.y + sin(dir) * (s.belly + 3.0))
    }
    val suit = mkSuit(p.x, p.y, if (d != null) 0.0 else s.vx, if (d != null) 0.0 else s.vy, s)
    suit.a = s.a
    GS.ev = suit
    s.thr = 0.0; s.rcx = 0.0; s.rcy = 0.0; s.tqi = 0.0
    s.wantM = false
    s.isPl = false
    GS.pl = null
    GS.W?.stats?.let { it.eva++ }
    AU.air(true)
    AU.puff()
    GS.PC.scan = false
    Hooks.closeStation()
    if (!silent) {
        Hooks.toast("SUIT · air ${C.EVA.air.toInt()}s · cutter on [MINE] · sack fills itself · F at the hull to board", "good")
    }
}

/** climb aboard a ship (own hull or the crew shuttle you came out of) */
fun boardShip(s: Ship?) {
    val ev = GS.ev ?: return
    if (s == null || s.dead) return
    ev.carry?.let { carry ->
        val t = carry.type
        if (s.own && cadd(s, t, carry.qty) > 0) {
            carry.dead = true
        } else {
            carry.held = false
            carry.x = ev.x
            carry.y = ev.y
        }
        ev.carry = null
    }
    if (s.own) {
        stow(s)
        s.isPl = true
        GS.pl = s
    }
    GS.ev = null
    AU.air(false)
    AU.thunk()
    if (!s.own) Hooks.boardCrew(s)
}

/** empty the sack into a ship's hold (own) or the crew tally (shuttle) */
fun stow(s: Ship): Int {
    val ev = GS.ev ?: return 0
    val G = GS.G ?: return 0
    var n = 0
    val keys = ev.sack.keys.toList()
    for (g in keys) {
        val q = ev.sack[g] ?: 0
        if (q <= 0) continue
        val t = if (s.own) {
            cadd(s, g, q)
        } else {
            G.crew?.let { cr -> cr.haul[g] = (cr.haul[g] ?: 0) + q }
            q
        }
        val rem = q - t
        if (rem <= 0) ev.sack.remove(g) else ev.sack[g] = rem
        n += t
    }
    if (n > 0) {
        AU.blip()
        Hooks.toast("STOWED $n units${if (s.own) "" else " to the crew tally"}", "good")
    }
    return n
}

fun ping() {
    val ev = GS.ev ?: return
    val W = GS.W ?: return
    if (ev.pingT < 1.2) return
    ev.pingT = 0.0
    ev.pingEnd = W.time + 6.0
    AU.tick(0.8)
    for (p in W.picks) {
        if (hy(p.x - ev.x, p.y - ev.y) < C.EVA.ping) p.rev = W.time + 6.0
    }
    for (c in W.contacts) {
        if (!c.done && hy(c.x - ev.x, c.y - ev.y) < C.EVA.ping * 1.3) c.hint = W.time + 6.0
    }
    var n = 0
    for (b in W.rocks) {
        if (b.cat == 0 && hy(b.x - ev.x, b.y - ev.y) - b.rad < C.EVA.ping * 0.6) {
            Hooks.onCatalog(b, "suit")
            n++
        }
    }
    Hooks.toast(if (n > 0) "HAND SCANNER · $n body surveyed" else "HAND SCANNER · valuables outlined 6s", "good")
}

private fun cutter(e: SuitState, dt: Double) {
    e.beam = null
    if (!GS.CTL.mine) return
    val G = GS.G ?: return
    var dx = cos(e.a)
    var dy = sin(e.a)
    var h = ray(e.x, e.y, dx, dy, C.CUT.rng)
    if (h == null || h.b.kind == "station") {
        var best: Double? = null
        var bd = 1e9
        for (k in -6..6) {
            val a = e.a + k * 0.09
            val q = ray(e.x, e.y, cos(a), sin(a), C.CUT.rng)
            if (q != null && q.b.kind != "station" && q.d + abs(k) * 2.0 < bd) {
                bd = q.d + abs(k) * 2.0
                best = a
            }
        }
        if (best != null) {
            e.a += ad(best, e.a) * min(1.0, dt * 6.0)
            dx = cos(e.a)
            dy = sin(e.a)
            h = ray(e.x, e.y, dx, dy, C.CUT.rng)
        }
    }
    if (h != null && h.b.kind != "station") {
        e.beam = BeamInfo(e.x, e.y, h.x, h.y)
        val m = cut(h.b, h.cx, h.cy, C.CUT.dps * dt * (if (G.suitUp > 0.0) G.suitUp else 1.0))
        if (m != 0) spill(m, h.x, h.y, dx, dy, true, 0.6)
        if (rand() < 0.3) sparks(h.x, h.y, 2, COL_CUT_SPARK, 40.0)
    } else {
        e.beam = BeamInfo(e.x, e.y, e.x + dx * C.CUT.rng, e.y + dy * C.CUT.rng, miss = true)
    }
}

fun stepEVA(dt: Double) {
    val e = GS.ev ?: return
    val W = GS.W ?: return
    val G = GS.G ?: return
    if (e.dead || e.hide) return
    e.pingT += dt
    e.bob += dt
    val r = hy(e.x, e.y).let { if (it == 0.0) 1.0 else it }
    val ux = e.x / r
    val uy = e.y / r
    val g = geff(r)
    gacc(e.x, e.y, e.vx, e.vy, GA)
    e.vx += GA.x * dt
    e.vy += GA.y * dt

    val CTL = GS.CTL
    val cr = cos(GS.cam.rot)
    val sr = sin(GS.cam.rot)
    val wx = CTL.lx * cr - CTL.ly * sr
    val wy = CTL.lx * sr + CTL.ly * cr
    val al = hy(wx, wy)
    val onFloor = e.grounded > 0.0
    val nearGround = r - surfR(atan2(e.y, e.x)) < 60.0

    if (g > 0.4 && nearGround) {
        val tx = -uy
        val ty = ux
        val vt = e.vx * tx + e.vy * ty
        val vr = e.vx * ux + e.vy * uy
        val want = CTL.lx * C.EVA.walk * (if (e.carry != null) 0.7 else 1.0)
        if (onFloor || abs(CTL.lx) > 0.1) {
            val acc = if (onFloor) 40.0 else 7.0
            val dv = cl(want - vt, -acc * dt, acc * dt)
            e.vx += tx * dv
            e.vy += ty * dv
        }
        if (abs(CTL.lx) > 0.2) {
            val sg = sign(CTL.lx)
            e.a = atan2(ty * sg, tx * sg)
        }
        if (CTL.ly < -0.3 && vr < 12.0) {
            e.vx += ux * C.EVA.acc * -CTL.ly * dt * 1.2
            e.vy += uy * C.EVA.acc * -CTL.ly * dt * 1.2
            e.jet = 0.2
            if (rand() < 0.3) gas(e.x - ux * 3.0, e.y - uy * 3.0, e.vx - ux * 25.0, e.vy - uy * 25.0)
        } else if (CTL.ly > 0.3 && !onFloor && vr > -12.0) {
            e.vx -= ux * C.EVA.acc * 0.5 * dt
            e.vy -= uy * C.EVA.acc * 0.5 * dt
            e.jet = 0.15
        }
        if (onFloor && abs(want) > 1.0 && rand() < dt * 4.0) {
            dust(e.x - ux * 2.6, e.y - uy * 2.6, ux, uy, 1)
        }
    } else if (al > 0.08) {
        val a = C.EVA.acc * (if (e.carry != null) 0.68 else 1.0)
        val dx = wx / al
        val dy = wy / al
        var rvx = e.vx
        var rvy = e.vy
        val nb = near(e.x, e.y, 200.0).firstOrNull()
        if (nb != null) {
            rvx -= nb.vx
            rvy -= nb.vy
        }
        if (rvx * dx + rvy * dy < 30.0) {
            e.vx += dx * a * min(1.0, al) * dt
            e.vy += dy * a * min(1.0, al) * dt
        }
        e.jet = 0.15
        if (rand() < 0.25) gas(e.x - wx * 3.0, e.y - wy * 3.0, e.vx - wx * 25.0, e.vy - wy * 25.0)
    } else if (g < 0.8 || !nearGround) {
        var rvx = e.vx
        var rvy = e.vy
        val nb = near(e.x, e.y, 60.0).firstOrNull()
        if (nb != null) {
            rvx -= nb.vx
            rvy -= nb.vy
        }
        val m = hy(rvx, rvy)
        if (m > 0.05) {
            val k = min(m, C.EVA.acc * 0.45 * dt) / m
            e.vx -= rvx * k
            e.vy -= rvy * k
        }
    }

    if (abs(CTL.rx) > 0.15) {
        e.a += CTL.rx * 2.6 * dt
    } else if (!onFloor && al > 0.3 && !CTL.mine) {
        e.a += ad(atan2(wy, wx), e.a) * min(1.0, dt * 3.0)
    }
    e.x += e.vx * dt
    e.y += e.vy * dt
    e.grounded = max(0.0, e.grounded - dt)

    collideCircle(
        x = e.x, y = e.y, vx = e.vx, vy = e.vy, rad = 2.6,
        onHit = { im, x, y, _, _, _ ->
            if (im > 7.0) {
                e.suit -= (im - 7.0) * 3.0
                sparks(x, y, 3, COL_SUIT_SPARK, 40.0)
                AU.thunk()
            }
        },
        update = { nx, ny, nvx, nvy, _ ->
            e.x = nx; e.y = ny; e.vx = nvx; e.vy = nvy
        }
    )
    val gc = groundEntity(
        e.x, e.y, e.vx, e.vy, 2.6, dt, 0.1,
        if (onFloor && abs(CTL.lx) < 0.1) 9.0 else 0.6
    ) { _, _, nx, ny, nvx, nvy, _ ->
        e.x = nx; e.y = ny; e.vx = nvx; e.vy = nvy
    }
    if (gc != null) {
        e.grounded = 0.15
        if (gc.im > 9.0) {
            e.suit -= (gc.im - 9.0) * 3.0
            AU.thunk()
        }
    }
    if (g > 0.4 && nearGround && solidAt(e.x - ux * 3.3, e.y - uy * 3.3) != null) {
        e.grounded = 0.15
        if (abs(CTL.lx) < 0.1) {
            val tx = -uy
            val ty = ux
            val vt = e.vx * tx + e.vy * ty
            val k = 1.0 - exp(-9.0 * dt)
            e.vx -= tx * vt * k
            e.vy -= ty * vt * k
        }
    }
    if (e.jet > 0.0) e.jet -= dt
    cutter(e, dt)

    val q = inBody(e.x, e.y)
    val home = e.home?.let { h -> !h.dead && hy(e.x - h.x, e.y - h.y) < 14.0 } == true
    if (pressurised(q) || home) {
        e.suit = min(100.0, e.suit + dt * 30.0)
    } else {
        val airMul = if (W.flare?.on == true) 3.5 else 1.0
        val airCap = C.EVA.air * (if (G.airUp > 0.0) G.airUp else 1.0)
        e.suit -= dt * (100.0 / airCap) * airMul
    }

    val sackCap = floor(C.EVA.sack * (if (G.sackUp > 0.0) G.sackUp else 1.0)).toInt()
    if (sackN(e) < sackCap) {
        for (p in W.picks) {
            val def = GD[p.type]
            if (p.dead || p.held || def == null || p.age < 0.3) continue
            val d = hy(p.x - e.x, p.y - e.y)
            if (d < (if (def.story) 30.0 else 9.0)) {
                val k = (90.0 * dt) / max(3.0, d)
                p.vx += (e.x - p.x) * k
                p.vy += (e.y - p.y) * k
                if (d < 3.5) {
                    val room = sackCap - sackN(e)
                    val t = min(room, p.qty)
                    if (t > 0) {
                        e.sack[p.type] = (e.sack[p.type] ?: 0) + t
                        p.qty -= t
                        if (p.qty <= 0) p.dead = true
                        AU.blip()
                    }
                }
            }
        }
    } else if (e.fullT == 0.0 || W.time - e.fullT > 12.0) {
        e.fullT = W.time
        Hooks.toast("SACK FULL — stow it at the hull", "warn")
    }

    val homeShip = e.home
    if (homeShip != null && hy(e.x - homeShip.x, e.y - homeShip.y) < 16.0 && sackN(e) > 0) {
        stow(homeShip)
    }
    e.carry?.let { carry ->
        carry.x = e.x + cos(e.a) * 4.0
        carry.y = e.y + sin(e.a) * 4.0
        carry.vx = e.vx
        carry.vy = e.vy
    }
    if (e.suit <= 0.0) {
        e.dead = true
        AU.warn()
        Hooks.suitOut()
    }
}

fun nearestCrew(): CrewSpotHit? {
    val ev = GS.ev ?: return null
    val W = GS.W ?: return null
    var best: CrewSpotHit? = null
    var bd = 15.0
    for (b in W.bodies) {
        if (b.dead || b.crew.isEmpty()) continue
        if (abs(b.x - ev.x) > b.rad + 20.0 || abs(b.y - ev.y) > b.rad + 20.0) continue
        val c = cos(b.a)
        val s = sin(b.a)
        for (cr in b.crew) {
            val x = b.x + cr.lx * c - cr.ly * s
            val y = b.y + cr.lx * s + cr.ly * c
            val d = hy(ev.x - x, ev.y - y)
            if (d < bd) {
                bd = d
                best = CrewSpotHit(cr, b)
            }
        }
    }
    return best
}
