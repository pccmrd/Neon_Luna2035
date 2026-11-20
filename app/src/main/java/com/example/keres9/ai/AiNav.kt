package com.example.keres9.ai

import com.example.keres9.core.AutopOut
import com.example.keres9.core.Body
import com.example.keres9.core.C
import com.example.keres9.core.ControlFrame
import com.example.keres9.core.GS
import com.example.keres9.core.Pad
import com.example.keres9.core.Port
import com.example.keres9.core.Ship
import com.example.keres9.core.SuitState
import com.example.keres9.core.Targetable
import com.example.keres9.core.Vec2
import com.example.keres9.core.WorldPortPose
import com.example.keres9.core.ad
import com.example.keres9.core.cl
import com.example.keres9.core.gacc
import com.example.keres9.core.geff
import com.example.keres9.core.hy
import com.example.keres9.sim.IN
import com.example.keres9.sim.SteerOpts
import com.example.keres9.sim.SteerTelemetry
import com.example.keres9.sim.alive
import com.example.keres9.sim.detour
import com.example.keres9.sim.inBody
import com.example.keres9.sim.standoff
import com.example.keres9.sim.steer
import com.example.keres9.world.ca
import com.example.keres9.world.cw
import com.example.keres9.world.near
import com.example.keres9.world.portW
import com.example.keres9.world.ray
import com.example.keres9.world.segBlock
import com.example.keres9.world.surfR
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

val inp = ControlFrame().apply { ai = true }

data class FlightDiag(val mode: String, val d: Double, val rv: Double, val v: Double)

object NAV {
    var dt: Double = 1.0 / 60.0
    var t: Double = 0.0
    var stuck: Double = 0.0
    var flight: FlightDiag? = null
}

fun resetInp() {
    inp.lx = 0.0
    inp.ly = 0.0
    inp.rx = 0.0
    inp.ry = 0.0
    inp.thr = 0.0
    inp.mine = false
    inp.brake = false
    inp.ai = true
}

fun press(e: String) {
    IN.edge.add(e)
}

/** world vector -> screen-frame stick (the camera rotates with local up) */
fun stickW(wx: Double, wy: Double) {
    val c = cos(-GS.cam.rot)
    val s = sin(-GS.cam.rot)
    inp.lx = cl(wx * c - wy * s, -1.0, 1.0)
    inp.ly = cl(wx * s + wy * c, -1.0, 1.0)
}

private val OUT = AutopOut()
private val GA = Vec2()
private const val BAND_V = 24.0
private const val ARC_STEP = 0.11

fun fly(
    tx: Double,
    ty: Double,
    vmax: Double = 70.0,
    stop: Double = 0.0,
    face: Double? = null,
    rcsOnly: Boolean = false,
    hold: Boolean = false,
    up: Boolean = false,
    through: Boolean = false,
    tvx: Double = 0.0,
    tvy: Double = 0.0
): SteerTelemetry {
    val pl = GS.pl ?: return SteerTelemetry(0.0, "HOLD", 0.0, 0.0)
    val effVmax = if (hy(pl.x, pl.y) > C.RM + 700.0) min(vmax, BAND_V) else vmax
    val t = steer(
        pl, tx, ty,
        SteerOpts(
            vmax = effVmax,
            stop = stop,
            face = face,
            rcsOnly = rcsOnly,
            hold = hold,
            up = up,
            through = through,
            tvx = tvx,
            tvy = tvy,
            out = OUT
        )
    )
    inp.thr = OUT.thr
    stickW(OUT.rcx, OUT.rcy)
    inp.rx = OUT.tqi
    NAV.flight = FlightDiag(t.mode, t.d, t.rv, hy(pl.vx, pl.vy))
    return t
}

