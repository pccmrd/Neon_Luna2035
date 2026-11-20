package com.example.keres9.sim

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.keres9.audio.AU
import com.example.keres9.core.Body
import com.example.keres9.core.C
import com.example.keres9.core.CLS
import com.example.keres9.core.DockState
import com.example.keres9.core.FC
import com.example.keres9.core.GD
import com.example.keres9.core.GS
import com.example.keres9.core.MD
import com.example.keres9.core.Pad
import com.example.keres9.core.Port
import com.example.keres9.core.Ship
import com.example.keres9.core.ShipModule
import com.example.keres9.core.Vec2
import com.example.keres9.core.ad
import com.example.keres9.core.hy
import com.example.keres9.core.parseHexColor
import com.example.keres9.core.sh
import com.example.keres9.world.bvel
import com.example.keres9.world.portW
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class ModSpec(val t: String, val x: Int, val y: Int)
data class ShipLayout(val w: Int, val h: Int, val tail: Boolean = false, val m: List<ModSpec>)

private fun spine(from: Int, to: Int, y: Int): List<ModSpec> = (from downTo to).map { ModSpec("hull", it, y) }

val LAY: Map<String, ShipLayout> = mapOf(
    "wren" to ShipLayout(
        w = 10, h = 7,
        m = listOf(ModSpec("clamp", 9, 3), ModSpec("cock", 8, 3)) + spine(7, 1, 3) + listOf(
            ModSpec("cargo", 3, 2), ModSpec("cargo", 4, 2), ModSpec("cargo", 5, 2), ModSpec("cargo", 3, 4), ModSpec("cargo", 4, 4),
            ModSpec("fuel", 2, 2), ModSpec("fuel", 2, 4), ModSpec("eng", 0, 3),
            ModSpec("rcs", 1, 1), ModSpec("rcs", 1, 5), ModSpec("rcs", 8, 1), ModSpec("rcs", 8, 5),
            ModSpec("mine", 7, 5), ModSpec("scan", 6, 1), ModSpec("hab", 6, 2)
        )
    ),
    "hare" to ShipLayout(
        w = 11, h = 7, tail = true,
        m = listOf(ModSpec("clamp", 10, 3), ModSpec("cock", 9, 3), ModSpec("hab", 8, 3)) + spine(7, 1, 3) + listOf(
            ModSpec("cargo", 4, 2), ModSpec("cargo", 5, 2), ModSpec("cargo", 6, 2),
            ModSpec("cargo", 4, 4), ModSpec("cargo", 5, 4), ModSpec("cargo", 6, 4),
            ModSpec("fuel", 2, 2), ModSpec("fuel", 3, 2), ModSpec("fuel", 2, 4), ModSpec("fuel", 3, 4),
            ModSpec("eng", 0, 2), ModSpec("eng", 0, 3), ModSpec("eng", 0, 4),
            ModSpec("gear", 0, 0), ModSpec("gear", 0, 6),
            ModSpec("rcs", 8, 2), ModSpec("rcs", 8, 4), ModSpec("rcs", 2, 1), ModSpec("rcs", 2, 5),
            ModSpec("mine", 7, 4), ModSpec("scan", 7, 2), ModSpec("hull", 1, 1), ModSpec("hull", 1, 5)
        )
    ),
    "mule" to ShipLayout(
        w = 14, h = 8,
        m = listOf(ModSpec("clamp", 13, 3), ModSpec("clamp", 13, 4), ModSpec("cock", 12, 3), ModSpec("cock", 12, 4)) +
            spine(11, 1, 3) + spine(11, 1, 4) + listOf(
            ModSpec("hab", 8, 2), ModSpec("hab", 9, 2), ModSpec("hab", 10, 2),
            ModSpec("hab", 8, 5), ModSpec("hab", 9, 5), ModSpec("hab", 10, 5),
            ModSpec("cargo", 4, 2), ModSpec("cargo", 5, 2), ModSpec("cargo", 6, 2),
            ModSpec("cargo", 4, 5), ModSpec("cargo", 5, 5), ModSpec("cargo", 6, 5),
            ModSpec("fuel", 2, 2), ModSpec("fuel", 2, 5), ModSpec("eng", 0, 3), ModSpec("eng", 0, 4),
            ModSpec("rcs", 1, 1), ModSpec("rcs", 1, 6), ModSpec("rcs", 11, 1), ModSpec("rcs", 11, 6),
            ModSpec("gear", 6, 7), ModSpec("gear", 9, 7), ModSpec("scan", 7, 1)
        )
    ),
    "hauler" to ShipLayout(
        w = 13, h = 7,
        m = listOf(ModSpec("clamp", 12, 3), ModSpec("cock", 11, 3), ModSpec("hab", 10, 3)) + spine(9, 1, 3) + listOf(
            ModSpec("cargo", 5, 2), ModSpec("cargo", 6, 2), ModSpec("cargo", 7, 2),
            ModSpec("cargo", 5, 4), ModSpec("cargo", 6, 4), ModSpec("cargo", 7, 4),
            ModSpec("fuel", 3, 2), ModSpec("fuel", 3, 4), ModSpec("eng", 0, 3),
            ModSpec("rcs", 1, 0), ModSpec("rcs", 1, 6), ModSpec("rcs", 11, 0), ModSpec("rcs", 11, 6), ModSpec("gear", 6, 6)
        )
    ),
    "pros" to ShipLayout(
        w = 11, h = 7,
        m = listOf(ModSpec("clamp", 10, 3), ModSpec("cock", 9, 3)) + spine(8, 1, 3) + listOf(
            ModSpec("cargo", 4, 2), ModSpec("cargo", 5, 2), ModSpec("cargo", 4, 4),
            ModSpec("fuel", 2, 2), ModSpec("eng", 0, 3),
            ModSpec("mine", 7, 5), ModSpec("scan", 6, 0),
            ModSpec("rcs", 1, 0), ModSpec("rcs", 1, 6), ModSpec("rcs", 9, 0), ModSpec("rcs", 9, 6)
        )
    ),
    "lander" to ShipLayout(
        w = 12, h = 7, tail = true,
        m = listOf(ModSpec("clamp", 11, 3), ModSpec("cock", 10, 3), ModSpec("hab", 9, 3)) + spine(8, 1, 3) + listOf(
            ModSpec("cargo", 4, 2), ModSpec("cargo", 5, 2), ModSpec("cargo", 6, 2),
            ModSpec("cargo", 4, 4), ModSpec("cargo", 5, 4), ModSpec("cargo", 6, 4),
            ModSpec("fuel", 2, 2), ModSpec("fuel", 2, 4),
            ModSpec("eng", 0, 2), ModSpec("eng", 0, 3), ModSpec("eng", 0, 4),
            ModSpec("gear", 0, 0), ModSpec("gear", 0, 6),
            ModSpec("rcs", 9, 1), ModSpec("rcs", 9, 5), ModSpec("rcs", 2, 1), ModSpec("rcs", 2, 5)
        )
    ),
    "tug" to ShipLayout(
        w = 9, h = 7,
        m = listOf(ModSpec("clamp", 8, 2), ModSpec("clamp", 8, 3), ModSpec("clamp", 8, 4), ModSpec("cock", 7, 3)) +
            spine(6, 1, 3) + listOf(
            ModSpec("fuel", 3, 2), ModSpec("fuel", 3, 4), ModSpec("fuel", 4, 2), ModSpec("fuel", 4, 4),
            ModSpec("eng", 0, 1), ModSpec("eng", 0, 3), ModSpec("eng", 0, 5),
            ModSpec("hull", 1, 1), ModSpec("hull", 1, 5),
            ModSpec("rcs", 6, 0), ModSpec("rcs", 6, 6), ModSpec("scan", 5, 1)
        )
    ),
    "drone" to ShipLayout(
        w = 5, h = 3,
        m = listOf(
            ModSpec("scan", 4, 1), ModSpec("cock", 3, 1), ModSpec("hull", 2, 1),
            ModSpec("eng", 0, 1), ModSpec("rcs", 1, 0), ModSpec("rcs", 1, 2), ModSpec("hull", 1, 1)
        )
    )
)

