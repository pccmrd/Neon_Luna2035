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
private val COL_C96F2F = parseHexColor("#c96f2f")
private val COL_FFD166 = parseHexColor("#ffd166")
private val COL_CANOPY_DARK = parseHexColor("#123652")
private val COL_CANOPY_MID = parseHexColor("#1e5b82")
private val COL_CANOPY_SPEC = parseHexColor("#9eeeff")
private val COL_NOZZLE_DARK = parseHexColor("#26303d")
private val COL_NOZZLE_RIM = parseHexColor("#5c6c80")
private val COL_HULL_FREE = parseHexColor("#7a8ea3")
private val COL_HULL_SELENE = parseHexColor("#5b748e")
private val COL_HULL_CREW = parseHexColor("#6c655a")
private val COL_HULL_HELION = parseHexColor("#634842")
private val COL_HULL_RIM = parseHexColor("#536d5d")
private val COL_FLASH_WHITE = Color.argb(217, 255, 240, 210)

fun sprite(s: Ship) {
    val S = 16
    val w = s.gw * S
    val h = s.gh * S
    val bmp = s.spr ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { s.spr = it }
    bmp.eraseColor(0)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)

    val hullBase = when (s.fac) {
        "selene" -> COL_HULL_SELENE
        "crew" -> COL_HULL_CREW
        "helion" -> COL_HULL_HELION
        "rim" -> COL_HULL_RIM
        else -> COL_HULL_FREE
    }
    val facAccent = FC[s.fac] ?: COL_53D9FF
    val bodyTypes = setOf("hull", "cock", "hab", "cargo", "fuel", "eng")
    val occBody = Array(s.gh) { BooleanArray(s.gw) }
    val occAny = Array(s.gh) { BooleanArray(s.gw) }
    for (m in s.mods) {
        if (m.gy in 0 until s.gh && m.gx in 0 until s.gw) {
            occAny[m.gy][m.gx] = true
            if (m.t in bodyTypes) occBody[m.gy][m.gx] = true
        }
    }

    // Pass 1: Continuous enclosed fuselage carapace roof
    for (m in s.mods) {
        if (m.t == "clamp" || m.t == "rcs" || m.t == "gear") continue
        val px = (m.gx * S).toFloat()
        val py = (m.gy * S).toFloat()
        val sf = S.toFloat()
        val d = 1.0 - (m.hp / m.max).coerceIn(0.0, 1.0)
        val midFrac = 1.0 - kotlin.math.abs((m.gy + 0.5) / s.gh - 0.5) * 0.4
        val plateCol = if (m.dead) COL_DEAD_MOD else sh(hullBase, midFrac * (1.0 - d * 0.35))

        p.style = Paint.Style.FILL
        p.color = plateCol
        c.drawRect(px, py, px + sf, py + sf, p)

        // Subtle exterior fuselage bevel & dorsal plating seams
        val hasTop = m.gy > 0 && occBody[m.gy - 1][m.gx]
        val hasBot = m.gy < s.gh - 1 && occBody[m.gy + 1][m.gx]
        val hasLeft = m.gx > 0 && occBody[m.gy][m.gx - 1]
        val hasRight = m.gx < s.gw - 1 && occBody[m.gy][m.gx + 1]

        if (!hasTop) {
            p.color = sh(plateCol, 1.24)
            c.drawRect(px, py, px + sf, py + 2.5f, p)
        }
        if (!hasBot) {
            p.color = sh(plateCol, 0.68)
            c.drawRect(px, py + sf - 2.5f, px + sf, py + sf, p)
        }
        if (!hasLeft && m.t != "eng") {
            p.color = sh(plateCol, 1.10)
            c.drawRect(px, py, px + 2f, py + sf, p)
        }
        if (!hasRight) {
            p.color = sh(plateCol, 0.80)
            c.drawRect(px + sf - 2f, py, px + sf, py + sf, p)
        }
        // Subtle hull seam lines (sealed outer skin)
        p.color = Color.argb(48, 8, 12, 18)
        c.drawRect(px + sf - 1f, py + 1f, px + sf, py + sf - 1f, p)
        c.drawRect(px + 1f, py + sf - 1f, px + sf - 1f, py + sf, p)
    }

    // Pass 2: Dorsal racing stripe along fuselage center spine
    val spineY = (h / 2f)
    p.style = Paint.Style.FILL
    p.color = Color.argb(175, Color.red(facAccent), Color.green(facAccent), Color.blue(facAccent))
    for (m in s.mods) {
        if (m.dead || m.t !in bodyTypes || m.t == "cock" || m.t == "eng") continue
        val cy = (m.gy + 0.5f) * S
        if (kotlin.math.abs(cy - spineY) <= S * 0.65f) {
            val px = (m.gx * S).toFloat()
            c.drawRect(px, spineY - 1.5f, px + S, spineY + 1.5f, p)
        }
    }

    // Pass 3: Exterior roof hardware (closed canopy dome, cargo hatch doors, engine cowlings, clamps, radomes)
    for (m in s.mods) {
        val px = (m.gx * S).toFloat()
        val py = (m.gy * S).toFloat()
        val sf = S.toFloat()
        if (m.dead) {
            p.style = Paint.Style.FILL
            p.color = COL_DEAD_MOD
            c.drawRect(px + 1f, py + 1f, px + sf - 1f, py + sf - 1f, p)
            p.color = Color.argb(140, 255, 110, 40)
            c.drawCircle(px + sf * 0.5f, py + sf * 0.5f, 2f, p)
            continue
        }
        when (m.t) {
            "cock" -> {
                // Tinted aerospace cockpit canopy dome (opaque glass roof)
                p.style = Paint.Style.FILL
                p.color = COL_CANOPY_DARK
                c.drawRoundRect(px + 2f, py + 2.5f, px + sf - 1.5f, py + sf - 2.5f, 4f, 4f, p)
                p.color = COL_CANOPY_MID
                c.drawRoundRect(px + 3.5f, py + 3.5f, px + sf - 2.5f, py + sf - 3.5f, 3f, 3f, p)
                p.color = COL_CANOPY_SPEC
                c.drawRoundRect(px + 5.5f, py + 4.5f, px + sf - 4f, py + 6.8f, 1.5f, 1.5f, p)
            }
            "cargo" -> {
                // Closed armored clam-shell cargo bay roof hatches
                val hatchCol = sh(hullBase, 0.90)
                p.style = Paint.Style.FILL
                p.color = hatchCol
                c.drawRect(px + 2f, py + 2f, px + sf - 2f, py + sf - 2f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1f
                p.color = sh(hullBase, 0.62)
                c.drawRect(px + 2f, py + 2f, px + sf - 2f, py + sf - 2f, p)
                c.drawLine(px + 2f, py + sf * 0.5f, px + sf - 2f, py + sf * 0.5f, p)
                p.style = Paint.Style.FILL
                p.color = facAccent
                c.drawRect(px + 3.5f, py + sf * 0.5f - 1f, px + 5.5f, py + sf * 0.5f + 1f, p)
            }
            "hab" -> {
                // Armored crew blister with tiny exterior observation slit
                p.style = Paint.Style.FILL
                p.color = sh(hullBase, 1.08)
                c.drawRoundRect(px + 2.5f, py + 3f, px + sf - 2.5f, py + sf - 3f, 2f, 2f, p)
                p.color = COL_CANOPY_MID
                c.drawRect(px + 5f, py + 5f, px + sf - 5f, py + 7f, p)
            }
            "fuel" -> {
                // Ribbed dorsal propellant nacelle cowling
                p.style = Paint.Style.FILL
                p.color = sh(hullBase, 0.92)
                c.drawRoundRect(px + 1.5f, py + 2.5f, px + sf - 1.5f, py + sf - 2.5f, 3f, 3f, p)
                p.color = Color.argb(140, 255, 179, 71)
                c.drawRect(px + 4f, py + 3.5f, px + 5.5f, py + sf - 3.5f, p)
            }
            "eng" -> {
                // Dark thruster cowling & bell nozzle at rear
                p.style = Paint.Style.FILL
                p.color = COL_NOZZLE_DARK
                c.drawRect(px + 2f, py + 2f, px + sf, py + sf - 2f, p)
                p.color = COL_NOZZLE_RIM
                c.drawRect(px, py + 3f, px + 4f, py + sf - 3f, p)
                p.color = COL_C96F2F
                c.drawRect(px, py + 5f, px + 2f, py + sf - 5f, p)
            }
            "clamp" -> {
                // Heavy nose grappling pincers
                p.style = Paint.Style.FILL
                p.color = COL_NOZZLE_RIM
                c.drawRect(px, py + 2f, px + sf * 0.55f, py + sf - 2f, p)
                p.color = COL_FFD166
                c.drawRect(px + sf * 0.45f, py + 1.5f, px + sf, py + 4.5f, p)
                c.drawRect(px + sf * 0.45f, py + sf - 4.5f, px + sf, py + sf - 1.5f, p)
            }
            "rcs" -> {
                // Compact RCS thruster blister
                p.style = Paint.Style.FILL
                p.color = sh(hullBase, 0.85)
                c.drawRoundRect(px + 3f, py + 3f, px + sf - 3f, py + sf - 3f, 2f, 2f, p)
                p.color = COL_NOZZLE_RIM
                c.drawRect(px + sf * 0.5f - 1.5f, py + 1f, px + sf * 0.5f + 1.5f, py + sf - 1f, p)
            }
            "gear" -> {
                // Stowed landing skid housing
                p.style = Paint.Style.FILL
                p.color = COL_NOZZLE_DARK
                c.drawRect(px + 3f, py + 4f, px + sf - 3f, py + sf - 4f, p)
                p.color = COL_NOZZLE_RIM
                c.drawRect(px + 4f, py + 6f, px + sf - 4f, py + sf - 6f, p)
            }
            "scan" -> {
                // Circular dorsal sensor radome
                p.style = Paint.Style.FILL
                p.color = COL_NOZZLE_RIM
                c.drawCircle(px + sf * 0.5f, py + sf * 0.5f, 5.2f, p)
                p.color = COL_53D9FF
                c.drawCircle(px + sf * 0.5f, py + sf * 0.5f, 2.2f, p)
            }
            "mine" -> {
                // Cutter emitter turret housing
                p.style = Paint.Style.FILL
                p.color = COL_NOZZLE_RIM
                c.drawRect(px + 3f, py + 4f, px + sf - 2f, py + sf - 4f, p)
                p.color = COL_53D9FF
                c.drawRect(px + sf - 4f, py + 5.5f, px + sf - 1f, py + sf - 5.5f, p)
            }
            "gun" -> {
                p.style = Paint.Style.FILL
                p.color = COL_NOZZLE_DARK
                c.drawCircle(px + sf * 0.5f, py + sf * 0.5f, 4.5f, p)
                p.color = COL_C96F2F
                c.drawRect(px + sf * 0.5f, py + sf * 0.5f - 1.5f, px + sf, py + sf * 0.5f + 1.5f, p)
            }
        }
        // Combat scorch weathering on damaged intact modules
        val dmg = 1.0 - (m.hp / m.max).coerceIn(0.0, 1.0)
        if (dmg > 0.25) {
            p.style = Paint.Style.FILL
            p.color = Color.argb((dmg * 110).toInt().coerceIn(0, 140), 12, 10, 10)
            c.drawCircle(px + sf * 0.45f, py + sf * 0.55f, (dmg * 5.0).toFloat(), p)
        }
        if (m.flash > 0.0) {
            p.style = Paint.Style.FILL
            p.color = COL_FLASH_WHITE
            c.drawRect(px, py, px + sf, py + sf, p)
        }
    }
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