fun flyRoute(
    tx: Double,
    ty: Double,
    vmax: Double = 70.0,
    stop: Double = 0.0,
    face: Double? = null,
    rcsOnly: Boolean = false,
    hold: Boolean = false,
    up: Boolean = false,
    through: Boolean = false,
    tvx: Double = 0.0,
    tvy: Double = 0.0,
    skip: Body? = null
): SteerTelemetry {
    val pl = GS.pl ?: return SteerTelemetry(0.0, "HOLD", 0.0, 0.0)
    val rs = hy(pl.x, pl.y)
    val rt = hy(tx, ty)
    if (rs > C.RM + 700.0 && rt > C.RM + 700.0) {
        val asAng = atan2(pl.y, pl.x)
        val dA = ad(atan2(ty, tx), asAng)
        if (abs(dA) > 0.14) {
            val a = asAng + sign(dA) * ARC_STEP
            val r = rs + (rt - rs) * (ARC_STEP / abs(dA))
            val ax = cos(a) * r
            val ay = sin(a) * r
            val wp = detour(pl.x, pl.y, ax, ay, pl.rad + 8.0, skip) ?: Vec2(ax, ay)
            val t = fly(wp.x, wp.y, vmax = min(BAND_V, vmax), through = true)
            return t.copy(wp = true)
        }
    }
    val wp = detour(pl.x, pl.y, tx, ty, pl.rad + 8.0, skip)
    if (wp != null) {
        val t = fly(wp.x, wp.y, vmax = min(30.0, vmax), through = true)
        return t.copy(wp = true)
    }
    return fly(tx, ty, vmax = vmax, stop = stop, face = face, rcsOnly = rcsOnly, hold = hold, up = up, through = through, tvx = tvx, tvy = tvy)
}

fun engineOK(): Boolean {
    val pl = GS.pl ?: return false
    return alive(pl, "eng").isNotEmpty() && pl.fuel > 0.0
}

data class AiTelemetry(
    val r: Double,
    val alt: Double,
    val vr: Double,
    val vt: Double,
    val ux: Double,
    val uy: Double,
    val g: Double
)

fun tel(o: Targetable): AiTelemetry {
    val r = hy(o.x, o.y).let { if (it == 0.0) 1.0 else it }
    val ux = o.x / r
    val uy = o.y / r
    return AiTelemetry(
        r = r,
        alt = r - surfR(atan2(o.y, o.x)),
        vr = o.vx * ux + o.vy * uy,
        vt = -o.vx * uy + o.vy * ux,
        ux = ux,
        uy = uy,
        g = geff(r)
    )
}

fun tel(e: SuitState): AiTelemetry {
    val r = hy(e.x, e.y).let { if (it == 0.0) 1.0 else it }
    val ux = e.x / r
    val uy = e.y / r
    return AiTelemetry(
        r = r,
        alt = r - surfR(atan2(e.y, e.x)),
        vr = e.vx * ux + e.vy * uy,
        vt = -e.vx * uy + e.vy * ux,
        ux = ux,
        uy = uy,
        g = geff(r)
    )
}

data class ApproachPortResult(val along: Double, val lat: Double, val ae: Double, val w: WorldPortPose)
data class ApproachPadResult(val h: Double, val lat: Double, val w: WorldPortPose)

fun approachPort(st: Body, p: Port): ApproachPortResult {
    val pl = GS.pl ?: return ApproachPortResult(0.0, 0.0, 0.0, portW(st, p))
    val w = portW(st, p)
    val nx = cos(w.dir)
    val ny = sin(w.dir)
    val want = w.dir + PI
    val ae = abs(ad(want, pl.a))
    val noseX = pl.x + cos(pl.a) * pl.nose
    val noseY = pl.y + sin(pl.a) * pl.nose
    val along = (noseX - w.x) * nx + (noseY - w.y) * ny
    val lat = (noseX - w.x) * -ny + (noseY - w.y) * nx
    val k = if (ae < 0.12 && abs(lat) < 3.0) max(1.5, along - 6.0) else max(14.0, along)
    val ax = w.x + nx * (k + pl.nose)
    val ay = w.y + ny * (k + pl.nose)
    val t = steer(pl, ax, ay, SteerOpts(vmax = if (along > 40.0) 8.0 else 2.2, face = want, rcsOnly = along < 60.0, hold = true, out = OUT))
    inp.thr = OUT.thr
    stickW(OUT.rcx, OUT.rcy)
    inp.rx = OUT.tqi
    NAV.flight = FlightDiag("DOCK", along, t.rv, hy(pl.vx, pl.vy))
    return ApproachPortResult(along, lat, ae, w)
}

