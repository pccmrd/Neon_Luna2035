package com.example.keres9.sim

import com.example.keres9.audio.AU
import com.example.keres9.core.Bit
import com.example.keres9.core.C
import com.example.keres9.core.GD
import com.example.keres9.core.GS
import com.example.keres9.core.Pick
import com.example.keres9.core.Vec2
import com.example.keres9.core.ad
import com.example.keres9.core.an
import com.example.keres9.core.cl
import com.example.keres9.core.gacc
import com.example.keres9.core.hy
import com.example.keres9.core.parseHexColor
import com.example.keres9.core.rand
import com.example.keres9.core.rr
import com.example.keres9.world.bvel
import com.example.keres9.world.collideCircle
import com.example.keres9.world.cut
import com.example.keres9.world.groundEntity
import com.example.keres9.world.lw
import com.example.keres9.world.ray
import com.example.keres9.world.surfN
import com.example.keres9.world.surfR
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

private val GA = Vec2()
private val COL_AMBER = parseHexColor("#ffb347")
private val COL_ORANGE = parseHexColor("#ff8c1a")
private val COL_FLASH = parseHexColor("#ffd9a0")

private fun nearCam(x: Double, y: Double, d: Double): Boolean = hy(x - GS.cam.x, y - GS.cam.y) < d

/** multi-point rigid contact against the regolith; tail-landers settle when upright and slow */
private fun groundShip(s: com.example.keres9.core.Ship, dt: Double) {
    val r = hy(s.x, s.y)
    val th = atan2(s.y, s.x)
    val S = surfR(th)
    if (r - S > s.nose + 6.0) {
        s.settleT = 0.0
        return
    }
    val hw = s.nose
    val hh = s.belly
    val invI = 12.0 / (4.0 * hw * hw + 4.0 * hh * hh)
    var hit = false
    var maxIm = 0.0
    val pts = arrayOf(
        -hw to -hh, -hw to hh, hw to -hh, hw to hh,
        0.0 to -hh, 0.0 to hh, -hw to 0.0, hw to 0.0
    )
    for ((lx, ly) in pts) {
        val p = sw(s, lx, ly)
        val pr = hy(p.x, p.y)
        val pth = atan2(p.y, p.x)
        val Sp = surfR(pth)
        val pen = Sp - pr
        if (pen <= 0.0) continue
        hit = true
        val n = surfN(pth)
        s.x += n.x * pen
        s.y += n.y * pen
        val rx = p.x - s.x
        val ry = p.y - s.y
        val vpx = s.vx - ry * s.va
        val vpy = s.vy + rx * s.va
        val vn = vpx * n.x + vpy * n.y
        if (vn >= 0.0) continue
        val rn = rx * n.y - ry * n.x
        val j = (-1.15 * vn) / (1.0 + rn * rn * invI)
        s.vx += j * n.x
        s.vy += j * n.y
        s.va += j * rn * invI
        val tx = -n.y
        val ty = n.x
        val vt = vpx * tx + vpy * ty
        val rt = rx * ty - ry * tx
        val jt = cl(-vt / (1.0 + rt * rt * invI), -0.7 * j, 0.7 * j)
        s.vx += jt * tx
        s.vy += jt * ty
        s.va += jt * rt * invI
        val gearPt = s.tailLand && lx < 0.0 && s.gear && s.gearOK
        val lim = if (gearPt) C.TOUCH.v + 1.3 else 2.2
        if (-vn > maxIm) maxIm = -vn
        if (-vn > lim) {
            dmgAt(s, p.x, p.y, (-vn - lim) * 7.0, null)
            sparks(p.x, p.y, 6, COL_AMBER, 50.0)
        }
        if (-vn > 1.2 && nearCam(p.x, p.y, 700.0)) {
            dust(p.x, p.y, n.x, n.y, min(10.0, -vn * 2.0).toInt())
        }
    }
    if (!hit) {
        s.settleT = 0.0
        return
    }
    s.va *= exp(-2.2 * dt)
    if (s.isPl && maxIm > 2.5) {
        GS.cam.shake = min(16.0, GS.cam.shake + maxIm * 1.2)
        AU.thunk()
    }
    val up = atan2(s.y, s.x)
    val upright = abs(ad(s.a, up)) < C.TOUCH.a
    if (s.tailLand && s.gear && s.gearOK && upright && hy(s.vx, s.vy) < 0.8 && abs(s.va) < 0.08 && s.thr < 0.08) {
        s.settleT += dt
        if (s.settleT > 0.5) {
            s.settleT = 0.0
            settle(s)
        }
    } else {
        s.settleT = 0.0
    }
}

