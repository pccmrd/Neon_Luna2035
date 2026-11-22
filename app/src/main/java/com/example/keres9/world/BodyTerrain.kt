package com.example.keres9.world

import android.graphics.Bitmap
import com.example.keres9.core.Body
import com.example.keres9.core.FLR
import com.example.keres9.core.GS
import com.example.keres9.core.HeatSpot
import com.example.keres9.core.MAT
import com.example.keres9.core.Pad
import com.example.keres9.core.Port
import com.example.keres9.core.Vec2
import com.example.keres9.core.WorldPortPose
import com.example.keres9.core.cl
import com.example.keres9.core.hy
import com.example.keres9.core.sh
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class CellHit(val cx: Int, val cy: Int, val i: Int)
data class RayHit(val x: Double, val y: Double, val b: Body, val cx: Int, val cy: Int, val d: Double)

fun ensureBodyBitmaps(b: Body) {
    if (b.roofBmp == null) {
        b.roofBmp = Bitmap.createBitmap(b.gw, b.gh, Bitmap.Config.ARGB_8888)
        b.roofDirty = true
    }
    if (b.floorBmp == null) {
        b.floorBmp = Bitmap.createBitmap(b.gw, b.gh, Bitmap.Config.ARGB_8888)
        b.floorDirty = true
    }
    if (b.roofDirty) {
        b.roofBmp?.setPixels(b.roofPixels, 0, b.gw, 0, 0, b.gw, b.gh)
        b.roofDirty = false
    }
    if (b.floorDirty) {
        b.floorBmp?.setPixels(b.floorPixels, 0, b.gw, 0, 0, b.gw, b.gh)
        b.floorDirty = false
    }
}

/** cell -> world (cell centre) */
fun cw(b: Body, cx: Double, cy: Double): Vec2 {
    val lx = (cx + 0.5) * b.cs - (b.gw * b.cs) / 2.0
    val ly = (cy + 0.5) * b.cs - (b.gh * b.cs) / 2.0
    val c = cos(b.a)
    val s = sin(b.a)
    return Vec2(b.x + lx * c - ly * s, b.y + lx * s + ly * c)
}

fun cw(b: Body, cx: Int, cy: Int): Vec2 = cw(b, cx.toDouble(), cy.toDouble())

/** world -> cell (null when outside the grid) */
fun ca(b: Body, wx: Double, wy: Double): CellHit? {
    val c = cos(-b.a)
    val s = sin(-b.a)
    val dx = wx - b.x
    val dy = wy - b.y
    val lx = dx * c - dy * s + (b.gw * b.cs) / 2.0
    val ly = dx * s + dy * c + (b.gh * b.cs) / 2.0
    val cx = floor(lx / b.cs).toInt()
    val cy = floor(ly / b.cs).toInt()
    if (cx < 0 || cy < 0 || cx >= b.gw || cy >= b.gh) return null
    return CellHit(cx, cy, cy * b.gw + cx)
}

/** body-local -> world */
fun lw(b: Body, lx: Double, ly: Double): Vec2 {
    val c = cos(b.a)
    val s = sin(b.a)
    return Vec2(b.x + lx * c - ly * s, b.y + lx * s + ly * c)
}

/** paint one roof (solid) pixel with bevel lighting */
fun pR(b: Body, cx: Int, cy: Int) {
    if (cx < 0 || cy < 0 || cx >= b.gw || cy >= b.gh) return
    val i = cy * b.gw + cx
    val m = b.sol[i]
    if (m == 0) {
        val cap = b.roofMask[i].toInt()
        if (cap != 0) {
            val hash = ((cx * 73856093) xor (cy * 19349663) xor b.seed).toLong() and 0xFFFFFFFFL
            var f = 0.80 + (hash % 100L) / 360.0
            if (cy > 0 && b.sol[i - b.gw] == 0 && b.roofMask[i - b.gw].toInt() == 0) f += 0.14
            if (cy < b.gh - 1 && b.sol[i + b.gw] == 0 && b.roofMask[i + b.gw].toInt() == 0) f -= 0.16
            val mat = MAT.getOrNull(cap) ?: MAT[1]!!
            b.roofPixels[i] = sh(mat.color, f)
        } else {
            b.roofPixels[i] = 0
        }
        b.roofDirty = true
        return
    }
    val hash = ((cx * 73856093) xor (cy * 19349663) xor b.seed).toLong() and 0xFFFFFFFFL
    var f = 0.84 + (hash % 100L) / 333.0
    if (cy > 0 && b.sol[i - b.gw] == 0) f += 0.24
    if (cx < b.gw - 1 && b.sol[i + 1] == 0) f += 0.10
    if (cy < b.gh - 1 && b.sol[i + b.gw] == 0) f -= 0.24
    if (cx > 0 && b.sol[i - 1] == 0) f -= 0.10
    val mat = MAT.getOrNull(m) ?: return
    b.roofPixels[i] = sh(mat.color, f)
    b.roofDirty = true
}

