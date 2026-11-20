package com.example.keres9.world

import com.example.keres9.core.C
import com.example.keres9.core.SeededRng
import com.example.keres9.core.TAU
import com.example.keres9.core.Vec2
import com.example.keres9.core.cl
import com.example.keres9.core.ri
import com.example.keres9.core.rr
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sin

const val MOON_N = 9600
const val MOON_STEP = TAU / MOON_N
const val LUNAR_DAY = 1200.0

data class FlatZone(val th: Double, val half: Double, val lvl: Double = 0.0)
data class CraterDef(val th: Double, val w: Double, val d: Double)
data class BoulderDef(val th: Double, val s: Double, val sh: Int)

data class GroundContact(val im: Double, val nx: Double, val ny: Double)

object MOON {
    val S = FloatArray(MOON_N)
    var flat: List<FlatZone> = emptyList()
    val craters = mutableListOf<CraterDef>()
    val boulders = mutableListOf<BoulderDef>()
    var seed: Int = 0
    const val sun0: Double = -PI / 2.0 + 1.0
}

private fun wrapI(i: Int): Int = ((i % MOON_N) + MOON_N) % MOON_N
private fun thI(th: Double): Double {
    var t = th % TAU
    if (t < 0.0) t += TAU
    return t / MOON_STEP
}

/** surface radius at longitude th (linear interpolation of the table) */
fun surfR(th: Double): Double {
    val f = thI(th)
    val i = floor(f).toInt()
    val k = f - i
    return MOON.S[wrapI(i)] * (1.0 - k) + MOON.S[wrapI(i + 1)] * k
}

/** outward surface normal at longitude th */
fun surfN(th: Double): Vec2 {
    val d = 0.0012
    val r0 = surfR(th - d)
    val r1 = surfR(th + d)
    val r = (r0 + r1) / 2.0
    val dr = (r1 - r0) / (2.0 * d * r)
    val ux = cos(th)
    val uy = sin(th)
    val tx = -uy
    val ty = ux
    var nx = ux - tx * dr
    var ny = uy - ty * dr
    val m = hypot(nx, ny).let { if (it == 0.0) 1.0 else it }
    nx /= m
    ny /= m
    return Vec2(nx, ny)
}

/** altitude of (x,y) above the local regolith */
fun altAt(x: Double, y: Double): Double = hypot(x, y) - surfR(atan2(y, x))

/** build the heightfield for a seed; `flat` zones are levelled pads for the settlements */
fun genMoon(seed: Int, flat: List<FlatZone>) {
    val r = SeededRng(seed xor 0x51ee)
    val rf: () -> Double = { r.next() }
    MOON.seed = seed
    MOON.flat = flat
    MOON.craters.clear()
    MOON.boulders.clear()
    val p = doubleArrayOf(rf() * TAU, rf() * TAU, rf() * TAU, rf() * TAU)
    for (k in 0 until 150) {
        val w = rr(rf, 14.0, if (k < 12) 260.0 else 120.0)
        MOON.craters.add(CraterDef(rf() * TAU, w / C.RM, w * rr(rf, 0.12, 0.24)))
    }
    val S = MOON.S
    for (i in 0 until MOON_N) {
        val th = i * MOON_STEP
        var h = 16.0 * sin(3.0 * th + p[0]) +
            10.0 * sin(7.0 * th + p[1]) +
            5.0 * sin(19.0 * th + p[2]) +
            2.4 * sin(61.0 * th + p[3]) +
            1.1 * sin(173.0 * th)
        for (c in MOON.craters) {
            var x = th - c.th
            x -= round(x / TAU) * TAU
            val q = abs(x) / c.w
            if (q > 2.2) continue
            if (q < 1.0) h -= c.d * (1.0 - q * q)
            h += c.d * 0.24 * exp(-((q - 1.0) / 0.32) * ((q - 1.0) / 0.32))
        }
        S[i] = (C.RM + h).toFloat()
    }
    for (z in flat) {
        val half = z.half / C.RM
        val sh = 70.0 / C.RM
        val lvl = C.RM + z.lvl
        for (i in 0 until MOON_N) {
            var x = i * MOON_STEP - z.th
            x -= round(x / TAU) * TAU
            val a = abs(x)
            if (a > half + sh) continue
            val k = if (a <= half) 1.0 else 1.0 - (a - half) / sh
            val s = k * k * (3.0 - 2.0 * k)
            S[i] = (S[i] * (1.0 - s) + lvl * s).toFloat()
        }
    }
    for (k in 0 until 900) {
        val th = rf() * TAU
        val skip = flat.any { z ->
            var x = th - z.th
            x -= round(x / TAU) * TAU
            abs(x) < (z.half + 20.0) / C.RM
        }
        if (skip) continue
        MOON.boulders.add(BoulderDef(th, rr(rf, 1.2, if (k < 120) 7.0 else 3.5), ri(rf, 0, 3)))
    }
    MOON.boulders.sortBy { it.th }
}

/**
 * circle-vs-regolith contact. Pushes the entity out, reflects the inward
 * velocity (restitution `e`), applies sliding friction when grounded.
 */
inline fun groundEntity(
    x: Double,
    y: Double,
    vx: Double,
    vy: Double,
    rad: Double,
    dt: Double,
    e: Double = 0.25,
    fr: Double = 3.5,
    apply: (nx: Double, ny: Double, newX: Double, newY: Double, newVx: Double, newVy: Double, im: Double) -> Unit
): GroundContact? {
    val r = hypot(x, y).let { if (it == 0.0) 1.0 else it }
    val th = atan2(y, x)
    val S = surfR(th)
    if (r > S + rad + 0.01) return null
    val n = surfN(th)
    val pen = S + rad - r
    val outX = x + (x / r) * pen
    val outY = y + (y / r) * pen
    var outVx = vx
    var outVy = vy
    val vn = outVx * n.x + outVy * n.y
    var im = 0.0
    if (vn < 0.0) {
        im = -vn
        outVx -= n.x * vn * (1.0 + e)
        outVy -= n.y * vn * (1.0 + e)
    }
    val tx = -n.y
    val ty = n.x
    val vt = outVx * tx + outVy * ty
    val k = exp(-fr * dt)
    outVx -= tx * vt * (1.0 - k)
    outVy -= ty * vt * (1.0 - k)
    apply(n.x, n.y, outX, outY, outVx, outVy, im)
    return GroundContact(im, n.x, n.y)
}

fun sunAng(t: Double): Double = MOON.sun0 - (TAU / LUNAR_DAY) * t
fun daylight(th: Double, t: Double): Double = cl(cos(th - sunAng(t)) * 1.4 + 0.1, 0.0, 1.0)