fun stepShip(s: com.example.keres9.core.Ship, dt: Double) {
    if (s.dead) return
    val W = GS.W ?: return
    val rf = ::rand
    if (s.rail) {
        if (s.dirty) sprite(s)
        return
    }
    for (m in s.mods) {
        if (m.flash > 0.0) {
            m.flash -= dt
            if (m.flash <= 0.0) s.dirty = true
        }
    }
    if (s.hitT > 0.0) s.hitT -= dt
    val d = s.dock
    if (d != null) {
        if ((d.ty == "ground" || (d.ty == "pad" && s.tailLand)) && s.thr > 0.06 && s.fuel > 0.0 && alive(s, "eng").isNotEmpty()) {
            val wasPad = d.ty == "pad"
            undock(s)
            if (wasPad) s.liftT = W.time
            if (s.isPl) Hooks.toast("LIFT-OFF", "good")
        } else {
            attach(s)
            laser(s, dt)
            if (s.dirty) sprite(s)
            return
        }
    }
    gacc(s.x, s.y, s.vx, s.vy, GA)
    s.vx += GA.x * dt
    s.vy += GA.y * dt
    if (s.thr > 0.0 && alive(s, "eng").isNotEmpty() && s.fuel > 0.0) {
        val a = s.thrust * s.thr
        s.vx += cos(s.a) * a * dt
        s.vy += sin(s.a) * a * dt
        s.fuel = max(0.0, s.fuel - s.thr * dt * s.burn)
        if (nearCam(s.x, s.y, 1400.0)) {
            val bk = s.a + PI
            for (e in alive(s, "eng")) {
                val w = mw(s, e)
                val sp = rr(rf, 40.0, 90.0) * s.thr
                val da = bk + rr(rf, -0.12, 0.12)
                bit(
                    Bit(
                        x = w.x,
                        y = w.y,
                        vx = s.vx + cos(da) * sp,
                        vy = s.vy + sin(da) * sp,
                        life = 0.28,
                        max = 0.28,
                        s = rr(rf, 2.0, 4.5) * s.thr + 1.0,
                        c = if (rf() < 0.5) COL_AMBER else COL_ORANGE,
                        t = "thrust"
                    )
                )
            }
            val r = hy(s.x, s.y).let { if (it == 0.0) 1.0 else it }
            val alt = r - surfR(atan2(s.y, s.x))
            if (alt < 70.0 && s.thr > 0.2 && rf() < s.thr * 0.8) {
                val ex = cos(bk)
                val ey = sin(bk)
                val ux = s.x / r
                val uy = s.y / r
                if (ex * ux + ey * uy < -0.5) {
                    val gx = s.x - ux * alt
                    val gy = s.y - uy * alt
                    dust(gx + rr(rf, -6.0, 6.0), gy + rr(rf, -6.0, 6.0), ux, uy, (3.0 + (70.0 - alt) * 0.1).toInt())
                }
            }
        }
    }
    val rl = hy(s.rcx, s.rcy)
    if (rl > 0.05 && s.fuel > 0.0 && s.rcsA > 0.0) {
        val a = s.rcsA * min(1.0, rl)
        s.vx += (s.rcx / rl) * a * dt
        s.vy += (s.rcy / rl) * a * dt
        s.fuel = max(0.0, s.fuel - rl * dt * 0.06 * s.rcsA)
        if (s.isPl && rf() < 0.3) AU.puff()
        if (rf() < 0.55 * min(1.0, rl) && nearCam(s.x, s.y, 900.0)) {
            val ux = -s.rcx / rl
            val uy = -s.rcy / rl
            jet(
                s.x + ux * s.rad * 0.8 + rr(rf, -2.0, 2.0),
                s.y + uy * s.rad * 0.8 + rr(rf, -2.0, 2.0),
                s.vx + ux * 38.0,
                s.vy + uy * 38.0
            )
        }
    }
    s.va += s.tqi * s.tq * dt
    s.va *= exp(-(if (abs(s.tqi) > 0.05) 0.12 else 0.95) * dt)
    if (abs(s.tqi) > 0.25 && rf() < 0.3 && nearCam(s.x, s.y, 700.0)) {
        val sg = sign(s.tqi)
        val ca = cos(s.a)
        val sa = sin(s.a)
        val n = s.nose * 0.8
        jet(s.x + ca * n, s.y + sa * n, s.vx - sa * 30.0 * -sg, s.vy + ca * 30.0 * -sg)
    }
    s.x += s.vx * dt
    s.y += s.vy * dt
    s.a = an(s.a + s.va * dt)
    val r = hy(s.x, s.y)
    if (r > C.RS + 2400.0 && !s.leaving && r > 1.0) {
        s.vx -= (s.x / r) * 3.0 * dt
        s.vy -= (s.y / r) * 3.0 * dt
    }
    collideCircle(
        x = s.x,
        y = s.y,
        vx = s.vx,
        vy = s.vy,
        rad = s.rad * 0.72,
        hasVa = true,
        va = s.va,
        onHit = { im, x, y, b, cx, cy ->
            if (im > 3.0) {
                dmgAt(s, x, y, (im - 3.0) * 2.4, null)
                sparks(x, y, min(14.0, im * 2.0).toInt(), COL_AMBER, im * 8.0)
                if (cx >= 0 && b.kind != "station") cut(b, cx, cy, im * 2.5)
                if (s.isPl) {
                    GS.cam.shake = min(18.0, GS.cam.shake + im * 1.4)
                    AU.thunk()
                    if (im > 9.0) AU.warn()
                }
            }
        },
        update = { nx, ny, nvx, nvy, nva ->
            s.x = nx; s.y = ny; s.vx = nvx; s.vy = nvy; s.va = nva
        }
    )
    groundShip(s, dt)
    if (s.hitT > 0.0 && alive(s).size < s.mods.size * 0.6 && rf() < 0.3) {
        smoke(s.x, s.y, s.vx + rr(rf, -5.0, 5.0), s.vy + rr(rf, -5.0, 5.0), 2.5, 1.6)
    }
    laser(s, dt)
    if (s.dirty) sprite(s)
}

