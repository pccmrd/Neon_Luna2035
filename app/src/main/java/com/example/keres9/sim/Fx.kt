package com.example.keres9.sim

import com.example.keres9.core.Bit
import com.example.keres9.core.Flash
import com.example.keres9.core.GS
import com.example.keres9.core.TAU
import com.example.keres9.core.Vec2
import com.example.keres9.core.gacc
import com.example.keres9.core.hy
import com.example.keres9.core.parseHexColor
import com.example.keres9.core.rand
import com.example.keres9.core.rr
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

private val GA = Vec2()
private val COL_ICE = parseHexColor("#a8ecff")
private val COL_SMOKE = parseHexColor("#3a3530")
private val COL_DUST = parseHexColor("#8f877c")
private val COL_JET = parseHexColor("#d8f4ff")
private val COL_FLASH = parseHexColor("#ffd9a0")
private val COL_FIRE = parseHexColor("#ff8c1a")
private val COL_AMBER = parseHexColor("#ffb347")

fun bit(p: Bit) {
    val W = GS.W ?: return
    if (W.bits.size > 900) W.bits.removeAt(0)
    W.bits.add(p)
}

fun sparks(x: Double, y: Double, n: Int, c: Int, sp: Double = 60.0) {
    val rf = ::rand
    for (i in 0 until n) {
        val a = rf() * TAU
        val v = rr(rf, sp * 0.3, sp)
        bit(Bit(x, y, cos(a) * v, sin(a) * v, rr(rf, 0.2, 0.6), 0.6, rr(rf, 1.0, 2.4), c, "spark"))
    }
}

fun gas(x: Double, y: Double, vx: Double, vy: Double, c: Int = COL_ICE) {
    bit(Bit(x, y, vx, vy, 0.9, 0.9, rr(::rand, 2.0, 5.0), c, "gas"))
}

fun smoke(x: Double, y: Double, vx: Double, vy: Double, s: Double = 4.0, life: Double = 2.2) {
    bit(Bit(x, y, vx, vy, life, life, s, COL_SMOKE, "smoke"))
}

fun dust(x: Double, y: Double, nx: Double, ny: Double, n: Int = 4) {
    val rf = ::rand
    val tx = -ny
    val ty = nx
    for (i in 0 until n) {
        val sp = rr(rf, -1.0, 1.0)
        val v = rr(rf, 6.0, 26.0)
        bit(
            Bit(
                x, y,
                (tx * sp * 1.6 + nx * rr(rf, 0.2, 0.9)) * v,
                (ty * sp * 1.6 + ny * rr(rf, 0.2, 0.9)) * v,
                rr(rf, 0.8, 1.8), 1.8, rr(rf, 0.8, 1.8), COL_DUST, "dust"
            )
        )
    }
}

fun jet(x: Double, y: Double, vx: Double, vy: Double) {
    bit(Bit(x, y, vx, vy, 0.28, 0.28, rr(::rand, 1.2, 2.4), COL_JET, "jet"))
}

fun boomFx(x: Double, y: Double, z: Double = 1.0) {
    val W = GS.W ?: return
    val rf = ::rand
    bit(Bit(x, y, 0.0, 0.0, 0.35, 0.35, 26.0 * z, COL_FLASH, "flash"))
    bit(Bit(x, y, 0.0, 0.0, 0.6, 0.6, 8.0 * z, COL_FIRE, "ring"))
    if (z >= 1.0) bit(Bit(x, y, 0.0, 0.0, 1.1, 1.1, 15.0 * z, COL_FLASH, "shock"))
    sparks(x, y, (26.0 * z).toInt(), COL_AMBER, 120.0 * z)
    val fireCount = (5.0 * z).toInt()
    for (i in 0 until fireCount) {
        val a = rf() * TAU
        val v = rr(rf, 6.0, 30.0) * z
        bit(Bit(x + rr(rf, -4.0, 4.0), y + rr(rf, -4.0, 4.0), cos(a) * v, sin(a) * v, rr(rf, 0.4, 0.9), 0.9, rr(rf, 4.0, 9.0) * z, COL_FIRE, "fire"))
    }
    val smokeCount = (8.0 * z).toInt()
    for (i in 0 until smokeCount) {
        smoke(x + rr(rf, -8.0, 8.0), y + rr(rf, -8.0, 8.0), rr(rf, -22.0, 22.0), rr(rf, -22.0, 22.0), rr(rf, 3.0, 7.0) * z, rr(rf, 1.6, 3.2))
    }
    W.flashes.add(Flash(x, y, 90.0 * z, 0.5 * min(2.0, z), 0.5 * min(2.0, z)))
    if (hy(x - GS.cam.x, y - GS.cam.y) < 500.0 && z >= 1.2) {
        GS.cam.flash = min(0.5, GS.cam.flash + 0.18 * z)
    }
}

fun stepBits(dt: Double) {
    val W = GS.W ?: return
    val it = W.bits.iterator()
    while (it.hasNext()) {
        val p = it.next()
        p.life -= dt
        if (p.life <= 0.0) {
            it.remove()
            continue
        }
        p.x += p.vx * dt
        p.y += p.vy * dt
        when (p.t) {
            "gas" -> p.s += dt * 6.0
            "dust" -> {
                gacc(p.x, p.y, p.vx, p.vy, GA)
                p.vx += GA.x * dt
                p.vy += GA.y * dt
            }
            "smoke" -> {
                p.s += dt * 4.0
                p.vx *= 1.0 - dt * 0.4
                p.vy *= 1.0 - dt * 0.4
            }
            "fire" -> {
                p.s *= 1.0 + dt * 0.8
                p.vx *= 1.0 - dt
                p.vy *= 1.0 - dt
            }
        }
    }
    val fit = W.flashes.iterator()
    while (fit.hasNext()) {
        val f = fit.next()
        f.t -= dt
        if (f.t <= 0.0) fit.remove()
    }
    GS.cam.flash *= exp(-5.0 * dt)
}