fun approachPad(st: Body, p: Pad): ApproachPadResult {
    val pl = GS.pl ?: return ApproachPadResult(0.0, 0.0, portW(st, p))
    val w = portW(st, p)
    val nx = cos(w.dir)
    val ny = sin(w.dir)
    val tailX = pl.x - cos(pl.a) * pl.nose
    val tailY = pl.y - sin(pl.a) * pl.nose
    val h = (tailX - w.x) * nx + (tailY - w.y) * ny
    val lat = (tailX - w.x) * -ny + (tailY - w.y) * nx
    val hold = if (abs(lat) > 4.0) max(h, 30.0) else max(0.0, h - (if (h > 25.0) 10.0 else 4.0))
    val tx = w.x + nx * (hold + pl.nose)
    val ty = w.y + ny * (hold + pl.nose)
    val vmax = when {
        h > 80.0 -> 14.0
        h > 25.0 -> 5.0
        else -> 1.6
    }
    val t = steer(pl, tx, ty, SteerOpts(vmax = vmax, face = w.dir, up = true, out = OUT))
    inp.thr = OUT.thr
    stickW(OUT.rcx, OUT.rcy)
    val targetAng = if (h < 30.0) w.dir else atan2(pl.y, pl.x)
    inp.rx = cl(ad(targetAng, pl.a) * 2.6 - pl.va * 1.9, -1.0, 1.0)
    NAV.flight = FlightDiag("LAND", h, t.rv, hy(pl.vx, pl.vy))
    return ApproachPadResult(h, lat, w)
}

// ─── EVA ────────────────────────────────────────────────────────────────
private fun freeCell(b: Body, x: Int, y: Int): Boolean =
    x >= 0 && y >= 0 && x < b.gw && y < b.gh && b.sol[y * b.gw + x] == 0

private fun roomy(b: Body, x: Int, y: Int): Boolean =
    freeCell(b, x, y) && freeCell(b, x + 1, y) && freeCell(b, x - 1, y) && freeCell(b, x, y + 1) && freeCell(b, x, y - 1)

private fun localCell(b: Body, x: Double, y: Double): Pair<Int, Int> {
    val c = cos(-b.a)
    val s = sin(-b.a)
    val dx = x - b.x
    val dy = y - b.y
    val cx = floor((dx * c - dy * s + (b.gw * b.cs) / 2.0) / b.cs).toInt()
    val cy = floor((dx * s + dy * c + (b.gh * b.cs) / 2.0) / b.cs).toInt()
    return cx to cy
}

private fun nearestFree(b: Body, cx0: Int, cy0: Int, ok: (Body, Int, Int) -> Boolean): Pair<Int, Int>? {
    val cx = cx0.coerceIn(0, b.gw - 1)
    val cy = cy0.coerceIn(0, b.gh - 1)
    for (r in 0 until 8) {
        for (y in (cy - r)..(cy + r)) {
            for (x in (cx - r)..(cx + r)) {
                if ((abs(x - cx) == r || abs(y - cy) == r) && ok(b, x, y)) return x to y
            }
        }
    }
    return null
}

private val BFS_DIRS = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
private val LOS_OFFS = doubleArrayOf(-0.8, 0.0, 0.8)

private fun los(b: Body, i: Int, j: Int): Boolean {
    val x0 = (i % b.gw) + 0.5
    val y0 = (i / b.gw) + 0.5
    val x1 = (j % b.gw) + 0.5
    val y1 = (j / b.gw) + 0.5
    val L = hy(x1 - x0, y1 - y0)
    val n = ceil(L * 3.0).toInt()
    val div = if (L == 0.0) 1.0 else L
    val px = -(y1 - y0) / div
    val py = (x1 - x0) / div
    for (s in 0..n) {
        val frac = if (n == 0) 0.0 else s.toDouble() / n.toDouble()
        val x = x0 + (x1 - x0) * frac
        val y = y0 + (y1 - y0) * frac
        for (o in LOS_OFFS) {
            if (!freeCell(b, floor(x + px * o).toInt(), floor(y + py * o).toInt())) return false
        }
    }
    return true
}