fun stepShips(dt: Double) {
    val W = GS.W ?: return
    for (s in W.ships) stepShip(s, dt)
    val a = W.ships.filter { !it.dead && it.dock == null && !it.ghost && !it.rail }
    for (i in a.indices) {
        for (j in (i + 1) until a.size) {
            val p = a[i]
            val q = a[j]
            val dx = q.x - p.x
            val dy = q.y - p.y
            if (abs(dx) > 40.0 || abs(dy) > 40.0) continue
            val d = hy(dx, dy)
            val rrSum = p.rad + q.rad
            if (d < rrSum && d > 0.01) {
                val nx = dx / d
                val ny = dy / d
                val pen = (rrSum - d) / 2.0
                p.x -= nx * pen
                p.y -= ny * pen
                q.x += nx * pen
                q.y += ny * pen
                val vn = (q.vx - p.vx) * nx + (q.vy - p.vy) * ny
                if (vn < 0.0) {
                    val im = -vn * 1.25
                    p.vx -= nx * im * 0.5
                    p.vy -= ny * im * 0.5
                    q.vx += nx * im * 0.5
                    q.vy += ny * im * 0.5
                    if (-vn > 4.0) {
                        val mx = (p.x + q.x) / 2.0
                        val my = (p.y + q.y) / 2.0
                        dmgAt(p, mx, my, -vn * 2.0, q)
                        dmgAt(q, mx, my, -vn * 2.0, p)
                        sparks(mx, my, 8, COL_FLASH, 60.0)
                        if (p.isPl || q.isPl) {
                            AU.thunk()
                            GS.cam.shake += 6.0
                        }
                    }
                }
            }
        }
    }
    W.ships.removeAll { it.dead && !it.isPl }
}

fun nearPick(x: Double, y: Double, r: Double, pred: ((Pick) -> Boolean)? = null): Pick? {
    val W = GS.W ?: return null
    var best: Pick? = null
    var bd = r
    for (p in W.picks) {
        if (p.dead || p.held || (pred != null && !pred(p))) continue
        val d = hy(p.x - x, p.y - y)
        if (d < bd) {
            bd = d
            best = p
        }
    }
    return best
}