fun alive(s: Ship, t: String? = null): List<ShipModule> = s.mods.filter { !it.dead && (t == null || it.t == t) }

fun cmass(s: Ship): Int {
    var m = 0.0
    for ((g, q) in s.cargo) {
        val def = GD[g] ?: continue
        m += q * def.m
    }
    return m.toInt()
}

fun cadd(s: Ship, t: String, q: Int): Int {
    val def = GD[t] ?: return 0
    val take = min(q, floor((s.cargoMax - cmass(s)) / def.m).toInt())
    if (take > 0) {
        s.cargo[t] = (s.cargo[t] ?: 0) + take
        stats(s)
    }
    return max(0, take)
}

fun integ(s: Ship): Double {
    var h = 0.0
    var m = 0.0
    for (q in s.mods) {
        h += max(0.0, q.hp)
        m += q.max
    }
    return if (m > 0.0) h / m else 1.0
}

fun sw(s: Ship, lx: Double, ly: Double): Vec2 {
    val c = cos(s.a)
    val n = sin(s.a)
    return Vec2(s.x + lx * c - ly * n, s.y + lx * n + ly * c)
}

fun mw(s: Ship, m: ShipModule): Vec2 =
    sw(s, (m.gx + 0.5) * C.SS - (s.gw * C.SS) / 2.0, (m.gy + 0.5) * C.SS - (s.gh * C.SS) / 2.0)

