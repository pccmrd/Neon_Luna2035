package com.example.keres9.sim

import com.example.keres9.core.AutopOut
import com.example.keres9.core.Body
import com.example.keres9.core.C
import com.example.keres9.core.Ship
import com.example.keres9.core.TAU
import com.example.keres9.core.Vec2
import com.example.keres9.core.ad
import com.example.keres9.core.cl
import com.example.keres9.core.gacc
import com.example.keres9.core.hy
import com.example.keres9.world.ray
import com.example.keres9.world.segBlock
import com.example.keres9.world.surfR
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val GA = Vec2()
private val STANDOFF_ANGLES = doubleArrayOf(0.0, 0.15, -0.15, 0.3, -0.3, 0.5, -0.5, 0.8, -0.8)

data class SteerTelemetry(
    val d: Double,
    val mode: String,
    val rv: Double,
    val err: Double,
    val wp: Boolean = false
)

data class SteerOpts(
    val vmax: Double = 70.0,
    val stop: Double = 0.0,
    val face: Double? = null,
    val va: Double = 0.0,
    val rcsOnly: Boolean = false,
    val hold: Boolean = false,
    val up: Boolean = false,
    val through: Boolean = false,
    val kv: Double = 1.1,
    val tvx: Double = 0.0,
    val tvy: Double = 0.0,
    val out: AutopOut = AutopOut()
)

fun engOK(s: Ship): Boolean = alive(s, "eng").isNotEmpty() && s.fuel > 0.0

fun steer(s: Ship, tx: Double, ty: Double, o: SteerOpts = SteerOpts()): SteerTelemetry {
    val out = o.out
    val dx = tx - s.x
    val dy = ty - s.y
    val d = hy(dx, dy).let { if (it < 0.001) 0.001 else it }
    val eng = engOK(s) && !o.rcsOnly
    val aMax = if (eng) s.thrust else s.rcsA
    gacc(s.x, s.y, s.vx, s.vy, GA)
    val gm = hy(GA.x, GA.y)
    val budget = max(0.4, aMax - gm)
    val vCor = (0.5 * aMax) / (2.0 * C.OM)
    val vmax = min(o.vmax, vCor)
    val rvx = s.vx - o.tvx
    val rvy = s.vy - o.tvy
    val closing = (rvx * dx + rvy * dy) / d
    val dEff = max(0.0, d - o.stop - max(0.0, closing) * 1.2)
    val vs = if (o.through) {
        min(vmax, max(8.0, sqrt(2.0 * budget * 0.55 * (d + 150.0))))
    } else {
        min(vmax, min(sqrt(2.0 * budget * 0.55 * dEff), max(0.0, d - o.stop) * 0.9))
    }
    val vdx = o.tvx + (dx / d) * vs
    val vdy = o.tvy + (dy / d) * vs
    val ax = (vdx - s.vx) * o.kv - GA.x
    val ay = (vdy - s.vy) * o.kv - GA.y
    val am = hy(ax, ay)
    out.thr = 0.0
    out.rcx = 0.0
    out.rcy = 0.0
    var face = o.face
    val mode: String
    if (eng && (am > s.rcsA * 0.7 || o.up)) {
        val want = atan2(ay, ax)
        val err = abs(ad(want, s.a))
        face = want
        if (err < 0.35 && s.thrust > 0.0) out.thr = cl((am / s.thrust) * cos(err), 0.0, 1.0)
        mode = if (err < 0.35) "BURN" else "TURN"
        val rx = ax - cos(s.a) * out.thr * s.thrust
        val ry = ay - sin(s.a) * out.thr * s.thrust
        val rm = hy(rx, ry)
        if (rm > 0.05 && s.rcsA > 0.0) {
            val k = min(1.0, rm / s.rcsA)
            out.rcx = (rx / rm) * k
            out.rcy = (ry / rm) * k
        }
    } else {
        if (am > 0.03) {
            val k = min(1.0, am / max(0.1, s.rcsA))
            out.rcx = (ax / am) * k
            out.rcy = (ay / am) * k
        }
        mode = if (am > 0.5) "RCS" else "HOLD"
        if (face == null && d > 30.0 && !o.hold) face = atan2(dy, dx)
    }
    out.tqi = if (face != null) {
        cl(ad(face, s.a) * 2.6 - (s.va - o.va) * 1.9, -1.0, 1.0)
    } else {
        cl(-s.va * 1.8, -1.0, 1.0)
    }
    return SteerTelemetry(
        d = d,
        mode = mode,
        rv = hy(rvx, rvy),
        err = if (face != null) abs(ad(face, s.a)) else 0.0
    )
}

fun applyOut(s: Ship, out: AutopOut) {
    s.thr = out.thr
    s.rcx = out.rcx
    s.rcy = out.rcy
    s.tqi = out.tqi
}

/** one-hop detour around whatever blocks the straight corridor (and never through the Moon) */
fun detour(x0: Double, y0: Double, x1: Double, y1: Double, w: Double = 14.0, skip: Body? = null): Vec2? {
    val mx = (x0 + x1) / 2.0
    val my = (y0 + y1) / 2.0
    val mr = hy(mx, my)
    val mth = atan2(my, mx)
    if (mr < surfR(mth) + 160.0 && hy(x1 - x0, y1 - y0) > 400.0) {
        val rr = max(surfR(mth) + 400.0, min(hy(x0, y0), hy(x1, y1)))
        return Vec2(cos(mth) * rr, sin(mth) * rr)
    }
    val blk = segBlock(x0, y0, x1, y1, w, skip) ?: return null
    val R = blk.rad + 50.0 + w
    var best: Vec2? = null
    var bc = 1e9
    for (k in 0 until 20) {
        val a = (k / 20.0) * TAU
        val cx = blk.x + cos(a) * R
        val cy = blk.y + sin(a) * R
        if (hy(cx, cy) < surfR(atan2(cy, cx)) + 60.0) continue
        if (hy(cx - x0, cy - y0) < 30.0) continue
        if (segBlock(x0, y0, cx, cy, w, skip) != null) continue
        val cost = hy(cx - x0, cy - y0) + hy(x1 - cx, y1 - cy) +
            (if (segBlock(cx, cy, x1, y1, w, skip) != null) 400.0 else 0.0)
        if (cost < bc) {
            bc = cost
            best = Vec2(cx, cy)
        }
    }
    if (best == null) {
        val a = atan2(y0 - blk.y, x0 - blk.x)
        best = Vec2(blk.x + cos(a) * (R + 40.0), blk.y + sin(a) * (R + 40.0))
    }
    return best
}

/** stand-off point outside a body, on the side facing (fx, fy) */
fun standoff(b: Body, fx: Double, fy: Double, extra: Double = 20.0): Vec2 {
    val a0 = atan2(fy - b.y, fx - b.x)
    val R = b.rad + extra
    for (da in STANDOFF_ANGLES) {
        val ux = cos(a0 + da)
        val uy = sin(a0 + da)
        val h = ray(b.x + ux * (b.rad + 4.0), b.y + uy * (b.rad + 4.0), -ux, -uy, b.rad + 4.0)
        if (h != null && h.b === b && h.d < b.rad * 0.85) {
            val d = hy(h.x - b.x, h.y - b.y) + extra
            return Vec2(b.x + ux * d, b.y + uy * d)
        }
    }
    return Vec2(b.x + cos(a0) * R, b.y + sin(a0) * R)
}
