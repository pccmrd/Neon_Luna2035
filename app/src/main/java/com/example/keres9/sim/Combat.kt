package com.example.keres9.sim

import com.example.keres9.audio.AU
import com.example.keres9.core.BeamInfo
import com.example.keres9.core.C
import com.example.keres9.core.Debris
import com.example.keres9.core.GS
import com.example.keres9.core.MAT
import com.example.keres9.core.MD
import com.example.keres9.core.Meteor
import com.example.keres9.core.Ship
import com.example.keres9.core.ShipModule
import com.example.keres9.core.hy
import com.example.keres9.core.parseHexColor
import com.example.keres9.core.rand
import com.example.keres9.core.ri
import com.example.keres9.core.rr
import com.example.keres9.world.addPick
import com.example.keres9.world.ca
import com.example.keres9.world.cut
import com.example.keres9.world.near
import com.example.keres9.world.ray
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val COL_DEAD = parseHexColor("#141a22")
private val COL_SPARK_STN = parseHexColor("#ffd9a0")
private val COL_SPARK_LASER = parseHexColor("#ffb347")
private val COL_METEOR_TRAIL = parseHexColor("#ffcf8a")

fun dmgMod(s: Ship, m: ShipModule, d0: Double, by: Ship?) {
    if (m.dead || s.dead) return
    if (s.crewShuttle) {
        m.flash = 0.08
        return
    }
    val d = if (s.isPl || s.own) d0 * Hooks.armor() else d0
    m.hp -= d
    m.flash = 0.08
    s.dirty = true
    s.hitT = 0.25
    if (m.hp <= 0.0) {
        m.dead = true
        val w = mw(s, m)
        boomFx(w.x, w.y, 0.5)
        gas(w.x, w.y, s.vx, s.vy)
        stats(s)
        if (m.t == "cock" || alive(s).size < s.mods.size * 0.32) {
            killShip(s, by)
        }
    }
}

fun dmgAt(s: Ship, x: Double, y: Double, d: Double, by: Ship?) {
    val c = cos(-s.a)
    val n = sin(-s.a)
    val dx = x - s.x
    val dy = y - s.y
    val gx = kotlin.math.floor((dx * c - dy * n + (s.gw * C.SS) / 2.0) / C.SS).toInt()
    val gy = kotlin.math.floor((dx * n + dy * c + (s.gh * C.SS) / 2.0) / C.SS).toInt()
    var i = if (gx in 0 until s.gw && gy in 0 until s.gh) s.grid[gy * s.gw + gx] else 0
    if (i == 0 || s.mods[i - 1].dead) {
        var bd = 1e9
        i = 0
        for (k in s.mods.indices) {
            val m = s.mods[k]
            if (m.dead) continue
            val d2 = hy((m.gx - gx).toDouble(), (m.gy - gy).toDouble())
            if (d2 < bd) {
                bd = d2
                i = k + 1
            }
        }
        if (bd > 3.0 || i == 0) return
    }
    dmgMod(s, s.mods[i - 1], d, by)
}

fun killShip(s: Ship, @Suppress("UNUSED_PARAMETER") by: Ship?) {
    if (s.dead) return
    val W = GS.W ?: return
    val rf = ::rand
    s.dead = true
    s.dock = null
    boomFx(s.x, s.y, 1.4)
    AU.boom(1.2)
    if (hy(s.x - GS.cam.x, s.y - GS.cam.y) < 900.0) {
        GS.cam.shake = min(22.0, GS.cam.shake + 14.0)
    }
    for ((g, qty) in s.cargo) {
        var q = qty
        while (q > 0) {
            val t = min(q, ri(rf, 1, 3))
            q -= t
            addPick(
                s.x + rr(rf, -10.0, 10.0),
                s.y + rr(rf, -10.0, 10.0),
                g,
                t,
                s.vx + rr(rf, -22.0, 22.0),
                s.vy + rr(rf, -22.0, 22.0)
            )
        }
    }
    s.cargo.clear()
    for (m in s.mods) {
        val w = mw(s, m)
        W.deb.add(
            Debris(
                x = w.x,
                y = w.y,
                vx = s.vx + rr(rf, -30.0, 30.0),
                vy = s.vy + rr(rf, -30.0, 30.0),
                a = s.a,
                va = rr(rf, -2.0, 2.0),
                w = C.SS,
                h = C.SS,
                c = if (m.dead) COL_DEAD else (MD[m.t]?.c ?: COL_DEAD),
                life = 45.0,
                hot = rr(rf, 1.0, 2.6)
            )
        )
    }
    if (s.own || s.isPl) Hooks.shipLost(s)
}

/** ore yield for a broken cell, thrown back toward the cutter */
fun spill(m: Int, x: Double, y: Double, dx: Double, dy: Double, isPl: Boolean, k: Double = 1.0) {
    val W = GS.W ?: return
    val mt = MAT.getOrNull(m) ?: return
    val rf = ::rand
    if (isPl) W.stats.mined++
    if (mt.ore != null && mt.ore != "parts") {
        addPick(
            x - dx * 4.0,
            y - dy * 4.0,
            mt.ore,
            if (rf() < 0.45 * k) 2 else 1,
            -dx * 14.0 + rr(rf, -8.0, 8.0),
            -dy * 14.0 + rr(rf, -8.0, 8.0)
        )
    } else if (m == 1 && rf() < 0.14 * k) {
        addPick(
            x - dx * 4.0,
            y - dy * 4.0,
            "fe",
            1,
            -dx * 14.0 + rr(rf, -8.0, 8.0),
            -dy * 14.0 + rr(rf, -8.0, 8.0)
        )
    } else if (mt.ore == "parts" && rf() < 0.2) {
        addPick(x - dx * 4.0, y - dy * 4.0, "parts", 1, -dx * 10.0, -dy * 10.0)
    }
    sparks(x, y, 6, mt.color, 80.0)
}