fun stats(s: Ship) {
    val u = s.upg
    fun n(t: String) = alive(s, t).size
    val K = CLS[s.cls] ?: CLS["hauler"]!!
    val L = LAY[s.cls] ?: LAY["hauler"]!!
    fun base(t: String) = max(1, L.m.count { it.t == t })
    s.mass = 12.0 + s.mods.size * 0.12 + cmass(s) * 0.3
    s.thrust = (K.thr * (1.0 + (u["eng"] ?: 0) * 0.16) * (n("eng").toDouble() / base("eng"))) / s.mass
    s.rcsA = K.rcs * (n("rcs").toDouble() / base("rcs"))
    s.tq = K.tq * max(0.25, n("rcs").toDouble() / base("rcs"))
    s.cargoMax = (K.cargo * (0.4 + 0.6 * (n("cargo").toDouble() / base("cargo")))).roundToInt() + (u["cargo"] ?: 0) * 12
    s.fuelMax = K.fuel + (u["fuel"] ?: 0) * 35.0
    s.burn = K.burn
    s.nose = (s.gw * C.SS) / 2.0
    s.belly = (s.gh * C.SS) / 2.0
    s.rad = ((hy(s.gw.toDouble(), s.gh.toDouble()) * C.SS) / 2.0) * 0.82
    s.lasR = C.MINE.rng + (u["laser"] ?: 0) * 30.0
    s.lasD = C.MINE.dps + (u["laser"] ?: 0) * 14.0
    s.scanR = C.SCAN.rng + (u["scan"] ?: 0) * 160.0
    s.gearOK = K.gear && n("gear") > 0
}

private val COL_DEAD_MOD = parseHexColor("#141a22")
private val COL_53D9FF = parseHexColor("#53d9ff")
private val COL_FF8C1A = parseHexColor("#ff8c1a")
private val COL_8A6A2F = parseHexColor("#8a6a2f")
private val COL_FFB347 = parseHexColor("#ffb347")
private val COL_FFD166 = parseHexColor("#ffd166")
private val COL_6DF08F = parseHexColor("#6df08f")
private val COL_8CFF9E = parseHexColor("#8cff9e")
private val COL_8B98A8 = parseHexColor("#8b98a8")
private val COL_FLASH_WHITE = Color.argb(217, 255, 240, 210)