private fun bfs(
    b: Body,
    ax: Double,
    ay: Double,
    tx: Double,
    ty: Double,
    toEdge: Boolean,
    ok: (Body, Int, Int) -> Boolean
): MutableList<Vec2>? {
    val lc = localCell(b, ax, ay)
    val s = nearestFree(b, lc.first, lc.second, ok) ?: return null
    val N = b.gw * b.gh
    val prev = IntArray(N) { -1 }
    val q = IntArray(N)
    val si = s.second * b.gw + s.first
    var gi = -1
    if (!toEdge) {
        val lg = localCell(b, tx, ty)
        val g = nearestFree(b, lg.first, lg.second, ok) ?: return null
        gi = g.second * b.gw + g.first
    }
    var h = 0
    var tl = 0
    q[tl++] = si
    prev[si] = si
    var best = -1
    var bc = 1e9
    while (h < tl) {
        val i = q[h++]
        if (i == gi) break
        val x = i % b.gw
        val y = i / b.gw
        if (toEdge && (x <= 1 || y <= 1 || x >= b.gw - 2 || y >= b.gh - 2)) {
            val w = cw(b, x, y)
            val c = hy(w.x - tx, w.y - ty) + h * 0.02
            if (c < bc) {
                bc = c
                best = i
            }
        }
        for ((ox, oy) in BFS_DIRS) {
            val nx = x + ox
            val ny = y + oy
            if (nx < 0 || ny < 0 || nx >= b.gw || ny >= b.gh) continue
            val j = ny * b.gw + nx
            if (prev[j] >= 0 || !ok(b, nx, ny)) continue
            prev[j] = i
            q[tl++] = j
        }
    }
    if (toEdge) gi = best
    if (gi < 0 || prev[gi] < 0) return null
    val cells = mutableListOf<Int>()
    var cur = gi
    while (cur != si) {
        cells.add(cur)
        cur = prev[cur]
    }
    cells.add(si)
    cells.reverse()
    val out = mutableListOf(cells[0])
    var k = 0
    while (k < cells.size - 1) {
        var j = min(cells.size - 1, k + 6)
        while (j > k + 1) {
            if (los(b, cells[k], cells[j])) break
            j--
        }
        out.add(cells[j])
        k = j
    }
    return out.mapTo(mutableListOf()) { idx -> cw(b, idx % b.gw, idx / b.gw) }
}

private fun inside(b: Body, x: Double, y: Double): Boolean {
    val c = ca(b, x, y) ?: return false
    return b.sol[c.i] == 0
}

data class EvaPath(val points: MutableList<Vec2>, val hop: Boolean = false)

fun evaRoute(tx: Double, ty: Double): EvaPath {
    val ev = GS.ev ?: return EvaPath(mutableListOf(Vec2(tx, ty)))
    val qa = inBody(ev.x, ev.y)
    val A = if (qa != null && qa.b.kind == "station") qa.b else null
    var B: Body? = null
    for (b in near(tx, ty, 2.0)) {
        if (b.kind == "station" && inside(b, tx, ty)) {
            B = b
            break
        }
    }
    val checks = arrayOf(::roomy, ::freeCell)
    for (ok in checks) {
        if (A != null && A === B) {
            val p = bfs(A, ev.x, ev.y, tx, ty, false, ok)
            if (p != null) {
                p.add(Vec2(tx, ty))
                return EvaPath(p, false)
            }
            continue
        }
        var path = mutableListOf<Vec2>()
        if (A != null) {
            val p = bfs(A, ev.x, ev.y, tx, ty, true, ok) ?: continue
            path = p
        }
        if (B != null) {
            val ax = if (path.isNotEmpty()) path.last().x else ev.x
            val ay = if (path.isNotEmpty()) path.last().y else ev.y
            val p = bfs(B, tx, ty, ax, ay, true, ok) ?: continue
            p.reverse()
            path.addAll(p)
        }
        path.add(Vec2(tx, ty))
        var hopped = false
        if (A == null && B == null) {
            val h = suitHop(tx, ty)
            if (h != null) {
                path.add(0, h)
                hopped = true
            }
        }
        return EvaPath(path, hopped)
    }
    return EvaPath(mutableListOf(Vec2(tx, ty)))
}