fun pF(b: Body, cx: Int, cy: Int) {
    if (cx < 0 || cy < 0 || cx >= b.gw || cy >= b.gh) return
    val i = cy * b.gw + cx
    val f = b.flr[i]
    if (f == 0 || f >= FLR.size) {
        b.floorPixels[i] = 0
    } else {
        val hash = ((cx * 297121507) xor (cy * 486187739) xor b.seed).toLong() and 0xFFFFFFFFL
        var shade = 0.90 + (hash % 100L) / 480.0
        if (f == 1 || f == 2) {
            if ((cx % 3 == 0) || (cy % 3 == 0)) shade *= 0.91
        } else if (f == 4 || f == 5 || f == 7) {
            if ((hash % 11L) == 0L) shade *= 1.15
        }
        b.floorPixels[i] = sh(FLR[f], shade)
    }
    b.floorDirty = true
}

/** damage a cell; returns the material index when it breaks, else 0 */
fun cut(b: Body, cx: Int, cy: Int, d: Double, quiet: Boolean = false): Int {
    if (cx < 0 || cy < 0 || cx >= b.gw || cy >= b.gh) return 0
    val i = cy * b.gw + cx
    val m = b.sol[i]
    if (m == 0) return 0
    val mat = MAT.getOrNull(m) ?: return 0
    val hp = (b.dmg[i] ?: mat.hp) - d
    if (hp > 0.0) {
        b.dmg[i] = hp
        return 0
    }
    b.dmg.remove(i)
    b.sol[i] = 0
    b.roofMask[i] = 0
    if (b.flr[i] == 0) b.flr[i] = 4
    b.cut.add(i)
    pR(b, cx, cy)
    pF(b, cx, cy)
    if (cx > 0) pR(b, cx - 1, cy)
    if (cx < b.gw - 1) pR(b, cx + 1, cy)
    if (cy > 0) pR(b, cx, cy - 1)
    if (cy < b.gh - 1) pR(b, cx, cy + 1)
    val W = GS.W
    if (!quiet && W != null) {
        val w = cw(b, cx, cy)
        W.heat.add(HeatSpot(w.x, w.y, b, cx, cy, W.time, mat.color))
        if (W.heat.size > 260) W.heat.removeAt(0)
    }
    return m
}

/** bodies whose bounding radius is within r of (x,y) */
fun near(x: Double, y: Double, r: Double): List<Body> {
    val W = GS.W ?: return emptyList()
    val out = ArrayList<Body>(8)
    for (b in W.bodies) {
        val q = b.rad + r
        if (abs(b.x - x) < q && abs(b.y - y) < q) out.add(b)
    }
    return out
}

/** velocity of a body's material point at (x,y) */
fun bvel(b: Body, x: Double, y: Double): Vec2 = Vec2(b.vx - (y - b.y) * b.va, b.vy + (x - b.x) * b.va)

/** world pose & velocity of a docking port */
fun portW(st: Body, p: Port): WorldPortPose {
    val w = cw(st, p.cx, p.cy)
    return WorldPortPose(w.x, w.y, p.dir + st.a, st.vx - (w.y - st.y) * st.va, st.vy + (w.x - st.x) * st.va, st.va)
}

/** world pose & velocity of a pad */
fun portW(st: Body, p: Pad): WorldPortPose {
    val w = cw(st, p.cx, p.cy)
    return WorldPortPose(w.x, w.y, p.dir + st.a, st.vx - (w.y - st.y) * st.va, st.vy + (w.x - st.x) * st.va, st.va)
}

