package com.example.keres9.core

import android.graphics.Color
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.math.sin

const val TAU: Double = PI * 2.0

data class Vec2(var x: Double = 0.0, var y: Double = 0.0)

fun cl(v: Double, a: Double, b: Double): Double = if (v < a) a else if (v > b) b else v
fun cl(v: Float, a: Float, b: Float): Float = if (v < a) a else if (v > b) b else v
fun cl(v: Int, a: Int, b: Int): Int = if (v < a) a else if (v > b) b else v

fun lp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
fun hy(x: Double, y: Double): Double = hypot(x, y)

/** wrap angle to (-PI, PI] */
fun an(angle: Double): Double {
    var a = angle % TAU
    if (a > PI) a -= TAU
    if (a < -PI) a += TAU
    return a
}

/** signed shortest angular difference a - b */
fun ad(a: Double, b: Double): Double = an(a - b)

private val numFmt = NumberFormat.getNumberInstance(Locale.US)
fun fm(n: Double): String = numFmt.format(n.roundToLong())
fun fm(n: Int): String = numFmt.format(n)
fun fm(n: Long): String = numFmt.format(n)

fun ds(x0: Double, y0: Double, x1: Double, y1: Double): Double = hypot(x0 - x1, y0 - y1)
fun ds(a: Targetable, b: Targetable): Double = hypot(a.x - b.x, a.y - b.y)

/** Mulberry32 seeded PRNG matching JS rng(s) bit-for-bit */
class SeededRng(seed: Int) {
    private var s: Int = seed

    fun next(): Double {
        s = s + 0x6d2b79f5
        var t = (s xor (s ushr 15)) * (1 or s)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        val u = (t xor (t ushr 14)).toLong() and 0xFFFFFFFFL
        return u.toDouble() / 4294967296.0
    }

    operator fun invoke(): Double = next()
}

fun <T> pk(r: () -> Double, list: List<T>): T = list[(r() * list.size).toInt().coerceIn(0, list.lastIndex)]
fun rr(r: () -> Double, a: Double, b: Double): Double = a + r() * (b - a)
fun ri(r: () -> Double, a: Int, b: Int): Int = (a + r() * (b - a + 1)).toInt().coerceIn(a, b)

fun rand(): Double = Math.random()

private val shadeCache = ConcurrentHashMap<Long, Int>()

fun parseHexColor(hex: String): Int {
    val clean = if (hex.startsWith("#")) hex.substring(1) else hex
    return (0xFF000000L or clean.toLong(16)).toInt()
}

/** shade a #rrggbb color by factor f (cached ARGB Int) */
fun sh(argb: Int, f: Double): Int {
    val q = (f * 100.0).toInt().coerceIn(0, 400)
    val key = (argb.toLong() shl 16) or (q.toLong() and 0xFFFFL)
    shadeCache[key]?.let { return it }
    val r = cl(((argb ushr 16) and 0xFF) * f, 0.0, 255.0).toInt()
    val g = cl(((argb ushr 8) and 0xFF) * f, 0.0, 255.0).toInt()
    val b = cl((argb and 0xFF) * f, 0.0, 255.0).toInt()
    val out = Color.argb(255, r, g, b)
    if (shadeCache.size < 4096) shadeCache[key] = out
    return out
}

fun withAlpha(argb: Int, alpha: Double): Int {
    val a = cl(alpha * 255.0, 0.0, 255.0).toInt()
    return (argb and 0x00FFFFFF) or (a shl 24)
}

/** distance from point to segment, returns Pair(d, t) */
fun segDist(px: Double, py: Double, x0: Double, y0: Double, x1: Double, y1: Double): Pair<Double, Double> {
    val dx = x1 - x0
    val dy = y1 - y0
    val l2 = dx * dx + dy * dy
    val len = if (l2 == 0.0) 1.0 else l2
    val t = cl(((px - x0) * dx + (py - y0) * dy) / len, 0.0, 1.0)
    return hy(px - (x0 + dx * t), py - (y0 + dy * t)) to t
}

fun bearing(fx: Double, fy: Double, tx: Double, ty: Double): Int =
    (((atan2(ty - fy, tx - fx) * 180.0) / PI + 450.0) % 360.0).toInt()