fun sprite(s: Ship) {
    val S = 4
    val w = s.gw * S
    val h = s.gh * S
    val bmp = s.spr ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { s.spr = it }
    bmp.eraseColor(0)
    val c = Canvas(bmp)
    val p = Paint()
    for (m in s.mods) {
        val px = (m.gx * S).toFloat()
        val py = (m.gy * S).toFloat()
        if (m.dead) {
            p.color = COL_DEAD_MOD
            c.drawRect(px, py, px + S, py + S, p)
            continue
        }
        val d = 1.0 - m.hp / m.max
        val col = MD[m.t]?.c ?: COL_8B98A8
        p.color = sh(col, 1.0 - d * 0.5)
        c.drawRect(px, py, px + S, py + S, p)
        p.color = sh(col, 1.25 - d * 0.5)
        c.drawRect(px, py, px + S, py + 1f, p)
        p.color = sh(col, 0.6)
        c.drawRect(px, py + S - 1f, px + S, py + S, p)
        when (m.t) {
            "cock" -> { p.color = COL_53D9FF; c.drawRect(px + 1f, py + 1f, px + 3f, py + 3f, p) }
            "eng" -> { p.color = COL_FF8C1A; c.drawRect(px, py + 1f, px + 1f, py + 3f, p) }
            "mine" -> { p.color = COL_53D9FF; c.drawRect(px + S - 1f, py + 1f, px + S, py + 3f, p) }
            "cargo" -> { p.color = COL_8A6A2F; c.drawRect(px + 1f, py + 1f, px + 3f, py + 3f, p) }
            "fuel" -> { p.color = COL_FFB347; c.drawRect(px + 1f, py + 1f, px + 3f, py + 2f, p) }
            "clamp" -> {
                p.color = COL_FFD166
                c.drawRect(px + S - 1f, py, px + S, py + 1f, p)
                c.drawRect(px + S - 1f, py + 3f, px + S, py + 4f, p)
            }
            "gear" -> { p.color = COL_6DF08F; c.drawRect(px + 1f, py + 1f, px + 3f, py + 3f, p) }
            "scan" -> { p.color = COL_53D9FF; c.drawRect(px + 1f, py, px + 3f, py + 1f, p) }
            "hab" -> { p.color = COL_8CFF9E; c.drawRect(px + 1f, py + 1f, px + 2f, py + 2f, p) }
        }
        if (m.flash > 0.0) {
            p.color = COL_FLASH_WHITE
            c.drawRect(px, py, px + S, py + S, p)
        }
    }
    p.color = FC[s.fac] ?: COL_8B98A8
    c.drawRect(w - 2f, 0f, w.toFloat(), 1f, p)
    c.drawRect(0f, h - 1f, 2f, h.toFloat(), p)
    s.dirty = false
}

fun mkShip(cls: String, fac: String, name: String, x: Double, y: Double): Ship {
    val L = LAY[cls] ?: LAY["hauler"]!!
    val s = Ship(cls, cls, fac, name, x, y, L.w, L.h, L.tail)
    L.m.forEachIndexed { i, ms ->
        val hp = MD[ms.t]?.hp ?: 30.0
        s.mods.add(ShipModule(ms.t, ms.x, ms.y, hp, hp))
        s.grid[ms.y * L.w + ms.x] = i + 1
    }
    stats(s)
    s.fuel = s.fuelMax
    sprite(s)
    GS.W?.ships?.add(s)
    return s
}

fun mkOwned(cls: String, x: Double, y: Double): Ship {
    val s = mkShip(cls, "free", if (cls == "wren") "WREN" else "HARE", x, y)
    s.own = true
    s.upg = mutableMapOf("eng" to 0, "cargo" to 0, "laser" to 0, "fuel" to 0, "scan" to 0)
    stats(s)
    s.fuel = s.fuelMax
    return s
}

fun dockPort(s: Ship, b: Body, p: Port, silent: Boolean = false) {
    p.occ = s
    s.dock = DockState(ty = "port", b = b, pPort = p)
    s.thr = 0.0; s.rcx = 0.0; s.rcy = 0.0; s.tqi = 0.0
    attach(s)
    if (s.isPl && !silent) {
        AU.chime()
        Hooks.onDock(b, "port")
    }
}

fun dockPad(s: Ship, b: Body, p: Pad, silent: Boolean = false) {
    p.occ = s
    s.dock = DockState(ty = "pad", b = b, pPad = p)
    s.thr = 0.0; s.rcx = 0.0; s.rcy = 0.0; s.tqi = 0.0
    attach(s)
    if (s.isPl && !silent) {
        AU.chime()
        Hooks.onDock(b, "pad")
    }
}

fun settle(s: Ship) {
    s.dock = DockState(ty = "ground", x = s.x, y = s.y, a = s.a)
    s.vx = 0.0; s.vy = 0.0; s.va = 0.0
    s.thr = 0.0; s.rcx = 0.0; s.rcy = 0.0; s.tqi = 0.0
    if (s.isPl) Hooks.onTouch(s)
}

fun anchor(s: Ship, b: Body, cx: Int, cy: Int) {
    val c = cos(-b.a)
    val n = sin(-b.a)
    val dx = s.x - b.x
    val dy = s.y - b.y
    val ao = ad(s.a, b.a)
    s.dock = DockState(
        ty = "anchor", b = b, cx = cx, cy = cy,
        lx = dx * c - dy * n, ly = dx * n + dy * c,
        ao = ao, ao0 = ao
    )
    s.thr = 0.0; s.rcx = 0.0; s.rcy = 0.0; s.tqi = 0.0
    if (s.isPl) {
        AU.thunk()
        Hooks.toast("HARPOON SET · locked to ${b.name}", "good")
    }
}