/** circle-vs-terrain collision helper */
inline fun collideCircle(
    x: Double,
    y: Double,
    vx: Double,
    vy: Double,
    rad: Double,
    hasVa: Boolean = false,
    va: Double = 0.0,
    crossinline onHit: (im: Double, wx: Double, wy: Double, b: Body, cx: Int, cy: Int) -> Unit = { _, _, _, _, _, _ -> },
    update: (newX: Double, newY: Double, newVx: Double, newVy: Double, newVa: Double) -> Unit
) {
    var ex = x
    var ey = y
    var evx = vx
    var evy = vy
    var eva = va
    for (b in near(ex, ey, rad + 8.0)) {
        val c = cos(-b.a)
        val s = sin(-b.a)
        val dx = ex - b.x
        val dy = ey - b.y
        val lx = dx * c - dy * s + (b.gw * b.cs) / 2.0
        val ly = dx * s + dy * c + (b.gh * b.cs) / 2.0
        val R = rad + b.cs
        val x0 = max(0, ((lx - R) / b.cs).toInt())
        val x1 = min(b.gw - 1, ((lx + R) / b.cs).toInt())
        val y0 = max(0, ((ly - R) / b.cs).toInt())
        val y1 = min(b.gh - 1, ((ly + R) / b.cs).toInt())
        val cb = cos(b.a)
        val sb = sin(b.a)
        for (cy in y0..y1) {
            for (cx in x0..x1) {
                if (b.sol[cy * b.gw + cx] == 0) continue
                val rx = cx * b.cs
                val ry = cy * b.cs
                val px = cl(lx, rx, rx + b.cs)
                val py = cl(ly, ry, ry + b.cs)
                var ddx = lx - px
                var ddy = ly - py
                var d = hy(ddx, ddy)
                if (d >= rad) continue
                if (d < 0.001) {
                    ddx = lx - rx - b.cs / 2.0
                    ddy = ly - ry - b.cs / 2.0
                    d = hy(ddx, ddy).let { if (it == 0.0) 1.0 else it }
                }
                val nlx = ddx / d
                val nly = ddy / d
                val nx = nlx * cb - nly * sb
                val ny = nlx * sb + nly * cb
                ex += nx * (rad - d)
                ey += ny * (rad - d)
                val w = cw(b, cx, cy)
                val v = bvel(b, w.x, w.y)
                val vn = (evx - v.x) * nx + (evy - v.y) * ny
                if (vn < 0.0) {
                    evx -= nx * vn * 1.28
                    evy -= ny * vn * 1.28
                    if (hasVa) eva += (nx * (evy - v.y) - ny * (evx - v.x)) * 0.004
                    onHit(-vn, w.x, w.y, b, cx, cy)
                }
            }
        }
    }
    update(ex, ey, evx, evy, eva)
}

/** march a ray through terrain; first solid hit or null */
fun ray(x0: Double, y0: Double, dx: Double, dy: Double, range: Double): RayHit? {
    var d = 0.0
    while (d < range) {
        val x = x0 + dx * d
        val y = y0 + dy * d
        for (b in near(x, y, 4.0)) {
            val c = ca(b, x, y)
            if (c != null && b.sol[c.i] != 0) {
                return RayHit(x, y, b, c.cx, c.cy, d)
            }
        }
        d += 1.6
    }
    return null
}

/** Is the corridor x0,y0 -> x1,y1 (half-width w) free of solid terrain? */
fun segBlock(x0: Double, y0: Double, x1: Double, y1: Double, w: Double = 0.0, skip: Body? = null): Body? {
    val W = GS.W ?: return null
    val L = hy(x1 - x0, y1 - y0).let { if (it == 0.0) 1.0 else it }
    val ux = (x1 - x0) / L
    val uy = (y1 - y0) / L
    var best: Body? = null
    var bs = 1e9
    for (b in W.bodies) {
        if (b === skip) continue
        val t = cl((b.x - x0) * ux + (b.y - y0) * uy, 0.0, L)
        val px = x0 + ux * t
        val py = y0 + uy * t
        if (hy(b.x - px, b.y - py) > b.rad + w) continue
        val s0 = max(0.0, t - b.rad - w)
        val s1 = min(L, min(t + b.rad + w, bs))
        val st = b.cs * 0.8
        var s = s0
        march@ while (s <= s1) {
            val qx = x0 + ux * s
            val qy = y0 + uy * s
            if (w <= 0.0) {
                val c = ca(b, qx, qy)
                if (c != null && b.sol[c.i] != 0) {
                    best = b
                    bs = s
                    break@march
                }
            } else {
                for (o in doubleArrayOf(-w, 0.0, w)) {
                    val c = ca(b, qx - uy * o, qy + ux * o)
                    if (c != null && b.sol[c.i] != 0) {
                        best = b
                        bs = s
                        break@march
                    }
                }
            }
            s += st
        }
    }
    return best
}

/** integrate free-moving bodies */
fun stepBodies(dt: Double) {
    val W = GS.W ?: return
    for (b in W.bodies) {
        b.mover?.invoke(b, dt)
        if (b.kin) {
            for (c in b.contacts) {
                val w = lw(b, c.lx, c.ly)
                c.x = w.x
                c.y = w.y
            }
            continue
        }
        if (b.vx == 0.0 && b.vy == 0.0 && b.va == 0.0) continue
        b.x += b.vx * dt
        b.y += b.vy * dt
        b.a += b.va * dt
        for (c in b.contacts) {
            val w = lw(b, c.lx, c.ly)
            c.x = w.x
            c.y = w.y
        }
    }
}

/** is there solid terrain (any body) at a world point? */
fun solidAt(x: Double, y: Double): Body? {
    for (b in near(x, y, 2.0)) {
        val c = ca(b, x, y)
        if (c != null && b.sol[c.i] != 0) return b
    }
    return null
}