private val LASER_OFFSETS = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

/** ship mining laser: carves terrain with a soft 3-cell footprint, ejects ore toward the miner */
fun laser(s: Ship, dt: Double) {
    s.beam = null
    if (!s.wantM || s.dead || s.fuel <= 0.0) return
    val m = alive(s, "mine").firstOrNull() ?: return
    val w = mw(s, m)
    val dx = cos(s.a)
    val dy = sin(s.a)
    s.fuel = max(0.0, s.fuel - dt * 0.12)
    val h = ray(w.x, w.y, dx, dy, s.lasR)
    if (h != null) {
        s.beam = BeamInfo(w.x, w.y, h.x, h.y, hit = true)
        val b = h.b
        if (b.kind == "station") {
            if (rand() < 0.3) sparks(h.x, h.y, 2, COL_SPARK_STN, 40.0)
            return
        }
        val mined = mutableListOf<Int>()
        val m0 = cut(b, h.cx, h.cy, s.lasD * dt)
        if (m0 != 0) mined.add(m0)
        for ((ox, oy) in LASER_OFFSETS) {
            val cx = h.cx + ox
            val cy = h.cy + oy
            if (cx in 0 until b.gw && cy in 0 until b.gh) {
                val mm = cut(b, cx, cy, s.lasD * dt * 0.35)
                if (mm != 0) mined.add(mm)
            }
        }
        if (rand() < 0.35) sparks(h.x, h.y, 2, COL_SPARK_LASER, 55.0)
        if (rand() < 0.08) {
            smoke(
                h.x - dx * 3.0,
                h.y - dy * 3.0,
                -dx * 8.0 + rr(::rand, -6.0, 6.0),
                -dy * 8.0 + rr(::rand, -6.0, 6.0),
                2.5,
                1.4
            )
        }
        for (m2 in mined) spill(m2, h.x, h.y, dx, dy, s.isPl)
    } else {
        s.beam = BeamInfo(w.x, w.y, w.x + dx * s.lasR, w.y + dy * s.lasR, hit = false)
    }
}

/** micrometeoroids: fast, hot, indiscriminate — they cut rock and shred hulls */
fun stepMeteors(dt: Double) {
    val W = GS.W ?: return
    val ev = GS.ev
    val rf = ::rand
    for (m in W.meteors) {
        m.life -= dt
        if (m.life <= 0.0) {
            m.dead = true
            continue
        }
        val steps = 3
        for (s in 0 until steps) {
            if (m.dead) break
            m.px = m.x
            m.py = m.y
            m.x += (m.vx * dt) / steps
            m.y += (m.vy * dt) / steps
            for (t in near(m.x, m.y, 4.0)) {
                val c = ca(t, m.x, m.y)
                if (c != null && t.sol[c.i] != 0) {
                    if (t.kind != "station") {
                        for (oy in -1..1) {
                            for (ox in -1..1) {
                                val cx = c.cx + ox
                                val cy = c.cy + oy
                                if (cx in 0 until t.gw && cy in 0 until t.gh) {
                                    cut(t, cx, cy, if (ox != 0 || oy != 0) 40.0 * m.r else 140.0 * m.r)
                                }
                            }
                        }
                    }
                    impact(m)
                    break
                }
            }
            if (m.dead) break
            if (hypot(m.x, m.y) < C.RM + 60.0) {
                impact(m)
                break
            }
            for (sh in W.ships) {
                if (sh.dead) continue
                if (hy(sh.x - m.x, sh.y - m.y) < sh.rad) {
                    dmgAt(sh, m.x, m.y, 22.0 * m.r, null)
                    sh.vx += m.vx * 0.01 * m.r
                    sh.vy += m.vy * 0.01 * m.r
                    if (sh.isPl) {
                        GS.cam.shake = min(20.0, GS.cam.shake + 8.0)
                        AU.warn()
                    }
                    impact(m)
                    break
                }
            }
            if (!m.dead && ev != null && !ev.dead && !ev.hide && hy(ev.x - m.x, ev.y - m.y) < 4.0) {
                ev.suit -= 30.0
                impact(m)
            }
        }
        if (!m.dead && rf() < 0.5) {
            bit(
                com.example.keres9.core.Bit(
                    x = m.x,
                    y = m.y,
                    vx = m.vx * 0.05 + rr(rf, -6.0, 6.0),
                    vy = m.vy * 0.05 + rr(rf, -6.0, 6.0),
                    life = 0.5,
                    max = 0.5,
                    s = 1.6 * m.r,
                    c = COL_METEOR_TRAIL,
                    t = "thrust"
                )
            )
        }
    }
    W.meteors.removeAll { it.dead }
}

private fun impact(m: Meteor) {
    m.dead = true
    boomFx(m.x, m.y, 0.45 + m.r * 0.35)
    if (hy(m.x - GS.cam.x, m.y - GS.cam.y) < 600.0) {
        AU.boom(0.5)
        GS.cam.shake = min(14.0, GS.cam.shake + 3.0)
    }
}