fun undock(s: Ship) {
    val d = s.dock ?: return
    s.dock = null
    if (d.ty == "ground") {
        val r = hypot(s.x, s.y).let { if (it == 0.0) 1.0 else it }
        s.vx = (s.x / r) * 0.6
        s.vy = (s.y / r) * 0.6
        s.va = 0.0
        s.x += (s.x / r) * 0.6
        s.y += (s.y / r) * 0.6
    } else {
        d.pPort?.occ = null
        d.pPad?.occ = null
        val b = d.b
        val dir: Double
        val bvx: Double
        val bvy: Double
        when {
            d.ty == "anchor" -> {
                dir = s.a + PI
                bvx = b?.vx ?: 0.0
                bvy = b?.vy ?: 0.0
            }
            d.pPort != null && b != null -> {
                val pw = portW(b, d.pPort)
                dir = pw.dir; bvx = pw.vx; bvy = pw.vy
            }
            d.pPad != null && b != null -> {
                val pw = portW(b, d.pPad)
                dir = pw.dir; bvx = pw.vx; bvy = pw.vy
            }
            else -> {
                dir = s.a + PI; bvx = 0.0; bvy = 0.0
            }
        }
        val sp = if (d.ty == "pad" && s.tailLand) 0.8 else 3.0
        s.vx = bvx + cos(dir) * sp
        s.vy = bvy + sin(dir) * sp
        s.va = 0.0
    }
    if (s.isPl) {
        AU.puff()
        Hooks.closeStation()
    }
}

fun attach(s: Ship) {
    val d = s.dock ?: return
    if (d.ty == "ground") {
        s.x = d.x; s.y = d.y; s.a = d.a
        s.vx = 0.0; s.vy = 0.0; s.va = 0.0
        return
    }
    val b = d.b ?: return
    if (d.ty == "anchor") {
        if (!b.dead && b.sol[d.cy * b.gw + d.cx] == 0) {
            var bi = -1
            var bd = 1e9
            for (oy in -3..3) {
                for (ox in -3..3) {
                    val x = d.cx + ox
                    val y = d.cy + oy
                    if (x < 0 || y < 0 || x >= b.gw || y >= b.gh) continue
                    if (b.sol[y * b.gw + x] != 0 && ox * ox + oy * oy < bd) {
                        bd = (ox * ox + oy * oy).toDouble()
                        bi = y * b.gw + x
                    }
                }
            }
            if (bi >= 0) {
                d.cx = bi % b.gw
                d.cy = bi / b.gw
            }
        }
        if (b.dead || b.sol[d.cy * b.gw + d.cx] == 0) {
            s.dock = null
            if (s.isPl) Hooks.toast("ANCHOR SHEARED — the rock gave way", "warn")
            return
        }
        val ao0 = d.ao0 ?: d.ao.also { d.ao0 = it }
        if (abs(s.tqi) > 0.05) {
            d.ao = ao0 + max(-0.75, min(0.75, d.ao - ao0 + s.tqi * C.DT * 0.45))
        }
        val c = cos(b.a)
        val n = sin(b.a)
        s.x = b.x + d.lx * c - d.ly * n
        s.y = b.y + d.lx * n + d.ly * c
        s.a = b.a + d.ao
        val v = bvel(b, s.x, s.y)
        s.vx = v.x
        s.vy = v.y
        s.va = b.va
        return
    }
    val w = when {
        d.pPad != null -> portW(b, d.pPad)
        d.pPort != null -> portW(b, d.pPort)
        else -> return
    }
    if (d.ty == "pad") {
        if (s.tailLand) {
            s.a = w.dir
            s.x = w.x + cos(w.dir) * (s.nose + 1.2)
            s.y = w.y + sin(w.dir) * (s.nose + 1.2)
        } else {
            s.a = w.dir + PI / 2.0
            s.x = w.x + cos(w.dir) * (s.belly + 1.2)
            s.y = w.y + sin(w.dir) * (s.belly + 1.2)
        }
    } else {
        s.a = w.dir + PI
        s.x = w.x + cos(w.dir) * (s.nose + 1.5)
        s.y = w.y + sin(w.dir) * (s.nose + 1.5)
    }
    s.vx = w.vx
    s.vy = w.vy
    s.va = w.va
}