fun suitHop(tx: Double, ty: Double): Vec2? {
    val ev = GS.ev ?: return null
    val L = hy(tx - ev.x, ty - ev.y)
    if (L < 3.0) return null
    val k = max(0.0, L - 8.0) / L
    val blk = segBlock(ev.x, ev.y, ev.x + (tx - ev.x) * k, ev.y + (ty - ev.y) * k, 1.0)
    if (blk == null || inBody(ev.x, ev.y) != null) return null
    val aE = atan2(ev.y - blk.y, ev.x - blk.x)
    val dA = ad(atan2(ty - blk.y, tx - blk.x), aE)
    val sg = if (dA < 0.0) -1.0 else 1.0
    fun surf(a: Double): Double {
        val R0 = blk.rad + 12.0
        val h = ray(blk.x + cos(a) * R0, blk.y + sin(a) * R0, -cos(a), -sin(a), R0)
        return if (h != null) R0 - h.d else 0.0
    }
    for (dir in doubleArrayOf(sg, -sg)) {
        for (st in doubleArrayOf(0.35, 0.2, 0.1)) {
            val a = aE + dir * (if (dir == sg) min(abs(dA), st) else st)
            for (off in doubleArrayOf(7.0, 14.0)) {
                val r = max(surf(a) + off, hy(ev.x - blk.x, ev.y - blk.y) * 0.6)
                val wp = Vec2(blk.x + cos(a) * r, blk.y + sin(a) * r)
                if (segBlock(ev.x, ev.y, wp.x, wp.y, 1.0) == null) return wp
            }
        }
    }
    return if (hy(ev.x - blk.x, ev.y - blk.y) < surf(aE) + 6.0) {
        Vec2(ev.x + cos(aE) * 6.0, ev.y + sin(aE) * 6.0)
    } else null
}

fun jetTo(x: Double, y: Double, vmax: Double = 8.0, ref: Targetable? = null): Double {
    val ev = GS.ev ?: return 0.0
    val dx = x - ev.x
    val dy = y - ev.y
    val d = hy(dx, dy).let { if (it < 0.001) 0.001 else it }
    val r = hy(ev.x, ev.y).let { if (it == 0.0) 1.0 else it }
    val ux = ev.x / r
    val uy = ev.y / r
    if (geff(r) > 0.4 && r - surfR(atan2(ev.y, ev.x)) < 40.0) {
        val along = dx * -uy + dy * ux
        val up = dx * ux + dy * uy
        inp.lx = if (abs(along) > 1.2) cl(along / 4.0, -1.0, 1.0) else 0.0
        val vt = ev.vx * -uy + ev.vy * ux
        NAV.stuck = if (abs(inp.lx) > 0.5 && abs(vt) < 1.5) NAV.stuck + NAV.dt else 0.0
        if (up > 4.0 && up < 25.0 && abs(along) < 8.0 && tel(ev).vr < 4.0) {
            inp.ly = -1.0
        } else if (NAV.stuck > 0.5) {
            inp.ly = -1.0
            if (NAV.stuck > 1.1) NAV.stuck = 0.0
        }
        return d
    }
    val rvx = ref?.vx ?: 0.0
    val rvy = ref?.vy ?: 0.0
    gacc(ev.x, ev.y, ev.vx, ev.vy, GA)
    val vs = min(vmax, d * 0.9)
    val ex = rvx + (dx / d) * vs - ev.vx - GA.x * 0.5
    val ey = rvy + (dy / d) * vs - ev.vy - GA.y * 0.5
    val m = hy(ex, ey)
    if (m > 0.08) {
        val l = min(1.0, m / 1.4)
        stickW((ex / m) * l, (ey / m) * l)
    }
    return d
}

fun evaFace(a: Double): Double {
    val ev = GS.ev ?: return 0.0
    val e = ad(a, ev.a)
    inp.rx = cl(e * 2.0, -1.0, 1.0)
    return abs(e)
}