fun stepPicks(dt: Double) {
    val W = GS.W ?: return
    val pl = GS.pl
    val ev = GS.ev
    val f = GS.focus()
    for (p in W.picks) {
        if (p.dead || p.held) continue
        if (f == null || hy(p.x - f.x, p.y - f.y) > 1600.0) {
            val h = p.home
            if (h != null) {
                val w = lw(h, p.hx, p.hy)
                val v = bvel(h, w.x, w.y)
                p.x = w.x; p.y = w.y; p.vx = v.x; p.vy = v.y
            }
            continue
        }
        p.age += dt
        gacc(p.x, p.y, p.vx, p.vy, GA)
        p.vx += GA.x * dt
        p.vy += GA.y * dt
        p.x += p.vx * dt
        p.y += p.vy * dt
        p.a += p.va * dt

        var beamed = false
        var px = pl?.x ?: 0.0
        var py = pl?.y ?: 0.0
        val def = GD[p.type]
        if (pl != null && !pl.dead && ev == null && def != null && def.story && (pl.dock == null || pl.dock?.ty == "anchor")) {
            val dx = pl.x - p.x
            val dy = pl.y - p.y
            val d = hy(dx, dy)
            if (d < 100.0 && d > 1.0) {
                val h = ray(p.x, p.y, dx / d, dy / d, max(0.0, d - pl.rad))
                beamed = h == null
            }
            if (!beamed && d < 100.0) {
                val m = alive(pl, "mine").firstOrNull()
                if (m != null) {
                    val o = mw(pl, m)
                    val mx = o.x - p.x
                    val my = o.y - p.y
                    val md = hy(mx, my)
                    if (md > 1.0) {
                        val h = ray(p.x, p.y, mx / md, my / md, max(0.0, md - 2.0))
                        beamed = h == null
                        if (beamed) {
                            px = o.x
                            py = o.y
                        }
                    }
                }
            }
        }
        if (!beamed) {
            collideCircle(
                x = p.x, y = p.y, vx = p.vx, vy = p.vy, rad = p.r,
                update = { nx, ny, nvx, nvy, _ ->
                    p.x = nx; p.y = ny; p.vx = nvx; p.vy = nvy
                }
            )
        }
        val gc = groundEntity(p.x, p.y, p.vx, p.vy, p.r, dt, 0.2, 4.0) { _, _, nx, ny, nvx, nvy, _ ->
            p.x = nx; p.y = ny; p.vx = nvx; p.vy = nvy
        }
        if (gc != null) p.va *= 0.9

        val h = p.home
        if (h != null) {
            val dx = p.x - h.x
            val dy = p.y - h.y
            if (h.dead || hy(dx, dy) > h.rad + 6.0) {
                p.home = null
            } else {
                val c = cos(-h.a)
                val sn = sin(-h.a)
                p.hx = dx * c - dy * sn
                p.hy = dx * sn + dy * c
            }
        }
        if (pl != null && !pl.dead && (pl.dock == null || pl.dock?.ty == "anchor") && ev == null && def != null) {
            val d = hy(p.x - pl.x, p.y - pl.y)
            val tr = if (def.story) 100.0 else 24.0
            if (d < tr && cmass(pl) < pl.cargoMax) {
                val pd = hy(px - p.x, py - p.y)
                val k = (140.0 * dt) / max(6.0, pd)
                p.vx += (px - p.x) * k
                p.vy += (py - p.y) * k
                if (px != pl.x) {
                    val vx = p.vx - pl.vx
                    val vy = p.vy - pl.vy
                    val pdSafe = if (pd == 0.0) 1.0 else pd
                    val ux = (px - p.x) / pdSafe
                    val uy = (py - p.y) / pdSafe
                    val vs = vx * -uy + vy * ux
                    p.vx -= -uy * vs
                    p.vy -= ux * vs
                }
                if (d < 7.0) {
                    val t = cadd(pl, p.type, p.qty)
                    if (t > 0) {
                        p.qty -= t
                        if (p.qty <= 0) p.dead = true
                        AU.blip()
                        Hooks.toast("SCOOPED $t ${def.n}", null)
                    }
                }
            }
        }
    }
    W.picks.removeAll { it.dead }
    if (W.picks.size > 700) {
        W.picks.subList(0, W.picks.size - 700).clear()
    }
}

fun stepDeb(dt: Double) {
    val W = GS.W ?: return
    val rf = ::rand
    for (d in W.deb) {
        d.life -= dt
        gacc(d.x, d.y, d.vx, d.vy, GA)
        d.vx += GA.x * dt * 0.8
        d.vy += GA.y * dt * 0.8
        d.x += d.vx * dt
        d.y += d.vy * dt
        d.a += d.va * dt
        val gc = groundEntity(d.x, d.y, d.vx, d.vy, 1.5, dt, 0.3, 3.0) { _, _, nx, ny, nvx, nvy, _ ->
            d.x = nx; d.y = ny; d.vx = nvx; d.vy = nvy
        }
        if (gc != null) d.va *= 0.8
        if (d.hot > 0.0) {
            d.hot -= dt
            if (rf() < 0.12) smoke(d.x, d.y, d.vx * 0.3, d.vy * 0.3, 1.6, 1.1)
        }
    }
    W.deb.removeAll { it.life <= 0.0 }
    if (W.deb.size > 260) {
        W.deb.subList(0, W.deb.size - 260).clear()
    }
}
